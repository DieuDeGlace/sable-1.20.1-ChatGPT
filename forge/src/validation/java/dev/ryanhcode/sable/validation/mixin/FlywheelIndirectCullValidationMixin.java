package dev.ryanhcode.sable.validation.mixin;

import com.mojang.logging.LogUtils;
import dev.engine_room.flywheel.backend.engine.embed.EmbeddedEnvironment;
import dev.engine_room.flywheel.backend.engine.indirect.BufferBindings;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectBuffers;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectCullingGroup;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectInstancer;
import dev.ryanhcode.sable.neoforge.compatibility.flywheel.SableFlywheelMatrixBuffer;
import dev.ryanhcode.sable.validation.SableForgeRenderValidationTelemetry;
import net.minecraft.core.Vec3i;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reads Flywheel's real post-compute model descriptors in validation runs.  A visual
 * being created is not proof that the indirect backend admitted an instance through
 * its GPU culling pass; the descriptor's instanceCount is the draw command evidence.
 */
@Mixin(value = IndirectCullingGroup.class, remap = false)
public abstract class FlywheelIndirectCullValidationMixin {
    private static final Logger SABLE$LOGGER = LogUtils.getLogger();
    private static final AtomicInteger SABLE$MATRIX_DIAGNOSTICS = new AtomicInteger();

    @Shadow @Final private IndirectBuffers buffers;
    @Shadow @Final private List<IndirectInstancer<?>> instancers;
    @Shadow @Final private long instanceStride;

    @Inject(method = "submitSolid", at = @At("HEAD"))
    private void sable$readEmbeddedCullResults(final CallbackInfo ci) {
        if (this.instancers.isEmpty() || this.buffers.model.handle() == 0) {
            return;
        }

        final int byteCount = Math.toIntExact(this.instancers.size() * IndirectBuffers.MODEL_STRIDE);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final ByteBuffer descriptors = stack.malloc(byteCount);
            GL45.glGetNamedBufferSubData(this.buffers.model.handle(), 0L, descriptors);

            int embeddedModels = 0;
            int embeddedInstances = 0;
            int visibleEmbeddedInstances = 0;
            for (int i = 0; i < this.instancers.size(); i++) {
                final IndirectInstancer<?> instancer = this.instancers.get(i);
                if (instancer.environment.matrixIndex() <= 0) {
                    continue;
                }
                embeddedModels++;
                embeddedInstances += instancer.instanceCount();
                visibleEmbeddedInstances += descriptors.getInt(
                        Math.toIntExact(i * IndirectBuffers.MODEL_STRIDE));

                if (SABLE$MATRIX_DIAGNOSTICS.get() < 12) {
                    this.sable$recordExactMatrixDiagnostic(descriptors, i, instancer);
                }
            }

            if (embeddedModels > 0) {
                SableForgeRenderValidationTelemetry.recordFlywheelEmbeddedCull(
                        embeddedModels, embeddedInstances, visibleEmbeddedInstances);
            }
        }
    }

    private void sable$recordExactMatrixDiagnostic(final ByteBuffer descriptors, final int modelIndex,
                                                    final IndirectInstancer<?> instancer) {
        final int descriptorOffset = Math.toIntExact(modelIndex * IndirectBuffers.MODEL_STRIDE);
        final int matrixIndex = descriptors.getInt(descriptorOffset + 8);
        final int matrixHandle = GL45.glGetIntegeri(
                GL45.GL_SHADER_STORAGE_BUFFER_BINDING, BufferBindings.MATRICES);
        if (matrixIndex <= 0 || matrixHandle == 0
                || !(instancer.environment instanceof EmbeddedEnvironment)
                || !(instancer.environment instanceof EmbeddedEnvironmentValidationAccessor accessor)) {
            return;
        }

        final int diagnostic = SABLE$MATRIX_DIAGNOSTICS.getAndIncrement();
        if (diagnostic >= 12) {
            return;
        }

        final int matrixBytes = SableFlywheelMatrixBuffer.INFO_SIZE_BYTES;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final ByteBuffer gpuBytes = stack.malloc(matrixBytes);
            GL45.glGetNamedBufferSubData(matrixHandle, (long) matrixIndex * matrixBytes, gpuBytes);

            final float[] gpuPose = new float[16];
            for (int j = 0; j < gpuPose.length; j++) {
                gpuPose[j] = gpuBytes.getFloat(j * Float.BYTES);
            }

            final Matrix4f cpuMatrix = accessor.sable$getPoseComposed();
            final float[] cpuPose = new float[16];
            cpuMatrix.get(cpuPose);
            float maxError = 0.0f;
            for (int j = 0; j < cpuPose.length; j++) {
                maxError = Math.max(maxError, Math.abs(cpuPose[j] - gpuPose[j]));
            }

            final Vec3i renderOrigin = accessor.sable$getRenderOrigin();
            final String instanceDiagnostic = this.sable$readFirstInstance(modelIndex);
            SABLE$LOGGER.info("Sable FLYWHEEL MATRIX PIPELINE DIAGNOSTIC model/index/handle {}/{}/{}, "
                            + "instances/visible {}/{}, sphere [{}, {}, {}, {}], origin {}, "
                            + "CPU/GPU max error {}, CPU {}, GPU {}, {}",
                    modelIndex, matrixIndex, matrixHandle,
                    instancer.instanceCount(), descriptors.getInt(descriptorOffset),
                    descriptors.getFloat(descriptorOffset + 12),
                    descriptors.getFloat(descriptorOffset + 16),
                    descriptors.getFloat(descriptorOffset + 20),
                    descriptors.getFloat(descriptorOffset + 24),
                    renderOrigin, maxError, Arrays.toString(cpuPose), Arrays.toString(gpuPose),
                    instanceDiagnostic);
        }
    }

    private String sable$readFirstInstance(final int modelIndex) {
        final int descriptorHandle = this.buffers.objectStorage.frameDescriptorBuffer.handle();
        final int objectHandle = this.buffers.objectStorage.objectBuffer.handle();
        if (descriptorHandle == 0 || objectHandle == 0 || this.instanceStride <= 0) {
            return "instance buffers unavailable";
        }

        final int pageCount = this.buffers.objectStorage.capacity();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final ByteBuffer pageDescriptors = stack.malloc(pageCount * Integer.BYTES * 2);
            GL45.glGetNamedBufferSubData(descriptorHandle, 0L, pageDescriptors);
            for (int page = 0; page < pageCount; page++) {
                final int offset = page * Integer.BYTES * 2;
                final int descriptorModel = pageDescriptors.getInt(offset);
                final int validity = pageDescriptors.getInt(offset + Integer.BYTES);
                if (descriptorModel != modelIndex || validity == 0) {
                    continue;
                }

                final int localIndex = Integer.numberOfTrailingZeros(validity);
                final int sourceIndex = page * 32 + localIndex;
                final int byteCount = Math.toIntExact(this.instanceStride);
                final ByteBuffer instance = stack.malloc(byteCount);
                GL45.glGetNamedBufferSubData(objectHandle, (long) sourceIndex * this.instanceStride, instance);

                final float[] floatWords = new float[byteCount / Float.BYTES];
                for (int i = 0; i < floatWords.length; i++) {
                    floatWords[i] = instance.getFloat(i * Float.BYTES);
                }
                return "instance stride/source/page/bit " + this.instanceStride + "/" + sourceIndex
                        + "/" + page + "/" + localIndex + ", float words "
                        + Arrays.toString(floatWords);
            }
        }
        return "no live source instance for model " + modelIndex;
    }
}
