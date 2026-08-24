package dev.ryanhcode.sable.validation;

import org.joml.Vector3d;
import net.minecraft.client.renderer.RenderType;

import java.util.concurrent.atomic.AtomicLong;

/** Draw and pose evidence owned exclusively by the development validation mod. */
public final class SableForgeRenderValidationTelemetry {
    private static final AtomicLong SINGLE_BLOCK_DRAWS = new AtomicLong();
    private static final AtomicLong CHUNK_LAYER_DRAWS = new AtomicLong();
    private static final AtomicLong CHUNK_CUTOUT_DRAWS = new AtomicLong();
    private static final AtomicLong BLOCK_ENTITY_DRAWS = new AtomicLong();
    private static final AtomicLong CHEST_BLOCK_ENTITY_DRAWS = new AtomicLong();
    private static final AtomicLong FLYWHEEL_SUB_LEVEL_VISUALS = new AtomicLong();
    private static final AtomicLong FLYWHEEL_EMBEDDED_CULL_SAMPLES = new AtomicLong();
    private static final AtomicLong FLYWHEEL_EMBEDDED_INPUT_INSTANCES = new AtomicLong();
    private static final AtomicLong FLYWHEEL_EMBEDDED_VISIBLE_INSTANCES = new AtomicLong();
    private static final AtomicLong CHUNK_DIRTY_CALLS = new AtomicLong();
    private static Vector3d firstMotionDrawPose;
    private static Vector3d lastMotionDrawPose;
    private static int motionDrawPoseSamples;
    private static double motionDrawPoseDistance;
    private static final AtomicLong ROTATED_ENTITY_DRAWS = new AtomicLong();
    private static final AtomicLong ROTATED_ENTITY_MATRIX_MISMATCHES = new AtomicLong();
    private static final AtomicLong ROTATED_NAME_TAG_DRAWS = new AtomicLong();
    private static final AtomicLong ROTATED_NAME_TAG_MATRIX_MISMATCHES = new AtomicLong();
    private static final AtomicLong ROTATED_DEBUG_CROSSHAIR_DRAWS = new AtomicLong();
    private static final AtomicLong ROTATED_DEBUG_CROSSHAIR_MATRIX_MISMATCHES = new AtomicLong();
    private static final AtomicLong ROTATED_COMPASS_ANGLES = new AtomicLong();
    private static final AtomicLong ROTATED_COMPASS_ANGLE_MISMATCHES = new AtomicLong();

    private SableForgeRenderValidationTelemetry() {
    }

    public static void recordSingleBlockDraws(final int draws) {
        SINGLE_BLOCK_DRAWS.addAndGet(draws);
    }

    public static void recordChunkLayerDraws(final RenderType layer, final int draws) {
        CHUNK_LAYER_DRAWS.addAndGet(draws);
        if (layer == RenderType.cutout()) {
            CHUNK_CUTOUT_DRAWS.addAndGet(draws);
        }
    }

    public static void recordBlockEntityDraws(final int draws) {
        BLOCK_ENTITY_DRAWS.addAndGet(draws);
    }

    public static void recordChestBlockEntityDraw() {
        CHEST_BLOCK_ENTITY_DRAWS.incrementAndGet();
    }

    public static void recordFlywheelSubLevelVisual() {
        FLYWHEEL_SUB_LEVEL_VISUALS.incrementAndGet();
    }

    public static void recordFlywheelEmbeddedCull(final int models, final int inputInstances,
                                                   final int visibleInstances) {
        FLYWHEEL_EMBEDDED_CULL_SAMPLES.addAndGet(models);
        FLYWHEEL_EMBEDDED_INPUT_INSTANCES.addAndGet(inputInstances);
        FLYWHEEL_EMBEDDED_VISIBLE_INSTANCES.addAndGet(visibleInstances);
    }

    public static void resetFlywheelEmbeddedCull() {
        FLYWHEEL_EMBEDDED_CULL_SAMPLES.set(0L);
        FLYWHEEL_EMBEDDED_INPUT_INSTANCES.set(0L);
        FLYWHEEL_EMBEDDED_VISIBLE_INSTANCES.set(0L);
    }

    public static void recordChunkDirty() {
        CHUNK_DIRTY_CALLS.incrementAndGet();
    }

