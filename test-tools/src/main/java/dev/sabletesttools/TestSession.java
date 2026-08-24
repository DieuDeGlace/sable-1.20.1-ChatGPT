package dev.sabletesttools;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

import java.util.UUID;

final class TestSession {
    enum Mode { CUBOID, CONNECTED }

    BlockPos first;
    BlockPos second;
    ResourceKey<Level> dimension;
    Mode mode = Mode.CUBOID;
    int maxBlocks = 4096;
    String name = "sable-test";
    final Vector3d linearVelocity = new Vector3d();
    final Vector3d angularVelocity = new Vector3d();
    final Vector3d offset = new Vector3d();
    double yaw;
    double pitch;
    double roll;
    UUID lastSubLevel;
    UUID heldSubLevel;
    ResourceKey<Level> heldDimension;
    final Vector3d heldLocalPoint = new Vector3d();
    long lastGravityDiagnosticTick = Long.MIN_VALUE;
    double gravityRange = 32.0D;
    double holdDistance = 4.0D;
    double launchSpeed = 20.0D;
    double followStrength = 8.0D;
    double maxFollowSpeed = 30.0D;

    void select(final ResourceKey<Level> newDimension, final BlockPos position, final boolean firstPoint) {
        if (this.dimension != null && this.dimension != newDimension) {
            this.first = null;
            this.second = null;
        }
        this.dimension = newDimension;
        if (firstPoint) {
            this.first = position.immutable();
        } else {
            this.second = position.immutable();
        }
    }

    void clearSelection() {
        this.first = null;
        this.second = null;
        this.dimension = null;
    }

    void resetConfig() {
        this.mode = Mode.CUBOID;
        this.maxBlocks = 4096;
        this.name = "sable-test";
        this.linearVelocity.zero();
        this.angularVelocity.zero();
        this.offset.zero();
        this.yaw = 0.0D;
        this.pitch = 0.0D;
        this.roll = 0.0D;
    }

    void resetGravityConfig() {
        this.gravityRange = 32.0D;
        this.holdDistance = 4.0D;
        this.launchSpeed = 20.0D;
        this.followStrength = 8.0D;
        this.maxFollowSpeed = 30.0D;
    }

    void clearHeldSubLevel() {
        this.heldSubLevel = null;
        this.heldDimension = null;
        this.heldLocalPoint.zero();
        this.lastGravityDiagnosticTick = Long.MIN_VALUE;
    }
}
