package dev.sabletesttools;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class TestSessions {
    private static final Map<UUID, TestSession> SESSIONS = new ConcurrentHashMap<>();

    private TestSessions() {
    }

    static TestSession get(final ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new TestSession());
    }

    static void remove(final UUID playerId) {
        SESSIONS.remove(playerId);
    }
}
