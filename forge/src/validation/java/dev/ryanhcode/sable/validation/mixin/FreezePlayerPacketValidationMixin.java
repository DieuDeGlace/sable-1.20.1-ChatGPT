package dev.ryanhcode.sable.validation.mixin;

import dev.ryanhcode.sable.network.packets.tcp.ClientboundFreezePlayerPacket;
import dev.ryanhcode.sable.network.tcp.PacketContext;
import dev.ryanhcode.sable.validation.SableForgeNetworkValidationTelemetry;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records packet 8 state at the exact production-handler boundary before a later tick consumes it. */
@Mixin(value = ClientboundFreezePlayerPacket.class, remap = false)
public abstract class FreezePlayerPacketValidationMixin {
    @Inject(method = "handle", at = @At("RETURN"), remap = false)
    private void sable$recordAppliedFreezeState(final PacketContext context, final CallbackInfo ci) {
        final Player player = context.player();
        if (player != null) {
            SableForgeNetworkValidationTelemetry.recordAppliedFreezeState(
                    (ClientboundFreezePlayerPacket) (Object) this, player);
        }
    }
}
