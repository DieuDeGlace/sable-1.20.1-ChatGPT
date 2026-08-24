package dev.ryanhcode.sable.validation;

import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFreezePlayerPacket;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Packet receipt counters owned exclusively by the development validation mod. */
public final class SableForgeNetworkValidationTelemetry {
    private static final ConcurrentHashMap<Class<?>, AtomicInteger> HANDLED_COUNTS = new ConcurrentHashMap<>();
    private static final AtomicInteger SPLIT_INTERPOLATION_HISTORY = new AtomicInteger();
    private static final AtomicInteger APPLIED_FREEZE_STATES = new AtomicInteger();

    private SableForgeNetworkValidationTelemetry() {
    }

    public static void record(final SableTCPPacket packet) {
        HANDLED_COUNTS.computeIfAbsent(packet.getClass(), ignored -> new AtomicInteger()).incrementAndGet();
    }

    public static int handledCount(final Class<? extends SableTCPPacket> packetClass) {
        final AtomicInteger count = HANDLED_COUNTS.get(packetClass);
        return count == null ? 0 : count.get();
    }

    public static void recordSplitInterpolationHistory(final ClientSubLevel subLevel) {
        SPLIT_INTERPOLATION_HISTORY.accumulateAndGet(subLevel.getInterpolator().buffer.size(), Math::max);
    }

    public static int splitInterpolationHistory() {
        return SPLIT_INTERPOLATION_HISTORY.get();
    }

    public static void recordAppliedFreezeState(final ClientboundFreezePlayerPacket packet,
                                                final Player player) {
        final PlayerFreezeExtension freeze = (PlayerFreezeExtension) player;
        if (packet.subLevelID().equals(freeze.sable$getFrozenToSubLevel())
                && freeze.sable$getFrozenToSubLevelAnchor() != null
                && packet.localPosition().equals(freeze.sable$getFrozenToSubLevelAnchor())) {
            APPLIED_FREEZE_STATES.incrementAndGet();
        }
    }

    public static int appliedFreezeStates() {
        return APPLIED_FREEZE_STATES.get();
    }
}
