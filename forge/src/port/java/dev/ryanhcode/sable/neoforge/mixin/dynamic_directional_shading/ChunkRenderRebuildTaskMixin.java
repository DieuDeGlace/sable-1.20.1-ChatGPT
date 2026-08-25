package dev.ryanhcode.sable.neoforge.mixin.dynamic_directional_shading;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Forge 1.20.1 keeps chunk tessellation inside the private RebuildTask#compile
 * method instead of the 1.21 SectionCompiler used by upstream Sable.
 */
@Mixin(targets = "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask")
public abstract class ChunkRenderRebuildTaskMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean LOGGED_SUB_LEVEL_COMPILE = new AtomicBoolean();
    private static final int LAYER_RESULT_TRACE_LIMIT = 32;
    private static final AtomicInteger LAYER_RESULT_TRACES = new AtomicInteger();

    @Inject(method = "compile", at = @At("HEAD"))
    private void sable$clearCompileContext(final float cameraX, final float cameraY, final float cameraZ,
                                           final ChunkBufferBuilderPack buffers,
                                           final CallbackInfoReturnable<Object> cir) {
        SableDynamicDirectionalShading.setCompilingSubLevel(false);
    }

    @Inject(method = "compile", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;enableCaching()V",
            shift = At.Shift.AFTER))
    private void sable$beginSubLevelCompile(final float cameraX, final float cameraY, final float cameraZ,
                                            final ChunkBufferBuilderPack buffers,
                                            final CallbackInfoReturnable<Object> cir,
                                            final @Local(ordinal = 0) BlockPos origin) {
        final Minecraft minecraft = Minecraft.getInstance();
        final SubLevelContainer container = minecraft.level == null
                ? null : SubLevelContainer.getContainer(minecraft.level);
        final ChunkPos chunkPos = new ChunkPos(origin);
        final boolean onSubLevel = container != null && container.getPlot(chunkPos) != null;
        SableDynamicDirectionalShading.setCompilingSubLevel(onSubLevel);
        if (onSubLevel) {
            SableDynamicDirectionalShading.beginLightTrace("chunk:" + chunkPos);
        }

        if (onSubLevel && LOGGED_SUB_LEVEL_COMPILE.compareAndSet(false, true)) {
            LOGGER.info("[SABLE-DYNAMIC-SHADE-COMPILE] first sub-level mesh compile chunk={} thread={}",
                    chunkPos, Thread.currentThread().getName());
        }
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private void sable$endSubLevelCompile(final float cameraX, final float cameraY, final float cameraZ,
                                          final ChunkBufferBuilderPack buffers,
                                          final CallbackInfoReturnable<Object> cir) {
        if (SableDynamicDirectionalShading.isCompilingSubLevel()
                && LAYER_RESULT_TRACES.getAndIncrement() < LAYER_RESULT_TRACE_LIMIT
                && cir.getReturnValue() instanceof final ChunkRenderCompileResultsAccessor accessor) {
            final StringBuilder layers = new StringBuilder();
            accessor.sable$getRenderedLayers().forEach((renderType, renderedBuffer) -> {
                if (!layers.isEmpty()) {
                    layers.append(';');
                }
                layers.append('{')
                        .append("canonical=").append(sable$canonicalChunkLayer(renderType))
                        .append(",identity=").append(System.identityHashCode(renderType))
                        .append(",chunkLayerId=").append(renderType.getChunkLayerId())
                        .append(",vertices=").append(renderedBuffer.drawState().vertexCount())
                        .append('}');
            });
            LOGGER.info("SABLE-SUBLEVEL-CHUNK-COMPILE-LAYERS: thread={}, layerCount={}, layers={}",
                    Thread.currentThread().getName(), accessor.sable$getRenderedLayers().size(), layers);
        }
        SableDynamicDirectionalShading.endLightTrace();
        SableDynamicDirectionalShading.setCompilingSubLevel(false);
    }

    private static String sable$canonicalChunkLayer(final RenderType renderType) {
        if (renderType == RenderType.solid()) return "solid";
        if (renderType == RenderType.cutoutMipped()) return "cutout_mipped";
        if (renderType == RenderType.cutout()) return "cutout";
        if (renderType == RenderType.translucent()) return "translucent";
        if (renderType == RenderType.tripwire()) return "tripwire";
        return "non_canonical";
    }
}

/** Exposes the CPU-side layer buffers produced by a vanilla chunk rebuild. */
@Mixin(targets = "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask$CompileResults")
interface ChunkRenderCompileResultsAccessor {
    @Accessor("renderedLayers")
    Map<RenderType, BufferBuilder.RenderedBuffer> sable$getRenderedLayers();
}
