package dev.ryanhcode.sable.neoforge.mixin.diagnostic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.GameType;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bounded evidence for whether vanilla actually submits the first-person hand and where it submits it. */
@Mixin(GameRenderer.class)
public abstract class FirstPersonHandDiagnosticMixin {
    @Unique
    private static final Logger SABLE$LOGGER = LogUtils.getLogger();
    @Unique
    private static boolean sable$loggedEntry;
    @Unique
    private static boolean sable$loggedSubmission;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void sable$logHandEntry(final PoseStack poseStack, final Camera camera, final float partialTick,
                                    final CallbackInfo ci) {
        if (sable$loggedEntry) {
            return;
        }
        sable$loggedEntry = true;
        SABLE$LOGGER.info(
                "SABLE-FIRST-PERSON-HAND: stage=entry cameraType={} firstPerson={} hideGui={} spectator={} "
                        + "sleeping={} drawFbo={} readFbo={} mainFbo={}",
                this.minecraft.options.getCameraType(),
                this.minecraft.options.getCameraType().isFirstPerson(),
                this.minecraft.options.hideGui,
                this.minecraft.gameMode != null && this.minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR,
                this.minecraft.player != null && this.minecraft.player.isSleeping(),
                GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                this.minecraft.getMainRenderTarget().frameBufferId);
    }

    @Inject(
            method = "renderItemInHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void sable$logHandSubmission(final PoseStack poseStack, final Camera camera, final float partialTick,
                                         final CallbackInfo ci) {
        if (sable$loggedSubmission) {
            return;
        }
        sable$loggedSubmission = true;
        SABLE$LOGGER.info("SABLE-FIRST-PERSON-HAND: stage=vanilla-submit drawFbo={} readFbo={} mainFbo={}",
                GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                this.minecraft.getMainRenderTarget().frameBufferId);
    }
}
