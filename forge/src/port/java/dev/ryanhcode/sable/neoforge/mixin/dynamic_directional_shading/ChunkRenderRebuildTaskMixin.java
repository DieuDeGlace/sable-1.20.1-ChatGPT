package dev.ryanhcode.sable.neoforge.mixin.dynamic_directional_shading;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forge 1.20.1 keeps chunk tessellation inside the private RebuildTask#compile
 * method instead of the 1.21 SectionCompiler used by upstream Sable.
 */
@Mixin(targets = "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask")
public abstract class ChunkRenderRebuildTaskMixin {
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
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private void sable$endSubLevelCompile(final float cameraX, final float cameraY, final float cameraZ,
                                          final ChunkBufferBuilderPack buffers,
                                          final CallbackInfoReturnable<Object> cir) {
        SableDynamicDirectionalShading.setCompilingSubLevel(false);
    }
}
