package dev.ryanhcode.sable.validation.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.validation.SableForgeRenderValidationTelemetry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiCameraRotationValidationMixin {
    @Shadow @Final private Minecraft minecraft;
    @Unique private Matrix4f sable$validationCrosshairBase;
    @Unique private Quaterniond sable$validationCrosshairOrientation;

    @Inject(method = "renderCrosshair", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V", ordinal = 0))
    private void sable$captureCrosshairBase(final GuiGraphics graphics, final CallbackInfo ci) {
        this.sable$validationCrosshairBase = null;
        this.sable$validationCrosshairOrientation = null;
        if (!Boolean.getBoolean("sable.ridingValidation") || this.minecraft.player == null) return;
        if (!(this.minecraft.player.getVehicle() != null
                && Sable.HELPER.getContaining(this.minecraft.player.getVehicle()) instanceof ClientSubLevel subLevel)) return;
        final float partialTick = this.minecraft.getFrameTime();
        final Quaterniond orientation = EntitySubLevelRotationHelper.getEntityOrientation(
                this.minecraft.gameRenderer.getMainCamera().getEntity(), ignored -> subLevel.renderPose(), partialTick,
                EntitySubLevelRotationHelper.Type.CAMERA);
        if (orientation == null || orientation.angle() < 0.1D) return;
        this.sable$validationCrosshairBase = new Matrix4f(
                com.mojang.blaze3d.systems.RenderSystem.getModelViewStack().last().pose());
        this.sable$validationCrosshairOrientation = new Quaterniond(orientation);
    }

    @Inject(method = "renderCrosshair", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private void sable$verifyCrosshairRotation(final GuiGraphics graphics, final CallbackInfo ci) {
        if (this.sable$validationCrosshairBase == null || this.sable$validationCrosshairOrientation == null) return;
        final Camera camera = this.minecraft.gameRenderer.getMainCamera();
        final float partialTick = this.minecraft.getFrameTime();
        final Matrix4f expected = new Matrix4f(this.sable$validationCrosshairBase)
                .rotate(Axis.XN.rotationDegrees(camera.getEntity().getViewXRot(partialTick)))
                .rotate(Axis.YP.rotationDegrees(camera.getEntity().getViewYRot(partialTick)))
                .rotate(new Quaternionf(this.sable$validationCrosshairOrientation).conjugate());
        final Matrix4f actual = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack().last().pose();
        SableForgeRenderValidationTelemetry.recordRotatedDebugCrosshairDraw(
                expected.equals(actual, 1.0E-4F));
        this.sable$validationCrosshairBase = null;
        this.sable$validationCrosshairOrientation = null;
    }
}
