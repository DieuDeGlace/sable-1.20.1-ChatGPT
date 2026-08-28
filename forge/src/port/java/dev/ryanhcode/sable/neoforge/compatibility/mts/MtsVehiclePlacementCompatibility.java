package dev.ryanhcode.sable.neoforge.compatibility.mts;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapts MTS vehicle placement from Sable plot coordinates to visible-world
 * coordinates without linking Sable's production artifact to MTS classes.
 */
public final class MtsVehiclePlacementCompatibility {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ITEM_VEHICLE_CLASS =
            "minecrafttransportsimulator.items.instances.ItemVehicle";
    private static final Vec3 VEHICLE_PLACEMENT_OFFSET = new Vec3(0.5, 1.0, 0.5);
    private static final Set<Class<?>> WARNED_POINT_TYPES = ConcurrentHashMap.newKeySet();

    private static final ClassValue<Optional<Method>> POINT_SETTERS = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(final Class<?> type) {
            try {
                return Optional.of(type.getMethod("set", double.class, double.class, double.class));
            } catch (final NoSuchMethodException ignored) {
                return Optional.empty();
            }
        }
    };

    private MtsVehiclePlacementCompatibility() {
    }

    /**
     * Returns whether the wrapped MTS item is a vehicle item. Walking the
     * hierarchy preserves the same semantic boundary for MTS vehicle-item
     * subclasses without treating unrelated MTS block-click handlers as
     * vehicle placement.
     */
    public static boolean isVehicleItem(final Object wrappedItem) {
        if (wrappedItem == null) {
            return false;
        }

        for (Class<?> type = wrappedItem.getClass(); type != null; type = type.getSuperclass()) {
            if (ITEM_VEHICLE_CLASS.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * MTS places a vehicle at {@code clickedBlockOrigin + (0.5, 1, 0.5)}.
     * Transform that final local placement point through the sub-level pose,
     * then remove MTS's unchanged world-space offset so its original addition
     * reconstructs the desired visible-world point.
     */
    public static Vec3 projectClickedBlockOrigin(final Pose3dc pose, final BlockPos clickedPos) {
        final Vec3 localPlacementPoint = Vec3.atLowerCornerOf(clickedPos).add(VEHICLE_PLACEMENT_OFFSET);
        return pose.transformPosition(localPlacementPoint).subtract(VEHICLE_PLACEMENT_OFFSET);
    }

    /**
     * Updates the newly-created MTS Point3D through its public mutation API.
     * Reflection is confined to the optional compatibility boundary so Sable
     * can still build and run without redistributing or linking MTS code.
     */
    public static boolean trySetPointCoordinates(final Object point, final Vec3 coordinates) {
        if (point == null) {
            return false;
        }

        final Optional<Method> setter = POINT_SETTERS.get(point.getClass());
        if (setter.isEmpty()) {
            warnPointContract(point.getClass(), null);
            return false;
        }

        try {
            setter.get().invoke(point, coordinates.x, coordinates.y, coordinates.z);
            return true;
        } catch (final ReflectiveOperationException exception) {
            warnPointContract(point.getClass(), exception);
            return false;
        }
    }

    private static void warnPointContract(final Class<?> pointType, final Throwable cause) {
        if (!WARNED_POINT_TYPES.add(pointType)) {
            return;
        }

        if (cause == null) {
            LOGGER.warn("MTS vehicle placement compatibility skipped: {} has no public set(double, double, double) method",
                    pointType.getName());
        } else {
            LOGGER.warn("MTS vehicle placement compatibility skipped: could not update {}", pointType.getName(), cause);
        }
    }
}
