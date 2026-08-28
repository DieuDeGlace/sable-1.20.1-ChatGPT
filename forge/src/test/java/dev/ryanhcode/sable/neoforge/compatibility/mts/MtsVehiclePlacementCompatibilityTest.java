package dev.ryanhcode.sable.neoforge.compatibility.mts;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MtsVehiclePlacementCompatibilityTest {

    private static final Vec3 MTS_OFFSET = new Vec3(0.5, 1.0, 0.5);

    @Test
    void ordinaryIdentityPosePreservesClickedOrigin() {
        final BlockPos clickedPos = new BlockPos(12, 34, -56);

        final Vec3 projected = MtsVehiclePlacementCompatibility.projectClickedBlockOrigin(
                new Pose3d(), clickedPos);

        assertVecEquals(Vec3.atLowerCornerOf(clickedPos), projected);
    }

    @Test
    void projectedOriginMakesMtsOffsetReconstructTransformedPlacementPoint() {
        final Pose3d pose = new Pose3d(
                new Vector3d(80.25, 15.5, -42.75),
                new Quaterniond().rotationXYZ(Math.toRadians(18.0), Math.toRadians(91.0), Math.toRadians(-7.0)),
                new Vector3d(2.0, -1.0, 4.0),
                new Vector3d(1.0)
        );
        final BlockPos clickedPos = new BlockPos(7, 3, -9);
        final Vec3 localPlacementPoint = Vec3.atLowerCornerOf(clickedPos).add(MTS_OFFSET);

        final Vec3 projectedOrigin = MtsVehiclePlacementCompatibility.projectClickedBlockOrigin(pose, clickedPos);
        final Vec3 mtsFinalPosition = projectedOrigin.add(MTS_OFFSET);

        assertVecEquals(pose.transformPosition(localPlacementPoint), mtsFinalPosition);
    }

    @Test
    void pointMutationUsesPublicMtsStyleSetContract() {
        final TestPoint point = new TestPoint();
        final Vec3 projected = new Vec3(1.25, -2.5, 3.75);

        assertTrue(MtsVehiclePlacementCompatibility.trySetPointCoordinates(point, projected));
        assertEquals(projected.x, point.x, 1.0E-9);
        assertEquals(projected.y, point.y, 1.0E-9);
        assertEquals(projected.z, point.z, 1.0E-9);
    }

    @Test
    void missingPointMutationContractFailsWithoutChangingControlFlow() {
        assertFalse(MtsVehiclePlacementCompatibility.trySetPointCoordinates(new Object(), Vec3.ZERO));
    }

    private static void assertVecEquals(final Vec3 expected, final Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0E-9);
        assertEquals(expected.y, actual.y, 1.0E-9);
        assertEquals(expected.z, actual.z, 1.0E-9);
    }

    public static final class TestPoint {
        private double x;
        private double y;
        private double z;

        public TestPoint set(final double x, final double y, final double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }
    }
}
