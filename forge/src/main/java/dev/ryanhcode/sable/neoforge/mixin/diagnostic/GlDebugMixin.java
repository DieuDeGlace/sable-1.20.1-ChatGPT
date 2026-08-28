package dev.ryanhcode.sable.neoforge.mixin.diagnostic;

import com.mojang.blaze3d.platform.GlDebug;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.neoforge.diagnostic.GlProgramStateTrace;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emits one bounded native call stack for the unresolved framebuffer depth-format fault. */
@Mixin(GlDebug.class)
public abstract class GlDebugMixin {
    @Unique
    private static final Logger SABLE$LOGGER = LogUtils.getLogger();
    @Unique
    private static boolean sable$loggedDepthFormatFault;
    @Unique
    private static boolean sable$loggedNoActiveProgramFault;

    @Inject(method = "printDebugLog", at = @At("HEAD"))
    private static void sable$captureDepthFormatCallsite(final int source, final int type, final int id,
                                                          final int severity, final int length,
                                                          final long messagePointer, final long userParam,
                                                          final CallbackInfo ci) {
        if (messagePointer == 0L || length <= 0) {
            return;
        }

        final String message = MemoryUtil.memUTF8(messagePointer, length);
        if (!sable$loggedDepthFormatFault && message.contains("Depth formats do not match")) {
            sable$loggedDepthFormatFault = true;
            if (dev.ryanhcode.sable.util.DiagnosticLogLimiter.tryAcquire("SABLE-GL-DEPTH-FORMAT-CALLSITE")) {
                SABLE$LOGGER.error("SABLE-GL-DEPTH-FORMAT-CALLSITE: source={}, type={}, id={}, severity={}, message={}",
                        source, type, id, severity, message, new Throwable("first native depth-format fault call stack"));
            }
        }

        if (!sable$loggedNoActiveProgramFault && message.contains("No active program")) {
            sable$loggedNoActiveProgramFault = true;
            GlProgramStateTrace.nativeNoActiveProgram(message);
            if (dev.ryanhcode.sable.util.DiagnosticLogLimiter.tryAcquire("SABLE-GL-NO-ACTIVE-PROGRAM-CALLSITE")) {
                SABLE$LOGGER.error("SABLE-GL-NO-ACTIVE-PROGRAM-CALLSITE: source={}, type={}, id={}, severity={}, message={}",
                        source, type, id, severity, message,
                        new Throwable("first native no-active-program fault call stack"));
            }
        }
    }
}
