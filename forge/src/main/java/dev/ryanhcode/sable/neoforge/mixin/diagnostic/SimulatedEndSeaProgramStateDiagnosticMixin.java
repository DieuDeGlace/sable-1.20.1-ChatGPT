package dev.ryanhcode.sable.neoforge.mixin.diagnostic;

import dev.ryanhcode.sable.neoforge.diagnostic.GlProgramStateTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Labels Simulated's End Sea Veil program unbind without changing it. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.end_sea.EndSeaRenderer", remap = false)
public abstract class SimulatedEndSeaProgramStateDiagnosticMixin {
    private static final String SABLE$UNBIND =
            "Lfoundry/veil/api/client/render/shader/program/ShaderProgram;unbind()V";

    @Inject(method = "renderLayers", at = @At(value = "INVOKE", target = SABLE$UNBIND), remap = false)
    private static void sable$beforeUnbind(final CallbackInfo ci) {
        GlProgramStateTrace.directProgramBoundary("simulated-end-sea-unbind-head");
    }

    @Inject(method = "renderLayers", at = @At(value = "INVOKE", target = SABLE$UNBIND,
            shift = At.Shift.AFTER), remap = false)
    private static void sable$afterUnbind(final CallbackInfo ci) {
        GlProgramStateTrace.directProgramBoundary("simulated-end-sea-unbind-tail");
    }
}
