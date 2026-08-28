package dev.ryanhcode.sable.neoforge.mixin.compatibility.veil;

import com.mojang.blaze3d.pipeline.RenderTarget;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_NEAREST;

/** Matches the explicit color-only post resolves used by locked-upstream Veil 4.3.2. */
@Mixin(value = PostProcessingManager.class, remap = false)
public abstract class PostProcessingManagerMixin {
    @Redirect(
            method = "runPipeline()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lfoundry/veil/api/client/render/framebuffer/AdvancedFbo;resolveToFramebuffer(Lcom/mojang/blaze3d/pipeline/RenderTarget;)V"
            )
    )
    private void sable$resolveActivePostColorOnly(final AdvancedFbo source, final RenderTarget target) {
        source.resolveToFramebuffer(target, GL_COLOR_BUFFER_BIT, GL_NEAREST);
    }

    @Redirect(
            method = "runPipeline(Lfoundry/veil/api/client/render/post/PostPipeline;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lfoundry/veil/api/client/render/framebuffer/AdvancedFbo;resolveToAdvancedFbo(Lfoundry/veil/api/client/render/framebuffer/AdvancedFbo;)V"
            )
    )
    private void sable$resolveIntoPostColorOnly(final AdvancedFbo source, final AdvancedFbo target,
                                                 final PostPipeline pipeline, final boolean resolvePost) {
        source.resolveToAdvancedFbo(target, GL_COLOR_BUFFER_BIT, GL_NEAREST);
    }

    @Redirect(
            method = "runPipeline(Lfoundry/veil/api/client/render/post/PostPipeline;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lfoundry/veil/api/client/render/framebuffer/AdvancedFbo;resolveToFramebuffer(Lcom/mojang/blaze3d/pipeline/RenderTarget;)V"
            )
    )
    private void sable$resolveFromPostColorOnly(final AdvancedFbo source, final RenderTarget target,
                                                 final PostPipeline pipeline, final boolean resolvePost) {
        source.resolveToFramebuffer(target, GL_COLOR_BUFFER_BIT, GL_NEAREST);
    }
}
