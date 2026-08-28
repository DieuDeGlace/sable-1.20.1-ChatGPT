package dev.ryanhcode.sable.neoforge.mixin.diagnostic;

import dev.ryanhcode.sable.neoforge.diagnostic.GlProgramStateTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Labels CodeChickenLib's direct GL20 program binds and releases. */
@Pseudo
@Mixin(targets = "codechicken.lib.render.shader.ShaderProgram", remap = false)
public abstract class CodeChickenShaderProgramStateDiagnosticMixin {
    @Inject(method = "use", at = @At("HEAD"), remap = false)
    private void sable$beforeUse(final CallbackInfo ci) {
        GlProgramStateTrace.directProgramBoundary("codechicken-use-head");
    }

    @Inject(method = "use", at = @At("RETURN"), remap = false)
    private void sable$afterUse(final CallbackInfo ci) {
        GlProgramStateTrace.directProgramBoundary("codechicken-use-tail");
    }

    @Inject(method = "release", at = @At("HEAD"), remap = false)
    private void sable$beforeRelease(final CallbackInfo ci) {
        GlProgramStateTrace.directProgramBoundary("codechicken-release-head");
    }

    @Inject(method = "release", at = @At("RETURN"), remap = false)
    private void sable$afterRelease(final CallbackInfo ci) {
        GlProgramStateTrace.directProgramBoundary("codechicken-release-tail");
    }
}
