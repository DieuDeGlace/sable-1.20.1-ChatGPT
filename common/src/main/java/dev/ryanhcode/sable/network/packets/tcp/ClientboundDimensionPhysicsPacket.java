package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.network.tcp.PacketContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import dev.ryanhcode.sable.network.codec.ByteBufCodecs;
import dev.ryanhcode.sable.network.codec.StreamCodec;
import dev.ryanhcode.sable.network.tcp.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public record ClientboundDimensionPhysicsPacket(List<DimensionPhysics> dimensionPhysics) implements SableTCPPacket {
    public static final CustomPacketPayload.Type<ClientboundDimensionPhysicsPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("sable", "dimension_physics"), ClientboundDimensionPhysicsPacket.class);

    public static final StreamCodec<ByteBuf, ClientboundDimensionPhysicsPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(
                    ArrayList::new,
                    DimensionPhysics.STREAM_CODEC
            ), ClientboundDimensionPhysicsPacket::dimensionPhysics,
            ClientboundDimensionPhysicsPacket::new
    );


    @Override
    public void handle(final PacketContext context) {
        Minecraft.getInstance().execute(() -> {
            DimensionPhysicsData.clearPhysics();
            for (final DimensionPhysics dimensionPhysic : this.dimensionPhysics) {
                final ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionPhysic.dimension());
                DimensionPhysicsData.putPhysics(dimension, dimensionPhysic);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
