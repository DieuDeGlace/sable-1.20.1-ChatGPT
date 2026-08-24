package dev.ryanhcode.sable.validation.mixin;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.validation.SableForgeRenderValidationTelemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CompassItemPropertyFunction.class)
public abstract class CompassCameraRotationValidationMixin {
    @Inject(method = "getAngleFromEntityToPos", at = @At("RETURN"))
    private void sable$verifyMountedCompassAngle(final Entity entity, final BlockPos target,
                                                  final CallbackInfoReturnable<Double> cir) {
        if (!Boolean.getBoolean("sable.ridingValidation") || entity != Minecraft.getInstance().player
                || entity.getVehicle() == null
                || !(Sable.HELPER.getContaining(entity.getVehicle()) instanceof ClientSubLevel subLevel)) return;
        final Vec3 localTarget = subLevel.lastPose().transformPositionInverse(Vec3.atCenterOf(target));
        final Vec3 localEntity = subLevel.lastPose().transformPositionInverse(entity.position());
        final double expected = Math.atan2(localTarget.z - localEntity.z, localTarget.x - localEntity.x)
                / (Math.PI * 2.0D);
        SableForgeRenderValidationTelemetry.recordRotatedCompassAngle(
                Math.abs(expected - cir.getReturnValueD()) < 1.0E-7D);
    }
}
