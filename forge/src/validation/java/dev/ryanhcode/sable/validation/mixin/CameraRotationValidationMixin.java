package dev.ryanhcode.sable.validation.mixin;

import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.validation.CameraRotationValidationTelemetry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraRotationValidationMixin {
    @Shadow @Final private Quaternionf rotation;
    @Shadow private Entity entity;

    @Inject(method = "setRotation(FF)V", at = @At("TAIL"))
    private void sable$validateAppliedSubLevelRotation(final float yRot, final float xRot,
                                                       final CallbackInfo ci) {
        if (!Boolean.getBoolean("sable.ridingValidation")) {
            return;
        }
        final float partialTick = Minecraft.getInstance().getFrameTime();
        final Quaterniond inherited = EntitySubLevelRotationHelper.getEntityOrientation(
                this.entity, subLevel -> ((ClientSubLevel) subLevel).renderPose(), partialTick,
                EntitySubLevelRotationHelper.Type.CAMERA);
        if (inherited == null) {
            return;
        }
        final Quaternionf expected = new Quaternionf().rotationYXZ(
                -yRot * ((float) Math.PI / 180.0F),
                xRot * ((float) Math.PI / 180.0F), 0.0F)
                .premul(new Quaternionf(inherited));
        final double error = 1.0D - Math.abs(expected.dot(this.rotation));
        if (error > 1.0E-5D) {
            throw new IllegalStateException("Production Camera quaternion mismatch at setRotation: expected="
                    + expected + ", actual=" + this.rotation + ", error=" + error);
        }
        CameraRotationValidationTelemetry.markVerified();
    }
}
