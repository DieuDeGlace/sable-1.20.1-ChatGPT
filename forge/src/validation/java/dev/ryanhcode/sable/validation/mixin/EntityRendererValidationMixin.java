package dev.ryanhcode.sable.validation.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.validation.SableForgeRenderValidationTelemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Validation-only proof of the production name-tag orientation redirect. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererValidationMixin<T extends Entity> {
    @Unique private Matrix3f sableValidation$nameTagBase;
    @Unique private Quaternionf sableValidation$expectedNameTagRotation;

    @Inject(method = "renderNameTag", at = @At("HEAD"))
    private void sableValidation$captureNameTagBase(final T entity, final Component displayName,
                                                     final PoseStack poseStack, final MultiBufferSource buffers,
                                                     final int light, final CallbackInfo ci) {
        this.sableValidation$nameTagBase = null;
        this.sableValidation$expectedNameTagRotation = null;
        if (!(entity instanceof net.minecraft.client.player.LocalPlayer)
                || !"Sable rotated name-tag validation".equals(displayName.getString())
                || entity.getVehicle() == null
                || !(Sable.HELPER.getContaining(entity.getVehicle()) instanceof ClientSubLevel)) {
            return;
        }
        final Quaterniond orientation = EntitySubLevelRotationHelper.getEntityOrientation(entity,
                subLevel -> ((ClientSubLevel) subLevel).renderPose(), Minecraft.getInstance().getFrameTime(),
                EntitySubLevelRotationHelper.Type.ENTITY);
        if (orientation == null || orientation.angle() < 0.1D) {
            return;
        }
        this.sableValidation$nameTagBase = new Matrix3f(poseStack.last().normal());
        this.sableValidation$expectedNameTagRotation = new Quaternionf(orientation)
                .conjugate().mul(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
    }

    @Inject(method = "renderNameTag", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V",
            shift = At.Shift.AFTER))
    private void sableValidation$verifyNameTagRotation(final T entity, final Component displayName,
                                                        final PoseStack poseStack, final MultiBufferSource buffers,
                                                        final int light, final CallbackInfo ci) {
        if (this.sableValidation$nameTagBase == null || this.sableValidation$expectedNameTagRotation == null) {
            return;
        }
        final Matrix3f expected = new Matrix3f(this.sableValidation$nameTagBase)
                .rotate(this.sableValidation$expectedNameTagRotation);
        final Matrix3f actual = poseStack.last().normal();
        SableForgeRenderValidationTelemetry.recordRotatedNameTagDraw(
                sableValidation$matches(expected, actual));
    }

    @Unique
    private static boolean sableValidation$matches(final Matrix3f expected, final Matrix3f actual) {
        final float epsilon = 1.0E-4F;
        return Math.abs(expected.m00() - actual.m00()) <= epsilon
                && Math.abs(expected.m01() - actual.m01()) <= epsilon
                && Math.abs(expected.m02() - actual.m02()) <= epsilon
                && Math.abs(expected.m10() - actual.m10()) <= epsilon
                && Math.abs(expected.m11() - actual.m11()) <= epsilon
                && Math.abs(expected.m12() - actual.m12()) <= epsilon
                && Math.abs(expected.m20() - actual.m20()) <= epsilon
                && Math.abs(expected.m21() - actual.m21()) <= epsilon
                && Math.abs(expected.m22() - actual.m22()) <= epsilon;
    }
}
