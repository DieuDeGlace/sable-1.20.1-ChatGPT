package dev.ryanhcode.sable.render.dynamic_shade;

public class SableDynamicDirectionalShading {
    private static boolean isEnabled = false;
    private static final ThreadLocal<Boolean> COMPILING_SUB_LEVEL = ThreadLocal.withInitial(() -> false);

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

}
