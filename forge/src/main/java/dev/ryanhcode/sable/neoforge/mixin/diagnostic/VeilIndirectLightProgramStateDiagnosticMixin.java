package dev.ryanhcode.sable.neoforge.mixin.diagnostic;

import dev.ryanhcode.sable.neoforge.diagnostic.GlProgramStateTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Labels Veil's deferred indirect-light program unbind without changing it. */
@Pseudo
@Mixin(targets = "foundry.veil.api.client.render.deferred.light.renderer.IndirectLightRenderer", remap = false)
public abstract class VeilIndirectLightProgramStateDiagnosticMixin {
    private static final String SABLE$UNBIND =
            "Lfoundry/veil/api/client/render/shader/program/ShaderProgram;unbind()V";

    @Inject(method = "updateVisibility", at = @At(value = "INVOKE", target = SABLE$UNBIND), remap = false)
    private void sable$beforeUnbind(final CallbackInfoReturnable<Integer> cir) {
        GlProgramStateTrace.directProgramBoundary("veil-indirect-unbind-head");
    }

    @Inject(method = "updateVisibility", at = @At(value = "INVOKE", target = SABLE$UNBIND,
            shift = At.Shift.AFTER), remap = false)
    private void sable$afterUnbind(final CallbackInfoReturnable<Integer> cir) {
        GlProgramStateTrace.directProgramBoundary("veil-indirect-unbind-tail");
    }
}
