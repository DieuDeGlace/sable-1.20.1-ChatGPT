package dev.ryanhcode.sable.network.tcp;

import dev.ryanhcode.sable.network.codec.StreamCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class SablePacketManager {
    private static SablePacketManager active;

    private final SimpleChannel channel;
    private final Set<Integer> registeredDiscriminators = ConcurrentHashMap.newKeySet();
    private int nextMessageId;

    private SablePacketManager(final ResourceLocation id, final String protocol) {
        this.channel = NetworkRegistry.ChannelBuilder.named(id)
                .networkProtocolVersion(() -> protocol)
                .clientAcceptedVersions(protocol::equals)
                .serverAcceptedVersions(protocol::equals)
                .simpleChannel();
    }

    public static SablePacketManager create(final String namespace, final String protocol) {
        active = new SablePacketManager(ResourceLocation.fromNamespaceAndPath(namespace, "main"), protocol);
        return active;
    }

    public <P extends SableTCPPacket> void registerClientbound(
            final CustomPacketPayload.Type<P> type,
            final StreamCodec<? super FriendlyByteBuf, P> codec,
            final BiConsumer<P, PacketContext> handler) {
        this.register(this.nextAvailableDiscriminator(), type, codec, handler, NetworkDirection.PLAY_TO_CLIENT);
    }

    public <P extends SableTCPPacket> void registerClientbound(
            final int discriminator,
            final CustomPacketPayload.Type<P> type,
            final StreamCodec<? super FriendlyByteBuf, P> codec,
            final BiConsumer<P, PacketContext> handler) {
        this.register(discriminator, type, codec, handler, NetworkDirection.PLAY_TO_CLIENT);
    }

    public <P extends SableTCPPacket> void registerServerbound(
            final CustomPacketPayload.Type<P> type,
            final StreamCodec<? super FriendlyByteBuf, P> codec,
            final BiConsumer<P, PacketContext> handler) {
        this.register(this.nextAvailableDiscriminator(), type, codec, handler, NetworkDirection.PLAY_TO_SERVER);
    }

    public <P extends SableTCPPacket> void registerServerbound(
            final int discriminator,
            final CustomPacketPayload.Type<P> type,
            final StreamCodec<? super FriendlyByteBuf, P> codec,
            final BiConsumer<P, PacketContext> handler) {
        this.register(discriminator, type, codec, handler, NetworkDirection.PLAY_TO_SERVER);
    }

    private <P extends SableTCPPacket> void register(
            final int discriminator,
            final CustomPacketPayload.Type<P> type,
            final StreamCodec<? super FriendlyByteBuf, P> codec,
            final BiConsumer<P, PacketContext> handler,
            final NetworkDirection direction) {
        if (discriminator < 0 || discriminator > 255) {
            throw new IllegalArgumentException("Packet discriminator is outside the one-byte Forge range: "
                    + discriminator);
        }
        if (!this.registeredDiscriminators.add(discriminator)) {
            throw new IllegalArgumentException("Duplicate packet discriminator: " + discriminator);
        }
        this.channel.registerMessage(discriminator, type.packetClass(),
                (packet, buffer) -> codec.encode(buffer, packet),
                codec::decode,
                (packet, contextSupplier) -> handle(packet, handler, contextSupplier),
                java.util.Optional.of(direction));
    }

    private int nextAvailableDiscriminator() {
        while (this.registeredDiscriminators.contains(this.nextMessageId)) {
            this.nextMessageId++;
        }
        return this.nextMessageId++;
    }

    private static <P extends SableTCPPacket> void handle(
            final P packet,
            final BiConsumer<P, PacketContext> handler,
            final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context forgeContext = contextSupplier.get();
        forgeContext.enqueueWork(() -> {
            handler.accept(packet, createContext(forgeContext));
        });
        forgeContext.setPacketHandled(true);
    }

    public int encodedDiscriminator(final SableTCPPacket packet) {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        this.channel.encodeMessage(packet, buffer);
        return buffer.readUnsignedByte();
    }

    private static PacketContext createContext(final NetworkEvent.Context context) {
        final ServerPlayer sender = context.getSender();
        return sender != null ? new ServerPacketContext(sender) : new ClientPacketContext();
    }

    public static PacketSink player(final ServerPlayer player) {
        return packet -> requireActive().channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static PacketSink server() {
        return packet -> requireActive().channel.sendToServer(packet);
    }

    /**
     * Converts a Sable clientbound message to Forge 47's vanilla custom-payload
     * packet so it can retain ordering inside a vanilla bundle with chunk data.
     */
    @SuppressWarnings("unchecked")
    public static Packet<ClientGamePacketListener> clientboundVanillaPacket(final SableTCPPacket packet) {
        return (Packet<ClientGamePacketListener>) requireActive().channel
                .toVanillaPacket(packet, NetworkDirection.PLAY_TO_CLIENT);
    }

    private static SablePacketManager requireActive() {
        if (active == null) {
            throw new IllegalStateException("Sable packet manager has not been initialized");
        }
        return active;
    }

    @FunctionalInterface
    public interface PacketSink {
        void sendPacket(SableTCPPacket packet);
    }

    private record ServerPacketContext(ServerPlayer player) implements PacketContext {
        @Override
        public Level level() {
            return this.player.serverLevel();
        }
    }

    private static final class ClientPacketContext implements PacketContext {
        @Override
        public Level level() {
            return Minecraft.getInstance().level;
        }

        @Override
        public Player player() {
            return Minecraft.getInstance().player;
        }
    }
}
