package dev.ryanhcode.sable.neoforge.mixin.diagnostic;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.ryanhcode.sable.neoforge.diagnostic.GlProgramStateTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records every GlStateManager program bind and unbind without changing it. */
@Mixin(value = GlStateManager.class, priority = 2000)
public abstract class GlStateManagerProgramStateDiagnosticMixin {
    @Inject(method = "_glUseProgram", at = @At("HEAD"))
    private static void sable$beforeUseProgram(final int program, final CallbackInfo ci) {
        GlProgramStateTrace.beforeGlStateManagerCall(program);
    }

    @Inject(method = "_glUseProgram", at = @At("RETURN"))
    private static void sable$afterUseProgram(final int program, final CallbackInfo ci) {
        GlProgramStateTrace.afterGlStateManagerCall(program);
    }
}
