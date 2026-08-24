package dev.ryanhcode.sable.network.tcp;

import net.minecraft.resources.ResourceLocation;

public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    record Type<T extends CustomPacketPayload>(ResourceLocation id, Class<T> packetClass) {
    }
}
