package dev.ryanhcode.sable.mixin.compatibility.computercraft;

import com.llamalad7.mixinextras.sugar.Local;
import dan200.computercraft.api.network.PacketReceiver;
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessNetwork;
import dev.ryanhcode.sable.Sable;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WirelessNetwork.class)
public class WirelessNetworkMixin {

    @Redirect(
            method = "tryTransmit(Ldan200/computercraft/api/network/PacketReceiver;Ldan200/computercraft/api/network/Packet;DZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D",
                    remap = true
            ),
            remap = false
    )
    private static double getPosition(final Vec3 a, final Vec3 b, @Local(ordinal = 0, argsOnly = true) final PacketReceiver packetReceiver) {
        return Sable.HELPER.distanceSquaredWithSubLevels(packetReceiver.getLevel(), a, b);
    }

}
