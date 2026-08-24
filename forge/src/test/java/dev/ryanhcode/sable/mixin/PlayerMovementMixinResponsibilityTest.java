package dev.ryanhcode.sable.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prevents the client/server plot-coordinate conversion hooks from being registered twice. */
class PlayerMovementMixinResponsibilityTest {

    private static final Path MIXIN_CONFIG = Path.of(System.getProperty("user.dir"))
            .resolve("../common/src/main/resources/sable.mixins.json")
            .normalize();
    private static final Path PRODUCTION_REFMAP = Path.of(System.getProperty("user.dir"))
            .resolve("src/port/resources/sable.refmap.json")
            .normalize();

    @Test
    void eachPlayerMovementResponsibilityHasOneProductionOwner() throws IOException {
        final String config = Files.readString(MIXIN_CONFIG);

        assertExactlyOnce(config, "admitted.LocalPlayerPacketCoordinateMixin");
        assertExactlyOnce(config,
                "entity.entities_stick_sublevels.player.ServerboundMovePlayerPacketMixin");
        assertExactlyOnce(config,
                "entity.entities_stick_sublevels.player.ServerGamePacketListenerImplMixin");

        assertFalse(config.contains("admitted.LocalPlayerMovePacketMixin"),
                "Client inverse coordinate conversion has both upstream and port owners");
        assertFalse(config.contains("entity.entities_stick_sublevels.player.LocalPlayerMixin"),
                "Unsafe player-position mutation remains active beside the non-mutating packet adapter");
        assertFalse(config.contains("admitted.PlayerMovePacketMixin"),
                "Serverbound packet conversion has both upstream and port owners");
        assertFalse(config.contains("admitted.ServerPlayerMovePacketMixin"),
                "Server listener coordinate conversion has both upstream and port owners");
    }

    @Test
    void plotBlockEntityTickPolicyHasOneProductionOwner() throws IOException {
        final String config = Files.readString(MIXIN_CONFIG);

        assertExactlyOnce(config, "plot.LevelBlockEntityTickPolicyMixin");
        assertFalse(config.contains("plot.ServerLevelTickPolicyMixin"),
                "The unsafe 1.20.1 whole-chunk tick adapter is still active");
        assertFalse(config.contains("\"plot.ServerLevelMixin\""),
                "The 1.21 upstream tick-policy mixin and its 1.20 target adapter are both active");
    }

    @Test
    void clientPacketAdapterNeverMutatesPlayerPosition() throws IOException {
        final Path source = Path.of(System.getProperty("user.dir"))
                .resolve("src/port/java/dev/ryanhcode/sable/admitted/LocalPlayerPacketCoordinateMixin.java")
                .normalize();
        final String code = Files.readString(source);

        assertFalse(code.contains("sable$setPosField"),
                "Movement packet adapter must never expose plot coordinates through Entity.position");
        assertTrue(code.contains("@ModifyExpressionValue"));
        assertTrue(code.contains("getX()D"));
        assertTrue(code.contains("getY()D"));
        assertTrue(code.contains("getZ()D"));
    }

    @Test
    void clientPacketAdapterHasExactFormalMappings() throws IOException {
        final String refmap = Files.readString(PRODUCTION_REFMAP);
        final String owner = "dev/ryanhcode/sable/mixin/admitted/LocalPlayerPacketCoordinateMixin";
        final String quotedOwner = '"' + owner + '"';

        assertEquals(2, countOccurrences(refmap, quotedOwner),
                "The base and searge refmap sections must both own the packet adapter");
        int from = 0;
        for (int section = 0; section < 2; section++) {
            final int start = refmap.indexOf(quotedOwner, from);
            final int end = refmap.indexOf('}', start);
            final String mappings = refmap.substring(start, end);
            assertTrue(mappings.contains("\"sendPosition\": \"Lnet/minecraft/client/player/LocalPlayer;m_108640_()V\""));
            assertTrue(mappings.contains("\"Lnet/minecraft/client/player/LocalPlayer;getX()D\": \"Lnet/minecraft/client/player/LocalPlayer;m_20185_()D\""));
            assertTrue(mappings.contains("\"Lnet/minecraft/client/player/LocalPlayer;getY()D\": \"Lnet/minecraft/client/player/LocalPlayer;m_20186_()D\""));
            assertTrue(mappings.contains("\"Lnet/minecraft/client/player/LocalPlayer;getZ()D\": \"Lnet/minecraft/client/player/LocalPlayer;m_20189_()D\""));
            from = end + 1;
        }
        assertFalse(refmap.contains("dev/ryanhcode/sable/mixin/admitted/LocalPlayerMovePacketMixin"));
    }

    private static void assertExactlyOnce(final String config, final String mixin) {
        final String needle = '"' + mixin + '"';
        final int count = countOccurrences(config, needle);
        assertEquals(1, count, "Expected one active owner for " + mixin + ", found " + count);
    }

    private static int countOccurrences(final String text, final String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }
}
