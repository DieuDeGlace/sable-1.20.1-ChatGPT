package dev.ryanhcode.sable.validation;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Real M4 assembly/split fixture. All transfers use the production assembly
 * helper and heat-map splitter; the validator only supplies deterministic
 * world input and checks conservation before and after a restart.
 */
final class SableForgeM4AssemblyValidation {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PHASE_CREATE = "assembly-create";
    private static final String PHASE_RELOAD = "assembly-reload";
    private static final String SENTINEL_KEY = "sable_m4_assembly";
    private static final String SENTINEL_VALUE = "split-conservation-v1";
    private static final String SOURCE_LEFT_KEY = "source_left";
    private static final String SOURCE_BRIDGE_KEY = "source_bridge";
    private static final String SOURCE_CHEST_KEY = "source_chest";
    private static final BlockPos LOCAL_LEFT = BlockPos.ZERO;
    private static final BlockPos LOCAL_BRIDGE = new BlockPos(1, 0, 0);
    private static final BlockPos LOCAL_CHEST = new BlockPos(2, 0, 0);
    private static ValidationState state;
    private static ReloadValidationState reloadState;

    private SableForgeM4AssemblyValidation() {
    }

    static void onServerStarted(final ServerStartedEvent event, final String phase) {
        final ServerLevel level = requireOverworld(event.getServer());
        final ServerSubLevelContainer container = requireContainer(level);
        container.physicsSystem().setPaused(true);

        if (PHASE_CREATE.equals(phase)) {
            beginCreate(event.getServer(), level, container);
        } else if (PHASE_RELOAD.equals(phase)) {
            if (reloadState != null) {
                throw new IllegalStateException("M4 assembly reload validation is already active");
            }
            reloadState = new ReloadValidationState(event.getServer(), level, container);
        } else {
            throw new IllegalArgumentException("Unknown assembly validation phase: " + phase);
        }
    }

    static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        final String phase = System.getProperty("sable.m4ValidationPhase", "");
        if (PHASE_RELOAD.equals(phase)) {
            final ReloadValidationState reload = reloadState;
            if (reload != null && reload.server == event.getServer() && ++reload.ticks >= 2) {
                reloadState = null;
                verifyReload(reload.server, reload.level, reload.container);
            }
            return;
        }
        if (!PHASE_CREATE.equals(phase)) {
            return;
        }
        final ValidationState current = state;
        if (current == null || current.server != event.getServer()) {
            return;
        }

        current.ticks++;
        if (!current.bridgeRemoved && current.ticks >= 2) {
            verifyAssembledState(current);
            if (!current.root.getPlot().getEmbeddedLevelAccessor().setBlock(
                    LOCAL_BRIDGE, Blocks.AIR.defaultBlockState(), 3)) {
                throw new IllegalStateException("M4 assembly validation could not remove the split bridge");
            }
            current.bridgeRemoved = true;
            LOGGER.info("Sable M4 ASSEMBLY SUCCESS VERIFIED; BRIDGE REMOVED FOR PRODUCTION SPLIT "
                            + "(uuid {}, source {}, plot {}, cross-source-chunk {}->{})",
                    current.root.getUniqueId(), current.sourceBlocks, current.root.getPlot().plotPos,
                    current.sourceBlocks.get(0).getX() >> 4, current.sourceBlocks.get(1).getX() >> 4);
            return;
        }

        if (current.bridgeRemoved) {
            final List<ServerSubLevel> fixtureSubLevels = fixtureSubLevels(current);
            if (fixtureSubLevels.size() == 2) {
                verifyConservation(current.level, fixtureSubLevels, current.sourceBlocks);
                persistFixtureIdentity(current.container, current.root, fixtureSubLevels, current.sourceBlocks);
                LOGGER.info("Sable M4 PRODUCTION HEATMAP SPLIT CONSERVATION VERIFIED "
                                + "(root {}, child {}, sublevels 2, solids 2, stone 1, chest 1, diamonds 7)",
                        current.root.getUniqueId(), fixtureSubLevels.stream()
                                .filter(subLevel -> subLevel != current.root)
                                .findFirst().orElseThrow().getUniqueId());
                saveAndStop(current.server, "ASSEMBLY-CREATE");
                state = null;
                return;
            }
        }

