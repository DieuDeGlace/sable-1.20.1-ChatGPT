package dev.ryanhcode.sable.network.codec;

import java.util.function.BiConsumer;
import java.util.function.Function;

public interface StreamCodec<B, V> {
    V decode(B buffer);

    void encode(B buffer, V value);

    static <B, V> StreamCodec<B, V> of(final BiConsumer<B, V> encoder, final Function<B, V> decoder) {
        return new StreamCodec<>() {
            @Override
            public V decode(final B buffer) {
                return decoder.apply(buffer);
            }

            @Override
            public void encode(final B buffer, final V value) {
                encoder.accept(buffer, value);
            }
        };
    }

    static <B, V> StreamCodec<B, V> ofMember(final BiConsumer<V, B> encoder, final Function<B, V> decoder) {
        return of((buffer, value) -> encoder.accept(value, buffer), decoder);
    }

    default <O> StreamCodec<B, O> apply(final Function<StreamCodec<B, V>, StreamCodec<B, O>> transformer) {
        return transformer.apply(this);
    }

    static <B, A, V> StreamCodec<B, V> composite(
            final StreamCodec<? super B, A> first, final Function<V, A> firstGetter,
            final Function<A, V> constructor) {
        return of((buffer, value) -> first.encode(buffer, firstGetter.apply(value)),
                buffer -> constructor.apply(first.decode(buffer)));
    }

    static <B, A, C, V> StreamCodec<B, V> composite(
            final StreamCodec<? super B, A> first, final Function<V, A> firstGetter,
            final StreamCodec<? super B, C> second, final Function<V, C> secondGetter,
            final BiFunction2<A, C, V> constructor) {
        return of((buffer, value) -> {
            first.encode(buffer, firstGetter.apply(value));
            second.encode(buffer, secondGetter.apply(value));
        }, buffer -> constructor.apply(first.decode(buffer), second.decode(buffer)));
    }

    static <B, A, C, D, V> StreamCodec<B, V> composite(
            final StreamCodec<? super B, A> first, final Function<V, A> firstGetter,
            final StreamCodec<? super B, C> second, final Function<V, C> secondGetter,
            final StreamCodec<? super B, D> third, final Function<V, D> thirdGetter,
            final Function3<A, C, D, V> constructor) {
        return of((buffer, value) -> {
            first.encode(buffer, firstGetter.apply(value));
            second.encode(buffer, secondGetter.apply(value));
            third.encode(buffer, thirdGetter.apply(value));
        }, buffer -> constructor.apply(first.decode(buffer), second.decode(buffer), third.decode(buffer)));
    }

    @FunctionalInterface
    interface BiFunction2<A, B, R> {
        R apply(A first, B second);
    }

    @FunctionalInterface
    interface Function3<A, B, C, R> {
        R apply(A first, B second, C third);
    }
}
