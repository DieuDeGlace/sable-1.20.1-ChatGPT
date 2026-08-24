package dev.ryanhcode.sable.network.codec;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.ryanhcode.sable.physics.config.dimension_physics.BezierResourceFunction;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockMaterial;
import dev.ryanhcode.sable.util.SableCodecUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PhysicsValueCodecsTest {

    @Test
    void dimensionPhysicsRoundTripsEveryOptionalFieldInDeclaredOrder() {
        final BezierResourceFunction pressure = new BezierResourceFunction(List.of(
                new BezierResourceFunction.BezierPoint(-64.0, 1.5, -0.01),
                new BezierResourceFunction.BezierPoint(320.0, 0.0, -0.02)));
        final DimensionPhysics expected = new DimensionPhysics(
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                42,
                Optional.of(0.09F),
                Optional.of(new Vector3f(1.0F, -11.0F, 2.0F)),
                Optional.of(1.25),
                Optional.of(pressure),
                Optional.of(new Vector3f(0.0F, 0.0F, 1.0F)),
                true);
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        DimensionPhysics.STREAM_CODEC.encode(buffer, expected);
        final DimensionPhysics decoded = DimensionPhysics.STREAM_CODEC.decode(buffer);

        assertEquals(expected.dimension(), decoded.dimension());
        assertEquals(expected.priority(), decoded.priority());
        assertEquals(expected.universalDrag(), decoded.universalDrag());
        assertEquals(expected.baseGravity(), decoded.baseGravity());
        assertEquals(expected.basePressure(), decoded.basePressure());
        assertEquals(expected.pressureFunction().orElseThrow().getPoints(),
                decoded.pressureFunction().orElseThrow().getPoints());
        assertEquals(expected.magneticNorth(), decoded.magneticNorth());
        assertEquals(expected.ignoreChunks(), decoded.ignoreChunks());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void dimensionPhysicsRoundTripsAbsentOptionalFields() {
        final DimensionPhysics expected = new DimensionPhysics(
                ResourceLocation.fromNamespaceAndPath("sable", "empty"),
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false);
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        DimensionPhysics.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, DimensionPhysics.STREAM_CODEC.decode(buffer));
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void dimensionPhysicsRejectsMalformedPresentOptionalField() {
        final var result = DimensionPhysics.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {
                  "dimension": "minecraft:overworld",
                  "universal_drag": "not-a-float"
                }
                """));

        assertTrue(result.error().isPresent(),
                "A present malformed field must remain an error, matching DFU 8.0.16 lenient=false");
    }

    @Test
    void floatingMaterialCodecUsesTargetFriendlyByteBufCodecBridge() {
        final FloatingBlockMaterial expected = new FloatingBlockMaterial(
                true, false, true, 8.5, 0.25, 0.1, 0.2, 0.3, 0.4);
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        FloatingBlockMaterial.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, FloatingBlockMaterial.STREAM_CODEC.decode(buffer));
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void dispatchedMapSelectsValueCodecFromDecodedKey() {
        final Map<ResourceLocation, Object> expected = new LinkedHashMap<>();
        expected.put(ResourceLocation.fromNamespaceAndPath("sable", "mass"), 3.5D);
        expected.put(ResourceLocation.fromNamespaceAndPath("sable", "fragile"), true);
        final Codec<Map<ResourceLocation, Object>> codec = SableCodecUtil.dispatchedMap(
                ResourceLocation.CODEC, key -> switch (key.getPath()) {
            case "mass" -> com.mojang.serialization.Codec.DOUBLE;
            case "fragile" -> com.mojang.serialization.Codec.BOOL;
            default -> throw new IllegalArgumentException("Unknown test key: " + key);
        });

        final var encoded = codec.encodeStart(JsonOps.INSTANCE, expected).result().orElseThrow();
        final Map<ResourceLocation, Object> decoded = codec.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();

        assertEquals(expected, decoded);
        assertTrue(codec.parse(JsonOps.INSTANCE,
                        JsonParser.parseString("{\"sable:mass\":\"not-a-double\"}"))
                .error().isPresent());
    }
}