        if (current.ticks > 200) {
            throw new IllegalStateException("M4 production split did not complete within 200 server ticks; "
                    + "loaded fixture sublevels=" + fixtureSubLevels(current).size());
        }
    }

    private static void beginCreate(final MinecraftServer server, final ServerLevel level,
                                    final ServerSubLevelContainer container) {
        if (state != null) {
            throw new IllegalStateException("M4 assembly validation is already active");
        }
        final int initialCount = container.getLoadedCount();
        final Set<UUID> existingIds = new HashSet<>();
        container.getAllSubLevels().forEach(subLevel -> existingIds.add(subLevel.getUniqueId()));

        final List<BlockPos> sourceBlocks = findIsolatedCrossChunkSource(level);
        final BlockPos left = sourceBlocks.get(0);
        final BlockPos bridge = sourceBlocks.get(1);
        final BlockPos chestPos = sourceBlocks.get(2);
        final BlockPos emptyProbe = left.above(4);

        final SubLevelAssemblyHelper.GatherResult emptyResult = SubLevelAssemblyHelper.gatherConnectedBlocks(
                emptyProbe, level, 16, null);
        require(emptyResult.assemblyState() == SubLevelAssemblyHelper.GatherResult.State.NO_BLOCKS,
                "M4 empty assembly refusal returned " + emptyResult.assemblyState());

        require(level.setBlock(left, Blocks.STONE.defaultBlockState(), 3),
                "M4 source stone could not be placed");
        require(level.setBlock(bridge, Blocks.OBSIDIAN.defaultBlockState(), 3),
                "M4 source bridge could not be placed");
        require(level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3),
                "M4 source chest could not be placed");
        require(level.getBlockEntity(chestPos) instanceof ChestBlockEntity,
                "M4 source ChestBlockEntity was not created");
        final ChestBlockEntity sourceChest = (ChestBlockEntity) level.getBlockEntity(chestPos);
        sourceChest.setItem(4, new ItemStack(Items.DIAMOND, 7));
        sourceChest.setChanged();

        final SubLevelAssemblyHelper.GatherResult limitResult = SubLevelAssemblyHelper.gatherConnectedBlocks(
                left, level, 2, null);
        require(limitResult.assemblyState() == SubLevelAssemblyHelper.GatherResult.State.TOO_MANY_BLOCKS,
                "M4 maximum-size assembly refusal returned " + limitResult.assemblyState());
        require(container.getLoadedCount() == initialCount
                        && level.getBlockState(left).is(Blocks.STONE)
                        && level.getBlockState(bridge).is(Blocks.OBSIDIAN)
                        && level.getBlockState(chestPos).is(Blocks.CHEST)
                        && sourceChest.getItem(4).is(Items.DIAMOND)
                        && sourceChest.getItem(4).getCount() == 7,
                "M4 refused gather mutated world blocks, inventory, or sublevel count");
        LOGGER.info("Sable M4 ASSEMBLY REFUSAL SAFETY VERIFIED (empty {}, limit {}, world unchanged true)",
                emptyResult.assemblyState(), limitResult.assemblyState());

        final String command = "sable assemble connected %d %d %d 16".formatted(
                left.getX(), left.getY(), left.getZ());
        final int commandResult = server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withLevel(level).withPermission(4), command);
        require(commandResult == 1, "M4 registered assemble command returned " + commandResult);
        require(!level.captureBlockSnapshots, "M4 assembly left Forge block snapshot capture enabled");
        require(container.getLoadedCount() == initialCount + 1,
                "M4 assembly did not create exactly one sublevel");
        final ServerSubLevel root = container.getAllSubLevels().stream()
                .filter(subLevel -> !existingIds.contains(subLevel.getUniqueId()))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "M4 assemble command created no discoverable sublevel"));
        final ChunkPos localChunk = root.getPlot().toLocal(root.getPlot().getCenterChunk());
        SableForgeCapabilityValidation.set(root.getPlot().getChunk(localChunk),
                SableForgeCapabilityValidation.SENTINEL);
        require(container.addForceLoadTicket(root, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE),
                "M4 assembly root force-load ticket was not added");

        state = new ValidationState(server, level, container, root, existingIds, sourceBlocks);
        verifyAssembledState(state);
    }

    private static void verifyAssembledState(final ValidationState current) {
        requireSourceAir(current.level, current.sourceBlocks);
        final LevelAccessor plotLevel = current.root.getPlot().getEmbeddedLevelAccessor();
        require(plotLevel.getBlockState(LOCAL_LEFT).is(Blocks.STONE)
                        && plotLevel.getBlockState(LOCAL_BRIDGE).is(Blocks.OBSIDIAN)
                        && plotLevel.getBlockState(LOCAL_CHEST).is(Blocks.CHEST),
                "M4 assembled block states do not match their source order");
        require(plotLevel.getBlockEntity(LOCAL_CHEST) instanceof ChestBlockEntity,
                "M4 assembled ChestBlockEntity was not transferred");
        final ChestBlockEntity chest = (ChestBlockEntity) plotLevel.getBlockEntity(LOCAL_CHEST);
        require(chest.getItem(4).is(Items.DIAMOND) && chest.getItem(4).getCount() == 7,
                "M4 assembled chest inventory was not transferred exactly once");
    }

    private static void verifyReload(final MinecraftServer server, final ServerLevel level,
                                     final ServerSubLevelContainer container) {
        final List<ServerSubLevel> fixtureSubLevels = container.getAllSubLevels().stream()
                .filter(SableForgeM4AssemblyValidation::hasSentinel)
                .toList();
        require(fixtureSubLevels.size() == 2,
                "M4 split reload expected two tagged sublevels, found " + fixtureSubLevels.size());
        final CompoundTag tag = fixtureSubLevels.get(0).getUserDataTag();
        require(tag != null, "M4 split reload source coordinates are missing");
        final List<BlockPos> sourceBlocks = List.of(
                BlockPos.of(tag.getLong(SOURCE_LEFT_KEY)),
                BlockPos.of(tag.getLong(SOURCE_BRIDGE_KEY)),
                BlockPos.of(tag.getLong(SOURCE_CHEST_KEY)));
        verifyConservation(level, fixtureSubLevels, sourceBlocks);
        for (final ServerSubLevel subLevel : fixtureSubLevels) {
            require(subLevel.getLastSerializationPointer() != null,
                    "M4 split reload storage pointer is missing for " + subLevel.getUniqueId());
        }
        final long restoredCapabilityCount = fixtureSubLevels.stream()
                .flatMap(subLevel -> subLevel.getPlot().getLoadedChunks().stream())
                .filter(holder -> SableForgeCapabilityValidation.get(holder.getChunk())
                        == SableForgeCapabilityValidation.SENTINEL)
                .count();
        require(restoredCapabilityCount == 1,
                "M4 split reload expected one non-empty Forge chunk capability sentinel, found "
                        + restoredCapabilityCount);
        final long directTicketCount = fixtureSubLevels.stream()
                .filter(subLevel -> container.getAllTickets().containsKey(subLevel.getUniqueId())
                        && container.collectForceLoadTickets().containsKey(subLevel))
                .count();
        require(directTicketCount == 1,
                "M4 split dependency chain expected one direct ticket, found " + directTicketCount);
        LOGGER.info("Sable M4 ASSEMBLY/SPLIT SAVE-RESTART-RELOAD CONSERVATION VERIFIED "
                        + "(sublevels 2, solids 2, stone 1, chest 1, diamonds 7, "
                        + "pointers active, one direct ticket restored two saved dependencies, "
                        + "non-empty ForgeCaps round trip 1)");
        saveAndStop(server, "ASSEMBLY-RELOAD");
    }

    private static void persistFixtureIdentity(final ServerSubLevelContainer container,
                                               final ServerSubLevel ticketRoot,
                                               final List<ServerSubLevel> fixtureSubLevels,
                                               final List<BlockPos> sourceBlocks) {
        for (final ServerSubLevel subLevel : fixtureSubLevels) {
            final CompoundTag tag = new CompoundTag();
            tag.putString(SENTINEL_KEY, SENTINEL_VALUE);
            tag.putLong(SOURCE_LEFT_KEY, sourceBlocks.get(0).asLong());
            tag.putLong(SOURCE_BRIDGE_KEY, sourceBlocks.get(1).asLong());
            tag.putLong(SOURCE_CHEST_KEY, sourceBlocks.get(2).asLong());
            subLevel.setUserDataTag(tag);
        }
        require(container.getAllTickets().containsKey(ticketRoot.getUniqueId())
                        && container.collectForceLoadTickets().containsKey(ticketRoot),
                "M4 split root direct ticket was lost");
        require(container.collectForceLoadedSubLevels().containsAll(fixtureSubLevels),
                "M4 split root ticket did not cover its complete dependency chain");
    }

    private static List<ServerSubLevel> fixtureSubLevels(final ValidationState current) {
        return current.container.getAllSubLevels().stream()
                .filter(subLevel -> !current.existingIds.contains(subLevel.getUniqueId()))
                .toList();
    }

    private static void verifyConservation(final ServerLevel level,
                                           final List<ServerSubLevel> fixtureSubLevels,
                                           final List<BlockPos> sourceBlocks) {
        requireSourceAir(level, sourceBlocks);
        require(fixtureSubLevels.size() == 2, "M4 split conservation requires exactly two sublevels");
        int solids = 0;
        int stones = 0;
        int chests = 0;
        int chestBlockEntities = 0;
        int diamonds = 0;
        for (final ServerSubLevel subLevel : fixtureSubLevels) {
            int subLevelSolids = 0;
            final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
            require(bounds != BoundingBox3i.EMPTY && bounds.volume() > 0,
                    "M4 split produced an empty sublevel " + subLevel.getUniqueId());
            for (final BlockPos pos : BlockPos.betweenClosed(
                    bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
                if (level.getBlockState(pos).isAir()) {
                    continue;
                }
                solids++;
                subLevelSolids++;
                if (level.getBlockState(pos).is(Blocks.STONE)) {
                    stones++;
                } else if (level.getBlockState(pos).is(Blocks.CHEST)) {
                    chests++;
                    require(level.getBlockEntity(pos) instanceof ChestBlockEntity,
                            "M4 split chest lost its BlockEntity at " + pos);
                    chestBlockEntities++;
                    final ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(pos);
                    if (chest.getItem(4).is(Items.DIAMOND)) {
                        diamonds += chest.getItem(4).getCount();
                    }
                } else {
                    throw new IllegalStateException("M4 split retained unexpected block "
                            + level.getBlockState(pos) + " at " + pos);
                }
            }
            require(subLevelSolids == 1,
                    "M4 split fragment contains " + subLevelSolids + " solids instead of one");
        }
        require(solids == 2 && stones == 1 && chests == 1
                        && chestBlockEntities == 1 && diamonds == 7,
                "M4 split conservation failed: solids=" + solids + ", stones=" + stones
                        + ", chests=" + chests + ", blockEntities=" + chestBlockEntities
                        + ", diamonds=" + diamonds);
    }

    private static List<BlockPos> findIsolatedCrossChunkSource(final ServerLevel level) {
        final BlockPos spawn = level.getSharedSpawnPos();
        for (int chunkOffset = 2; chunkOffset <= 12; chunkOffset++) {
            final int boundaryX = (Math.floorDiv(spawn.getX(), 16) + chunkOffset) * 16 - 1;
            final int z = spawn.getZ() + chunkOffset * 3;
            for (int y = level.getMaxBuildHeight() - 16;
                 y >= Math.max(level.getMinBuildHeight() + 16, spawn.getY() + 32); y -= 8) {
                final BlockPos left = new BlockPos(boundaryX, y, z);
                level.getChunkAt(left);
                level.getChunkAt(left.offset(2, 0, 0));
                boolean isolated = true;
                for (final BlockPos candidate : BlockPos.betweenClosed(
                        left.offset(-1, -1, -1), left.offset(3, 5, 1))) {
                    if (!level.getBlockState(candidate).isAir()) {
                        isolated = false;
                        break;
                    }
                }
                if (isolated) {
                    return List.of(left, left.east(), left.east(2));
                }
            }
        }
        throw new IllegalStateException("M4 could not find an inspected isolated cross-chunk source volume");
    }

    private static void requireSourceAir(final ServerLevel level, final List<BlockPos> sourceBlocks) {
        for (final BlockPos source : sourceBlocks) {
            require(level.getBlockState(source).isAir(),
                    "M4 source block was duplicated or not removed at " + source + ": "
                            + level.getBlockState(source));
            require(level.getBlockEntity(source) == null,
                    "M4 source BlockEntity was duplicated or not removed at " + source);
        }
    }

    private static boolean hasSentinel(final ServerSubLevel subLevel) {
        final CompoundTag tag = subLevel.getUserDataTag();
        return tag != null && SENTINEL_VALUE.equals(tag.getString(SENTINEL_KEY));
    }

    private static ServerLevel requireOverworld(final MinecraftServer server) {
        final ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) {
            throw new IllegalStateException("M4 assembly validation overworld is unavailable");
        }
        return level;
    }

    private static ServerSubLevelContainer requireContainer(final ServerLevel level) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IllegalStateException("M4 assembly validation container is unavailable");
        }
        return container;
    }

    private static void saveAndStop(final MinecraftServer server, final String marker) {
        if (!server.saveEverything(false, true, true)) {
            throw new IllegalStateException("M4 " + marker + " saveEverything returned false");
        }
        LOGGER.info("Sable M4 {} PHASE DISK FLUSH COMPLETED", marker);
        server.halt(false);
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class ValidationState {
        private final MinecraftServer server;
        private final ServerLevel level;
        private final ServerSubLevelContainer container;
        private final ServerSubLevel root;
        private final Set<UUID> existingIds;
        private final List<BlockPos> sourceBlocks;
        private int ticks;
        private boolean bridgeRemoved;

        private ValidationState(final MinecraftServer server, final ServerLevel level,
                                final ServerSubLevelContainer container, final ServerSubLevel root,
                                final Set<UUID> existingIds, final List<BlockPos> sourceBlocks) {
            this.server = server;
            this.level = level;
            this.container = container;
            this.root = root;
            this.existingIds = existingIds;
            this.sourceBlocks = new ArrayList<>(sourceBlocks);
        }
    }

    private static final class ReloadValidationState {
        private final MinecraftServer server;
        private final ServerLevel level;
        private final ServerSubLevelContainer container;
        private int ticks;

        private ReloadValidationState(final MinecraftServer server, final ServerLevel level,
                                      final ServerSubLevelContainer container) {
            this.server = server;
            this.level = level;
            this.container = container;
        }
    }
}
