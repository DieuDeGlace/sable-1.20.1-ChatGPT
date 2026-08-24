package dev.ryanhcode.sable.validation;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.util.SableNBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.joml.Vector3d;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Explicit two-process M4 fixture. The phase is supplied only by the dev run;
 * all data under test travels through Sable's production region, SavedData,
 * ticket, plot and block-entity serializers.
 */
final class SableForgeM4Validation {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID FIXTURE_ID = UUID.nameUUIDFromBytes(
            "sable-forge-1.20.1-m4-persistence-v1".getBytes(StandardCharsets.UTF_8));
    private static final String FIXTURE_NAME = "M4 persistence vertical slice";
    private static final String SENTINEL = "sable-m4-storage-v1";
    private static final BlockPos STONE_POS = BlockPos.ZERO;
    private static final BlockPos CHEST_POS = new BlockPos(1, 0, 0);
    private static final Vector3d LINEAR_VELOCITY = new Vector3d(1.25D, 0.5D, -0.25D);
    private static final Vector3d ANGULAR_VELOCITY = new Vector3d(0.0D, 0.2D, 0.0D);

    private SableForgeM4Validation() {
    }

    static void onServerStarted(final ServerStartedEvent event) {
        final String phase = System.getProperty("sable.m4ValidationPhase", "");
        if (phase.isEmpty()) {
            return;
        }

        final ServerLevel level = event.getServer().getLevel(Level.OVERWORLD);
        if (level == null) {
            throw new IllegalStateException("M4 validation overworld is unavailable");
        }
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IllegalStateException("M4 validation server container is unavailable");
        }

        switch (phase) {
            case "create" -> createFixture(container);
            case "reload" -> verifyReloadedFixture(container);
            case "assembly-create", "assembly-reload" -> {
                SableForgeM4AssemblyValidation.onServerStarted(event, phase);
                return;
            }
            default -> throw new IllegalArgumentException("Unknown Sable M4 validation phase: " + phase);
        }

