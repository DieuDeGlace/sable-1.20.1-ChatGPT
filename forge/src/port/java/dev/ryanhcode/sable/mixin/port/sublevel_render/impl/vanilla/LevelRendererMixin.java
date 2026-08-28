package dev.ryanhcode.sable.mixin.port.sublevel_render.impl.vanilla;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 1.20.1 adapter for upstream Sable's vanilla section-render hooks.
 * Physical sections must draw before the active vanilla shader is cleared so
 * Sampler2, the lightmap and every vanilla uniform remain the same draw state.
 */
@Mixin(value = LevelRenderer.class, priority = 1002)
public abstract class LevelRendererMixin {

    @Shadow
    private @Nullable ClientLevel level;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "compileChunks", at = @At("TAIL"))
    private void sable$compileSections(final Camera camera, final CallbackInfo ci) {
        if (this.level == null) {
            return;
        }

        final Iterable<ClientSubLevel> sublevels = ((ClientSubLevelContainer)
                ((SubLevelContainerHolder) this.level).sable$getPlotContainer()).getAllSubLevels();
        final RenderRegionCache renderRegionCache = new RenderRegionCache();
        final PrioritizeChunkUpdates chunkUpdates = this.minecraft.options.prioritizeChunkUpdates().get();

        for (final ClientSubLevel sublevel : sublevels) {
            sublevel.getRenderData().compileSections(chunkUpdates, renderRegionCache, camera);
        }
    }

    @Inject(method = "renderChunkLayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ShaderInstance;clear()V"), require = 0)
    private void sable$renderSubLevels(final RenderType renderType, final PoseStack poseStack,
                                       final double cameraX, final double cameraY, final double cameraZ,
                                       final Matrix4f projection, final CallbackInfo ci,
                                       final @Local ShaderInstance shader) {
        if (this.level == null) {
            return;
        }

        final Iterable<ClientSubLevel> sublevels = ((ClientSubLevelContainer)
                ((SubLevelContainerHolder) this.level).sable$getPlotContainer()).getAllSubLevels();
        SubLevelRenderDispatcher.get().renderSectionLayer(sublevels, renderType, shader,
                cameraX, cameraY, cameraZ, new Matrix4f(poseStack.last().pose()), projection,
                this.minecraft.getPartialTick());
    }
}
