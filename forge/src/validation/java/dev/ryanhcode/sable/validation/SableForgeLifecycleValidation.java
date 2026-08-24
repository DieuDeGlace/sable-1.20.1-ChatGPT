package dev.ryanhcode.sable.validation;

import com.simibubi.create.AllBlocks;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.SableServerConfig;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.SableClientConfig;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.command.SableCommand;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineProvider;
import dev.ryanhcode.sable.index.SableAttributes;
import dev.ryanhcode.sable.index.SableTags;
import dev.ryanhcode.sable.network.codec.StreamCodec;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundDimensionPhysicsPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFloatingBlockMaterialPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundPhysicsPropertyPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFreezePlayerPacket;
import dev.ryanhcode.sable.network.packets.tcp.ServerboundPunchSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ServerboundGizmoMoveSubLevelPacket;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotInfoDualPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundChangeBoundsSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundChangeSubLevelNamePacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFinalizeSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundEnterGizmoPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundRecentlySplitSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundSableUDPActivationPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStartTrackingSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStopMovingSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStopTrackingSubLevelPacket;
import dev.ryanhcode.sable.network.tcp.SablePacketManager;
import dev.ryanhcode.sable.network.tcp.SableTCPPackets;
import dev.ryanhcode.sable.mixinterface.block_properties.BlockStateExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.platform.SableAssemblyPlatform;
import dev.ryanhcode.sable.platform.SableChunkEventPlatform;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.platform.SableEventPublishPlatform;
import dev.ryanhcode.sable.platform.SableLoaderPlatform;
import dev.ryanhcode.sable.platform.SablePlatform;
import dev.ryanhcode.sable.platform.SablePlotPlatform;
import dev.ryanhcode.sable.platform.SableSubLevelRenderPlatform;
import dev.ryanhcode.sable.physics.config.block_properties.BlockStateConditionSet;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinition;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinitionLoader;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyTypes;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.physics.config.FloatingBlockMaterialDataHandler;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockMaterial;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Quaterniond;


final class SableForgeLifecycleValidation {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, M2ValidationState> M2_VALIDATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, AtomicInteger> M2_LOGIN_COUNTS = new ConcurrentHashMap<>();
    private static final Map<UUID, GizmoPermissionState> GIZMO_PERMISSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, WrongDirectionState> WRONG_DIRECTION_VALIDATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, GizmoEnterState> GIZMO_ENTER_VALIDATIONS = new ConcurrentHashMap<>();
    private static final Vector3d GIZMO_DESTINATION = new Vector3d(12.5D, 120.0D, 12.5D);

