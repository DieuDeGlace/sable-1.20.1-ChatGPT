package dev.ryanhcode.sable.mixin.admitted;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Adapts the 1.20.1 movement-packet coordinate reads without ever moving the
 * client player into plot space.  The upstream 1.21 implementation temporarily
 * mutates Entity.position and restores it at RETURN; that creates an observable
 * invalid player state for every other sendPosition injector and leaks plot
 * coordinates if the method exits exceptionally.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerPacketCoordinateMixin {

    @ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getX()D"))
    private double sable$packetLocalX(final double globalX) {
        final Vec3 local = this.sable$packetLocalPosition();
        return local == null ? globalX : local.x;
    }

    @ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getY()D"))
    private double sable$packetLocalY(final double globalY) {
        final Vec3 local = this.sable$packetLocalPosition();
        return local == null ? globalY : local.y;
    }

    @ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D"))
    private double sable$packetLocalZ(final double globalZ) {
        final Vec3 local = this.sable$packetLocalPosition();
        return local == null ? globalZ : local.z;
    }

    @Unique
    private Vec3 sable$packetLocalPosition() {
        final LocalPlayer player = (LocalPlayer) (Object) this;
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(player);
        if (trackingSubLevel == null || trackingSubLevel.isRemoved()) return null;
        return trackingSubLevel.logicalPose().transformPositionInverse(player.position());
    }
}
