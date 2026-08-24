package dev.ryanhcode.sable.validation.mixin;

import dev.ryanhcode.sable.validation.SableForgeRenderValidationTelemetry;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VanillaSubLevelRenderDispatcher.class, remap = false)
public abstract class RenderBridgeValidationMixin {
    @Inject(method = "renderAfterSections", at = @At("RETURN"), remap = false)
    private void sable$recordNativeRender(final Iterable<ClientSubLevel> subLevels,
                                           final double cameraX, final double cameraY,
                                           final double cameraZ, final Matrix4f modelView,
                                           final Matrix4f projection, final float partialTicks,
                                           final CallbackInfo ci) {
        int count = 0;
        for (final ClientSubLevel subLevel : subLevels) {
            count++;
            if ("M3 physics motion validation".equals(subLevel.getName())) {
                SableForgeRenderValidationTelemetry.recordMotionDrawPose(
                        new Vector3d(subLevel.renderPose().position()));
            }
        }
        SableForgeRenderValidationTelemetry.recordSingleBlockDraws(count);
    }
}
