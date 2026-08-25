package dev.ryanhcode.sable.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticLogLimiterTest {
    @AfterEach
    void resetLimiter() {
        DiagnosticLogLimiter.resetForTests();
    }

    @Test
    void enforcesPerProbeLimitWithoutStarvingAnotherProbe() {
        assertTrue(DiagnosticLogLimiter.tryAcquire("probe-a", 10, 2));
        assertTrue(DiagnosticLogLimiter.tryAcquire("probe-a", 10, 2));
        assertFalse(DiagnosticLogLimiter.tryAcquire("probe-a", 10, 2));
        assertTrue(DiagnosticLogLimiter.tryAcquire("probe-b", 10, 2));
    }

    @Test
    void enforcesSharedTotalLimit() {
        assertTrue(DiagnosticLogLimiter.tryAcquire("probe-a", 2, 2));
        assertTrue(DiagnosticLogLimiter.tryAcquire("probe-b", 2, 2));
        assertFalse(DiagnosticLogLimiter.tryAcquire("probe-c", 2, 2));
    }

    @Test
    void zeroDisablesDiagnosticRecords() {
        assertFalse(DiagnosticLogLimiter.tryAcquire("probe", 0, 10));
        assertFalse(DiagnosticLogLimiter.tryAcquire("probe", 10, 0));
    }
}
