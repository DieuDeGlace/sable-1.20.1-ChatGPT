package dev.ryanhcode.sable.mixin;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputerCraftCompatibilityContractTest {
    private static final String MIXIN =
            "compatibility.computercraft.WirelessNetworkMixin";
    private static final String OWNER =
            "dev/ryanhcode/sable/mixin/compatibility/computercraft/WirelessNetworkMixin";

    @Test
    void wirelessRangeUsesTheUpstreamSubLevelDistanceContract() throws Exception {
        final Path forgeRoot = Path.of(System.getProperty("user.dir")).normalize();
        final String source = Files.readString(forgeRoot.resolve(
                "../common/src/main/java/dev/ryanhcode/sable/mixin/compatibility/computercraft/WirelessNetworkMixin.java"));
        final String config = Files.readString(forgeRoot.resolve(
                "../common/src/main/resources/sable.mixins.json"));
        final String refmap = Files.readString(forgeRoot.resolve(
                "src/port/resources/sable.refmap.json"));
        final String properties = Files.readString(forgeRoot.resolve("../gradle.properties"));

        assertTrue(source.contains("dan200.computercraft.shared.peripheral.modem.wireless.WirelessNetwork"));
        assertTrue(source.contains(
                "tryTransmit(Ldan200/computercraft/api/network/PacketReceiver;Ldan200/computercraft/api/network/Packet;DZ)V"));
        assertTrue(source.contains(
                "Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"));
        assertTrue(source.contains("remap = true"));
        assertTrue(source.contains(
                "Sable.HELPER.distanceSquaredWithSubLevels(packetReceiver.getLevel(), a, b)"));
        assertEquals(1, count(config, '"' + MIXIN + '"'));
        assertEquals(2, count(refmap, '"' + OWNER + '"'));
        assertTrue(refmap.contains(
                "Lnet/minecraft/world/phys/Vec3;m_82557_(Lnet/minecraft/world/phys/Vec3;)D"));
        assertTrue(properties.contains("cc_tweaked_version=1.120.2"));
    }

    private static int count(final String text, final String needle) {
        int occurrences = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            occurrences++;
            index += needle.length();
        }
        return occurrences;
    }
}
