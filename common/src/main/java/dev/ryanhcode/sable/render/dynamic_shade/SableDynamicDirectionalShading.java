package dev.ryanhcode.sable.render.dynamic_shade;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class SableDynamicDirectionalShading {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LIGHT_TRACE_LIMIT = 48;
    private static final AtomicInteger LIGHT_TRACES_WRITTEN = new AtomicInteger();
    private static boolean isEnabled = false;
    private static final ThreadLocal<Boolean> COMPILING_SUB_LEVEL = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<LightTrace> LIGHT_TRACE = new ThreadLocal<>();

    public static boolean isEnabled() {
        return isEnabled;
    }

    public static void setIsEnabled(final boolean isEnabled) {
        SableDynamicDirectionalShading.isEnabled = isEnabled;
    }

    public static boolean isCompilingSubLevel() {
        return COMPILING_SUB_LEVEL.get();
    }

    public static void setCompilingSubLevel(final boolean compilingSubLevel) {
        COMPILING_SUB_LEVEL.set(compilingSubLevel);
    }

    public static void beginLightTrace(final String source) {
        LIGHT_TRACE.set(new LightTrace(source));
    }

    public static void recordLightCoordinates(final int blockUv, final int skyUv) {
        final LightTrace trace = LIGHT_TRACE.get();
        if (trace != null) {
            trace.record(blockUv, skyUv);
        }
    }

    public static void endLightTrace() {
        final LightTrace trace = LIGHT_TRACE.get();
        LIGHT_TRACE.remove();
        if (trace == null || LIGHT_TRACES_WRITTEN.getAndIncrement() >= LIGHT_TRACE_LIMIT) {
            return;
        }
        dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(LOGGER, "[SABLE-LIGHTMAP-COMPILE] source={} vertices={} blockUv=[{},{}] skyUv=[{},{}] samples={}",
                trace.source, trace.vertices,
                trace.vertices == 0 ? "n/a" : trace.minBlockUv,
                trace.vertices == 0 ? "n/a" : trace.maxBlockUv,
                trace.vertices == 0 ? "n/a" : trace.minSkyUv,
                trace.vertices == 0 ? "n/a" : trace.maxSkyUv,
                trace.samples);
    }

    private static final class LightTrace {
        private final String source;
        private final Set<String> samples = new LinkedHashSet<>();
        private int vertices;
        private int minBlockUv = Integer.MAX_VALUE;
        private int maxBlockUv = Integer.MIN_VALUE;
        private int minSkyUv = Integer.MAX_VALUE;
        private int maxSkyUv = Integer.MIN_VALUE;

        private LightTrace(final String source) {
            this.source = source;
        }

        private void record(final int blockUv, final int skyUv) {
            this.vertices++;
            this.minBlockUv = Math.min(this.minBlockUv, blockUv);
            this.maxBlockUv = Math.max(this.maxBlockUv, blockUv);
            this.minSkyUv = Math.min(this.minSkyUv, skyUv);
            this.maxSkyUv = Math.max(this.maxSkyUv, skyUv);
            if (this.samples.size() < 12) {
                this.samples.add(blockUv + "/" + skyUv);
            }
        }
    }
}
