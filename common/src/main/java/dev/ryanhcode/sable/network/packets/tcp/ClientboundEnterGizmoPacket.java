package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.network.tcp.PacketContext;
import net.minecraft.network.FriendlyByteBuf;
import dev.ryanhcode.sable.network.codec.StreamCodec;
import dev.ryanhcode.sable.network.tcp.CustomPacketPayload;

public record ClientboundEnterGizmoPacket() implements SableTCPPacket {

    public static final Type<ClientboundEnterGizmoPacket> TYPE = new Type<>(Sable.sablePath("enter_gizmo_mode"), ClientboundEnterGizmoPacket.class);
    public static final StreamCodec<FriendlyByteBuf, ClientboundEnterGizmoPacket> CODEC = StreamCodec.of((buf, value) -> value.write(buf), ClientboundEnterGizmoPacket::read);

    private static ClientboundEnterGizmoPacket read(final FriendlyByteBuf buf) {
        return new ClientboundEnterGizmoPacket();
    }

    private void write(final FriendlyByteBuf buf) {

    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void handle(final PacketContext context) {
        SableClient.GIZMO_HANDLER.start();
    }
}
