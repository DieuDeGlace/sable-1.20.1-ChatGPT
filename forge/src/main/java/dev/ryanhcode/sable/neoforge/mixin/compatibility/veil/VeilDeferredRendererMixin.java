package dev.ryanhcode.sable.neoforge.mixin.compatibility.veil;

import com.mojang.logging.LogUtils;
import foundry.veil.api.client.render.deferred.VeilDeferredRenderer;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_NEAREST;

/**
 * Veil 1.0 resolves both color and depth through the no-mask overload. A depth
 * format mismatch invalidates the complete blit, including color. Veil 4.3.2,
 * used by the locked upstream, resolves post-processing color explicitly.
 */
@Mixin(value = VeilDeferredRenderer.class, remap = false)
public abstract class VeilDeferredRendererMixin {
    @Unique
    private static final Logger SABLE$LOGGER = LogUtils.getLogger();
    @Unique
    private static boolean sable$loggedColorResolve;

    @Redirect(
            method = "blit",
            at = @At(
                    value = "INVOKE",
                    target = "Lfoundry/veil/api/client/render/framebuffer/AdvancedFbo;resolveToAdvancedFbo(Lfoundry/veil/api/client/render/framebuffer/AdvancedFbo;)V"
            )
    )
    private void sable$resolvePostColorOnly(final AdvancedFbo source, final AdvancedFbo target) {
        source.resolveToAdvancedFbo(target, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        if (!sable$loggedColorResolve) {
            sable$loggedColorResolve = true;
            dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(SABLE$LOGGER, "SABLE-VEIL-POST-RESOLVE: stage=deferred-blit mask=color filter=nearest");
        }
    }
}
