package dev.ryanhcode.sable.network.codec;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;

public final class ByteBufCodecs {
    public static final StreamCodec<ByteBuf, Boolean> BOOL = StreamCodec.of((buffer, value) -> friendly(buffer).writeBoolean(value), buffer -> friendly(buffer).readBoolean());
    public static final StreamCodec<ByteBuf, Integer> INT = StreamCodec.of((buffer, value) -> friendly(buffer).writeInt(value), buffer -> friendly(buffer).readInt());
    public static final StreamCodec<ByteBuf, Float> FLOAT = StreamCodec.of((buffer, value) -> friendly(buffer).writeFloat(value), buffer -> friendly(buffer).readFloat());
    public static final StreamCodec<ByteBuf, Double> DOUBLE = StreamCodec.of((buffer, value) -> friendly(buffer).writeDouble(value), buffer -> friendly(buffer).readDouble());
    public static final StreamCodec<ByteBuf, String> STRING_UTF8 = StreamCodec.of((buffer, value) -> friendly(buffer).writeUtf(value), buffer -> friendly(buffer).readUtf());
    public static final StreamCodec<ByteBuf, UUID> UUID = StreamCodec.of((buffer, value) -> friendly(buffer).writeUUID(value), buffer -> friendly(buffer).readUUID());
    public static final StreamCodec<ByteBuf, ResourceLocation> RESOURCE_LOCATION = StreamCodec.of((buffer, value) -> friendly(buffer).writeResourceLocation(value), buffer -> friendly(buffer).readResourceLocation());
    public static final StreamCodec<ByteBuf, Vector3f> VECTOR3F = StreamCodec.of((buffer, value) -> {
        friendly(buffer).writeFloat(value.x());
        friendly(buffer).writeFloat(value.y());
        friendly(buffer).writeFloat(value.z());
    }, buffer -> new Vector3f(friendly(buffer).readFloat(), friendly(buffer).readFloat(), friendly(buffer).readFloat()));

    private ByteBufCodecs() {
    }

    public static <B extends ByteBuf, V> StreamCodec<B, Optional<V>> optional(final StreamCodec<? super B, V> codec) {
        return StreamCodec.of((buffer, value) -> {
            friendly(buffer).writeBoolean(value.isPresent());
            value.ifPresent(element -> codec.encode(buffer, element));
        }, buffer -> friendly(buffer).readBoolean() ? Optional.of(codec.decode(buffer)) : Optional.empty());
    }

    public static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(
            final IntFunction<C> factory, final StreamCodec<? super B, V> elementCodec) {
        return StreamCodec.of((buffer, values) -> {
            friendly(buffer).writeVarInt(values.size());
            values.forEach(value -> elementCodec.encode(buffer, value));
        }, buffer -> {
            final int size = friendly(buffer).readVarInt();
            final C values = factory.apply(size);
            for (int i = 0; i < size; i++) {
                values.add(elementCodec.decode(buffer));
            }
            return values;
        });
    }

    public static <V> StreamCodec<ByteBuf, V> fromCodec(final Codec<V> codec) {
        return StreamCodec.of((buffer, value) -> friendly(buffer).writeWithCodec(NbtOps.INSTANCE, codec, value),
                buffer -> friendly(buffer).readWithCodec(NbtOps.INSTANCE, codec));
    }

    private static FriendlyByteBuf friendly(final ByteBuf buffer) {
        return buffer instanceof FriendlyByteBuf friendly ? friendly : new FriendlyByteBuf(buffer);
    }
}
