package dev.ryanhcode.sable.mixin.admitted;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerPlatformMotionMixin {
    @org.spongepowered.asm.mixin.Shadow public ServerPlayer player;
    @Unique private final Vector3d sable$trackedSubLevelPos = new Vector3d();

    /*
     * Forge 1.20.1 drives the player through this listener's tick and restores
     * firstGoodX/Y/Z immediately after ServerPlayer#doTick. Applying after that
     * restore is the first target-backed point where inherited platform motion
     * is not overwritten again in the same tick.
     */
    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;absMoveTo(DDDFF)V",
            shift = At.Shift.AFTER))
    private void sable$inheritTrackedPlatformMotion(final CallbackInfo ci) {
        final SubLevel tracking = Sable.HELPER.getTrackingSubLevel(this.player);
        if (tracking == null || tracking.isRemoved()) return;

        final Vector3d center = JOMLConversion.getAABBCenter(
                this.player.getBoundingBox(), this.sable$trackedSubLevelPos);
        final double oldX = center.x;
        final double oldY = center.y;
        final double oldZ = center.z;
        final Pose3dc pose = tracking.logicalPose();
        final Pose3dc lastPose = tracking.lastPose();
        final Vector3d moved = pose.transformPosition(lastPose.transformPositionInverse(center, center));
        final Vec3 position = this.player.position();
        final Vec3 expected = new Vec3(
                position.x + moved.x - oldX, position.y + moved.y - oldY, position.z + moved.z - oldZ);
        this.player.setPos(expected.x, expected.y, expected.z);
    }
}
