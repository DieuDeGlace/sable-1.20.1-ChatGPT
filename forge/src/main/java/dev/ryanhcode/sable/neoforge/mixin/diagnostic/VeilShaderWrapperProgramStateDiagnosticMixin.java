package dev.ryanhcode.sable.neoforge.mixin.diagnostic;

import dev.ryanhcode.sable.neoforge.diagnostic.GlProgramStateTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Labels Veil's ShaderInstance wrapper unbind without changing it. */
@Pseudo
@Mixin(targets = "foundry.veil.impl.client.render.shader.ShaderProgramImpl$Wrapper", remap = false)
public abstract class VeilShaderWrapperProgramStateDiagnosticMixin {
    private static final String SABLE$UNBIND =
            "Lfoundry/veil/api/client/render/shader/program/ShaderProgram;unbind()V";

    @Inject(method = "m_173362_", at = @At(value = "INVOKE", target = SABLE$UNBIND), remap = false)
    private void sable$beforeUnbind(final CallbackInfo ci) {
        GlProgramStateTrace.directProgramBoundary("veil-wrapper-unbind-head");
    }

    @Inject(method = "m_173362_", at = @At(value = "INVOKE", target = SABLE$UNBIND,
            shift = At.Shift.AFTER), remap = false)
    private void sable$afterUnbind(final CallbackInfo ci) {
        GlProgramStateTrace.directProgramBoundary("veil-wrapper-unbind-tail");
    }
}
