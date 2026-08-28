package dev.ryanhcode.sable.compatibility;

/**
 * Controls the Forge 1.20.1 Oculus program-cache compatibility recovery.
 *
 * <p>The configured value is persistent. A command override affects only the
 * current process, allowing an A/B test without changing the client config.</p>
 */
public final class SableIrisProgramRecovery {
    public enum Mode {
        CONFIGURED,
        ENABLED,
        DISABLED
    }

    private static volatile boolean configuredEnabled = true;
    private static volatile Mode mode = Mode.CONFIGURED;

    private SableIrisProgramRecovery() {
    }

    public static boolean isEnabled() {
        return switch (mode) {
            case CONFIGURED -> configuredEnabled;
            case ENABLED -> true;
            case DISABLED -> false;
        };
    }

    public static void setConfiguredEnabled(final boolean enabled) {
        configuredEnabled = enabled;
    }

    public static void setMode(final Mode requestedMode) {
        mode = requestedMode;
    }

    public static Mode mode() {
        return mode;
    }

    public static boolean configuredEnabled() {
        return configuredEnabled;
    }
}
