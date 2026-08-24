package dev.ryanhcode.sable.validation;

import dev.ryanhcode.sable.network.udp.AddressedSableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPPacketType;

import java.util.concurrent.atomic.AtomicInteger;

/** UDP receipt counters owned exclusively by the development validation mod. */
public final class SableForgeUDPValidationTelemetry {
    private static final AtomicInteger SNAPSHOTS = new AtomicInteger();
    private static final AtomicInteger SNAPSHOT_INFO = new AtomicInteger();

    private SableForgeUDPValidationTelemetry() {
    }

    public static void record(final AddressedSableUDPPacket packet) {
        if (packet.packet().getType() == SableUDPPacketType.SNAPSHOT) SNAPSHOTS.incrementAndGet();
        if (packet.packet().getType() == SableUDPPacketType.SNAPSHOT_INFO) SNAPSHOT_INFO.incrementAndGet();
    }

    public static int snapshotPacketsReceived() { return SNAPSHOTS.get(); }
    public static int snapshotInfoPacketsReceived() { return SNAPSHOT_INFO.get(); }
}
