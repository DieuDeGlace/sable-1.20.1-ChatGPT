package dev.ryanhcode.sable.network.codec;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ByteBufCodecsTest {

    @Test
    void primitiveAndValueCodecsRoundTripInFieldOrder() {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        final UUID uuid = UUID.fromString("cb406f63-b531-4b6a-a7b7-925947d7155f");
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("sable", "codec_test");
        final Vector3f vector = new Vector3f(1.25F, -2.5F, 9.75F);

        ByteBufCodecs.BOOL.encode(buffer, true);
        ByteBufCodecs.INT.encode(buffer, 0x10203040);
        ByteBufCodecs.FLOAT.encode(buffer, 4.5F);
        ByteBufCodecs.DOUBLE.encode(buffer, -18.25D);
        ByteBufCodecs.STRING_UTF8.encode(buffer, "Sable 1.20.1");
        ByteBufCodecs.UUID.encode(buffer, uuid);
        ByteBufCodecs.RESOURCE_LOCATION.encode(buffer, id);
        ByteBufCodecs.VECTOR3F.encode(buffer, vector);

        assertEquals(true, ByteBufCodecs.BOOL.decode(buffer));
        assertEquals(0x10203040, ByteBufCodecs.INT.decode(buffer));
        assertEquals(4.5F, ByteBufCodecs.FLOAT.decode(buffer));
        assertEquals(-18.25D, ByteBufCodecs.DOUBLE.decode(buffer));
        assertEquals("Sable 1.20.1", ByteBufCodecs.STRING_UTF8.decode(buffer));
        assertEquals(uuid, ByteBufCodecs.UUID.decode(buffer));
        assertEquals(id, ByteBufCodecs.RESOURCE_LOCATION.decode(buffer));
        assertEquals(vector, ByteBufCodecs.VECTOR3F.decode(buffer));
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void optionalAndCollectionCodecsPreservePresenceAndOrder() {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        final StreamCodec<FriendlyByteBuf, Optional<String>> optional =
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8);
        final StreamCodec<FriendlyByteBuf, List<Integer>> collection =
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.INT);

        optional.encode(buffer, Optional.empty());
        optional.encode(buffer, Optional.of("present"));
        collection.encode(buffer, List.of(7, 3, 11));

        assertEquals(Optional.empty(), optional.decode(buffer));
        assertEquals(Optional.of("present"), optional.decode(buffer));
        assertEquals(List.of(7, 3, 11), collection.decode(buffer));
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void compositeCodecUsesDeclaredWireOrder() {
        record Pair(int number, String name) {}

        final StreamCodec<FriendlyByteBuf, Pair> codec = StreamCodec.composite(
                ByteBufCodecs.INT, Pair::number,
                ByteBufCodecs.STRING_UTF8, Pair::name,
                Pair::new);
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        final Pair expected = new Pair(42, "physics");

        codec.encode(buffer, expected);

        assertEquals(42, buffer.readInt());
        assertEquals("physics", buffer.readUtf());
        buffer.readerIndex(0);
        assertEquals(expected, codec.decode(buffer));
        assertEquals(0, buffer.readableBytes());
    }
}
