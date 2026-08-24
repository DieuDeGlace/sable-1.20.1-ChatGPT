package dev.ryanhcode.sable.validation.mixin;

import dev.ryanhcode.sable.validation.SableForgeMovementValidationProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reads the actual packet object passed to vanilla's connection. */
@Mixin(ClientPacketListener.class)
public abstract class ClientMovementPacketValidationMixin {
    @Inject(method = "send", at = @At("HEAD"))
    private void sable$inspectMovementPacket(final Packet<?> packet, final CallbackInfo ci) {
        if (packet instanceof final ServerboundMovePlayerPacket movement
                && Minecraft.getInstance().player != null) {
            SableForgeMovementValidationProbe.observeClientPacket(movement,
                    Minecraft.getInstance().player.position());
        }
    }
}
