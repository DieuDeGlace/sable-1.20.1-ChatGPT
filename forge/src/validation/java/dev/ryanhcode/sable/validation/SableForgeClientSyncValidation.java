package dev.ryanhcode.sable.validation;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.backend.compile.FlwPrograms;
import dev.ryanhcode.sable.forge.SableForgeBootstrap;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.block_properties.BlockStateExtension;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundDimensionPhysicsPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFloatingBlockMaterialPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundPhysicsPropertyPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFinalizeSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStartTrackingSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStopTrackingSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStopMovingSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundChangeSubLevelNamePacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundChangeBoundsSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFreezePlayerPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundRecentlySplitSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ServerboundPunchSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ServerboundGizmoMoveSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundEnterGizmoPacket;
import dev.ryanhcode.sable.debug.GizmoScreen;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.mixinhelpers.block_outline_render.SubLevelCamera;
import dev.ryanhcode.sable.validation.mixin.CameraPositionInvoker;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotInfoDualPacket;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import dev.ryanhcode.sable.network.tcp.SablePacketManager;
import dev.ryanhcode.sable.physics.config.FloatingBlockMaterialDataHandler;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = SableForgeValidationMod.MOD_ID, value = Dist.CLIENT)
public final class SableForgeClientSyncValidation {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.getBoolean("sable.syncValidation");
    private static final String WRONG_DIRECTION_MODE = System.getProperty(
            "sable.wrongDirectionValidation", "");
    private static boolean awaitingSync;
    private static int waitedTicks;
    private static UUID observedSubLevel;
    private static double initialSubLevelX;
    private static boolean sawRealSubLevel;
    private static boolean sawMovedSubLevel;
    private static boolean sawRemovedSubLevel;
    private static int loginPass;
    private static ResourceKey<Level> initialDimension;
    private static UUID oldWorldSubLevel;
    private static boolean sawWorldChangeCleanup;
    private static UUID firstLifecycleSubLevel;
    private static ServerData reconnectServer;
    private static boolean reconnectRequested;
    private static int reconnectDelay = -1;
    private static boolean sawRenamedState;
    private static boolean sawExpandedBounds;
    private static boolean sawFreezeState;
    private static int appliedFreezeStatesAtLogin;
    private static UUID freezeTarget;
    private static Vector3d freezeAnchor;
    private static int freezeProbeSerialAtLogin;
    private static boolean sawFreezeTeleport;
    private static boolean sawSplitInterpolationHistory;
    private static boolean sawClientStone;
    private static boolean sawSingleBlockStone;
    private static boolean screenshotRequested;
    private static boolean screenshotSaved;
    private static long nativeDrawsAtLogin;
    private static int snapshotsAtLogin;
    private static int udpSnapshotsAtLogin;
    private static int udpSnapshotInfoAtLogin;
    private static int singleBlockReadyTicks;
    private static boolean sawChunkedRenderData;
    private static boolean sawClientChestBlockEntity;
    private static boolean sawClientCreateBlockEntity;
    private static boolean sawFlywheelFixtureAim;
    private static int flywheelFixtureAimTicks;
    private static int flywheelCullAngleStage;
    private static int flywheelCullAngleStageTicks;
    private static boolean sawFlywheelCullAngleSweep;
    private static boolean sawDynamicGlassState;
    private static boolean sawDynamicCutoutRender;
    private static int dynamicRenderSections;
    private static int dynamicCutoutSections;
    private static int dynamicSolidSections;
    private static long chunkDirtyCallsAtLogin;
    private static boolean dynamicModelLayerInspected;
    private static boolean dynamicRegionGlass;
    private static boolean dynamicModelCutoutLayer;
    private static long chunkDrawsAtLogin;
    private static long chunkCutoutDrawsAtLogin;
    private static long blockEntityDrawsAtLogin;
    private static long chestBlockEntityDrawsAtLogin;
    private static long flywheelVisualsAtLogin;
    private static Vector3d firstPhysicsLogical;
    private static Vector3d lastPhysicsLogical;
    private static Vector3d firstPhysicsRender;
    private static Vector3d lastPhysicsRender;
    private static int physicsLogicalSamples;
    private static int physicsRenderSamples;
    private static double physicsLogicalDistance;
    private static double physicsRenderDistance;
    private static boolean punchPacketSent;
    private static boolean movementNudgeSent;
    private static boolean sawPlayerRiding;
    private static boolean sawPlayerDismount;
    private static boolean ridingDismountCommandSent;
    private static UUID ridingVehicle;
    private static int ridingObservedTicks;
    private static boolean sawRetainedRidingVehicle;
    private static long rotatedEntityDrawsAtLogin;
    private static long rotatedEntityMismatchesAtLogin;
    private static double maxRidingRenderAngle;
    private static boolean sawRotatedEntityRender;
    private static long rotatedNameTagDrawsAtLogin;
    private static long rotatedNameTagMismatchesAtLogin;
    private static boolean sawRotatedNameTagRender;
    private static long rotatedCrosshairDrawsAtLogin;
    private static long rotatedCrosshairMismatchesAtLogin;
    private static long rotatedCompassAnglesAtLogin;
    private static long rotatedCompassMismatchesAtLogin;
    private static boolean sawRotatedDebugCrosshair;
    private static boolean sawRotatedCompassAngle;
    private static boolean sawRotatedViewVector;
    private static boolean sawActualCameraRotation;
    private static boolean sawCameraPositionTransform;
    private static boolean sawTransformedBlockOutline;
    private static int gizmoTargetTicks;
    private static boolean deniedGizmoPacketSent;
    private static boolean allowedGizmoPacketSent;
    private static boolean gizmoValidationCompleted;
    private static boolean wrongDirectionPacketSent;
    private static int wrongDirectionHandlerBaseline;
    private static int wrongDirectionTicks;
    private static boolean wrongDirectionLoggedIn;
    private static int gizmoEnterHandlerBaseline;
    private static boolean gizmoEnterVerified;

    private static final Vector3d GIZMO_DESTINATION = new Vector3d(12.5D, 120.0D, 12.5D);
    private static final Matrix4f BLOCK_OUTLINE_BASE_MATRIX = new Matrix4f();
    private static boolean hasBlockOutlineBaseMatrix;

    private SableForgeClientSyncValidation() {
    }

    public static void captureBlockOutlineBaseMatrix(final Matrix4f matrix) {
        BLOCK_OUTLINE_BASE_MATRIX.set(matrix);
        hasBlockOutlineBaseMatrix = true;
    }

    @SubscribeEvent
    public static void onRenderNameTag(final net.minecraftforge.client.event.RenderNameTagEvent event) {
        if (!ENABLED || !Boolean.getBoolean("sable.ridingValidation")) {
            return;
        }
        final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (event.getEntity() == minecraft.player && minecraft.player != null
                && minecraft.player.getVehicle() != null
                && Sable.HELPER.getContaining(minecraft.player.getVehicle()) instanceof ClientSubLevel) {
            event.setContent(Component.literal("Sable rotated name-tag validation"));
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
        }
    }

    @SubscribeEvent
    public static void onRenderBlockHighlight(final RenderHighlightEvent.Block event) {
        if (!ENABLED || !Boolean.getBoolean("sable.ridingValidation")
                || !(event.getCamera() instanceof SubLevelCamera subLevelCamera)) {
            return;
        }
        final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.level == null
                || !(Sable.HELPER.getContaining(minecraft.level,
                event.getTarget().getBlockPos()) instanceof ClientSubLevel subLevel)) {
            return;
        }

