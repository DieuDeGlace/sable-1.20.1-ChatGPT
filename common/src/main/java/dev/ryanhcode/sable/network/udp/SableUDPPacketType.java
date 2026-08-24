package dev.ryanhcode.sable.network.udp;

import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotInfoDualPacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPAuthenticationPacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPClientboundKeepAlivePacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPEchoPacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPServerboundAlivePacket;
import net.minecraft.network.FriendlyByteBuf;
import dev.ryanhcode.sable.network.codec.StreamCodec;

public enum SableUDPPacketType {
    PING(SableUDPEchoPacket.CODEC),
    SNAPSHOT(ClientboundSableSnapshotDualPacket.CODEC),
    SNAPSHOT_INFO(ClientboundSableSnapshotInfoDualPacket.CODEC),
    AUTH(SableUDPAuthenticationPacket.CODEC),
    KEEP_ALIVE_CLIENTBOUND(SableUDPClientboundKeepAlivePacket.CODEC),
    ALIVE_SERVERBOUND(SableUDPServerboundAlivePacket.CODEC);

    public static final SableUDPPacketType[] VALUES = SableUDPPacketType.values();

    private final StreamCodec<FriendlyByteBuf, ? extends SableUDPPacket> codec;

    SableUDPPacketType(final StreamCodec<FriendlyByteBuf, ? extends SableUDPPacket> codec) {
        this.codec = codec;
    }

    public SableUDPPacket create(final FriendlyByteBuf buf) {
        return this.codec.decode(buf);
    }

    public void write(final FriendlyByteBuf buf, final SableUDPPacket packet) {
        //noinspection unchecked,rawtypes
        ((StreamCodec) this.codec).encode(buf, packet);
    }
}
