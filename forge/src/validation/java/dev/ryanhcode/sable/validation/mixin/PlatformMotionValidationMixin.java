package dev.ryanhcode.sable.validation.mixin;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.validation.SableForgeMovementValidationProbe;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlatformMotionValidationMixin {
    @Shadow public ServerPlayer player;
    @Unique private final Vector3d sable$validationCenter = new Vector3d();
    @Unique private Vec3 sable$validationBefore;
    @Unique private Vec3 sable$validationExpected;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;absMoveTo(DDDFF)V",
            shift = At.Shift.NONE))
    private void sable$capturePlatformBaseline(final ServerPlayer player, final double x, final double y,
                                                final double z, final float yaw, final float pitch,
                                                final Operation<Void> original) {
        original.call(player, x, y, z, yaw, pitch);
        if (!Boolean.getBoolean("sable.movementValidation")) return;
        final SubLevel tracking = Sable.HELPER.getTrackingSubLevel(this.player);
        if (tracking == null || tracking.isRemoved()) return;
        final Vector3d center = JOMLConversion.getAABBCenter(this.player.getBoundingBox(), this.sable$validationCenter);
        final double oldX = center.x;
        final double oldY = center.y;
        final double oldZ = center.z;
        final Pose3dc pose = tracking.logicalPose();
        final Pose3dc lastPose = tracking.lastPose();
        final Vector3d moved = pose.transformPosition(lastPose.transformPositionInverse(center, center));
        this.sable$validationBefore = this.player.position();
        this.sable$validationExpected = new Vec3(this.sable$validationBefore.x + moved.x - oldX,
                this.sable$validationBefore.y + moved.y - oldY,
                this.sable$validationBefore.z + moved.z - oldZ);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void sable$afterPlatformMotion(final CallbackInfo ci) {
        if (this.sable$validationBefore != null) {
            SableForgeMovementValidationProbe.platformMove(this.sable$validationBefore,
                    this.sable$validationExpected, this.player.position());
        }
        this.sable$validationBefore = null;
        this.sable$validationExpected = null;
    }
}
