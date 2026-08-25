package dev.ryanhcode.sable.api;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SubLevelAssemblyHelperTest {
    private static final double EPSILON = 1.0E-7D;

    @Test
    void supportBoxBackportHandlesAllSixAttachmentDirections() {
        final AABB bounds = new AABB(10.25D, 20.25D, 30.25D, 10.75D, 20.75D, 30.75D);

        for (final Direction direction : Direction.values()) {
            final AABB actual = SubLevelAssemblyHelper.calculateHangingSupportBox(bounds, direction);
            final double offsetX = -0.5D * direction.getStepX();
            final double offsetY = -0.5D * direction.getStepY();
            final double offsetZ = -0.5D * direction.getStepZ();

            assertAll(direction.getName(),
                    () -> assertEquals(bounds.minX + offsetX - EPSILON, actual.minX),
                    () -> assertEquals(bounds.minY + offsetY - EPSILON, actual.minY),
                    () -> assertEquals(bounds.minZ + offsetZ - EPSILON, actual.minZ),
                    () -> assertEquals(bounds.maxX + offsetX + EPSILON, actual.maxX),
                    () -> assertEquals(bounds.maxY + offsetY + EPSILON, actual.maxY),
                    () -> assertEquals(bounds.maxZ + offsetZ + EPSILON, actual.maxZ));
        }
    }
}