    public static long singleBlockDraws() { return SINGLE_BLOCK_DRAWS.get(); }
    public static long chunkLayerDraws() { return CHUNK_LAYER_DRAWS.get(); }
    public static long chunkCutoutDraws() { return CHUNK_CUTOUT_DRAWS.get(); }
    public static long blockEntityDraws() { return BLOCK_ENTITY_DRAWS.get(); }
    public static long chestBlockEntityDraws() { return CHEST_BLOCK_ENTITY_DRAWS.get(); }
    public static long flywheelSubLevelVisuals() { return FLYWHEEL_SUB_LEVEL_VISUALS.get(); }
    public static long flywheelEmbeddedCullSamples() { return FLYWHEEL_EMBEDDED_CULL_SAMPLES.get(); }
    public static long flywheelEmbeddedInputInstances() { return FLYWHEEL_EMBEDDED_INPUT_INSTANCES.get(); }
    public static long flywheelEmbeddedVisibleInstances() { return FLYWHEEL_EMBEDDED_VISIBLE_INSTANCES.get(); }
    public static long chunkDirtyCalls() { return CHUNK_DIRTY_CALLS.get(); }
    public static long rotatedEntityDraws() { return ROTATED_ENTITY_DRAWS.get(); }
    public static long rotatedEntityMatrixMismatches() { return ROTATED_ENTITY_MATRIX_MISMATCHES.get(); }
    public static long rotatedNameTagDraws() { return ROTATED_NAME_TAG_DRAWS.get(); }
    public static long rotatedNameTagMatrixMismatches() { return ROTATED_NAME_TAG_MATRIX_MISMATCHES.get(); }
    public static long rotatedDebugCrosshairDraws() { return ROTATED_DEBUG_CROSSHAIR_DRAWS.get(); }
    public static long rotatedDebugCrosshairMatrixMismatches() { return ROTATED_DEBUG_CROSSHAIR_MATRIX_MISMATCHES.get(); }
    public static long rotatedCompassAngles() { return ROTATED_COMPASS_ANGLES.get(); }
    public static long rotatedCompassAngleMismatches() { return ROTATED_COMPASS_ANGLE_MISMATCHES.get(); }

    public static void recordRotatedEntityDraw(final boolean matrixMatches) {
        ROTATED_ENTITY_DRAWS.incrementAndGet();
        if (!matrixMatches) {
            ROTATED_ENTITY_MATRIX_MISMATCHES.incrementAndGet();
        }
    }

    public static void recordRotatedNameTagDraw(final boolean matrixMatches) {
        ROTATED_NAME_TAG_DRAWS.incrementAndGet();
        if (!matrixMatches) {
            ROTATED_NAME_TAG_MATRIX_MISMATCHES.incrementAndGet();
        }
    }

    public static void recordRotatedDebugCrosshairDraw(final boolean matrixMatches) {
        ROTATED_DEBUG_CROSSHAIR_DRAWS.incrementAndGet();
        if (!matrixMatches) {
            ROTATED_DEBUG_CROSSHAIR_MATRIX_MISMATCHES.incrementAndGet();
        }
    }

    public static void recordRotatedCompassAngle(final boolean angleMatches) {
        ROTATED_COMPASS_ANGLES.incrementAndGet();
        if (!angleMatches) {
            ROTATED_COMPASS_ANGLE_MISMATCHES.incrementAndGet();
        }
    }

    public static synchronized void resetMotionDrawTelemetry() {
        firstMotionDrawPose = null;
        lastMotionDrawPose = null;
        motionDrawPoseSamples = 0;
        motionDrawPoseDistance = 0.0D;
    }

    public static synchronized void recordMotionDrawPose(final Vector3d position) {
        if (firstMotionDrawPose == null) {
            firstMotionDrawPose = new Vector3d(position);
            lastMotionDrawPose = new Vector3d(position);
            motionDrawPoseSamples = 1;
            return;
        }
        if (position.distance(lastMotionDrawPose) > 0.01D) {
            motionDrawPoseSamples++;
            lastMotionDrawPose.set(position);
        }
        motionDrawPoseDistance = Math.max(motionDrawPoseDistance, position.distance(firstMotionDrawPose));
    }

    public static synchronized int motionDrawPoseSamples() { return motionDrawPoseSamples; }
    public static synchronized double motionDrawPoseDistance() { return motionDrawPoseDistance; }
}