        final Vec3 localOrigin = Vec3.atLowerCornerOf(event.getTarget().getBlockPos());
        final Vec3 localCamera = subLevelCamera.getPosition();
        final Vector3f transformed = event.getPoseStack().last().pose().transformPosition(new Vector3f(
                (float) (localOrigin.x - localCamera.x),
                (float) (localOrigin.y - localCamera.y),
                (float) (localOrigin.z - localCamera.z)));
        final Vec3 physicalOrigin = subLevel.renderPose().transformPosition(localOrigin);
        final Vec3 realCamera = subLevelCamera.getRenderCamera().getPosition();
        final Vector3d expected = new Vector3d(
                physicalOrigin.x - realCamera.x,
                physicalOrigin.y - realCamera.y,
                physicalOrigin.z - realCamera.z);
        if (!hasBlockOutlineBaseMatrix) {
            throw new IllegalStateException("Missing pre-transform block-outline matrix capture");
        }
        final Vector3f expectedInView = BLOCK_OUTLINE_BASE_MATRIX.transformPosition(new Vector3f(
                (float) expected.x, (float) expected.y, (float) expected.z));
        final double error = new Vector3d(expectedInView).distance(new Vector3d(transformed));
        if (error > 1.0E-3D) {
            throw new IllegalStateException("Production sublevel block-outline matrix mismatch: expected="
                    + expectedInView + ", actual=" + transformed + ", error=" + error);
        }
        if (!sawTransformedBlockOutline) {
            sawTransformedBlockOutline = true;
            LOGGER.info("Sable REAL SUBLEVEL BLOCK OUTLINE MATRIX VERIFIED (block {}, error {})",
                    event.getTarget().getBlockPos(), error);
        }
    }

    @SubscribeEvent
    public static void onLogin(final ClientPlayerNetworkEvent.LoggingIn event) {
        if (!ENABLED) {
            return;
        }
        awaitingSync = true;
        loginPass++;
        waitedTicks = 0;
        observedSubLevel = null;
        initialSubLevelX = 0.0D;
        sawRealSubLevel = false;
        sawMovedSubLevel = false;
        sawRemovedSubLevel = false;
        initialDimension = null;
        oldWorldSubLevel = null;
        sawRenamedState = false;
        sawExpandedBounds = false;
        sawFreezeState = false;
        appliedFreezeStatesAtLogin = SableForgeNetworkValidationTelemetry.appliedFreezeStates();
        freezeTarget = null;
        freezeAnchor = null;
        freezeProbeSerialAtLogin = SableForgeFreezeValidationProbe.snapshot().serial();
        sawFreezeTeleport = false;
        sawSplitInterpolationHistory = false;
        sawClientStone = false;
        sawSingleBlockStone = false;
        screenshotRequested = false;
        screenshotSaved = false;
        nativeDrawsAtLogin = SableForgeRenderValidationTelemetry.singleBlockDraws();
        snapshotsAtLogin = SableForgeNetworkValidationTelemetry.handledCount(ClientboundSableSnapshotDualPacket.class);
        udpSnapshotsAtLogin = SableForgeUDPValidationTelemetry.snapshotPacketsReceived();
        udpSnapshotInfoAtLogin = SableForgeUDPValidationTelemetry.snapshotInfoPacketsReceived();
        singleBlockReadyTicks = 0;
        sawChunkedRenderData = false;
        sawClientChestBlockEntity = false;
        sawClientCreateBlockEntity = false;
        sawFlywheelFixtureAim = false;
        flywheelFixtureAimTicks = 0;
        flywheelCullAngleStage = 0;
        flywheelCullAngleStageTicks = 0;
        sawFlywheelCullAngleSweep = false;
        sawDynamicGlassState = false;
        sawDynamicCutoutRender = false;
        dynamicRenderSections = 0;
        dynamicCutoutSections = 0;
        dynamicSolidSections = 0;
        chunkDirtyCallsAtLogin = SableForgeRenderValidationTelemetry.chunkDirtyCalls();
        dynamicModelLayerInspected = false;
        dynamicRegionGlass = false;
        dynamicModelCutoutLayer = false;
        chunkDrawsAtLogin = SableForgeRenderValidationTelemetry.chunkLayerDraws();
        chunkCutoutDrawsAtLogin = SableForgeRenderValidationTelemetry.chunkCutoutDraws();
        blockEntityDrawsAtLogin = SableForgeRenderValidationTelemetry.blockEntityDraws();
        chestBlockEntityDrawsAtLogin = SableForgeRenderValidationTelemetry.chestBlockEntityDraws();
        flywheelVisualsAtLogin = SableForgeRenderValidationTelemetry.flywheelSubLevelVisuals();
        firstPhysicsLogical = null;
        lastPhysicsLogical = null;
        firstPhysicsRender = null;
        lastPhysicsRender = null;
        physicsLogicalSamples = 0;
        physicsRenderSamples = 0;
        physicsLogicalDistance = 0.0D;
        physicsRenderDistance = 0.0D;
        punchPacketSent = false;
        movementNudgeSent = false;
        sawPlayerRiding = false;
        sawPlayerDismount = false;
        ridingDismountCommandSent = false;
        ridingVehicle = null;
        ridingObservedTicks = 0;
        sawRetainedRidingVehicle = false;
        rotatedEntityDrawsAtLogin = SableForgeRenderValidationTelemetry.rotatedEntityDraws();
        rotatedEntityMismatchesAtLogin = SableForgeRenderValidationTelemetry.rotatedEntityMatrixMismatches();
        maxRidingRenderAngle = 0.0D;
        sawRotatedEntityRender = false;
        rotatedNameTagDrawsAtLogin = SableForgeRenderValidationTelemetry.rotatedNameTagDraws();
        rotatedNameTagMismatchesAtLogin = SableForgeRenderValidationTelemetry.rotatedNameTagMatrixMismatches();
        sawRotatedNameTagRender = false;
        rotatedCrosshairDrawsAtLogin = SableForgeRenderValidationTelemetry.rotatedDebugCrosshairDraws();
        rotatedCrosshairMismatchesAtLogin = SableForgeRenderValidationTelemetry.rotatedDebugCrosshairMatrixMismatches();
        rotatedCompassAnglesAtLogin = SableForgeRenderValidationTelemetry.rotatedCompassAngles();
        rotatedCompassMismatchesAtLogin = SableForgeRenderValidationTelemetry.rotatedCompassAngleMismatches();
        sawRotatedDebugCrosshair = false;
        sawRotatedCompassAngle = false;
        sawRotatedViewVector = false;
        sawActualCameraRotation = false;
        CameraRotationValidationTelemetry.reset();
        sawCameraPositionTransform = false;
        sawTransformedBlockOutline = false;
        gizmoTargetTicks = 0;
        deniedGizmoPacketSent = false;
        allowedGizmoPacketSent = false;
        wrongDirectionPacketSent = false;
        wrongDirectionTicks = 0;
        wrongDirectionLoggedIn = true;
        gizmoEnterHandlerBaseline = SableForgeNetworkValidationTelemetry.handledCount(ClientboundEnterGizmoPacket.class);
        gizmoEnterVerified = false;
        wrongDirectionHandlerBaseline = SableForgeNetworkValidationTelemetry.handledCount(
                ServerboundGizmoMoveSubLevelPacket.class);
        SableForgeRenderValidationTelemetry.resetMotionDrawTelemetry();
        LOGGER.info("Sable M2 client validation login pass {} started", loginPass);
    }

    @SubscribeEvent
    public static void onLogout(final ClientPlayerNetworkEvent.LoggingOut event) {
        if (ENABLED && wrongDirectionLoggedIn && "s2c".equals(WRONG_DIRECTION_MODE)) {
            final int handled = SableForgeNetworkValidationTelemetry.handledCount(ServerboundGizmoMoveSubLevelPacket.class);
            if (handled != wrongDirectionHandlerBaseline) {
                throw new IllegalStateException("Wrong-direction serverbound packet reached its client handler; "
                        + "count " + wrongDirectionHandlerBaseline + " -> " + handled);
            }
            LOGGER.info("Sable REAL FORGE S2C WRONG-DIRECTION REJECTION VERIFIED "
                            + "(serverbound handler count {} -> {})",
                    wrongDirectionHandlerBaseline, handled);
        }
        wrongDirectionLoggedIn = false;
        if (ENABLED && reconnectRequested) {
            reconnectDelay = 40;
            LOGGER.info("Sable M2 client logged out; same-process reconnect scheduled");
        }
    }

    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }

        final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (Boolean.getBoolean("sable.gizmoEnterValidation") && !gizmoEnterVerified
                && SableForgeNetworkValidationTelemetry.handledCount(ClientboundEnterGizmoPacket.class)
                > gizmoEnterHandlerBaseline) {
            if (!(minecraft.screen instanceof GizmoScreen)) {
                throw new IllegalStateException("Production gizmo-enter handler ran without opening GizmoScreen; "
                        + "screen=" + minecraft.screen);
            }
            gizmoEnterVerified = true;
            LOGGER.info("Sable REAL CLIENTBOUND GIZMO-ENTER UI VERIFIED "
                            + "(handler count {} -> {}, screen {}, thread {})",
                    gizmoEnterHandlerBaseline,
                    SableForgeNetworkValidationTelemetry.handledCount(ClientboundEnterGizmoPacket.class),
                    minecraft.screen.getClass().getName(), Thread.currentThread().getName());
            minecraft.screen.onClose();
        }
        if ("c2s".equals(WRONG_DIRECTION_MODE) && minecraft.getConnection() != null
                && minecraft.player != null) {
            wrongDirectionTicks++;
            if (!wrongDirectionPacketSent && wrongDirectionTicks >= 60) {
                wrongDirectionPacketSent = true;
                SablePacketManager.server().sendPacket(
                        new ClientboundStopMovingSubLevelPacket(0x1020304050607080L));
                LOGGER.info("Sable wrong-direction validation sent registered clientbound discriminator 2 "
                        + "over the client-to-server connection");
            }
        }
        if (reconnectRequested && reconnectDelay >= 0 && --reconnectDelay <= 0 && minecraft.level == null) {
            reconnectRequested = false;
            reconnectDelay = -1;
            if (reconnectServer == null) {
                throw new IllegalStateException("M2 reconnect lost the current server address");
            }
            LOGGER.info("Sable M2 client starting same-process reconnect to {}", reconnectServer.ip);
            ConnectScreen.startConnecting(new TitleScreen(), minecraft,
                    ServerAddress.parseString(reconnectServer.ip), reconnectServer, false);
        }
        if (!awaitingSync) {
            return;
        }

        waitedTicks++;
        final SubLevelContainer clientContainer = minecraft.level == null
                ? null : SubLevelContainer.getContainer(minecraft.level);
        final boolean m2Enabled = Boolean.getBoolean("sable.m2Validation");
        final boolean m3Enabled = Boolean.getBoolean("sable.m3Validation");
        final boolean udpEnabled = Boolean.getBoolean("sable.udpValidation");
        if (m2Enabled && clientContainer != null) {
            if (initialDimension != null && loginPass == 1
                    && minecraft.level.dimension() != initialDimension) {
                if (oldWorldSubLevel == null || clientContainer.getSubLevel(oldWorldSubLevel) != null) {
                    throw new IllegalStateException("M2 client old-world sub-level survived the world change");
                }
                sawWorldChangeCleanup = true;
                observedSubLevel = null;
                sawSingleBlockStone = false;
                singleBlockReadyTicks = 0;
                sawPlayerRiding = false;
                sawPlayerDismount = false;
                ridingDismountCommandSent = false;
                ridingVehicle = null;
                ridingObservedTicks = 0;
                sawRetainedRidingVehicle = false;
                rotatedEntityDrawsAtLogin = SableForgeRenderValidationTelemetry.rotatedEntityDraws();
                rotatedEntityMismatchesAtLogin = SableForgeRenderValidationTelemetry.rotatedEntityMatrixMismatches();
                maxRidingRenderAngle = 0.0D;
                sawRotatedEntityRender = false;
                rotatedNameTagDrawsAtLogin = SableForgeRenderValidationTelemetry.rotatedNameTagDraws();
                rotatedNameTagMismatchesAtLogin = SableForgeRenderValidationTelemetry.rotatedNameTagMatrixMismatches();
                sawRotatedNameTagRender = false;
            }
            if (observedSubLevel == null && !clientContainer.getAllSubLevels().isEmpty()) {
                final SubLevel subLevel = clientContainer.getAllSubLevels().get(0);
                if (loginPass == 1 && !sawWorldChangeCleanup) {
                    if (oldWorldSubLevel == null) {
                        oldWorldSubLevel = subLevel.getUniqueId();
                        initialDimension = minecraft.level.dimension();
                        LOGGER.info("Sable M2 client observed pre-change sub-level (uuid {}, world {})",
                                oldWorldSubLevel, initialDimension.location());
                    }
                } else {
                    observedSubLevel = subLevel.getUniqueId();
                    if (loginPass == 2 && observedSubLevel.equals(firstLifecycleSubLevel)) {
                        throw new IllegalStateException("M2 reconnect reused the first lifecycle sub-level");
                    }
                    initialSubLevelX = subLevel.logicalPose().position().x();
                    sawRealSubLevel = true;
                    LOGGER.info("Sable M2 client observed real sub-level on pass {} (uuid {}, initial x {})",
                            loginPass, observedSubLevel, initialSubLevelX);
                }
            }
            if (observedSubLevel != null) {
                final SubLevel subLevel = clientContainer.getSubLevel(observedSubLevel);
                if (subLevel != null
                        && Math.abs(subLevel.logicalPose().position().x() - initialSubLevelX) > 1.0D) {
                    sawMovedSubLevel = true;
                } else if (sawRealSubLevel && subLevel == null) {
                    sawRemovedSubLevel = true;
                }
                if (Boolean.getBoolean("sable.gizmoValidation") && !gizmoValidationCompleted
                        && subLevel != null) {
                    gizmoTargetTicks++;
                    if (!deniedGizmoPacketSent && gizmoTargetTicks >= 10) {
                        SablePacketManager.server().sendPacket(new ServerboundGizmoMoveSubLevelPacket(
                                observedSubLevel, new Vector3d(GIZMO_DESTINATION)));
                        deniedGizmoPacketSent = true;
                        LOGGER.info("Sable gizmo validation sent unauthorized production packet "
                                        + "(sub-level {}, destination {})",
                                observedSubLevel, GIZMO_DESTINATION);
                    } else if (deniedGizmoPacketSent && !allowedGizmoPacketSent && gizmoTargetTicks >= 50) {
                        SablePacketManager.server().sendPacket(new ServerboundGizmoMoveSubLevelPacket(
                                observedSubLevel, new Vector3d(GIZMO_DESTINATION)));
                        allowedGizmoPacketSent = true;
                        gizmoValidationCompleted = true;
                        LOGGER.info("Sable gizmo validation sent authorized production packet "
                                        + "(sub-level {}, destination {})",
                                observedSubLevel, GIZMO_DESTINATION);
                    }
                }
            }
            for (final SubLevel candidate : clientContainer.getAllSubLevels()) {
                if (Boolean.getBoolean("sable.movementValidation") && !movementNudgeSent
                        && minecraft.player != null && candidate instanceof final ClientSubLevel movementCandidate
                        && movementCandidate.isFinalized()
                        && ("M2 real lifecycle validation".equals(candidate.getName())
                        || "M2 renamed lifecycle validation".equals(candidate.getName()))) {
                    ((EntityMovementExtension) minecraft.player).sable$setTrackingSubLevel(candidate);
                    SableForgeMovementValidationProbe.armClientPacket(candidate);
                    final net.minecraft.world.phys.Vec3 before = minecraft.player.position();
                    minecraft.player.setPos(before.x + 0.125D, before.y, before.z);
                    movementNudgeSent = true;
                    LOGGER.info("Sable movement validation triggered a real vanilla position packet "
                                    + "(pass {}, sub-level {}, {} -> {})",
                            loginPass, candidate.getUniqueId(), before, minecraft.player.position());
                }
                if ("M2 renamed lifecycle validation".equals(candidate.getName())) {
                    sawRenamedState = true;
                }
                final dev.ryanhcode.sable.companion.math.BoundingBox3ic bounds =
                        candidate.getPlot().getBoundingBox();
                if (bounds != null && bounds.maxX() >= 3) {
                    sawExpandedBounds = true;
                    if (candidate instanceof final ClientSubLevel clientSubLevel
                            && clientSubLevel.getRenderData() instanceof final VanillaChunkedSubLevelRenderData chunked) {
                        sawChunkedRenderData = true;
                        final net.minecraft.core.BlockPos dynamicPos = new net.minecraft.core.BlockPos(
                                bounds.maxX(), bounds.minY(), bounds.minZ());
                        if (candidate.getLevel().getBlockState(dynamicPos).is(Blocks.GLASS)) {
                            sawDynamicGlassState = true;
                            if (!dynamicModelLayerInspected) {
                                final net.minecraft.core.BlockPos sectionOrigin =
                                        net.minecraft.core.SectionPos.of(dynamicPos).origin();
                                final net.minecraft.client.renderer.chunk.RenderChunkRegion region =
                                        new net.minecraft.client.renderer.chunk.RenderRegionCache().createRegion(
                                                candidate.getLevel(), sectionOrigin.offset(-1, -1, -1),
                                                sectionOrigin.offset(16, 16, 16), 1);
                                if (region != null) {
                                    final net.minecraft.world.level.block.state.BlockState regionState =
                                            region.getBlockState(dynamicPos);
                                    dynamicRegionGlass = regionState.is(Blocks.GLASS);
                                    final BakedModel model = minecraft.getBlockRenderer().getBlockModel(regionState);
                                    final net.minecraft.world.level.block.entity.BlockEntity blockEntity =
                                            region.getBlockEntity(dynamicPos);
                                    final ModelData initialModelData = blockEntity == null
                                            ? ModelData.EMPTY : blockEntity.getModelData();
                                    final ModelData modelData = model.getModelData(
                                            region, dynamicPos, regionState, initialModelData);
                                    final RandomSource random = RandomSource.create();
                                    random.setSeed(regionState.getSeed(dynamicPos));
                                    dynamicModelCutoutLayer = model.getRenderTypes(
                                            regionState, random, modelData).contains(RenderType.cutout());
                                    dynamicModelLayerInspected = true;
                                    LOGGER.info("Sable M3 target compiler layer evidence for dynamic glass at {} "
                                                    + "(region glass {}, model cutout {})",
                                            dynamicPos, dynamicRegionGlass, dynamicModelCutoutLayer);
                                }
                            }
                            dynamicRenderSections = Math.max(dynamicRenderSections, chunked.allRenderSections().size());
                            dynamicCutoutSections = Math.max(dynamicCutoutSections,
                                    (int) chunked.allRenderSections().stream().filter(section ->
                                            !section.getCompiledChunk().isEmpty(RenderType.cutout())).count());
                            dynamicSolidSections = Math.max(dynamicSolidSections,
                                    (int) chunked.allRenderSections().stream().filter(section ->
                                            !section.getCompiledChunk().isEmpty(RenderType.solid())).count());
                            if (dynamicModelLayerInspected && dynamicRegionGlass && dynamicModelCutoutLayer
                                    && dynamicCutoutSections > 0
                                    && SableForgeRenderValidationTelemetry.chunkCutoutDraws()
                                    > chunkCutoutDrawsAtLogin) {
                                sawDynamicCutoutRender = true;
                            }
                        }
                    }
                }
                if (candidate instanceof final ClientSubLevel clientSubLevel
                        && "M2 split child validation".equals(candidate.getName())
                        && clientSubLevel.getInterpolator().buffer.size() > 2) {
                    sawSplitInterpolationHistory = true;
                }
                if (bounds != null && candidate.getLevel().getBlockState(
                        new net.minecraft.core.BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()))
                        .is(Blocks.STONE)) {
                    sawClientStone = true;
                    if ("M3 stable render validation".equals(candidate.getName())) {
                        sawSingleBlockStone = true;
                    }
                }
                if (Boolean.getBoolean("sable.punchValidation") && !punchPacketSent
                        && waitedTicks >= 40
                        && ("M2 real lifecycle validation".equals(candidate.getName())
                        || "M2 renamed lifecycle validation".equals(candidate.getName()))) {
                    final net.minecraft.core.BlockPos punchedBlock = candidate.getPlot().getCenterBlock();
                    SablePacketManager.server().sendPacket(new ServerboundPunchSubLevelPacket(
                            punchedBlock, new Vector3d(), new Vector3d(1.0D, 0.0D, 0.0D)));
                    punchPacketSent = true;
                    LOGGER.info("Sable punch validation sent production serverbound packet "
                                    + "(pass {}, sub-level {}, punched block {}, direction +X)",
                            loginPass, candidate.getUniqueId(), punchedBlock);
                }
                if (candidate instanceof final ClientSubLevel clientFixture
                        && "M3 block entity render validation".equals(candidate.getName())
                        && bounds != null
                        && candidate.getLevel().getBlockEntity(new net.minecraft.core.BlockPos(
                        bounds.minX(), bounds.minY(), bounds.minZ())) instanceof ChestBlockEntity) {
                    sawClientChestBlockEntity = true;
                    if (candidate.getLevel().getBlockEntity(new net.minecraft.core.BlockPos(
                            bounds.minX() + 1, bounds.minY(), bounds.minZ())) instanceof KineticBlockEntity) {
                        sawClientCreateBlockEntity = true;
                        if (m3Enabled && minecraft.player != null) {
                            final Vector3d target = clientFixture.renderPose().transformPosition(new Vector3d(
                                    bounds.minX() + 1.5D, bounds.minY() + 0.5D, bounds.minZ() + 0.5D));
                            final Vec3 eye = minecraft.player.getEyePosition();
                            final double dx = target.x - eye.x;
                            final double dy = target.y - eye.y;
                            final double dz = target.z - eye.z;
                            final double horizontal = Math.sqrt(dx * dx + dz * dz);
                            final float baseYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                            final float yawOffset = switch (flywheelCullAngleStage) {
                                case 1 -> -12.0F;
                                case 2 -> 12.0F;
                                default -> 0.0F;
                            };
                            final float yaw = baseYaw + yawOffset;
                            final float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

                            minecraft.player.setYRot(yaw);
                            minecraft.player.setXRot(pitch);
                            minecraft.player.yRotO = yaw;
                            minecraft.player.xRotO = pitch;

                            final Vec3 expectedLook = new Vec3(dx, dy, dz).normalize();
                            final double alignment = minecraft.player.getViewVector(1.0F).dot(expectedLook);
                            if (alignment < 0.965D) {
                                throw new IllegalStateException("M3 camera aim did not converge on the real "
                                        + "Flywheel fixture: target=" + target + ", eye=" + eye
                                        + ", alignment=" + alignment + ", yaw/pitch=" + yaw + "/" + pitch);
                            }
                            if (!sawFlywheelFixtureAim) {
                                final ResourceLocation matrixSourceId = new ResourceLocation(
                                        "flywheel", "internal/indirect/matrices.glsl");
                                final String runtimeMatrixSource = FlwPrograms.SOURCES.get(matrixSourceId)
                                        .source.toString();
                                final String matrixPack = minecraft.getResourceManager().getResource(
                                                matrixSourceId.withPrefix("flywheel/"))
                                        .map(resource -> resource.sourcePackId()).orElse("missing");
                                SableForgeRenderValidationTelemetry.resetFlywheelEmbeddedCull();
                                sawFlywheelFixtureAim = true;
                                LOGGER.info("Sable M3 CAMERA FORCED ONTO REAL CHEST/CREATE FIXTURE "
                                                + "(target {}, eye {}, yaw/pitch {}/{}, alignment {}, "
                                                + "runtime matrix pack {}, extended 192-byte struct {})",
                                        target, eye, yaw, pitch, alignment, matrixPack,
                                        runtimeMatrixSource.contains("lightingSceneMatrix")
                                                && runtimeMatrixSource.contains("skyLightScale"));
                            }
                            flywheelFixtureAimTicks++;
                            flywheelCullAngleStageTicks++;
                            if (!sawFlywheelCullAngleSweep && flywheelCullAngleStageTicks >= 20
                                    && SableForgeRenderValidationTelemetry.flywheelEmbeddedInputInstances() > 0
                                    && SableForgeRenderValidationTelemetry.flywheelEmbeddedVisibleInstances() > 0) {
                                LOGGER.info("Sable M3 FLYWHEEL GPU CULL ANGLE {} VERIFIED "
                                                + "(yaw offset {}, samples/input/visible {}/{}/{})",
                                        flywheelCullAngleStage + 1, yawOffset,
                                        SableForgeRenderValidationTelemetry.flywheelEmbeddedCullSamples(),
                                        SableForgeRenderValidationTelemetry.flywheelEmbeddedInputInstances(),
                                        SableForgeRenderValidationTelemetry.flywheelEmbeddedVisibleInstances());
                                flywheelCullAngleStage++;
                                flywheelCullAngleStageTicks = 0;
                                if (flywheelCullAngleStage >= 3) {
                                    sawFlywheelCullAngleSweep = true;
                                } else {
                                    SableForgeRenderValidationTelemetry.resetFlywheelEmbeddedCull();
                                }
                            }
                        }
                    }
                }
                if (candidate instanceof final ClientSubLevel clientSubLevel
                        && "M3 physics motion validation".equals(candidate.getName())) {
                    final Vector3d logical = new Vector3d(clientSubLevel.logicalPose().position());
                    final Vector3d rendered = new Vector3d(clientSubLevel.renderPose().position());
                    if (firstPhysicsLogical == null) {
                        firstPhysicsLogical = new Vector3d(logical);
                        lastPhysicsLogical = new Vector3d(logical);
                        firstPhysicsRender = new Vector3d(rendered);
                        lastPhysicsRender = new Vector3d(rendered);
                        physicsLogicalSamples = 1;
                        physicsRenderSamples = 1;
                    } else {
                        if (logical.distance(lastPhysicsLogical) > 0.01D) {
                            physicsLogicalSamples++;
                            lastPhysicsLogical.set(logical);
                        }
                        if (rendered.distance(lastPhysicsRender) > 0.01D) {
                            physicsRenderSamples++;
                            lastPhysicsRender.set(rendered);
                        }
                        physicsLogicalDistance = Math.max(physicsLogicalDistance,
                                logical.distance(firstPhysicsLogical));
                        physicsRenderDistance = Math.max(physicsRenderDistance,
                                rendered.distance(firstPhysicsRender));
                    }
                }
            }
            if (Boolean.getBoolean("sable.ridingValidation") && minecraft.player != null) {
                for (final net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
                    if (!sawRetainedRidingVehicle
                            && entity instanceof net.minecraft.world.entity.vehicle.AbstractMinecart
                            && Sable.HELPER.getContaining(entity) instanceof ClientSubLevel vehicleSubLevel) {
                        sawRetainedRidingVehicle = true;
                        LOGGER.info("Sable CLIENT RETAINED RIDING VEHICLE TRACKING VERIFIED "
                                        + "(pass {}, vehicle {}, sub-level {}, local position {})",
                                loginPass, entity.getUUID(), vehicleSubLevel.getUniqueId(), entity.position());
                    }
                }
                final net.minecraft.world.entity.Entity vehicle = minecraft.player.getVehicle();
                if (vehicle != null && Sable.HELPER.getContaining(vehicle) instanceof ClientSubLevel vehicleSubLevel) {
                    final double renderAngle = new org.joml.Quaterniond(
                            vehicleSubLevel.renderPose().orientation()).angle();
                    maxRidingRenderAngle = Math.max(maxRidingRenderAngle, renderAngle);
                    final Vec3 localFeet = new Vec3(vehicle.getX(),
                            vehicle.getY() + vehicle.getPassengersRidingOffset()
                                    + minecraft.player.getMyRidingOffset(), vehicle.getZ());
                    final Vec3 eyeOffset = minecraft.player.getEyePosition()
                            .subtract(minecraft.player.position());
                    final Vec3 expected = vehicleSubLevel.logicalPose()
                            .transformPosition(localFeet.add(eyeOffset)).subtract(eyeOffset);
                    final double error = minecraft.player.position().distanceTo(expected);
                    if (error > 0.35D) {
                        throw new IllegalStateException("Client LocalPlayer rotated riding projection mismatch: "
                                + "expected=" + expected + ", actual=" + minecraft.player.position()
                                + ", error=" + error + ", vehicle=" + vehicle.getUUID());
                    }
                    if (!sawPlayerRiding) {
                        sawPlayerRiding = true;
                        ridingVehicle = vehicle.getUUID();
                        LOGGER.info("Sable REAL LOCALPLAYER ROTATED RIDING VERIFIED "
                                        + "(pass {}, vehicle {}, sub-level {}, expected {}, actual {}, error {})",
                                loginPass, ridingVehicle, vehicleSubLevel.getUniqueId(), expected,
                                minecraft.player.position(), error);
                    }
                    ridingObservedTicks++;
                    final float partialTick = minecraft.getFrameTime();
                    final float pitch = minecraft.player.getViewXRot(partialTick) * ((float) Math.PI / 180.0F);
                    final float yaw = -minecraft.player.getViewYRot(partialTick) * ((float) Math.PI / 180.0F);
                    final float cosYaw = net.minecraft.util.Mth.cos(yaw);
                    final float sinYaw = net.minecraft.util.Mth.sin(yaw);
                    final float cosPitch = net.minecraft.util.Mth.cos(pitch);
                    final float sinPitch = net.minecraft.util.Mth.sin(pitch);
                    final Vector3d rawView = new Vector3d(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
                    final Vector3d expectedView = vehicleSubLevel.logicalPose().orientation()
                            .transform(new Vector3d(rawView));
                    final Vec3 actualView = minecraft.player.getViewVector(partialTick);
                    final double viewError = expectedView.distance(new Vector3d(actualView.x, actualView.y, actualView.z));
                    if (renderAngle >= 0.5D && viewError > 1.0E-5D) {
                        throw new IllegalStateException("Production rotated view vector mismatch: expected="
                                + expectedView + ", actual=" + actualView + ", error=" + viewError);
                    }
                    if (!sawRotatedViewVector && renderAngle >= 0.5D
                            && expectedView.distance(rawView) > 0.1D) {
                        sawRotatedViewVector = true;
                        LOGGER.info("Sable REAL ROTATED VIEW VECTOR VERIFIED (pass {}, expected {}, actual {}, error {})",
                                loginPass, expectedView, actualView, viewError);
                    }
                    final net.minecraft.client.Camera camera = minecraft.gameRenderer.getMainCamera();
                    if (renderAngle >= 0.5D && CameraRotationValidationTelemetry.verified()
                            && !sawActualCameraRotation) {
                        sawActualCameraRotation = true;
                        LOGGER.info("Sable REAL CAMERA QUATERNION VERIFIED at Camera.setRotation (pass {})",
                                loginPass);
                    }

                    if (renderAngle >= 0.5D) {
                        if (!sawCameraPositionTransform) {
                            final Vec3 savedCameraPosition = camera.getPosition();
                            final Vec3 plotPosition = Vec3.atCenterOf(
                                    vehicleSubLevel.getPlot().getCenterBlock()).add(0.0D, 1.0D, 0.0D);
                            final Vec3 expectedCameraPosition = vehicleSubLevel.renderPose()
                                    .transformPosition(plotPosition);
                            final CameraPositionInvoker invoker = (CameraPositionInvoker) camera;
                            try {
                                invoker.sable$invokeSetPosition(plotPosition);
                                final double positionError = camera.getPosition()
                                        .distanceTo(expectedCameraPosition);
                                if (positionError > 1.0E-7D) {
                                    throw new IllegalStateException("Production Camera position transform mismatch: "
                                            + "input=" + plotPosition + ", expected=" + expectedCameraPosition
                                            + ", actual=" + camera.getPosition() + ", error=" + positionError);
                                }
                                sawCameraPositionTransform = true;
                                LOGGER.info("Sable REAL CAMERA POSITION TRANSFORM VERIFIED "
                                                + "(pass {}, input {}, output {}, error {})",
                                        loginPass, plotPosition, camera.getPosition(), positionError);
                            } finally {
                                invoker.sable$invokeSetPosition(savedCameraPosition);
                            }
                        }

                        if (!sawTransformedBlockOutline) {
                            final net.minecraft.core.BlockPos outlineBlock =
                                    vehicleSubLevel.getPlot().getCenterBlock();
                            minecraft.hitResult = new net.minecraft.world.phys.BlockHitResult(
                                    Vec3.atCenterOf(outlineBlock), net.minecraft.core.Direction.UP,
                                    outlineBlock, false);
                        }
                    }
                    final net.minecraft.client.renderer.item.ItemPropertyFunction compassAngle =
                            ItemProperties.getProperty(Items.COMPASS, new ResourceLocation("angle"));
                    if (compassAngle == null) {
                        throw new IllegalStateException("Vanilla compass angle property is unavailable");
                    }
                    compassAngle.call(new ItemStack(Items.COMPASS), minecraft.level, minecraft.player, 0);
                    final long rotatedDraws = SableForgeRenderValidationTelemetry.rotatedEntityDraws()
                            - rotatedEntityDrawsAtLogin;
                    final long matrixMismatches = SableForgeRenderValidationTelemetry.rotatedEntityMatrixMismatches()
                            - rotatedEntityMismatchesAtLogin;
                    final long nameTagDraws = SableForgeRenderValidationTelemetry.rotatedNameTagDraws()
                            - rotatedNameTagDrawsAtLogin;
                    final long nameTagMismatches = SableForgeRenderValidationTelemetry.rotatedNameTagMatrixMismatches()
                            - rotatedNameTagMismatchesAtLogin;
                    final long crosshairDraws = SableForgeRenderValidationTelemetry.rotatedDebugCrosshairDraws()
                            - rotatedCrosshairDrawsAtLogin;
                    final long crosshairMismatches = SableForgeRenderValidationTelemetry.rotatedDebugCrosshairMatrixMismatches()
                            - rotatedCrosshairMismatchesAtLogin;
                    final long compassAngles = SableForgeRenderValidationTelemetry.rotatedCompassAngles()
                            - rotatedCompassAnglesAtLogin;
                    final long compassMismatches = SableForgeRenderValidationTelemetry.rotatedCompassAngleMismatches()
                            - rotatedCompassMismatchesAtLogin;
                    if (matrixMismatches != 0L) {
                        throw new IllegalStateException("Production entity rotation matrix mismatch: samples="
                                + rotatedDraws + ", mismatches=" + matrixMismatches
                                + ", renderAngle=" + renderAngle);
                    }
                    if (nameTagMismatches != 0L) {
                        throw new IllegalStateException("Production rotated name-tag matrix mismatch: samples="
                                + nameTagDraws + ", mismatches=" + nameTagMismatches
                                + ", renderAngle=" + renderAngle);
                    }
                    if (crosshairMismatches != 0L || compassMismatches != 0L) {
                        throw new IllegalStateException("Production camera rotation mismatch: crosshair="
                                + crosshairDraws + "/" + crosshairMismatches + ", compass="
                                + compassAngles + "/" + compassMismatches);
                    }
                    if (!sawRotatedCompassAngle && compassAngles > 0L) {
                        sawRotatedCompassAngle = true;
                        LOGGER.info("Sable REAL ROTATED COMPASS ANGLE VERIFIED (pass {}, samples {}, mismatches {})",
                                loginPass, compassAngles, compassMismatches);
                    }
                    if (!sawRotatedDebugCrosshair && crosshairDraws > 0L) {
                        sawRotatedDebugCrosshair = true;
                        LOGGER.info("Sable REAL ROTATED DEBUG CROSSHAIR MATRIX VERIFIED "
                                        + "(pass {}, samples {}, mismatches {})",
                                loginPass, crosshairDraws, crosshairMismatches);
                    }
                    if (!sawRotatedNameTagRender && nameTagDraws > 0L) {
                        sawRotatedNameTagRender = true;
                        LOGGER.info("Sable REAL ROTATED NAME-TAG MATRIX VERIFIED "
                                        + "(pass {}, render angle {}, matrix samples {}, mismatches {})",
                                loginPass, renderAngle, nameTagDraws, nameTagMismatches);
                    }
                    if (ridingObservedTicks >= 80 && (maxRidingRenderAngle < 0.5D
                            || rotatedDraws == 0L || nameTagDraws == 0L || crosshairDraws == 0L
                            || compassAngles == 0L || !sawRotatedViewVector)) {
                        throw new IllegalStateException("Entity render rotation was not observed before timeout: "
                                + "ticks=" + ridingObservedTicks + ", maxRenderAngle=" + maxRidingRenderAngle
                                + ", rotatedDraws=" + rotatedDraws + ", nameTagDraws=" + nameTagDraws
                                + ", crosshairDraws=" + crosshairDraws + ", compassAngles=" + compassAngles
                                + ", viewVector=" + sawRotatedViewVector);
                    }
                    if (rotatedDraws == 0L || nameTagDraws == 0L) {
                        minecraft.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    } else {
                        minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                        minecraft.options.renderDebug = true;
                        minecraft.options.hideGui = false;
                        minecraft.options.reducedDebugInfo().set(false);
                    }
                    if (ridingObservedTicks >= 12 && renderAngle >= 0.5D
                            && rotatedDraws > 0L && nameTagDraws > 0L && crosshairDraws > 0L
                            && compassAngles > 0L && sawRotatedViewVector) {
                        if (!sawRotatedEntityRender) {
                            sawRotatedEntityRender = true;
                            LOGGER.info("Sable REAL ROTATED ENTITY RENDER MATRIX VERIFIED "
                                            + "(pass {}, vehicle {}, render angle {}, matrix samples {}, mismatches {})",
                                    loginPass, vehicle.getUUID(), renderAngle, rotatedDraws, matrixMismatches);
                        }
                        if (!ridingDismountCommandSent) {
                            minecraft.getConnection().send(new ServerboundPlayerCommandPacket(
                                    minecraft.player,
                                    ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
                            ridingDismountCommandSent = true;
                            LOGGER.info("Sable riding validation sent the vanilla PRESS_SHIFT_KEY player command "
                                            + "after all rotated render assertions passed (pass {}, vehicle {})",
                                    loginPass, vehicle.getUUID());
                        }
                    }
                } else if (sawPlayerRiding && !sawPlayerDismount) {
                    minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    minecraft.options.renderDebug = false;
                    minecraft.options.keyShift.setDown(false);
                    minecraft.player.input.shiftKeyDown = false;
                    if (Sable.HELPER.getContaining(minecraft.player) != null) {
                        throw new IllegalStateException("Dismounted LocalPlayer remained inside plot storage space");
                    }
                    sawPlayerDismount = true;
                    LOGGER.info("Sable REAL LOCALPLAYER ROTATED DISMOUNT STATE VERIFIED "
                                    + "(pass {}, former vehicle {}, position {})",
                            loginPass, ridingVehicle, minecraft.player.position());
                }
            }
            if (minecraft.player instanceof final PlayerFreezeExtension freezeExtension
                    && freezeExtension.sable$getFrozenToSubLevel() != null
                    && freezeExtension.sable$getFrozenToSubLevelAnchor() != null) {
                sawFreezeState = true;
                freezeTarget = freezeExtension.sable$getFrozenToSubLevel();
                freezeAnchor = new Vector3d(freezeExtension.sable$getFrozenToSubLevelAnchor());
            } else if (Boolean.getBoolean("sable.freezeValidation") && sawFreezeState
                    && !sawFreezeTeleport && minecraft.player != null && freezeTarget != null) {
                final SubLevel target = clientContainer.getSubLevel(freezeTarget);
                if (target instanceof final ClientSubLevel clientTarget && clientTarget.isFinalized()) {
                    final Vector3d expected = clientTarget.lastPose()
                            .transformPosition(new Vector3d(freezeAnchor));
                    final double error = expected.distance(new Vector3d(
                            minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ()));
                    final EntityMovementExtension movement = (EntityMovementExtension) minecraft.player;
                    final double inherited = ((LivingEntityMovementExtension) minecraft.player)
                            .sable$getInheritedVelocity().lengthSquared();
                    if (movement.sable$getTrackingSubLevel() != target || inherited != 0.0D
                            || error > 0.35D) {
                        throw new IllegalStateException("Client freeze teleport did not complete: tracking="
                                + (movement.sable$getTrackingSubLevel() == null ? null
                                : movement.sable$getTrackingSubLevel().getUniqueId())
                                + ", target=" + freezeTarget + ", inheritedSquared=" + inherited
                                + ", expected=" + expected + ", actual=" + minecraft.player.position()
                                + ", error=" + error);
                    }
                    sawFreezeTeleport = true;
                    LOGGER.info("Sable CLIENT PLAYER FREEZE TELEPORT VERIFIED "
                                    + "(pass {}, child {}, expected {}, actual {}, error {})",
                            loginPass, freezeTarget, expected, minecraft.player.position(), error);
                }
            }
            if (Boolean.getBoolean("sable.freezeValidation") && !sawFreezeTeleport) {
                final SableForgeFreezeValidationProbe.Snapshot probe =
                        SableForgeFreezeValidationProbe.snapshot();
                if (probe.serial() > freezeProbeSerialAtLogin) {
                    final double error = probe.expected().distance(probe.actual());
                    if (!probe.trackingMatches() || probe.inheritedSquared() != 0.0D || error > 0.35D) {
                        throw new IllegalStateException("Client freeze probe recorded an invalid teleport: target="
                                + probe.target() + ", trackingMatches=" + probe.trackingMatches()
                                + ", inheritedSquared=" + probe.inheritedSquared() + ", expected="
                                + probe.expected() + ", actual=" + probe.actual() + ", error=" + error);
                    }
                    sawFreezeState = true;
                    sawFreezeTeleport = true;
                    LOGGER.info("Sable CLIENT PLAYER FREEZE TELEPORT VERIFIED by tick-local probe "
                                    + "(pass {}, child {}, expected {}, actual {}, error {})",
                            loginPass, probe.target(), probe.expected(), probe.actual(), error);
                }
            }
            if (sawRealSubLevel && sawSingleBlockStone) {
                singleBlockReadyTicks++;
            }
            if (m3Enabled && sawFlywheelCullAngleSweep && flywheelCullAngleStageTicks >= 10
                    && !screenshotRequested
                    && SableForgeRenderValidationTelemetry.singleBlockDraws() > nativeDrawsAtLogin
                    && SableForgeRenderValidationTelemetry.chunkLayerDraws() > chunkDrawsAtLogin
                    && SableForgeRenderValidationTelemetry.blockEntityDraws() > blockEntityDrawsAtLogin) {
                screenshotRequested = true;
                final String fileName = "sable-m3-pass-" + loginPass + ".png";
                Screenshot.grab(minecraft.gameDirectory, fileName, minecraft.getMainRenderTarget(), message -> {
                    screenshotSaved = true;
                    LOGGER.info("Sable M3 screenshot saved as {} ({})", fileName, message.getString());
                });
            }
        }
        final double bedrockMass = ((BlockStateExtension) (Object) Blocks.BEDROCK.defaultBlockState())
                .sable$getProperty(PhysicsBlockPropertyTypes.MASS.get());
        final boolean packetCountsComplete =
                SableForgeNetworkValidationTelemetry.handledCount(ClientboundPhysicsPropertyPacket.class) >= 19
                        && SableForgeNetworkValidationTelemetry.handledCount(ClientboundDimensionPhysicsPacket.class) >= 1
                        && SableForgeNetworkValidationTelemetry.handledCount(ClientboundFloatingBlockMaterialPacket.class) >= 1;
        final boolean stateComplete = Double.compare(bedrockMass, 1000.0D) == 0
                && FloatingBlockMaterialDataHandler.allMaterials.containsKey(
                ResourceLocation.fromNamespaceAndPath(SableForgeBootstrap.MOD_ID, "end_stone"));
        final int tcpSnapshots = SableForgeNetworkValidationTelemetry.handledCount(ClientboundSableSnapshotDualPacket.class);
        final int tcpSnapshotInfo = SableForgeNetworkValidationTelemetry.handledCount(ClientboundSableSnapshotInfoDualPacket.class);
        final int udpSnapshots = SableForgeUDPValidationTelemetry.snapshotPacketsReceived();
        final int udpSnapshotInfo = SableForgeUDPValidationTelemetry.snapshotInfoPacketsReceived();
        final boolean snapshotTransportComplete = udpEnabled
                ? udpSnapshotInfo > udpSnapshotInfoAtLogin && udpSnapshots > udpSnapshotsAtLogin
                : tcpSnapshotInfo >= 1 && tcpSnapshots >= 1;
        sawSplitInterpolationHistory |= SableForgeNetworkValidationTelemetry.splitInterpolationHistory() > 2;
        sawFreezeState |= SableForgeNetworkValidationTelemetry.appliedFreezeStates()
                > appliedFreezeStatesAtLogin;
        final boolean trackingComplete = !m2Enabled
                || (SableForgeNetworkValidationTelemetry.handledCount(ClientboundStartTrackingSubLevelPacket.class) >= loginPass + 1
                && SableForgeNetworkValidationTelemetry.handledCount(ClientboundFinalizeSubLevelPacket.class) >= loginPass + 1
                && snapshotTransportComplete
                && SableForgeNetworkValidationTelemetry.handledCount(ClientboundStopMovingSubLevelPacket.class) >= 1
                && SableForgeNetworkValidationTelemetry.handledCount(ClientboundStopTrackingSubLevelPacket.class) >= loginPass + 1
                && SableForgeNetworkValidationTelemetry.handledCount(ClientboundChangeSubLevelNamePacket.class) >= loginPass
                && SableForgeNetworkValidationTelemetry.handledCount(ClientboundChangeBoundsSubLevelPacket.class) >= loginPass
                && SableForgeNetworkValidationTelemetry.handledCount(ClientboundFreezePlayerPacket.class) >= loginPass
                && SableForgeNetworkValidationTelemetry.handledCount(ClientboundRecentlySplitSubLevelPacket.class) >= loginPass
                && sawRealSubLevel && sawMovedSubLevel && sawRemovedSubLevel
                && sawRenamedState && sawExpandedBounds && sawFreezeState && sawSplitInterpolationHistory
                && (loginPass > 1 || sawWorldChangeCleanup));
        final boolean freezeComplete = !Boolean.getBoolean("sable.freezeValidation") || sawFreezeTeleport;
        final boolean movementComplete = !Boolean.getBoolean("sable.movementValidation")
                || SableForgeMovementValidationProbe.clientPackets() > 0;
        final boolean ridingComplete = !Boolean.getBoolean("sable.ridingValidation")
                || (sawPlayerRiding && sawPlayerDismount && sawRotatedEntityRender && sawRotatedNameTagRender
                && sawRotatedDebugCrosshair && sawRotatedCompassAngle && sawRotatedViewVector
                && sawActualCameraRotation && sawCameraPositionTransform && sawTransformedBlockOutline
                && SableForgeRenderValidationTelemetry.rotatedEntityDraws() > rotatedEntityDrawsAtLogin
                && SableForgeRenderValidationTelemetry.rotatedEntityMatrixMismatches()
                == rotatedEntityMismatchesAtLogin
                && SableForgeRenderValidationTelemetry.rotatedNameTagDraws() > rotatedNameTagDrawsAtLogin
                && SableForgeRenderValidationTelemetry.rotatedNameTagMatrixMismatches()
                == rotatedNameTagMismatchesAtLogin
                && SableForgeRenderValidationTelemetry.rotatedDebugCrosshairDraws() > rotatedCrosshairDrawsAtLogin
                && SableForgeRenderValidationTelemetry.rotatedDebugCrosshairMatrixMismatches()
                == rotatedCrosshairMismatchesAtLogin
                && SableForgeRenderValidationTelemetry.rotatedCompassAngles() > rotatedCompassAnglesAtLogin
                && SableForgeRenderValidationTelemetry.rotatedCompassAngleMismatches()
                == rotatedCompassMismatchesAtLogin);
        final boolean m3Complete = !m3Enabled || (sawClientStone && sawSingleBlockStone && screenshotSaved
                && sawChunkedRenderData && sawClientChestBlockEntity && sawClientCreateBlockEntity
                && sawFlywheelFixtureAim && sawFlywheelCullAngleSweep
                && sawDynamicCutoutRender && BackendManager.isBackendOn()
                && SableForgeRenderValidationTelemetry.singleBlockDraws() > nativeDrawsAtLogin
                && SableForgeRenderValidationTelemetry.chunkLayerDraws() > chunkDrawsAtLogin
                && SableForgeRenderValidationTelemetry.chunkCutoutDraws() > chunkCutoutDrawsAtLogin
                && SableForgeRenderValidationTelemetry.blockEntityDraws() > blockEntityDrawsAtLogin
                && SableForgeRenderValidationTelemetry.chestBlockEntityDraws() > chestBlockEntityDrawsAtLogin
                && SableForgeRenderValidationTelemetry.flywheelSubLevelVisuals() > flywheelVisualsAtLogin
                && SableForgeRenderValidationTelemetry.flywheelEmbeddedCullSamples() > 0
                && SableForgeRenderValidationTelemetry.flywheelEmbeddedVisibleInstances() > 0
                && (udpEnabled ? udpSnapshots >= udpSnapshotsAtLogin + 3 : tcpSnapshots >= snapshotsAtLogin + 3)
                && physicsLogicalSamples >= 3 && physicsRenderSamples >= 3
                && physicsLogicalDistance > 0.25D && physicsRenderDistance > 0.25D
                && SableForgeRenderValidationTelemetry.motionDrawPoseSamples() >= 3
                && SableForgeRenderValidationTelemetry.motionDrawPoseDistance() > 0.25D);

        if (packetCountsComplete && stateComplete && trackingComplete && m3Complete && freezeComplete
                && movementComplete && ridingComplete) {
            SableForgeBootstrap.verifyLoadedTagContents();
            awaitingSync = false;
            LOGGER.info("Sable staged client datapack sync verified (packet counts {}/{}/{}, bedrock mass {}, "
                            + "floating materials {})",
                    SableForgeNetworkValidationTelemetry.handledCount(ClientboundPhysicsPropertyPacket.class),
                    SableForgeNetworkValidationTelemetry.handledCount(ClientboundDimensionPhysicsPacket.class),
                    SableForgeNetworkValidationTelemetry.handledCount(ClientboundFloatingBlockMaterialPacket.class),
                    bedrockMass, FloatingBlockMaterialDataHandler.allMaterials.size());
            if (m2Enabled) {
                LOGGER.info("Sable M2 real tracking lifecycle verified on login pass {} "
                                + "(start/finalize/{}/stop-moving/stop-tracking counts {}/{}/{}/{}/{}, "
                                + "created/moved/removed=true)",
                        loginPass, udpEnabled ? "UDP snapshot-info/snapshot" : "TCP snapshot-info/snapshot",
                        SableForgeNetworkValidationTelemetry.handledCount(ClientboundStartTrackingSubLevelPacket.class),
                        SableForgeNetworkValidationTelemetry.handledCount(ClientboundFinalizeSubLevelPacket.class),
                        udpEnabled ? (udpSnapshotInfo + "/" + udpSnapshots)
                                : (tcpSnapshotInfo + "/" + tcpSnapshots),
                        SableForgeNetworkValidationTelemetry.handledCount(ClientboundStopMovingSubLevelPacket.class),
                        SableForgeNetworkValidationTelemetry.handledCount(ClientboundStopTrackingSubLevelPacket.class));
                if (udpEnabled) {
                    LOGGER.info("Sable REAL UDP SNAPSHOT DATA PLANE VERIFIED on login pass {} "
                                    + "(received snapshot-info/snapshot {}->{}/{}->{}, "
                                    + "client lifecycle and movement state consumed=true)",
                            loginPass, udpSnapshotInfoAtLogin, udpSnapshotInfo,
                            udpSnapshotsAtLogin, udpSnapshots);
                }
                LOGGER.info("Sable M2 packets 3/7/8/12 business states verified on login pass {} "
                                + "(counts {}/{}/{}/{}, rename/bounds/freeze/split=true)",
                        loginPass,
                        SableForgeNetworkValidationTelemetry.handledCount(ClientboundChangeSubLevelNamePacket.class),
                        SableForgeNetworkValidationTelemetry.handledCount(ClientboundChangeBoundsSubLevelPacket.class),
                        SableForgeNetworkValidationTelemetry.handledCount(ClientboundFreezePlayerPacket.class),
                        SableForgeNetworkValidationTelemetry.handledCount(ClientboundRecentlySplitSubLevelPacket.class));
                if (Boolean.getBoolean("sable.freezeValidation")) {
                    LOGGER.info("Sable CLIENT/SERVER FREEZE TELEPORT PATH VERIFIED on login pass {}",
                            loginPass);
                }
                if (Boolean.getBoolean("sable.movementValidation")) {
                    LOGGER.info("Sable CLIENT LOCAL MOVEMENT PACKET PATH VERIFIED on login pass {} "
                                    + "(converted packets {})",
                            loginPass, SableForgeMovementValidationProbe.clientPackets());
                }
                if (Boolean.getBoolean("sable.ridingValidation")) {
                    LOGGER.info("Sable CLIENT/SERVER REAL-PLAYER RIDING AND DISMOUNT PATH VERIFIED "
                                    + "on login pass {} (vehicle {})",
                            loginPass, ridingVehicle);
                }
                if (loginPass == 1) {
                    firstLifecycleSubLevel = observedSubLevel;
                    reconnectServer = minecraft.getCurrentServer();
                    if (reconnectServer == null || minecraft.getConnection() == null) {
                        throw new IllegalStateException("M2 validation cannot start same-process reconnect");
                    }
                    reconnectRequested = true;
                    minecraft.getConnection().getConnection().disconnect(
                            Component.literal("Sable M2 same-process reconnect validation"));
                } else if (loginPass == 2) {
                    LOGGER.info("Sable M2 SAME-PROCESS RECONNECT AND WORLD-CHANGE CLEANUP VERIFIED");
                    if (m3Enabled) {
                        LOGGER.info("Sable M3 CLIENT SINGLE/CHUNK/BLOCK-ENTITY/FLYWHEEL/DYNAMIC-REBUILD RENDER "
                                        + "AND CONTINUOUS PHYSICS VERIFIED (draws {}/{}/{}, Flywheel visuals {}, embedded cull samples/input/visible {}/{}/{}, snapshots {}, logical samples/distance {}/{}, "
                                        + "render samples/distance {}/{}, drawn-pose samples/distance {}/{})",
                                SableForgeRenderValidationTelemetry.singleBlockDraws() - nativeDrawsAtLogin,
                                SableForgeRenderValidationTelemetry.chunkLayerDraws() - chunkDrawsAtLogin,
                                SableForgeRenderValidationTelemetry.blockEntityDraws() - blockEntityDrawsAtLogin,
                                SableForgeRenderValidationTelemetry.flywheelSubLevelVisuals() - flywheelVisualsAtLogin,
                                SableForgeRenderValidationTelemetry.flywheelEmbeddedCullSamples(),
                                SableForgeRenderValidationTelemetry.flywheelEmbeddedInputInstances(),
                                SableForgeRenderValidationTelemetry.flywheelEmbeddedVisibleInstances(),
                                udpEnabled ? udpSnapshots - udpSnapshotsAtLogin : tcpSnapshots - snapshotsAtLogin,
                                physicsLogicalSamples, physicsLogicalDistance,
                                physicsRenderSamples, physicsRenderDistance,
                                SableForgeRenderValidationTelemetry.motionDrawPoseSamples(),
                                SableForgeRenderValidationTelemetry.motionDrawPoseDistance());
                    }
                }
            }
        } else if (waitedTicks > 600) {
            throw new IllegalStateException("Timed out waiting for staged Sable client datapack sync; packet counts "
                    + SableForgeNetworkValidationTelemetry.handledCount(ClientboundPhysicsPropertyPacket.class) + "/"
                    + SableForgeNetworkValidationTelemetry.handledCount(ClientboundDimensionPhysicsPacket.class) + "/"
                    + SableForgeNetworkValidationTelemetry.handledCount(ClientboundFloatingBlockMaterialPacket.class)
                    + ", bedrock mass " + bedrockMass + ", floating materials "
                    + FloatingBlockMaterialDataHandler.allMaterials.size()
                    + ", M2 packet counts start/finalize/snapshot-info/snapshot/stop-moving/stop-tracking "
                    + SableForgeNetworkValidationTelemetry.handledCount(ClientboundStartTrackingSubLevelPacket.class) + "/"
                    + SableForgeNetworkValidationTelemetry.handledCount(ClientboundFinalizeSubLevelPacket.class) + "/"
                    + (udpEnabled ? (udpSnapshotInfo + "(udp)") : (tcpSnapshotInfo + "(tcp)")) + "/"
                    + (udpEnabled ? (udpSnapshots + "(udp)") : (tcpSnapshots + "(tcp)")) + "/"
                    + SableForgeNetworkValidationTelemetry.handledCount(ClientboundStopMovingSubLevelPacket.class) + "/"
                    + SableForgeNetworkValidationTelemetry.handledCount(ClientboundStopTrackingSubLevelPacket.class)
                    + ", M2 3/7/8/12 counts "
                    + SableForgeNetworkValidationTelemetry.handledCount(ClientboundChangeSubLevelNamePacket.class) + "/"
                    + SableForgeNetworkValidationTelemetry.handledCount(ClientboundChangeBoundsSubLevelPacket.class) + "/"
                    + SableForgeNetworkValidationTelemetry.handledCount(ClientboundFreezePlayerPacket.class) + "/"
                    + SableForgeNetworkValidationTelemetry.handledCount(ClientboundRecentlySplitSubLevelPacket.class)
                    + ", states lifecycle/rename/bounds/freeze/split " + sawRealSubLevel + "/"
                    + sawMovedSubLevel + "/" + sawRemovedSubLevel + "/" + sawRenamedState + "/"
                    + sawExpandedBounds + "/" + sawFreezeState + "/" + sawSplitInterpolationHistory
                    + ", freeze teleport " + sawFreezeTeleport
                    + ", movement client packets " + SableForgeMovementValidationProbe.clientPackets()
                    + ", player riding/dismount " + sawPlayerRiding + "/" + sawPlayerDismount
                    + ", retained riding vehicle " + sawRetainedRidingVehicle
                    + ", M3 stone/single/chunk/chest/create/dynamic-state/dynamic-layer/backend/screenshot/draws single-chunk-be-flywheel/snapshots/motion "
                    + sawClientStone + "/" + sawSingleBlockStone + "/" + sawChunkedRenderData + "/"
                    + sawClientChestBlockEntity + "/" + sawClientCreateBlockEntity + "/"
                    + sawFlywheelFixtureAim + ":" + flywheelFixtureAimTicks + ":"
                    + flywheelCullAngleStage + ":" + sawFlywheelCullAngleSweep + "/"
                    + sawDynamicGlassState + "/" + sawDynamicCutoutRender + "/"
                    + BackendManager.isBackendOn() + "/" + screenshotSaved + "/"
                    + (SableForgeRenderValidationTelemetry.singleBlockDraws() - nativeDrawsAtLogin) + "/"
                    + (SableForgeRenderValidationTelemetry.chunkLayerDraws() - chunkDrawsAtLogin) + "/"
                    + (SableForgeRenderValidationTelemetry.blockEntityDraws() - blockEntityDrawsAtLogin) + "/"
                    + (SableForgeRenderValidationTelemetry.chestBlockEntityDraws()
                    - chestBlockEntityDrawsAtLogin) + "/"
                    + (SableForgeRenderValidationTelemetry.flywheelSubLevelVisuals() - flywheelVisualsAtLogin) + "/"
                    + SableForgeRenderValidationTelemetry.flywheelEmbeddedCullSamples() + ":"
                    + SableForgeRenderValidationTelemetry.flywheelEmbeddedInputInstances() + ":"
                    + SableForgeRenderValidationTelemetry.flywheelEmbeddedVisibleInstances() + "/"
                    + (udpEnabled ? udpSnapshots - udpSnapshotsAtLogin : tcpSnapshots - snapshotsAtLogin)
                    + "/" + physicsLogicalSamples + ":" + physicsLogicalDistance + "/"
                    + physicsRenderSamples + ":" + physicsRenderDistance + "/"
                    + SableForgeRenderValidationTelemetry.motionDrawPoseSamples() + ":"
                    + SableForgeRenderValidationTelemetry.motionDrawPoseDistance()
                    + ", dynamic sections total/cutout/solid " + dynamicRenderSections + "/"
                    + dynamicCutoutSections + "/" + dynamicSolidSections
                    + ", chunk dirty calls since login "
                    + (SableForgeRenderValidationTelemetry.chunkDirtyCalls() - chunkDirtyCallsAtLogin)
                    + ", dynamic compiler region-glass/model-cutout " + dynamicRegionGlass + "/"
                    + dynamicModelCutoutLayer
                    + ", cutout draws since login "
                    + (SableForgeRenderValidationTelemetry.chunkCutoutDraws() - chunkCutoutDrawsAtLogin));

        }
    }
}