        // Execute the real Minecraft save path (including the production
        // ServerLevel mixin above), then end this validation process cleanly.
        if (!event.getServer().saveEverything(false, true, true)) {
            throw new IllegalStateException("M4 validation Minecraft saveEverything returned false");
        }
        LOGGER.info("Sable M4 {} PHASE DISK FLUSH COMPLETED", phase.toUpperCase());
        event.getServer().halt(false);
    }

    private static void createFixture(final ServerSubLevelContainer container) {
        if (container.getSubLevel(FIXTURE_ID) != null || container.getHoldingChunkMap().getHoldingSubLevel(FIXTURE_ID) != null
                || container.getAllTickets().containsKey(FIXTURE_ID)) {
            throw new IllegalStateException("M4 validation fixture already exists: " + FIXTURE_ID);
        }

        final Pose3d pose = new Pose3d();
        pose.position().set(12.25D, 96.5D, -6.75D);
        pose.orientation().rotationXYZ(0.125D, -0.25D, 0.375D);
        final ServerSubLevel subLevel = (ServerSubLevel) container.allocateSubLevel(FIXTURE_ID, 0, 0, pose);
        subLevel.setName(FIXTURE_NAME);
        subLevel.getPlot().newEmptyChunk(subLevel.getPlot().getCenterChunk());

        if (!subLevel.getPlot().getEmbeddedLevelAccessor().setBlock(
                STONE_POS, Blocks.STONE.defaultBlockState(), 3)) {
            throw new IllegalStateException("M4 validation could not place its stone block");
        }
        if (!subLevel.getPlot().getEmbeddedLevelAccessor().setBlock(
                CHEST_POS, Blocks.CHEST.defaultBlockState(), 3)) {
            throw new IllegalStateException("M4 validation could not place its chest block");
        }
        if (!(subLevel.getPlot().getEmbeddedLevelAccessor().getBlockEntity(CHEST_POS)
                instanceof final ChestBlockEntity chest)) {
            throw new IllegalStateException("M4 validation chest block entity was not created");
        }
        chest.setCustomName(Component.literal("M4 persisted chest"));
        chest.setItem(4, new ItemStack(Items.DIAMOND, 7));
        chest.setChanged();

        // Normal gameplay reaches this through ServerSubLevel.tick before an
        // autosave. The startup fixture saves in the same tick it is created,
        // so explicitly perform that production finalization step.
        subLevel.updateBoundingBox();
        subLevel.forceUpdateGlobalBounds();
        subLevel.updateLastPose();
        subLevel.lastNetworkedPose().set(subLevel.logicalPose());
        subLevel.setLastNetworkedStopped(true);
        container.physicsSystem().setPaused(true);
        container.physicsSystem().getPhysicsHandle(subLevel)
                .addLinearAndAngularVelocity(LINEAR_VELOCITY, ANGULAR_VELOCITY);

        final SubLevelData preSave = SubLevelSerializer.toData(subLevel, List.of());
        final CompoundTag expected = new CompoundTag();
        expected.putString("sentinel", SENTINEL);
        expected.put("pose", preSave.fullTag().getCompound("pose").copy());
        expected.put("world_bounds", preSave.fullTag().getCompound("world_bounds").copy());
        expected.put("plot", preSave.fullTag().getCompound("plot").copy());
        expected.put("linear_velocity", preSave.fullTag().getCompound("linear_velocity").copy());
        expected.put("angular_velocity", preSave.fullTag().getCompound("angular_velocity").copy());
        subLevel.setUserDataTag(expected);

        if (!container.addForceLoadTicket(subLevel, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)) {
            throw new IllegalStateException("M4 validation force-load ticket was not added");
        }
        LOGGER.info("Sable M4 CREATE FIXTURE READY (uuid {}, plot {}, bounds {}, pose {}, linear {}, angular {}, "
                        + "stone {}, chest item {}x{})",
                FIXTURE_ID, subLevel.getPlot().plotPos, subLevel.getPlot().getBoundingBox(),
                preSave.fullTag().getCompound("pose"), LINEAR_VELOCITY, ANGULAR_VELOCITY,
                subLevel.getPlot().getEmbeddedLevelAccessor().getBlockState(STONE_POS),
                chest.getItem(4).getItem(), chest.getItem(4).getCount());
    }

    private static void verifyReloadedFixture(final ServerSubLevelContainer container) {
        final ServerSubLevel subLevel = (ServerSubLevel) container.getSubLevel(FIXTURE_ID);
        if (subLevel == null) {
            throw new IllegalStateException("M4 persisted fixture was not force-loaded after restart");
        }
        container.physicsSystem().setPaused(true);

        final CompoundTag expected = subLevel.getUserDataTag();
        require(expected != null && SENTINEL.equals(expected.getString("sentinel")),
                "M4 user data sentinel was not preserved");
        require(FIXTURE_NAME.equals(subLevel.getName()), "M4 display name was not preserved");
        require(Blocks.STONE.defaultBlockState().equals(
                        subLevel.getPlot().getEmbeddedLevelAccessor().getBlockState(STONE_POS)),
                "M4 stone block was not preserved");
        require(Blocks.CHEST.defaultBlockState().equals(
                        subLevel.getPlot().getEmbeddedLevelAccessor().getBlockState(CHEST_POS)),
                "M4 chest block was not preserved");
        require(subLevel.getPlot().getEmbeddedLevelAccessor().getBlockEntity(CHEST_POS)
                        instanceof ChestBlockEntity,
                "M4 chest block entity was not restored");
        final ChestBlockEntity chest = (ChestBlockEntity) subLevel.getPlot()
                .getEmbeddedLevelAccessor().getBlockEntity(CHEST_POS);
        require(chest != null && chest.hasCustomName()
                        && Component.literal("M4 persisted chest").equals(chest.getCustomName()),
                "M4 chest custom name was not preserved");
        require(chest != null && chest.getItem(4).is(Items.DIAMOND) && chest.getItem(4).getCount() == 7,
                "M4 chest inventory was not preserved");

        final CompoundTag reserialized = SubLevelSerializer.toData(subLevel, List.of()).fullTag();
        require(expected.getCompound("pose").equals(SableNBTUtils.writePose3d(subLevel.logicalPose())),
                "M4 exact pose was not restored");
        require(expected.getCompound("world_bounds").equals(reserialized.getCompound("world_bounds")),
                "M4 exact world bounds were not restored");
        verifyStructuralPlotPayload(
                expected.getCompound("plot"), reserialized.getCompound("plot"));

        final Vector3d expectedLinear = SableNBTUtils.readVector3d(expected.getCompound("linear_velocity"))
                .mul(SableConfig.VELOCITY_RETAINED_ON_LOAD.get());
        final Vector3d expectedAngular = SableNBTUtils.readVector3d(expected.getCompound("angular_velocity"))
                .mul(SableConfig.VELOCITY_RETAINED_ON_LOAD.get());
        final Vector3d actualLinear = container.physicsSystem().getPhysicsHandle(subLevel)
                .getLinearVelocity(new Vector3d());
        final Vector3d actualAngular = container.physicsSystem().getPhysicsHandle(subLevel)
                .getAngularVelocity(new Vector3d());
        final double linearError = actualLinear.distance(expectedLinear);
        final double angularError = actualAngular.distance(expectedAngular);
        require(linearError < 1.0E-6D,
                "M4 linear velocity was not restored: expected " + expectedLinear + ", actual "
                        + actualLinear + ", error " + linearError);
        require(angularError < 1.0E-6D,
                "M4 angular velocity was not restored: expected " + expectedAngular + ", actual "
                        + actualAngular + ", error " + angularError);
        require(subLevel.getLastSerializationPointer() != null,
                "M4 storage pointer was not restored");
        require(container.getAllTickets().containsKey(FIXTURE_ID)
                        && container.collectForceLoadTickets().containsKey(subLevel),
                "M4 force-load ticket was not restored and activated");

        LOGGER.info("Sable M4 SAVE/RESTART/RELOAD VERTICAL SLICE VERIFIED (uuid {}, pointer {}, plot {}, "
                        + "bounds {}, pose {}, linear {} (error {}), angular {} (error {}), "
                        + "chest item {}x{}, ticket active true)",
                FIXTURE_ID, subLevel.getLastSerializationPointer(), subLevel.getPlot().plotPos,
                subLevel.getPlot().getBoundingBox(), subLevel.logicalPose(), actualLinear, linearError,
                actualAngular, angularError,
                chest.getItem(4).getItem(), chest.getItem(4).getCount());
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void verifyStructuralPlotPayload(final CompoundTag expected, final CompoundTag actual) {
        for (final String key : List.of("plot_x", "plot_z", "log_size", "biome", "data_version")) {
            require(java.util.Objects.equals(expected.get(key), actual.get(key)),
                    "M4 plot metadata was not restored at plot." + key + ": "
                            + expected.get(key) + " != " + actual.get(key));
        }

        final CompoundTag expectedChunks = expected.getCompound("chunks");
        final CompoundTag actualChunks = actual.getCompound("chunks");
        require(expectedChunks.getAllKeys().equals(actualChunks.getAllKeys()),
                "M4 plot chunk set was not restored: " + expectedChunks.getAllKeys()
                        + " != " + actualChunks.getAllKeys());
        for (final String chunkKey : expectedChunks.getAllKeys()) {
            final CompoundTag expectedChunk = expectedChunks.getCompound(chunkKey);
            final CompoundTag actualChunk = actualChunks.getCompound(chunkKey);
            final CompoundTag expectedSections = expectedChunk.getCompound("sections");
            final CompoundTag actualSections = actualChunk.getCompound("sections");
            require(expectedSections.getAllKeys().equals(actualSections.getAllKeys()),
                    "M4 plot section set was not restored in chunk " + chunkKey);
            for (final String sectionKey : expectedSections.getAllKeys()) {
                final Tag expectedStates = expectedSections.getCompound(sectionKey).get("block_states");
                final Tag actualStates = actualSections.getCompound(sectionKey).get("block_states");
                require(java.util.Objects.equals(expectedStates, actualStates),
                        "M4 block-state palette was not restored in chunk/section "
                                + chunkKey + "/" + sectionKey);
            }
            for (final String key : List.of("block_entities", "block_ticks", "fluid_ticks")) {
                final String difference = firstDifference(
                        expectedChunk.get(key), actualChunk.get(key),
                        "plot.chunks." + chunkKey + "." + key);
                require(difference == null, "M4 structural plot payload was not restored: " + difference);
            }
        }
    }

    private static String firstDifference(final Tag expected, final Tag actual, final String path) {
        if (expected == null || actual == null) {
            return expected == actual ? null : path + " null mismatch: " + expected + " != " + actual;
        }
        if (expected.getId() != actual.getId()) {
            return path + " type mismatch: " + expected.getId() + " != " + actual.getId();
        }
        if (expected instanceof final CompoundTag expectedCompound
                && actual instanceof final CompoundTag actualCompound) {
            if (!expectedCompound.getAllKeys().equals(actualCompound.getAllKeys())) {
                return path + " key mismatch: " + expectedCompound.getAllKeys()
                        + " != " + actualCompound.getAllKeys();
            }
            for (final String key : expectedCompound.getAllKeys()) {
                final String difference = firstDifference(
                        expectedCompound.get(key), actualCompound.get(key), path + "." + key);
                if (difference != null) {
                    return difference;
                }
            }
            return null;
        }
        if (expected instanceof final ListTag expectedList && actual instanceof final ListTag actualList) {
            if (expectedList.size() != actualList.size()) {
                return path + " list size mismatch: " + expectedList.size() + " != " + actualList.size();
            }
            for (int i = 0; i < expectedList.size(); i++) {
                final String difference = firstDifference(
                        expectedList.get(i), actualList.get(i), path + "[" + i + "]");
                if (difference != null) {
                    return difference;
                }
            }
            return null;
        }
        return expected.equals(actual) ? null : path + " value mismatch: " + expected + " != " + actual;
    }
}
