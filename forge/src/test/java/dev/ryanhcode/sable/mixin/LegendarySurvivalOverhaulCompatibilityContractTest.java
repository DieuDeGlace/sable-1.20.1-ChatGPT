package dev.ryanhcode.sable.mixin;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendarySurvivalOverhaulCompatibilityContractTest {
    @Test
    void lookedAtEntityDistanceOnlyCrossesCoordinateFramesWhenRequired() throws Exception {
        final Path forgeRoot = Path.of(System.getProperty("user.dir")).normalize();
        final String source = Files.readString(forgeRoot.resolve(
                "src/port/java/dev/ryanhcode/sable/admitted/LegendarySurvivalOverhaulWorldUtilMixin.java"));
        final String mixinConfig = Files.readString(forgeRoot.resolve(
                "src/main/resources/sable-forge.mixins.json"));
        final String productionRefmap = Files.readString(forgeRoot.resolve(
                "src/port/resources/sable.refmap.json"));

        assertTrue(source.contains("sfiomn.legendarysurvivaloverhaul.util.WorldUtil"));
        assertTrue(source.contains("getEntityLookedAt(Lnet/minecraft/world/entity/player/Player;D)"));
        assertTrue(source.contains("Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"));
        assertTrue(source.contains("original.call(hitPosition, eyePosition)"));
        assertTrue(source.contains("Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels"));
        assertTrue(mixinConfig.contains(
                "compatibility.legendarysurvivaloverhaul.LegendarySurvivalOverhaulWorldUtilMixin"));
        assertTrue(productionRefmap.contains(
                "dev/ryanhcode/sable/neoforge/mixin/compatibility/legendarysurvivaloverhaul/LegendarySurvivalOverhaulWorldUtilMixin"));
        assertTrue(productionRefmap.contains(
                "Lnet/minecraft/world/phys/Vec3;m_82554_(Lnet/minecraft/world/phys/Vec3;)D"));
    }
}
