package dev.ryanhcode.sable.util;

import dev.ryanhcode.sable.SableConfig;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process-lifetime budget shared by the temporary diagnostic probes retained in Sable and its add-ons.
 */
public final class DiagnosticLogLimiter {
    private static final AtomicInteger TOTAL_RECORDS = new AtomicInteger();
    private static final ConcurrentMap<String, AtomicInteger> RECORDS_BY_PROBE = new ConcurrentHashMap<>();

    private DiagnosticLogLimiter() {
    }

    public static boolean tryAcquire(final String probe) {
        return tryAcquire(probe, SableConfig.DIAGNOSTIC_LOG_TOTAL_LIMIT.get(),
                SableConfig.DIAGNOSTIC_LOG_PER_PROBE_LIMIT.get());
    }

    public static void info(final Logger logger, final String format, final Object... arguments) {
        if (tryAcquire(probeName(format))) {
            logger.info(format, arguments);
        }
    }

    public static void warn(final Logger logger, final String format, final Object... arguments) {
        if (tryAcquire(probeName(format))) {
            logger.warn(format, arguments);
        }
    }

    public static void error(final Logger logger, final String format, final Object... arguments) {
        if (tryAcquire(probeName(format))) {
            logger.error(format, arguments);
        }
    }

    private static String probeName(final String format) {
        final int colon = format.indexOf(':');
        final int bracket = format.indexOf(']');
        final int end = colon >= 0 && bracket >= 0 ? Math.min(colon, bracket)
                : Math.max(colon, bracket);
        return end < 0 ? format : format.substring(format.startsWith("[") ? 1 : 0, end);
    }

    @ApiStatus.Internal
    static boolean tryAcquire(final String probe, final int totalLimit, final int perProbeLimit) {
        if (totalLimit <= 0 || perProbeLimit <= 0 || TOTAL_RECORDS.get() >= totalLimit) {
            return false;
        }

        final AtomicInteger probeRecords = RECORDS_BY_PROBE.computeIfAbsent(probe, ignored -> new AtomicInteger());
        if (!incrementBelowLimit(probeRecords, perProbeLimit)) {
            return false;
        }
        if (!incrementBelowLimit(TOTAL_RECORDS, totalLimit)) {
            probeRecords.decrementAndGet();
            return false;
        }
        return true;
    }

    private static boolean incrementBelowLimit(final AtomicInteger counter, final int limit) {
        int current;
        do {
            current = counter.get();
            if (current >= limit) {
                return false;
            }
        } while (!counter.compareAndSet(current, current + 1));
        return true;
    }

    @ApiStatus.Internal
    static void resetForTests() {
        TOTAL_RECORDS.set(0);
        RECORDS_BY_PROBE.clear();
    }
}
