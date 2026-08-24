package dev.ryanhcode.sable.util;

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SableBufferUtilsTest {

    @Test
    void vectorsAndQuaternionPreserveDeclaredWireOrder() {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        SableBufferUtils.write(buffer, new Vector3d(1.25, -2.5, 3.75));
        SableBufferUtils.write(buffer, new Vector3f(4.5F, -5.25F, 6.75F));
        SableBufferUtils.write(buffer, new Quaterniond(0.125, -0.25, 0.5, 0.75));

        assertEquals(1.25, buffer.readDouble());
        assertEquals(-2.5, buffer.readDouble());
        assertEquals(3.75, buffer.readDouble());
        assertEquals(4.5F, buffer.readFloat());
        assertEquals(-5.25F, buffer.readFloat());
        assertEquals(6.75F, buffer.readFloat());
        assertEquals(0.125F, buffer.readFloat());
        assertEquals(-0.25F, buffer.readFloat());
        assertEquals(0.5F, buffer.readFloat());
        assertEquals(0.75F, buffer.readFloat());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void poseCodecRetainsUpstreamPositionOrientationRotationPointLayout() {
        final Pose3d expected = new Pose3d(
                new Vector3d(10.0, 20.0, 30.0),
                new Quaterniond(0.25, 0.5, 0.75, 1.0),
                new Vector3d(-1.0, -2.0, -3.0),
                new Vector3d(2.0, 3.0, 4.0));
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        SableBufferUtils.POSE3D_STREAM_CODEC.encode(buffer, expected);

        assertEquals(64, buffer.readableBytes());
        final Pose3d decoded = SableBufferUtils.POSE3D_STREAM_CODEC.decode(buffer);
        assertEquals(expected.position(), decoded.position());
        assertEquals(expected.orientation(), decoded.orientation());
        assertEquals(expected.rotationPoint(), decoded.rotationPoint());
        assertEquals(new Vector3d(1.0, 1.0, 1.0), decoded.scale());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void boundingBoxUsesSixSignedIntsInMinThenMaxOrder() {
        final BoundingBox3i expected = new BoundingBox3i(-4, -3, -2, 7, 8, 9);
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        SableBufferUtils.write(buffer, expected);

        assertEquals(-4, buffer.readInt());
        assertEquals(-3, buffer.readInt());
        assertEquals(-2, buffer.readInt());
        assertEquals(7, buffer.readInt());
        assertEquals(8, buffer.readInt());
        assertEquals(9, buffer.readInt());
        buffer.readerIndex(0);
        final BoundingBox3i decoded = SableBufferUtils.read(buffer, new BoundingBox3i());
        assertEquals(expected, decoded);
        assertEquals(0, buffer.readableBytes());
    }
}
