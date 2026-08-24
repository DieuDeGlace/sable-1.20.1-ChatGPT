package dev.ryanhcode.sable.validation.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.validation.SableForgeRenderValidationTelemetry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Validation-only proof that the production dispatcher applies the sub-level orientation. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherValidationMixin {
    @Unique private Matrix3f sableValidation$baseNormal;

    @Inject(method = "render", at = @At("HEAD"))
    private <E extends Entity> void sableValidation$captureBase(final E entity, final double x, final double y,
                                                                 final double z, final float yaw, final float partialTick,
                                                                 final PoseStack poseStack,
                                                                 final MultiBufferSource buffers, final int light,
                                                                 final CallbackInfo ci) {
        this.sableValidation$baseNormal = new Matrix3f(poseStack.last().normal());
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private <E extends Entity> void sableValidation$verifyRotation(final E entity, final double x, final double y,
                                                                    final double z, final float yaw,
                                                                    final float partialTick, final PoseStack poseStack,
                                                                    final MultiBufferSource buffers, final int light,
                                                                    final CallbackInfo ci) {
        if (!(entity instanceof net.minecraft.client.player.LocalPlayer && entity.getVehicle() != null
                && dev.ryanhcode.sable.Sable.HELPER.getContaining(entity.getVehicle()) instanceof ClientSubLevel)) {
            return;
        }
        final Quaterniond orientation = EntitySubLevelRotationHelper.getEntityOrientation(entity,
                subLevel -> ((ClientSubLevel) subLevel).renderPose(), partialTick,
                EntitySubLevelRotationHelper.Type.ENTITY);
        if (orientation == null || orientation.angle() < 0.1D || this.sableValidation$baseNormal == null) {
            return;
        }
        final Matrix3f expected = new Matrix3f(this.sableValidation$baseNormal)
                .rotate(new Quaternionf(orientation));
        final Matrix3f actual = poseStack.last().normal();
        SableForgeRenderValidationTelemetry.recordRotatedEntityDraw(sableValidation$matches(expected, actual));
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
