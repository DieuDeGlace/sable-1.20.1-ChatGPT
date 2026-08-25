package dev.ryanhcode.sable.neoforge.compatibility.flywheel;

import java.util.concurrent.atomic.AtomicInteger;

/** Bounded hand-off between visual creation and backend draw diagnostics. */
public final class SableFlywheelDiagnostics {
    private static final AtomicInteger EMBEDDED_VISUALS_CREATED = new AtomicInteger();

    private SableFlywheelDiagnostics() {
    }

    public static void embeddedVisualCreated() {
        EMBEDDED_VISUALS_CREATED.incrementAndGet();
    }

    public static int embeddedVisualsCreated() {
        return EMBEDDED_VISUALS_CREATED.get();
    }
}
