package dev.ryanhcode.sable.validation.mixin;

import dev.ryanhcode.sable.validation.SableForgeNetworkValidationTelemetry;
import dev.ryanhcode.sable.network.tcp.PacketContext;
import dev.ryanhcode.sable.network.tcp.SablePacketManager;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

@Mixin(value = SablePacketManager.class, remap = false)
public abstract class PacketManagerValidationMixin {
    @Inject(method = "handle", at = @At("HEAD"), remap = false)
    private static <P extends SableTCPPacket> void sable$recordPacket(
            final P packet,
            final BiConsumer<P, PacketContext> handler,
            final Supplier<NetworkEvent.Context> contextSupplier,
            final CallbackInfo ci) {
        SableForgeNetworkValidationTelemetry.record(packet);
    }
}
