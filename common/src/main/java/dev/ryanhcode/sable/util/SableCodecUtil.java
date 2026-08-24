package dev.ryanhcode.sable.util;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class SableCodecUtil {

    public static <N extends Number> Function<N, DataResult<N>> checkPositive(final boolean includeZero) {
        return (value) -> {
            if(includeZero) {
                if(value.doubleValue() < 0) return DataResult.error(() -> "Value " + value + " is not positive or 0");
            } else {
                if(value.doubleValue() <= 0) return DataResult.error(() -> "Value " + value + " is not positive");
            }

            return DataResult.success(value);
        };
    }

    public static Codec<Double> positiveDouble(final boolean includeZero) {
        return Codec.DOUBLE.flatXmap(checkPositive(includeZero), checkPositive(includeZero));
    }

    /**
     * Backport of DFU 8.0.16's non-lenient optional field behavior.
     *
     * <p>DFU 6.0.8's {@link Codec#optionalField(String, Codec)} silently treats a malformed
     * present value as an absent field. DFU 8.0.16's three-argument overload only does that
     * when its {@code lenient} argument is {@code true}; Sable uses {@code false}.</p>
     */
    public static <A> MapCodec<Optional<A>> strictOptionalField(final String name, final Codec<A> elementCodec) {
        return new MapCodec<>() {
            @Override
            public <T> DataResult<Optional<A>> decode(final DynamicOps<T> ops, final MapLike<T> input) {
                final T value = input.get(name);
                if (value == null) {
                    return DataResult.success(Optional.empty());
                }

                final DataResult<A> decoded = elementCodec.parse(ops, value);
                return decoded.map(Optional::of)
                        .setPartial(decoded.resultOrPartial(ignored -> {}));
            }

            @Override
            public <T> RecordBuilder<T> encode(final Optional<A> input, final DynamicOps<T> ops,
                                               final RecordBuilder<T> prefix) {
                if (input.isPresent()) {
                    return prefix.add(name, elementCodec.encodeStart(ops, input.get()));
                }
                return prefix;
            }

            @Override
            public <T> Stream<T> keys(final DynamicOps<T> ops) {
                return Stream.of(ops.createString(name));
            }

            @Override
            public String toString() {
                return "StrictOptionalField[" + name + ": " + elementCodec + "]";
            }
        };
    }

    /**
     * Backport of DFU 8.0.16's key-dispatched map codec, which is absent from DFU 6.0.8.
     */
    public static <K, V> Codec<Map<K, V>> dispatchedMap(final Codec<K> keyCodec,
                                                        final Function<K, Codec<? extends V>> valueCodecFunction) {
        return new DispatchedMapCodec<>(keyCodec, valueCodecFunction);
    }

    private record DispatchedMapCodec<K, V>(Codec<K> keyCodec,
                                             Function<K, Codec<? extends V>> valueCodecFunction)
            implements Codec<Map<K, V>> {

        @Override
        public <T> DataResult<T> encode(final Map<K, V> input, final DynamicOps<T> ops, final T prefix) {
            final RecordBuilder<T> mapBuilder = ops.mapBuilder();
            for (final Map.Entry<K, V> entry : input.entrySet()) {
                mapBuilder.add(this.keyCodec.encodeStart(ops, entry.getKey()),
                        this.encodeValue(this.valueCodecFunction.apply(entry.getKey()), entry.getValue(), ops));
            }
            return mapBuilder.build(prefix);
        }

        @SuppressWarnings("unchecked")
        private <T, V2 extends V> DataResult<T> encodeValue(final Codec<V2> codec, final V input,
                                                            final DynamicOps<T> ops) {
            return codec.encodeStart(ops, (V2) input);
        }

        @Override
        public <T> DataResult<Pair<Map<K, V>, T>> decode(final DynamicOps<T> ops, final T input) {
            return ops.getMap(input).flatMap(map -> {
                final Map<K, V> entries = new Object2ObjectArrayMap<>();
                final Stream.Builder<Pair<T, T>> failed = Stream.builder();

                final DataResult<Unit> finalResult = map.entries().reduce(
                        DataResult.success(Unit.INSTANCE, Lifecycle.stable()),
                        (result, entry) -> this.parseEntry(result, ops, entry, entries, failed),
                        (first, second) -> first.apply2stable((left, right) -> left, second));

                final Pair<Map<K, V>, T> pair = Pair.of(ImmutableMap.copyOf(entries), input);
                final T errors = ops.createMap(failed.build());
                return finalResult.map(ignored -> pair).setPartial(pair)
                        .mapError(error -> error + " missed input: " + errors);
            });
        }

        private <T> DataResult<Unit> parseEntry(final DataResult<Unit> result, final DynamicOps<T> ops,
                                                final Pair<T, T> input, final Map<K, V> entries,
                                                final Stream.Builder<Pair<T, T>> failed) {
            final DataResult<K> keyResult = this.keyCodec.parse(ops, input.getFirst());
            final DataResult<V> valueResult = keyResult.map(this.valueCodecFunction)
                    .flatMap(valueCodec -> valueCodec.parse(ops, input.getSecond()).map(Function.identity()));
            final DataResult<Pair<K, V>> entryResult = keyResult.apply2stable(Pair::of, valueResult);

            final Optional<Pair<K, V>> entry = entryResult.resultOrPartial(ignored -> {});
            if (entry.isPresent()) {
                final K key = entry.get().getFirst();
                final V value = entry.get().getSecond();
                if (entries.putIfAbsent(key, value) != null) {
                    failed.add(input);
                    return result.apply2stable((unit, ignored) -> unit,
                            DataResult.error(() -> "Duplicate entry for key: '" + key + "'"));
                }
            }
            if (entryResult.error().isPresent()) {
                failed.add(input);
            }

            return result.apply2stable((unit, ignored) -> unit, entryResult);
        }
    }

}
