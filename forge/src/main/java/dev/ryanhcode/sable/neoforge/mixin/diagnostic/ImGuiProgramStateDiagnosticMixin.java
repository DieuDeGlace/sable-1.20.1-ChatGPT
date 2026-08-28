package dev.ryanhcode.sable.neoforge.mixin.diagnostic;

import dev.ryanhcode.sable.neoforge.diagnostic.GlProgramStateTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Labels the embedded ImGui backend's direct GL32 program changes. */
@Pseudo
@Mixin(targets = "imgui.gl3.ImGuiImplGl3", remap = false)
public abstract class ImGuiProgramStateDiagnosticMixin {
    @Inject(method = "bind", at = @At("HEAD"), remap = false)
    private void sable$beforeBind(final int vertexArrayObject, final int vertexBuffer,
                                  final CallbackInfo ci) {
        GlProgramStateTrace.directProgramBoundary("imgui-bind-head");
    }

    @Inject(method = "bind", at = @At("RETURN"), remap = false)
    private void sable$afterBind(final int vertexArrayObject, final int vertexBuffer,
                                 final CallbackInfo ci) {
        GlProgramStateTrace.directProgramBoundary("imgui-bind-tail");
    }

    @Inject(method = "restoreModifiedGlState", at = @At("HEAD"), remap = false)
    private void sable$beforeRestore(final CallbackInfo ci) {
        GlProgramStateTrace.directProgramBoundary("imgui-restore-head");
    }

    @Inject(method = "restoreModifiedGlState", at = @At("RETURN"), remap = false)
    private void sable$afterRestore(final CallbackInfo ci) {
        GlProgramStateTrace.directProgramBoundary("imgui-restore-tail");
    }
}
