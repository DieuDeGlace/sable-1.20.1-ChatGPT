package dev.ryanhcode.sable.neoforge.mixin.diagnostic;

import com.mojang.blaze3d.shaders.ProgramManager;
import dev.ryanhcode.sable.neoforge.diagnostic.GlProgramStateTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Detects requests that Oculus' Java cache cancels despite different native state. */
@Mixin(ProgramManager.class)
public abstract class ProgramManagerProgramStateDiagnosticMixin {
    @Inject(method = "glUseProgram", at = @At("HEAD"))
    private static void sable$beforeUseProgram(final int program, final CallbackInfo ci) {
        GlProgramStateTrace.beforeProgramManagerCall(program);
    }

    @Inject(method = "glUseProgram", at = @At("RETURN"))
    private static void sable$afterUseProgram(final int program, final CallbackInfo ci) {
        GlProgramStateTrace.afterProgramManagerCall(program);
    }
}
