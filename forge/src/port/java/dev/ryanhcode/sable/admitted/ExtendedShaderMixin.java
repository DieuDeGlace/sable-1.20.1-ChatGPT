package dev.ryanhcode.sable.mixin.compatibility.iris;

import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.shaders.Uniform;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.compatibility.iris.ExtendedShaderExtension;
import dev.ryanhcode.sable.neoforge.diagnostic.GlProgramStateTrace;
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

import java.util.HashSet;
import java.util.Set;

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
    @Unique
    private static final int sable$PROGRAM_RECOVERY_TRACE_LIMIT = 256;

    @Unique
    private static final Set<String> sable$TRACED_PROGRAM_RECOVERIES = new HashSet<>();

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
        final ShaderInstance shader = (ShaderInstance) (Object) this;
        final int expectedProgram = shader.getId();
        final int activeProgram = GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        GlProgramStateTrace.observedCheckpoint("extended-recovery-entry", shader, activeProgram, false);
        if (activeProgram == expectedProgram) {
            return;
        }

        final boolean expectedProgramValid = GL20C.glIsProgram(expectedProgram);

        // ExtendedShader caches the last Java object it applied. Veil and
        // Flywheel can legitimately change the actual GL program without
        // touching that private cache. Rebinding here restores the invariant
        // ShaderInstance.apply() promises before any uniforms are uploaded.
        ProgramManager.glUseProgram(expectedProgram);
        final int activeAfter = GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        final boolean recovered = activeAfter == expectedProgram;
        final int shaderIdentity = System.identityHashCode(shader);
        final String traceKey = shaderIdentity + ":" + expectedProgram + ":" + activeProgram;
        synchronized (sable$TRACED_PROGRAM_RECOVERIES) {
            if (sable$TRACED_PROGRAM_RECOVERIES.size() >= sable$PROGRAM_RECOVERY_TRACE_LIMIT
                    || !sable$TRACED_PROGRAM_RECOVERIES.add(traceKey)) {
                return;
            }
        }
        dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(Sable.LOGGER, "SABLE-IRIS-PROGRAM-CACHE-RECOVERY: shader={}, shaderIdentity={}, "
                        + "expectedProgram={}, expectedProgramValid={}, activeBefore={}, activeAfter={}, recovered={}",
                shader.getName(), shaderIdentity, expectedProgram, expectedProgramValid,
                activeProgram, activeAfter, recovered);
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
