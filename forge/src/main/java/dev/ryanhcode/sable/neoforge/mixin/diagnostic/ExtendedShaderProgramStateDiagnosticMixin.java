package dev.ryanhcode.sable.neoforge.mixin.diagnostic;

import com.mojang.blaze3d.shaders.Uniform;
import dev.ryanhcode.sable.neoforge.diagnostic.GlProgramStateTrace;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Exact checkpoints for Oculus 1.8.0 ExtendedShader.
 *
 * <p>Production Oculus exposes the overridden methods under SRG names. Every
 * injection therefore uses the bytecode names verified from the locked JAR and
 * disables remapping.</p>
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ExtendedShader", remap = false, priority = 900)
public abstract class ExtendedShaderProgramStateDiagnosticMixin {
    @Inject(method = "m_173363_", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/uniforms/CapturedRenderingState;setCurrentAlphaTest(F)V",
            shift = At.Shift.AFTER), remap = false)
    private void sable$afterInjectedHeads(final CallbackInfo ci) {
        GlProgramStateTrace.checkpoint("extended-apply-after-heads", shader(), false);
    }

    @Inject(method = "m_173363_", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/shaders/ProgramManager;m_85578_(I)V",
            shift = At.Shift.AFTER), remap = false)
    private void sable$afterProgramBind(final CallbackInfo ci) {
        GlProgramStateTrace.checkpoint("extended-apply-after-own-bind", shader(), false);
    }

    @Inject(method = "m_173363_", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/IrisRenderSystem;texParameteriv(III[I)V",
            shift = At.Shift.AFTER), remap = false)
    private void sable$afterIntensitySwizzle(final CallbackInfo ci) {
        GlProgramStateTrace.checkpoint("extended-apply-after-intensity-swizzle", shader(), false);
    }

    @Inject(method = "m_173363_", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/IrisRenderSystem;bindTextureToUnit(III)V",
            ordinal = 0, shift = At.Shift.AFTER), remap = false)
    private void sable$afterTextureZero(final CallbackInfo ci) {
        GlProgramStateTrace.checkpoint("extended-apply-after-texture-0", shader(), false);
    }

    @Inject(method = "m_173363_", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/IrisRenderSystem;bindTextureToUnit(III)V",
            ordinal = 1, shift = At.Shift.AFTER), remap = false)
    private void sable$afterTextureOne(final CallbackInfo ci) {
        GlProgramStateTrace.checkpoint("extended-apply-after-texture-1", shader(), false);
    }

    @Inject(method = "m_173363_", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/IrisRenderSystem;bindTextureToUnit(III)V",
            ordinal = 2, shift = At.Shift.AFTER), remap = false)
    private void sable$afterTextureTwo(final CallbackInfo ci) {
        GlProgramStateTrace.checkpoint("extended-apply-after-texture-2", shader(), false);
    }

    @Inject(method = "m_173363_", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/pipeline/programs/ExtendedShader;uploadIfNotNull(Lcom/mojang/blaze3d/shaders/Uniform;)V",
            ordinal = 0), remap = false)
    private void sable$beforeFirstUniformUpload(final CallbackInfo ci) {
        GlProgramStateTrace.checkpoint("extended-before-first-uniform-upload", shader(), true);
    }

    @Inject(method = "m_173363_", at = @At("RETURN"), remap = false)
    private void sable$afterApply(final CallbackInfo ci) {
        GlProgramStateTrace.checkpoint("extended-apply-tail", shader(), false);
    }

    @Inject(method = "m_173362_", at = @At("HEAD"), remap = false)
    private void sable$beforeClear(final CallbackInfo ci) {
        GlProgramStateTrace.enterShaderClear("extended", shader());
    }

    @Inject(method = "m_173362_", at = @At("RETURN"), remap = false)
    private void sable$afterClear(final CallbackInfo ci) {
        GlProgramStateTrace.exitShaderClear("extended", shader());
    }

    private ShaderInstance shader() {
        return (ShaderInstance) (Object) this;
    }
}
