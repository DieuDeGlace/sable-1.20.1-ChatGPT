package dev.ryanhcode.sable.network.tcp;

import dev.ryanhcode.sable.network.tcp.PacketContext;
import dev.ryanhcode.sable.network.tcp.CustomPacketPayload;

public interface SableTCPPacket extends CustomPacketPayload {

    void handle(PacketContext context);
}
