package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.physics.config.FloatingBlockMaterialDataHandler;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockMaterial;
import dev.ryanhcode.sable.network.tcp.PacketContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import dev.ryanhcode.sable.network.codec.ByteBufCodecs;
import dev.ryanhcode.sable.network.codec.StreamCodec;
import dev.ryanhcode.sable.network.tcp.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClientboundFloatingBlockMaterialPacket(ResourceLocation name, FloatingBlockMaterial material) implements SableTCPPacket {
    public static final Type<ClientboundFloatingBlockMaterialPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("sable", "floating_material"), ClientboundFloatingBlockMaterialPacket.class);

    public static final StreamCodec<ByteBuf, ClientboundFloatingBlockMaterialPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.RESOURCE_LOCATION, ClientboundFloatingBlockMaterialPacket::name,
            FloatingBlockMaterial.STREAM_CODEC, ClientboundFloatingBlockMaterialPacket::material,
            ClientboundFloatingBlockMaterialPacket::new
    );

    @Override
    public void handle(PacketContext context) {
        Minecraft.getInstance().execute(() -> {
            FloatingBlockMaterialDataHandler.addMaterial(this.name, this.material);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