    static void register(final IEventBus modEventBus) {
        final SableForgeLifecycleValidation validation = new SableForgeLifecycleValidation();
        modEventBus.addListener(SableForgeCapabilityValidation::registerCapabilities);
        MinecraftForge.EVENT_BUS.addGenericListener(net.minecraft.world.level.chunk.LevelChunk.class,
                SableForgeCapabilityValidation::attachChunkCapability);
        MinecraftForge.EVENT_BUS.addListener(validation::runM2TrackingValidation);
        MinecraftForge.EVENT_BUS.addListener(validation::tickM2TrackingValidation);
        MinecraftForge.EVENT_BUS.addListener(validation::runWrongDirectionValidation);
        MinecraftForge.EVENT_BUS.addListener(validation::tickWrongDirectionValidation);
        MinecraftForge.EVENT_BUS.addListener(validation::finishWrongDirectionValidation);
        MinecraftForge.EVENT_BUS.addListener(validation::runGizmoEnterValidation);
        MinecraftForge.EVENT_BUS.addListener(validation::tickGizmoEnterValidation);
        MinecraftForge.EVENT_BUS.addListener(SableForgeM4Validation::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(SableForgeM4AssemblyValidation::onServerTick);
    }

    private void runM2TrackingValidation(final PlayerEvent.PlayerLoggedInEvent event) {
        if (!Boolean.getBoolean("sable.m2Validation")
                || !(event.getEntity() instanceof final net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        final M2ValidationState previous = M2_VALIDATIONS.remove(player.getUUID());
        if (previous != null && previous.container.getSubLevel(previous.subLevel.getUniqueId()) != null) {
            previous.container.removeSubLevel(previous.subLevel, SubLevelRemovalReason.REMOVED);
            if (previous.splitChild != null
                    && previous.container.getSubLevel(previous.splitChild.getUniqueId()) != null) {
                previous.container.removeSubLevel(previous.splitChild, SubLevelRemovalReason.REMOVED);
            }
            if (previous.displayChild != null
                    && previous.container.getSubLevel(previous.displayChild.getUniqueId()) != null) {
                previous.container.removeSubLevel(previous.displayChild, SubLevelRemovalReason.REMOVED);
            }
            if (previous.blockEntityChild != null
                    && previous.container.getSubLevel(previous.blockEntityChild.getUniqueId()) != null) {
                previous.container.removeSubLevel(previous.blockEntityChild, SubLevelRemovalReason.REMOVED);
            }
            previous.container.physicsSystem().setPaused(false);
        }

        final int login = M2_LOGIN_COUNTS.computeIfAbsent(player.getUUID(), ignored -> new AtomicInteger())
                .incrementAndGet();
        if (Boolean.getBoolean("sable.gizmoValidation")) {
            GIZMO_PERMISSIONS.computeIfAbsent(player.getUUID(), ignored -> {
                final boolean originallyOp = player.hasPermissions(1);
                if (originallyOp) {
                    player.server.getPlayerList().deop(player.getGameProfile());
                }
                LOGGER.info("Sable gizmo validation prepared an unauthorized client "
                                + "(player {}, originally op {})",
                        player.getUUID(), originallyOp);
                return new GizmoPermissionState(originallyOp);
            });
        }
        final net.minecraft.server.level.ServerLevel overworld = player.server.getLevel(
                net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("M2 validation overworld is unavailable");
        }
        if (Boolean.getBoolean("sable.m3Validation")) {
            overworld.setDayTime(6000L);
        }
        if (player.serverLevel() != overworld) {
            final BlockPos spawn = overworld.getSharedSpawnPos();
            if (!player.teleportTo(overworld, spawn.getX() + 0.5D, spawn.getY() + 2.0D,
                    spawn.getZ() + 0.5D, Set.of(), player.getYRot(), player.getXRot())) {
                throw new IllegalStateException("M2 validation could not normalize the player to the overworld");
            }
        }
        final M2ValidationState state = createM2ValidationState(player, login == 1);
        M2_VALIDATIONS.put(player.getUUID(), state);
        LOGGER.info("Sable M2 validation login {} started (world {}, world-change required {})",
                login, player.serverLevel().dimension().location(), state.worldChangeRequired);
    }

    private void runWrongDirectionValidation(final PlayerEvent.PlayerLoggedInEvent event) {
        final String mode = System.getProperty("sable.wrongDirectionValidation", "");
        if (mode.isEmpty()) {
            return;
        }
        if (!mode.equals("c2s") && !mode.equals("s2c")) {
            throw new IllegalStateException("Unknown wrong-direction validation mode: " + mode);
        }
        if (!(event.getEntity() instanceof final net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        WRONG_DIRECTION_VALIDATIONS.put(player.getUUID(), new WrongDirectionState(player, mode));
        LOGGER.info("Sable wrong-direction validation {} started (player {})", mode, player.getUUID());
    }

    private void runGizmoEnterValidation(final PlayerEvent.PlayerLoggedInEvent event) {
        if (!Boolean.getBoolean("sable.gizmoEnterValidation")
                || !(event.getEntity() instanceof final net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        GIZMO_ENTER_VALIDATIONS.put(player.getUUID(), new GizmoEnterState(player));
        LOGGER.info("Sable gizmo-enter validation started (player {})", player.getUUID());
    }

    private void tickGizmoEnterValidation(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (final GizmoEnterState state : GIZMO_ENTER_VALIDATIONS.values()) {
            if (!state.sent && ++state.ticks >= 60) {
                state.sent = true;
                SablePacketManager.player(state.player).sendPacket(new ClientboundEnterGizmoPacket());
                LOGGER.info("Sable gizmo-enter validation sent production clientbound discriminator 14 "
                        + "(player {})", state.player.getUUID());
            }
        }
    }

    private void tickWrongDirectionValidation(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (final WrongDirectionState state : WRONG_DIRECTION_VALIDATIONS.values()) {
            state.ticks++;
            if (state.mode.equals("s2c") && !state.packetSent && state.ticks >= 60) {
                state.packetSent = true;
                SablePacketManager.player(state.player).sendPacket(new ServerboundGizmoMoveSubLevelPacket(
                        UUID.fromString("12345678-1234-5678-9abc-def012345678"),
                        new Vector3d(1.25D, 2.5D, 3.75D)));
                LOGGER.info("Sable wrong-direction validation sent registered serverbound discriminator 16 "
                        + "over the server-to-client connection");
            }
        }
    }

    private void finishWrongDirectionValidation(final PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof final net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        final WrongDirectionState state = WRONG_DIRECTION_VALIDATIONS.remove(player.getUUID());
        if (state == null) {
            return;
        }
        if (!state.packetSent && state.mode.equals("s2c")) {
            throw new IllegalStateException("S2C wrong-direction validation disconnected before its packet send");
        }
        final Class<? extends dev.ryanhcode.sable.network.tcp.SableTCPPacket> handlerClass =
                state.mode.equals("c2s") ? ClientboundStopMovingSubLevelPacket.class
                        : ServerboundGizmoMoveSubLevelPacket.class;
        final int handled = SableForgeNetworkValidationTelemetry.handledCount(handlerClass);
        if (handled != state.handlerBaseline) {
            throw new IllegalStateException("Wrong-direction " + state.mode + " packet reached its handler; count "
                    + state.handlerBaseline + " -> " + handled);
        }
        LOGGER.info("Sable REAL FORGE {} WRONG-DIRECTION REJECTION VERIFIED "
                        + "(handler {} count {} -> {})",
                state.mode.toUpperCase(java.util.Locale.ROOT), handlerClass.getSimpleName(),
                state.handlerBaseline, handled);
    }

    private static M2ValidationState createM2ValidationState(
            final net.minecraft.server.level.ServerPlayer player, final boolean worldChangeRequired) {
        final net.minecraft.server.level.ServerLevel level = player.serverLevel();
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IllegalStateException("M2 validation could not find the server sub-level container");
        }
        final Pose3d pose = new Pose3d();
        pose.position().set(player.getX(), player.getY() + 2.0D, player.getZ());
        final ServerSubLevel subLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);
        subLevel.setName("M2 real lifecycle validation");
        subLevel.getPlot().newEmptyChunk(subLevel.getPlot().getCenterChunk());
        if (!subLevel.getPlot().getEmbeddedLevelAccessor()
                .setBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), 3)) {
            throw new IllegalStateException("M2 validation failed to place its real plot block");
        }
        subLevel.updateLastPose();
        subLevel.lastNetworkedPose().set(subLevel.logicalPose());
        subLevel.setLastNetworkedStopped(true);

        // M2 normally stays paused for its deterministic teleport. The punch
        // validation must permit a real Rapier step, then pauses again as soon
        // as the server observes the resulting +X velocity.
        container.physicsSystem().setPaused(!Boolean.getBoolean("sable.punchValidation"));
        LOGGER.info("Sable M2 real sub-level created (uuid {}, plot {}, block bounds {}, entity/profile uuid {}/{})",
                subLevel.getUniqueId(), subLevel.getPlot().plotPos,
                subLevel.getPlot().getBoundingBox(), player.getUUID(), player.getGameProfile().getId());
        return new M2ValidationState(container, subLevel, player, worldChangeRequired);
    }

    private static ServerSubLevel createM2SplitChild(final M2ValidationState state) {
        final Pose3d pose = new Pose3d();
        pose.position().set(state.subLevel.logicalPose().position()).add(1.0D, 1.0D, 0.0D);
        final ServerSubLevel child = (ServerSubLevel) state.container.allocateNewSubLevel(pose);
        child.setName("M2 split child validation");
        child.getPlot().newEmptyChunk(child.getPlot().getCenterChunk());
        if (!child.getPlot().getEmbeddedLevelAccessor()
                .setBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), 3)) {
            throw new IllegalStateException("M2 validation failed to place the split child's plot block");
        }
        child.updateLastPose();
        child.lastNetworkedPose().set(child.logicalPose());
        child.setLastNetworkedStopped(true);
        child.setSplitFrom(state.subLevel, new Pose3d());
        return child;
    }

    private static ServerSubLevel createM3DisplayChild(final M2ValidationState state) {
        final Vec3 look = state.player.getLookAngle();
        final double horizontalLength = Math.sqrt(look.x * look.x + look.z * look.z);
        final Vec3 horizontalLook = horizontalLength > 1.0E-6D
                ? new Vec3(look.x / horizontalLength, 0.0D, look.z / horizontalLength)
                : new Vec3(0.0D, 0.0D, 1.0D);
        final Vec3 target = state.player.getEyePosition().add(horizontalLook.scale(2.0D))
                .add(0.0D, -0.6D, 0.0D);
        final Pose3d pose = new Pose3d();
        pose.position().set(target.x, target.y, target.z);
        final ServerSubLevel child = (ServerSubLevel) state.container.allocateNewSubLevel(pose);
        child.setName("M3 stable render validation");
        child.getPlot().newEmptyChunk(child.getPlot().getCenterChunk());
        if (!child.getPlot().getEmbeddedLevelAccessor()
                .setBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), 3)) {
            throw new IllegalStateException("M3 validation failed to place its display block");
        }
        child.updateLastPose();
        child.lastNetworkedPose().set(child.logicalPose());
        child.setLastNetworkedStopped(true);
        return child;
    }

    private static ServerSubLevel createM3BlockEntityChild(final M2ValidationState state) {
        final Vec3 look = state.player.getLookAngle();
        final double horizontalLength = Math.sqrt(look.x * look.x + look.z * look.z);
        final Vec3 horizontalLook = horizontalLength > 1.0E-6D
                ? new Vec3(look.x / horizontalLength, 0.0D, look.z / horizontalLength)
                : new Vec3(0.0D, 0.0D, 1.0D);
        final Vec3 right = new Vec3(-horizontalLook.z, 0.0D, horizontalLook.x);
        final Vec3 target = state.player.getEyePosition().add(horizontalLook.scale(2.0D))
                .add(right.scale(1.25D)).add(0.0D, -0.6D, 0.0D);
        final Pose3d pose = new Pose3d();
        pose.position().set(target.x, target.y, target.z);
        final ServerSubLevel child = (ServerSubLevel) state.container.allocateNewSubLevel(pose);
        child.setName("M3 block entity render validation");
        child.getPlot().newEmptyChunk(child.getPlot().getCenterChunk());
        if (!child.getPlot().getEmbeddedLevelAccessor()
                .setBlock(BlockPos.ZERO, Blocks.CHEST.defaultBlockState(), 3)
                || !child.getPlot().getEmbeddedLevelAccessor()
                .setBlock(BlockPos.ZERO.east(), AllBlocks.SHAFT.getDefaultState(), 3)
                || child.getPlot().getEmbeddedLevelAccessor().getBlockEntity(BlockPos.ZERO) == null) {
            throw new IllegalStateException("M3 validation failed to create its chunked chest/Create fixture");
        }
        child.updateLastPose();
        child.lastNetworkedPose().set(child.logicalPose());
        child.setLastNetworkedStopped(true);
        return child;
    }

    private void tickM2TrackingValidation(final TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("sable.m2Validation") || event.phase != TickEvent.Phase.END) {
            return;
        }

        final java.util.Iterator<Map.Entry<UUID, M2ValidationState>> iterator =
                M2_VALIDATIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<UUID, M2ValidationState> entry = iterator.next();
            final M2ValidationState state = entry.getValue();
            state.ticks++;

            if (Boolean.getBoolean("sable.punchValidation") && !state.punchVerified
                    && SableForgeNetworkValidationTelemetry.handledCount(ServerboundPunchSubLevelPacket.class)
                    > state.punchPacketBaseline) {
                final Vector3d velocity = state.container.physicsSystem().getPhysicsHandle(state.subLevel)
                        .getLinearVelocity(new Vector3d());
                if (velocity.x() > state.punchVelocityBefore.x() + 1.0E-8D) {
                    state.punchVerified = true;
                    state.container.physicsSystem().setPaused(true);
                    LOGGER.info("Sable REAL CLIENT-FORGE-SERVER PUNCH VERIFIED "
                                    + "(player {}, sub-level {}, packet count {} -> {}, velocity {} -> {})",
                            state.player.getUUID(), state.subLevel.getUniqueId(), state.punchPacketBaseline,
                            SableForgeNetworkValidationTelemetry.handledCount(ServerboundPunchSubLevelPacket.class),
                            state.punchVelocityBefore, velocity);
                }
            }

            if (Boolean.getBoolean("sable.gizmoValidation")) {
                final int gizmoPacketCount = SableForgeNetworkValidationTelemetry.handledCount(
                        ServerboundGizmoMoveSubLevelPacket.class);
                if (state.gizmoPhase == 0 && gizmoPacketCount > state.gizmoPacketBaseline) {
                    if (state.subLevel.logicalPose().position().distance(state.gizmoPositionBefore) > 1.0E-8D) {
                        throw new IllegalStateException("Unauthorized production gizmo packet moved the sub-level; "
                                + "position " + state.gizmoPositionBefore + " -> "
                                + state.subLevel.logicalPose().position());
                    }
                    state.player.server.getPlayerList().op(state.player.getGameProfile());
                    state.gizmoPhase = 1;
                    LOGGER.info("Sable unauthorized gizmo packet rejection verified; temporary permission granted "
                                    + "for the authorized branch (player {}, packet count {} -> {})",
                            state.player.getUUID(), state.gizmoPacketBaseline, gizmoPacketCount);
                } else if (state.gizmoPhase == 1 && gizmoPacketCount > state.gizmoPacketBaseline + 1) {
                    if (state.subLevel.logicalPose().position().distance(GIZMO_DESTINATION) > 1.0E-8D) {
                        throw new IllegalStateException("Authorized production gizmo packet did not teleport to "
                                + GIZMO_DESTINATION + "; actual " + state.subLevel.logicalPose().position());
                    }
                    final GizmoPermissionState permission = GIZMO_PERMISSIONS.get(state.player.getUUID());
                    if (permission == null) {
                        throw new IllegalStateException("Gizmo validation lost the original permission state");
                    }
                    if (!permission.originallyOp) {
                        state.player.server.getPlayerList().deop(state.player.getGameProfile());
                    }
                    permission.restored = true;
                    state.gizmoPhase = 2;
                    LOGGER.info("Sable REAL CLIENT-FORGE-SERVER GIZMO PERMISSION AND TELEPORT VERIFIED "
                                    + "(player {}, sub-level {}, packet count {} -> {}, position {} -> {}, "
                                    + "original permission restored={})",
                            state.player.getUUID(), state.subLevel.getUniqueId(), state.gizmoPacketBaseline,
                            gizmoPacketCount, state.gizmoPositionBefore, state.subLevel.logicalPose().position(),
                            permission.originallyOp);
                }
            }

            if (state.worldChangeRequired && !state.worldChangeStarted
                    && state.ticks == (Boolean.getBoolean("sable.ridingValidation") ? 130 : 70)) {
                final net.minecraft.server.level.ServerLevel destination = state.player.server.getLevel(
                        state.player.serverLevel().dimension() == net.minecraft.world.level.Level.OVERWORLD
                                ? net.minecraft.world.level.Level.NETHER
                                : net.minecraft.world.level.Level.OVERWORLD);
                if (destination == null) {
                    throw new IllegalStateException("M2 validation destination world is unavailable");
                }
                final BlockPos spawn = destination.getSharedSpawnPos();
                state.worldChangeStarted = state.player.teleportTo(destination,
                        spawn.getX() + 0.5D, spawn.getY() + 2.0D, spawn.getZ() + 0.5D,
                        Set.of(), state.player.getYRot(), state.player.getXRot());
                if (!state.worldChangeStarted || state.player.serverLevel() != destination) {
                    throw new IllegalStateException("M2 validation player world change failed");
                }
                LOGGER.info("Sable M2 real world change started (old world {}, new world {}, old sub-level {})",
                        state.container.getLevel().dimension().location(), destination.dimension().location(),
                        state.subLevel.getUniqueId());
                continue;
            }

            if (state.worldChangeRequired && state.worldChangeStarted) {
                if (!state.returnStarted && state.ticks < 75) {
                    continue;
                }
                if (!state.returnStarted) {
                    if (!state.subLevel.getTrackingPlayers().isEmpty()) {
                        throw new IllegalStateException("M2 old-world tracking survived world change: "
                                + state.subLevel.getTrackingPlayers());
                    }
                    state.oldSubLevel = state.subLevel.getUniqueId();
                    state.container.removeSubLevel(state.subLevel, SubLevelRemovalReason.REMOVED);
                    if (state.splitChild != null
                            && state.container.getSubLevel(state.splitChild.getUniqueId()) != null) {
                        state.container.removeSubLevel(state.splitChild, SubLevelRemovalReason.REMOVED);
                    }
                    if (state.displayChild != null
                            && state.container.getSubLevel(state.displayChild.getUniqueId()) != null) {
                        state.container.removeSubLevel(state.displayChild, SubLevelRemovalReason.REMOVED);
                    }
                    if (state.blockEntityChild != null
                            && state.container.getSubLevel(state.blockEntityChild.getUniqueId()) != null) {
                        state.container.removeSubLevel(state.blockEntityChild, SubLevelRemovalReason.REMOVED);
                    }
                    state.container.physicsSystem().setPaused(false);
                    final net.minecraft.server.level.ServerLevel originalLevel = state.container.getLevel();
                    final BlockPos originalSpawn = originalLevel.getSharedSpawnPos();
                    if (!state.player.teleportTo(originalLevel,
                            originalSpawn.getX() + 0.5D, originalSpawn.getY() + 2.0D,
                            originalSpawn.getZ() + 0.5D, Set.of(),
                            state.player.getYRot(), state.player.getXRot())) {
                        throw new IllegalStateException("M2 validation return to original world failed");
                    }
                    state.returnStarted = true;
                    state.ticks = 0;
                    continue;
                }
                if (state.ticks < 40) {
                    continue;
                }
                final M2ValidationState destinationState = createM2ValidationState(state.player, false);
                M2_VALIDATIONS.put(entry.getKey(), destinationState);
                LOGGER.info("Sable M2 old-world cleanup verified and lifecycle resumed after return "
                                + "(old sub-level {}, lifecycle sub-level {})",
                        state.oldSubLevel, destinationState.subLevel.getUniqueId());
                continue;
            }

            if (state.ticks == 20) {
                final boolean retained = !state.subLevel.getTrackingPlayers().isEmpty();
                final boolean present = state.container.getSubLevel(state.subLevel.getUniqueId()) == state.subLevel;
                LOGGER.info("Sable M2 initial tracking diagnostic: retained={}, present={}, removed={}, mass={}, "
                                + "logical={}, player={}, entity/profile uuid={}/{}, levelPlayers={}",
                        retained, present, state.subLevel.isRemoved(), state.subLevel.getMassTracker().getMass(),
                        state.subLevel.logicalPose().position(), state.player.position(), state.player.getUUID(),
                        state.player.getGameProfile().getId(), state.container.getLevel().players().size());
                if (!present || state.subLevel.isRemoved()) {
                    throw new IllegalStateException("M2 real sub-level did not survive initial tracking: retained="
                            + retained + ", present=" + present + ", removed=" + state.subLevel.isRemoved()
                            + ", mass=" + state.subLevel.getMassTracker().getMass()
                            + ", centerOfMass=" + state.subLevel.getMassTracker().getCenterOfMass()
                            + ", logical=" + state.subLevel.logicalPose().position()
                            + ", player=" + state.player.position());
                }
            } else if (state.ticks == 30) {
                state.subLevel.setName("M2 renamed lifecycle validation");
                LOGGER.info("Sable M2 real sub-level renamed through ServerSubLevel (uuid {})",
                        state.subLevel.getUniqueId());
            } else if (state.ticks == 40) {
                if (!state.subLevel.getPlot().getEmbeddedLevelAccessor()
                        .setBlock(new BlockPos(3, 0, 0), Blocks.GLASS.defaultBlockState(), 3)) {
                    throw new IllegalStateException("M2 validation failed to expand its real plot bounds");
                }
                LOGGER.info("Sable M2 real plot expanded by a block change (uuid {}, bounds {})",
                        state.subLevel.getUniqueId(), state.subLevel.getPlot().getBoundingBox());
            } else if (state.ticks == 50) {
                state.splitHistorySeedPosition = new Vector3d(state.subLevel.logicalPose().position());
                final Vector3d moved = new Vector3d(state.splitHistorySeedPosition).add(0.03125D, 0.0D, 0.0D);
                state.container.physicsSystem().getPipeline().teleport(
                        state.subLevel, moved, state.subLevel.logicalPose().orientation());
                state.container.physicsSystem().updatePose(state.subLevel);
            } else if (state.ticks == 52) {
                if (state.splitHistorySeedPosition == null) {
                    throw new IllegalStateException("M2 split-history seed lost its origin pose");
                }
                state.container.physicsSystem().getPipeline().teleport(state.subLevel,
                        state.splitHistorySeedPosition, state.subLevel.logicalPose().orientation());
                state.container.physicsSystem().updatePose(state.subLevel);
                LOGGER.info("Sable M2 production snapshot history seeded before split (uuid {}, restored {})",
                        state.subLevel.getUniqueId(), state.splitHistorySeedPosition);
            } else if (state.ticks == 55) {
                state.splitChild = createM2SplitChild(state);
                if (Boolean.getBoolean("sable.m3Validation")) {
                    state.displayChild = createM3DisplayChild(state);
                    state.blockEntityChild = createM3BlockEntityChild(state);
                }
                final Vector3d freezeDestination = new Vector3d(
                        state.player.getX() + 1.0D, state.player.getY(), state.player.getZ());
                final Vector3d anchor = state.subLevel.lastPose()
                        .transformPositionInverse(new Vector3d(freezeDestination));
                if (Boolean.getBoolean("sable.freezeValidation")) {
                    state.freezeExpectedPosition = state.subLevel.lastPose()
                            .transformPosition(new Vector3d(anchor));
                    ((LivingEntityMovementExtension) state.player).sable$getInheritedVelocity()
                            .set(3.0D, 2.0D, 1.0D);
                    ((PlayerFreezeExtension) state.player).sable$freezeTo(
                            state.subLevel.getUniqueId(), anchor);
                }
                SablePacketManager.player(state.player).sendPacket(
                        new ClientboundFreezePlayerPacket(state.subLevel.getUniqueId(), anchor));
                LOGGER.info("Sable M2 split child queued and freeze state sent to finalized origin "
                                + "(origin {}, split child {}, anchor {})",
                        state.subLevel.getUniqueId(), state.splitChild.getUniqueId(), anchor);
            } else if (Boolean.getBoolean("sable.freezeValidation") && state.ticks == 56) {
                final PlayerFreezeExtension freeze = (PlayerFreezeExtension) state.player;
                final EntityMovementExtension movement = (EntityMovementExtension) state.player;
                final double positionError = state.player.position().distanceTo(
                        new Vec3(state.freezeExpectedPosition.x, state.freezeExpectedPosition.y,
                                state.freezeExpectedPosition.z));
                if (freeze.sable$getFrozenToSubLevel() != null
                        || movement.sable$getTrackingSubLevel() != state.subLevel
                        || ((LivingEntityMovementExtension) state.player).sable$getInheritedVelocity()
                        .lengthSquared() != 0.0D
                        || positionError > 0.35D) {
                    throw new IllegalStateException("Server freeze teleport did not complete: frozen="
                            + freeze.sable$getFrozenToSubLevel() + ", tracking="
                            + (movement.sable$getTrackingSubLevel() == null ? null
                            : movement.sable$getTrackingSubLevel().getUniqueId())
                            + ", inherited=" + ((LivingEntityMovementExtension) state.player)
                            .sable$getInheritedVelocity() + ", expected=" + state.freezeExpectedPosition
                            + ", actual=" + state.player.position() + ", error=" + positionError);
                }
                state.freezeVerified = true;
                LOGGER.info("Sable SERVER PLAYER FREEZE TELEPORT VERIFIED "
                                + "(player {}, child {}, expected {}, actual {}, error {})",
                        state.player.getUUID(), state.subLevel.getUniqueId(),
                        state.freezeExpectedPosition, state.player.position(), positionError);
            } else if (Boolean.getBoolean("sable.ridingValidation") && state.ticks == 60) {
                final Quaterniond quarterTurn = new Quaterniond().rotateY(Math.PI / 2.0D);
                state.container.physicsSystem().getPipeline().teleport(state.subLevel,
                        state.subLevel.logicalPose().position(), quarterTurn);
                state.container.physicsSystem().updatePose(state.subLevel);
                state.subLevel.updateLastPose();
                state.subLevel.lastNetworkedPose().set(state.subLevel.logicalPose());

                final Vector3d localVehiclePosition = new Vector3d(
                        state.subLevel.getMassTracker().getCenterOfMass()).add(0.0D, 1.0D, 0.0D);
                // A normal minecart is deliberately in sable:destroy_when_leaving_plot and
                // its upstream tail-tick guard compares against the physical plot bounds.
                // The command-block minecart exercises the same AbstractMinecart/riding path,
                // is retained by Sable, and is intentionally absent from that destroy tag.
                final AbstractMinecart vehicle = EntityType.COMMAND_BLOCK_MINECART.create(state.player.serverLevel());
                if (vehicle == null) {
                    throw new IllegalStateException("Riding validation could not create its retained minecart");
                }
                vehicle.setPos(localVehiclePosition.x, localVehiclePosition.y, localVehiclePosition.z);
                vehicle.setNoGravity(true);
                vehicle.setDeltaMovement(Vec3.ZERO);
                vehicle.setCustomName(net.minecraft.network.chat.Component.literal("Sable riding validation"));
                if (!state.player.serverLevel().addFreshEntity(vehicle)
                        || Sable.HELPER.getContaining(vehicle) != state.subLevel) {
                    vehicle.discard();
                    throw new IllegalStateException("Riding validation minecart was not retained in the real plot");
                }
                state.ridingVehicle = vehicle;
                // Player.rideTick() immediately dismounts a server player whose last
                // command left shift set. Clear any pre-fixture state; after all render
                // assertions pass, the client sends the vanilla PRESS_SHIFT_KEY command.
                state.player.setShiftKeyDown(false);
                if (!state.player.startRiding(vehicle, true)) {
                    vehicle.discard();
                    throw new IllegalStateException("Real ServerPlayer could not start riding the retained minecart");
                }
                LOGGER.info("Sable real-player riding fixture started (player {}, vehicle {}, sub-level {}, pose {})",
                        state.player.getUUID(), vehicle.getUUID(), state.subLevel.getUniqueId(),
                        state.subLevel.logicalPose());
            } else if (Boolean.getBoolean("sable.ridingValidation") && state.ticks == 66) {
                final AbstractMinecart vehicle = state.ridingVehicle;
                if (vehicle == null || state.player.getVehicle() != vehicle) {
                    throw new IllegalStateException("Real ServerPlayer lost the retained minecart before verification: "
                            + "shift=" + state.player.isShiftKeyDown() + ", vehicleRemoved="
                            + (vehicle == null ? null : vehicle.isRemoved()) + ", removalReason="
                            + (vehicle == null ? null : vehicle.getRemovalReason()));
                }
                vehicle.positionRider(state.player);
                final Vec3 localFeet = new Vec3(vehicle.getX(),
                        vehicle.getY() + vehicle.getPassengersRidingOffset()
                                + state.player.getMyRidingOffset(), vehicle.getZ());
                final Vec3 eyeOffset = state.player.getEyePosition().subtract(state.player.position());
                state.ridingExpectedPosition = state.subLevel.logicalPose()
                        .transformPosition(localFeet.add(eyeOffset)).subtract(eyeOffset);
                final double error = state.player.position().distanceTo(state.ridingExpectedPosition);
                if (error > 1.0E-8D) {
                    throw new IllegalStateException("Real ServerPlayer rotated riding projection mismatch: expected="
                            + state.ridingExpectedPosition + ", actual=" + state.player.position()
                            + ", error=" + error);
                }
                state.ridingVerified = true;
                LOGGER.info("Sable REAL SERVERPLAYER ROTATED RIDING VERIFIED "
                                + "(player {}, vehicle {}, expected {}, actual {}, error {})",
                        state.player.getUUID(), vehicle.getUUID(), state.ridingExpectedPosition,
                        state.player.position(), error);
            } else if (Boolean.getBoolean("sable.ridingValidation") && state.ticks == 125) {
                final AbstractMinecart vehicle = state.ridingVehicle;
                if (vehicle == null || vehicle.isRemoved()) {
                    throw new IllegalStateException("Retained minecart was unavailable after the client dismount command");
                }
                if (state.player.isPassenger() || state.player.getVehicle() != null) {
                    throw new IllegalStateException("Vanilla PRESS_SHIFT_KEY command did not dismount ServerPlayer by tick 125");
                }
                if (Sable.HELPER.getContaining(state.player) != null) {
                    throw new IllegalStateException("Dismounted ServerPlayer remained inside plot storage space");
                }
                state.dismountVerified = true;
                vehicle.discard();
                state.container.physicsSystem().getPipeline().teleport(state.subLevel,
                        state.subLevel.logicalPose().position(), new Quaterniond());
                state.container.physicsSystem().updatePose(state.subLevel);
                state.subLevel.updateLastPose();
                state.subLevel.lastNetworkedPose().set(state.subLevel.logicalPose());
                LOGGER.info("Sable REAL SERVERPLAYER ROTATED DISMOUNT VERIFIED "
                                + "(player {}, vanilla PRESS_SHIFT_KEY command consumed, actual {}, outside plot=true)",
                        state.player.getUUID(), state.player.position());
            } else if (Boolean.getBoolean("sable.movementValidation") && state.ticks == 88) {
                state.platformProbeBaseline = SableForgeMovementValidationProbe.platformMoves();
                ((EntityMovementExtension) state.player).sable$setTrackingSubLevel(state.subLevel);
                state.container.physicsSystem().getPipeline().resetVelocity(state.subLevel);
                state.container.physicsSystem().getPhysicsHandle(state.subLevel)
                        .addLinearAndAngularVelocity(new Vector3d(0.5D, 0.0D, 0.0D), new Vector3d());
                state.container.physicsSystem().setPaused(false);
                LOGGER.info("Sable movement validation started one real Rapier platform step "
                                + "(player {}, sub-level {}, pose {}, velocity {})",
                        state.player.getUUID(), state.subLevel.getUniqueId(), state.subLevel.logicalPose(),
                        state.container.physicsSystem().getPhysicsHandle(state.subLevel)
                                .getLinearVelocity(new Vector3d()));
            } else if (Boolean.getBoolean("sable.movementValidation") && state.ticks == 89) {
                state.container.physicsSystem().setPaused(true);
                state.container.physicsSystem().getPipeline().resetVelocity(state.subLevel);
                if (SableForgeMovementValidationProbe.platformMoves() <= state.platformProbeBaseline) {
                    throw new IllegalStateException("Real Rapier platform step did not displace the tracked player");
                }
                LOGGER.info("Sable real Rapier platform step inherited by player "
                                + "(platform moves {} -> {}, player {})",
                        state.platformProbeBaseline, SableForgeMovementValidationProbe.platformMoves(),
                        state.player.position());
            } else if (state.ticks == 95 && state.subLevel.getTrackingPlayers().isEmpty()) {
                throw new IllegalStateException("M2 validation player was not retained by the tracking system");
            } else if (state.ticks == 100) {
                final Vector3d destination = new Vector3d(state.subLevel.logicalPose().position())
                        .add(3.0D, 0.0D, 0.0D);
                state.container.physicsSystem().getPipeline().teleport(
                        state.subLevel, destination, state.subLevel.logicalPose().orientation());
                LOGGER.info("Sable M2 real sub-level teleported (uuid {}, destination {})",
                        state.subLevel.getUniqueId(), destination);
            } else if (state.ticks == 101) {
                if (state.subLevel.getLastNetworkedStopped()) {
                    throw new IllegalStateException("M2 tracking did not publish the teleported pose; logical="
                            + state.subLevel.logicalPose() + ", networked=" + state.subLevel.lastNetworkedPose());
                }
                LOGGER.info("Sable M2 real movement snapshot was published (uuid {}, tracking players {})",
                        state.subLevel.getUniqueId(), state.subLevel.getTrackingPlayers().size());
            } else if (state.ticks == 140) {
                if (!state.subLevel.getLastNetworkedStopped()) {
                    throw new IllegalStateException("M2 validation did not observe the sub-level stopping");
                }
                LOGGER.info("Sable M2 real sub-level movement and stop were processed (uuid {})",
                        state.subLevel.getUniqueId());
            } else if (Boolean.getBoolean("sable.m3Validation") && state.ticks == 150) {
                state.subLevel.setName("M3 physics motion validation");
                state.physicsStartPosition = new Vector3d(state.subLevel.logicalPose().position());
                state.container.physicsSystem().getPhysicsHandle(state.subLevel)
                        .addLinearAndAngularVelocity(new Vector3d(2.0D, 0.0D, 0.0D), new Vector3d());
                state.container.physicsSystem().setPaused(false);
                LOGGER.info("Sable M3 real Rapier motion started (uuid {}, start {}, velocity {})",
                        state.subLevel.getUniqueId(), state.physicsStartPosition,
                        state.container.physicsSystem().getPhysicsHandle(state.subLevel)
                                .getLinearVelocity(new Vector3d()));
            } else if (Boolean.getBoolean("sable.m3Validation") && state.ticks == 190) {
                final double distance = state.subLevel.logicalPose().position().distance(state.physicsStartPosition);
                final Vector3d velocity = state.container.physicsSystem().getPhysicsHandle(state.subLevel)
                        .getLinearVelocity(new Vector3d());
                if (distance < 0.25D) {
                    throw new IllegalStateException("M3 Rapier body did not move continuously; distance="
                            + distance + ", velocity=" + velocity);
                }
                state.physicsDistance = distance;
                state.container.physicsSystem().setPaused(true);
                LOGGER.info("Sable M3 real Rapier motion verified and paused (uuid {}, distance {}, velocity {})",
                        state.subLevel.getUniqueId(), distance, velocity);
            } else if (Boolean.getBoolean("sable.m3Validation") && state.ticks == 230) {
                if (!state.subLevel.getLastNetworkedStopped()) {
                    throw new IllegalStateException("M3 physics-driven motion did not publish a final stop");
                }
                LOGGER.info("Sable M3 physics-driven snapshot stream and final stop verified "
                                + "(uuid {}, distance {})",
                        state.subLevel.getUniqueId(), state.physicsDistance);
            } else if (state.ticks == (Boolean.getBoolean("sable.m3Validation") ? 280 : 240)) {
                if (Boolean.getBoolean("sable.punchValidation") && !state.punchVerified) {
                    throw new IllegalStateException("Production serverbound punch did not apply a Rapier impulse; "
                            + "packet count " + state.punchPacketBaseline + " -> "
                            + SableForgeNetworkValidationTelemetry.handledCount(ServerboundPunchSubLevelPacket.class)
                            + ", velocity " + state.punchVelocityBefore + " -> "
                            + state.container.physicsSystem().getPhysicsHandle(state.subLevel)
                            .getLinearVelocity(new Vector3d())
                            + ", player onGround/water/flying/climbable " + state.player.onGround() + "/"
                            + state.player.isInWater() + "/" + state.player.getAbilities().flying + "/"
                            + state.player.onClimbable());
                }
                if (Boolean.getBoolean("sable.gizmoValidation") && state.gizmoPhase != 2) {
                    throw new IllegalStateException("Production serverbound gizmo validation did not complete; "
                            + "phase " + state.gizmoPhase + ", packet count " + state.gizmoPacketBaseline + " -> "
                            + SableForgeNetworkValidationTelemetry.handledCount(ServerboundGizmoMoveSubLevelPacket.class));
                }
                if (Boolean.getBoolean("sable.freezeValidation") && !state.freezeVerified) {
                    throw new IllegalStateException("Server freeze teleport validation did not complete");
                }
                if (Boolean.getBoolean("sable.movementValidation")
                        && (SableForgeMovementValidationProbe.serverPackets() == 0
                        || SableForgeMovementValidationProbe.platformMoves() == 0)) {
                    throw new IllegalStateException("Movement validation did not close both server paths: packets="
                            + SableForgeMovementValidationProbe.serverPackets() + ", platform moves="
                            + SableForgeMovementValidationProbe.platformMoves());
                }
                if (state.container.getSubLevel(state.subLevel.getUniqueId()) == null) {
                    throw new IllegalStateException("M2 validation sub-level disappeared before explicit removal");
                }
                state.container.removeSubLevel(state.subLevel, SubLevelRemovalReason.REMOVED);
                if (state.splitChild != null
                        && state.container.getSubLevel(state.splitChild.getUniqueId()) != null) {
                    state.container.removeSubLevel(state.splitChild, SubLevelRemovalReason.REMOVED);
                }
                if (state.displayChild != null
                        && state.container.getSubLevel(state.displayChild.getUniqueId()) != null) {
                    state.container.removeSubLevel(state.displayChild, SubLevelRemovalReason.REMOVED);
                }
                if (state.blockEntityChild != null
                        && state.container.getSubLevel(state.blockEntityChild.getUniqueId()) != null) {
                    state.container.removeSubLevel(state.blockEntityChild, SubLevelRemovalReason.REMOVED);
                }
                state.container.physicsSystem().setPaused(false);
                M2_VALIDATIONS.remove(entry.getKey(), state);
                LOGGER.info("Sable M2 real sub-level removed and physics resumed (uuid {})",
                        state.subLevel.getUniqueId());
                if (Boolean.getBoolean("sable.movementValidation")) {
                    LOGGER.info("Sable SERVER MOVEMENT PACKET/PLATFORM PATHS VERIFIED "
                                    + "(decoded packets {}, platform moves {})",
                            SableForgeMovementValidationProbe.serverPackets(),
                            SableForgeMovementValidationProbe.platformMoves());
                }
                if (Boolean.getBoolean("sable.ridingValidation")
                        && (!state.ridingVerified || !state.dismountVerified)) {
                    throw new IllegalStateException("M2 lifecycle ended without real-player riding verification: "
                            + state.ridingVerified + "/" + state.dismountVerified);
                }
            }
        }
    }

    private static final class M2ValidationState {
        private final ServerSubLevelContainer container;
        private final ServerSubLevel subLevel;
        private final net.minecraft.server.level.ServerPlayer player;
        private final boolean worldChangeRequired;
        private boolean worldChangeStarted;
        private boolean returnStarted;
        private UUID oldSubLevel;
        private ServerSubLevel splitChild;
        private ServerSubLevel displayChild;
        private ServerSubLevel blockEntityChild;
        private Vector3d physicsStartPosition;
        private Vector3d splitHistorySeedPosition;
        private double physicsDistance;
        private final int punchPacketBaseline;
        private final Vector3d punchVelocityBefore;
        private boolean punchVerified;
        private final int gizmoPacketBaseline;
        private final Vector3d gizmoPositionBefore;
        private int gizmoPhase;
        private Vector3d freezeExpectedPosition;
        private boolean freezeVerified;
        private int platformProbeBaseline;
        private AbstractMinecart ridingVehicle;
        private Vec3 ridingExpectedPosition;
        private boolean ridingVerified;
        private boolean dismountVerified;
        private int ticks;

        private M2ValidationState(final ServerSubLevelContainer container,
                                  final ServerSubLevel subLevel,
                                  final net.minecraft.server.level.ServerPlayer player,
                                  final boolean worldChangeRequired) {
            this.container = container;
            this.subLevel = subLevel;
            this.player = player;
            this.worldChangeRequired = worldChangeRequired;
            this.punchPacketBaseline = SableForgeNetworkValidationTelemetry.handledCount(ServerboundPunchSubLevelPacket.class);
            this.punchVelocityBefore = container.physicsSystem().getPhysicsHandle(subLevel)
                    .getLinearVelocity(new Vector3d());
            this.gizmoPacketBaseline = SableForgeNetworkValidationTelemetry.handledCount(
                    ServerboundGizmoMoveSubLevelPacket.class);
            this.gizmoPositionBefore = new Vector3d(subLevel.logicalPose().position());
            final GizmoPermissionState permission = GIZMO_PERMISSIONS.get(player.getUUID());
            if (permission != null && permission.restored) {
                this.gizmoPhase = 2;
            }
        }
    }

    private static final class GizmoPermissionState {
        private final boolean originallyOp;
        private boolean restored;

        private GizmoPermissionState(final boolean originallyOp) {
            this.originallyOp = originallyOp;
        }
    }

    private static final class WrongDirectionState {
        private final net.minecraft.server.level.ServerPlayer player;
        private final String mode;
        private final int handlerBaseline;
        private boolean packetSent;
        private int ticks;

        private WrongDirectionState(final net.minecraft.server.level.ServerPlayer player, final String mode) {
            this.player = player;
            this.mode = mode;
            this.handlerBaseline = SableForgeNetworkValidationTelemetry.handledCount(mode.equals("c2s")
                    ? ClientboundStopMovingSubLevelPacket.class : ServerboundGizmoMoveSubLevelPacket.class);
        }
    }

    private static final class GizmoEnterState {
        private final net.minecraft.server.level.ServerPlayer player;
        private boolean sent;
        private int ticks;

        private GizmoEnterState(final net.minecraft.server.level.ServerPlayer player) {
            this.player = player;
        }
    }
}
