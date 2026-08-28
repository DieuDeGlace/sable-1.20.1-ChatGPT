package dev.ryanhcode.sable.neoforge.mixin.diagnostic;

import dev.ryanhcode.sable.neoforge.diagnostic.GlProgramStateTrace;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Labels vanilla ShaderInstance.clear() calls in the program-state ring. */
@Mixin(ShaderInstance.class)
public abstract class ShaderInstanceProgramStateDiagnosticMixin {
    @Inject(method = "clear", at = @At("HEAD"))
    private void sable$beforeClear(final CallbackInfo ci) {
        GlProgramStateTrace.enterShaderClear("vanilla", (ShaderInstance) (Object) this);
    }

    @Inject(method = "clear", at = @At("RETURN"))
    private void sable$afterClear(final CallbackInfo ci) {
        GlProgramStateTrace.exitShaderClear("vanilla", (ShaderInstance) (Object) this);
    }
}
