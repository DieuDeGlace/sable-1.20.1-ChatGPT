package dev.ryanhcode.sable.validation.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.validation.SableForgeClientSyncValidation;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the unmodified view matrix so validation can isolate Sable's outline transform. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererOutlineValidationMixin {

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;onDrawHighlight(Lnet/minecraft/client/renderer/LevelRenderer;Lnet/minecraft/client/Camera;Lnet/minecraft/world/phys/HitResult;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)Z"))
    private void sable$captureOutlineBaseMatrix(final PoseStack poseStack, final float partialTick,
                                                 final long finishNanoTime, final boolean renderBlockOutline,
                                                 final Camera camera, final GameRenderer gameRenderer,
                                                 final LightTexture lightTexture, final Matrix4f projectionMatrix,
                                                 final CallbackInfo ci) {
        SableForgeClientSyncValidation.captureBlockOutlineBaseMatrix(poseStack.last().pose());
    }
}
