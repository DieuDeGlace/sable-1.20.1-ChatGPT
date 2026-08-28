package dev.ryanhcode.sable.mixinhelpers.entity.entity_riding_sub_level_vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RidingRotationCaptureTest {

    @Test
    void reportsNoWriteWhenMountCallbackLeavesRotationAlone() {
        final Object rider = new Object();

        RidingRotationCapture.begin(rider);
        final RidingRotationCapture.Result result = RidingRotationCapture.finish(rider);

        assertTrue(result.captured());
        assertFalse(result.rotationWritten());
    }

    @Test
    void recordsOnlyWritesToTheActiveRider() {
        final Object rider = new Object();

        RidingRotationCapture.begin(rider);
        RidingRotationCapture.recordRotationWrite(new Object());
        assertFalse(RidingRotationCapture.finish(rider).rotationWritten());

        RidingRotationCapture.begin(rider);
        RidingRotationCapture.recordRotationWrite(rider);
        assertTrue(RidingRotationCapture.finish(rider).rotationWritten());
    }

    @Test
    void nestedCapturesRemainIndependent() {
        final Object outer = new Object();
        final Object inner = new Object();

        RidingRotationCapture.begin(outer);
        RidingRotationCapture.begin(inner);
        RidingRotationCapture.recordRotationWrite(outer);

        assertFalse(RidingRotationCapture.finish(inner).rotationWritten());
        assertTrue(RidingRotationCapture.finish(outer).rotationWritten());
        assertFalse(RidingRotationCapture.finish(outer).captured());
    }
}
