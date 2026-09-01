package dev.ryanhcode.sable.mixin.compatibility.iris;

import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.shaders.Uniform;
import dev.ryanhcode.sable.compatibility.SableIrisProgramRecovery;
import dev.ryanhcode.sable.mixinterface.compatibility.iris.ExtendedShaderExtension;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20C;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target-version adapter for the upstream Iris matrix bridge.
 *
 * <p>The string target keeps Oculus optional. 1.20.1 Oculus ships its
 * {@code ShaderInstance.apply()} override under the production SRG name, so
 * the cache-coherence injection deliberately targets that name without
 * remapping.</p>
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.programs.ExtendedShader", remap = false)
public class ExtendedShaderMixin implements ExtendedShaderExtension {
    @Shadow
    @Final
    private Uniform modelViewInverse;

    @Shadow
    @Final
    private Uniform normalMatrix;

    @Shadow
    @Final
    private Matrix4f tempMatrix4f;

    @Shadow
    @Final
    private Matrix3f tempMatrix3f;

    @Shadow
    @Final
    private float[] tempFloats;

    @Shadow
    @Final
    private float[] tempFloats2;

    @Inject(method = "m_173363_", at = @At("HEAD"), remap = false)
    private void sable$restoreProgramCacheCoherence(final CallbackInfo ci) {
        if (!SableIrisProgramRecovery.isEnabled()) {
            return;
        }

        final ShaderInstance shader = (ShaderInstance) (Object) this;
        final int expectedProgram = shader.getId();
        final int activeProgram = GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        if (activeProgram == expectedProgram) {
            return;
        }

        // ExtendedShader and Oculus' GlStateManager mixin both cache program
        // state. A renderer that calls native glUseProgram directly can change
        // the real context without updating either cache. Use the canonical
        // path first so a non-stale Oculus cache is updated normally.
        ProgramManager.glUseProgram(expectedProgram);

        // Oculus 1.8.0 can cancel a canonical bind when its cached value
        // already equals the request. If the real state remains mismatched
        // after the canonical attempt, bypass the cached path once.
        if (GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM) != expectedProgram) {
            GL20C.glUseProgram(expectedProgram);
        }
    }

    @Unique
    @Override
    public void sable$refreshModelMatrices() {
        final var modelView = ((ShaderInstance) (Object) this).MODEL_VIEW_MATRIX;

        if (modelView != null) {
            if (this.modelViewInverse != null) {
                this.modelViewInverse.set(this.tempMatrix4f.set(modelView.getFloatBuffer()).invert().get(this.tempFloats));
                this.modelViewInverse.upload();
            }

            if (this.normalMatrix != null) {
                this.normalMatrix.set(this.tempMatrix3f.set(this.tempMatrix4f.set(modelView.getFloatBuffer())).invert().transpose().get(this.tempFloats2));
                this.normalMatrix.upload();
            }
        }
    }
}
