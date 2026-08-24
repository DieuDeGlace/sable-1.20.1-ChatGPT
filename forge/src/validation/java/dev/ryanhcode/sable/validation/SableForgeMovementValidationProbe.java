package dev.ryanhcode.sable.validation;

import com.mojang.logging.LogUtils;
import net.minecraft.world.phys.Vec3;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

/** Runtime-only numerical evidence for the admitted player movement hooks. */
public final class SableForgeMovementValidationProbe {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicInteger CLIENT_PACKETS = new AtomicInteger();
    private static final AtomicInteger SERVER_PACKETS = new AtomicInteger();
    private static final AtomicInteger PLATFORM_MOVES = new AtomicInteger();
    private static volatile SubLevel clientArmedSubLevel;
    private static volatile Vec3 clientExpectedGlobal;
    private static volatile SubLevel clientExpectedSubLevel;
    private static volatile boolean clientPositionPacketObserved;

    private SableForgeMovementValidationProbe() {
    }

    public static void clientPacket(final Vec3 global, final Vec3 local,
                                    final Vec3 roundTrip, final Vec3 restored) {
        if (!enabled()) return;
        final double roundTripError = global.distanceTo(roundTrip);
        final double restoreError = global.distanceTo(restored);
        if (global.distanceTo(local) < 1.0D || roundTripError > 1.0E-7D || restoreError > 1.0E-12D) {
            throw new IllegalStateException("Invalid client local movement packet conversion: global=" + global
                    + ", local=" + local + ", roundTrip=" + roundTrip + ", restored=" + restored
                    + ", errors=" + roundTripError + "/" + restoreError);
        }
        if (CLIENT_PACKETS.incrementAndGet() == 1) {
            LOGGER.info("Sable REAL CLIENT LOCAL MOVEMENT PACKET VERIFIED "
                    + "(global {}, packet-local {}, round-trip error {}, restore error {})",
                    global, local, roundTripError, restoreError);
        }
    }

    public static void serverPacket(final Vec3 local, final Vec3 global, final Vec3 expected) {
        if (!enabled()) return;
        final double error = global.distanceTo(expected);
        if (global.distanceTo(local) < 1.0D || error > 1.0E-7D) {
            throw new IllegalStateException("Invalid server global movement packet conversion: local=" + local
                    + ", global=" + global + ", expected=" + expected + ", error=" + error);
        }
        if (SERVER_PACKETS.incrementAndGet() == 1) {
            LOGGER.info("Sable REAL FORGE SERVER GLOBAL MOVEMENT PACKET VERIFIED "
                    + "(packet-local {}, decoded-global {}, error {})", local, global, error);
        }
    }

    public static void platformMove(final Vec3 before, final Vec3 expected, final Vec3 actual) {
        if (!enabled() || before.distanceTo(expected) < 1.0E-6D) return;
        final double error = expected.distanceTo(actual);
        if (error > 1.0E-10D) {
            throw new IllegalStateException("Invalid server player platform displacement: before=" + before
                    + ", expected=" + expected + ", actual=" + actual + ", error=" + error);
        }
        if (PLATFORM_MOVES.incrementAndGet() == 1) {
            LOGGER.info("Sable REAL SERVER PLAYER PLATFORM DISPLACEMENT VERIFIED "
                    + "(before {}, expected/actual {}, error {})", before, actual, error);
        }
    }

    public static int clientPackets() {
        return CLIENT_PACKETS.get();
    }

    public static int serverPackets() {
        return SERVER_PACKETS.get();
    }

    public static int platformMoves() {
        return PLATFORM_MOVES.get();
    }

    public static void armClientPacket(final SubLevel subLevel) {
        if (enabled()) clientArmedSubLevel = subLevel;
    }

    public static SubLevel takeClientPacketArm() {
        final SubLevel armed = clientArmedSubLevel;
        clientArmedSubLevel = null;
        return armed;
    }

    public static void beginClientPacket(final Vec3 global, final SubLevel subLevel) {
        if (!enabled() || subLevel == null) return;
        clientExpectedGlobal = global;
        clientExpectedSubLevel = subLevel;
        clientPositionPacketObserved = false;
    }

    public static void observeClientPacket(final ServerboundMovePlayerPacket packet,
                                           final Vec3 currentPlayerPosition) {
        final Vec3 global = clientExpectedGlobal;
        final SubLevel subLevel = clientExpectedSubLevel;
        if (!enabled() || global == null || subLevel == null || !packet.hasPosition()) return;
        final Vec3 local = new Vec3(packet.getX(Double.NaN), packet.getY(Double.NaN),
                packet.getZ(Double.NaN));
        final org.joml.Vector3d transformed = subLevel.logicalPose().transformPosition(
                new org.joml.Vector3d(local.x, local.y, local.z));
        clientPacket(global, local, new Vec3(transformed.x, transformed.y, transformed.z),
                currentPlayerPosition);
        clientPositionPacketObserved = true;
    }

    public static void finishClientPacket(final Vec3 currentPlayerPosition) {
        final Vec3 global = clientExpectedGlobal;
        try {
            if (enabled() && global != null) {
                if (!clientPositionPacketObserved) {
                    throw new IllegalStateException("Movement validation did not observe a position-bearing packet");
                }
                final double error = global.distanceTo(currentPlayerPosition);
                if (error > 1.0E-12D) {
                    throw new IllegalStateException("Client player position changed while creating movement packet: "
                            + global + " -> " + currentPlayerPosition + ", error=" + error);
                }
            }
        } finally {
            clientExpectedGlobal = null;
            clientExpectedSubLevel = null;
            clientPositionPacketObserved = false;
        }
    }

    private static boolean enabled() {
        return Boolean.getBoolean("sable.movementValidation");
    }
}
