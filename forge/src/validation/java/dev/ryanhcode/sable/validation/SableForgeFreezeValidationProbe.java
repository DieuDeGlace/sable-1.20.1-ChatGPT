package dev.ryanhcode.sable.validation;

import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

/** Captures the transient client freeze result only while the explicit validation is enabled. */
public final class SableForgeFreezeValidationProbe {
    private static int serial;
    private static Snapshot snapshot = new Snapshot(0, null, new Vector3d(), new Vector3d(), false, 0.0D);

    private SableForgeFreezeValidationProbe() {
    }

    public static synchronized void record(final LocalPlayer player, final ClientSubLevel target,
                                           final UUID targetId, final Vector3dc anchor) {
        final Vector3d expected = target.lastPose().transformPosition(new Vector3d(anchor));
        final Vector3d actual = new Vector3d(player.getX(), player.getY(), player.getZ());
        final boolean trackingMatches = ((EntityMovementExtension) player).sable$getTrackingSubLevel() == target;
        final double inheritedSquared = ((LivingEntityMovementExtension) player)
                .sable$getInheritedVelocity().lengthSquared();
        snapshot = new Snapshot(++serial, targetId, expected, actual, trackingMatches, inheritedSquared);
    }

    public static synchronized Snapshot snapshot() {
        return snapshot;
    }

    public record Snapshot(int serial, UUID target, Vector3d expected, Vector3d actual,
                           boolean trackingMatches, double inheritedSquared) {
    }
}
