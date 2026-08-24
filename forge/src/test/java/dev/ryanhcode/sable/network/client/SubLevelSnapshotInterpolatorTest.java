package dev.ryanhcode.sable.network.client;

import dev.ryanhcode.sable.companion.math.Pose3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SubLevelSnapshotInterpolatorTest {

    @Test
    void samplesExactAndInterpolatedPositions() {
        final SubLevelSnapshotInterpolator interpolator = new SubLevelSnapshotInterpolator(pose(-5.0));
        interpolator.receiveSnapshot(10, pose(0.0));
        interpolator.receiveSnapshot(20, pose(10.0));

        interpolator.tick(10.0);
        assertEquals(0.0, interpolator.getInterpolatedPose().position().x(), 1.0E-9);

        interpolator.tick(15.0);
        assertEquals(5.0, interpolator.getInterpolatedPose().position().x(), 1.0E-9);
    }

    @Test
    void clampsDeadReckoningToOneTickAndStopsExtrapolating() {
        final SubLevelSnapshotInterpolator interpolator = new SubLevelSnapshotInterpolator(pose(-5.0));
        interpolator.receiveSnapshot(19, pose(9.0));
        interpolator.receiveSnapshot(20, pose(10.0));

        interpolator.tick(25.0);
        assertEquals(11.0, interpolator.getInterpolatedPose().position().x(), 1.0E-9);

        interpolator.receiveStop();
        interpolator.tick(25.0);
        assertEquals(10.0, interpolator.getInterpolatedPose().position().x(), 1.0E-9);
    }

    @Test
    void usesFirstFutureSnapshotAndPrunesSnapshotsOlderThanSixTicks() {
        final SubLevelSnapshotInterpolator interpolator = new SubLevelSnapshotInterpolator(pose(-5.0));
        interpolator.receiveSnapshot(0, pose(0.0));
        interpolator.receiveSnapshot(5, pose(5.0));
        interpolator.receiveSnapshot(10, pose(10.0));

        interpolator.tick(-2.0);
        assertEquals(0.0, interpolator.getInterpolatedPose().position().x(), 1.0E-9);

        interpolator.tick(12.0);
        assertEquals(1, interpolator.buffer.size());
        assertEquals(10, interpolator.buffer.get(0).gameTick());
    }

    @Test
    void ignoresASecondSnapshotForTheSameTick() {
        final SubLevelSnapshotInterpolator interpolator = new SubLevelSnapshotInterpolator(pose(-5.0));
        interpolator.receiveSnapshot(10, pose(1.0));
        interpolator.receiveSnapshot(10, pose(99.0));

        interpolator.tick(10.0);
        assertEquals(1, interpolator.buffer.size());
        assertEquals(1.0, interpolator.getInterpolatedPose().position().x(), 1.0E-9);
    }

    private static Pose3d pose(final double x) {
        final Pose3d pose = new Pose3d();
        pose.position().set(x, 0.0, 0.0);
        return pose;
    }
}
