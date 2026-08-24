package dev.ryanhcode.sable.validation;

public final class CameraRotationValidationTelemetry {
    private static volatile boolean verified;

    private CameraRotationValidationTelemetry() {
    }

    public static boolean verified() {
        return verified;
    }

    public static void markVerified() {
        verified = true;
    }

    public static void reset() {
        verified = false;
    }
}
