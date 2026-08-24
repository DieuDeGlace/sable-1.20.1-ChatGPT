package dev.ryanhcode.sable.validation.mixin;

import dev.ryanhcode.sable.validation.SableForgeUDPValidationTelemetry;
import dev.ryanhcode.sable.network.udp.AddressedSableUDPPacket;
import dev.ryanhcode.sable.network.udp.handler.SableUDPChannelHandlerClient;
import io.netty.channel.ChannelHandlerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SableUDPChannelHandlerClient.class, remap = false)
public abstract class UDPClientHandlerValidationMixin {
    @Inject(method = "channelRead0", at = @At("HEAD"), remap = false)
    private void sable$recordUDP(final ChannelHandlerContext context,
                                 final AddressedSableUDPPacket packet,
                                 final CallbackInfo ci) {
        if (Boolean.getBoolean("sable.udpValidation")) {
            SableForgeUDPValidationTelemetry.record(packet);
        }
    }
}
