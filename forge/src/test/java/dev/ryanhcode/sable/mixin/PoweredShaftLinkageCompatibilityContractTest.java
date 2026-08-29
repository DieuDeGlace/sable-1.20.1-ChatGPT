package dev.ryanhcode.sable.mixin;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PoweredShaftLinkageCompatibilityContractTest {
    @Test
    void onlyRepairsImpossibleZeroOwnershipInsideOneSableSubLevel() throws Exception {
        final Path forgeRoot = Path.of(System.getProperty("user.dir")).normalize();
        final String source = Files.readString(forgeRoot.resolve(
                "src/main/java/dev/ryanhcode/sable/neoforge/mixin/compatibility/create/steam_engine/PoweredShaftBlockEntityMixin.java"));
        final String mixinConfig = Files.readString(forgeRoot.resolve(
                "src/port/resources/sable-create.mixins.json"));

        assertTrue(source.contains("@Mixin(PoweredShaftBlockEntity.class)"));
        assertTrue(source.contains("method = \"canBePoweredBy\""));
        assertTrue(source.contains("at = @At(\"RETURN\")"));
        assertTrue(source.contains("cancellable = true"));
        assertTrue(source.contains("this.initialTicks != 0"));
        assertTrue(source.contains("BlockPos.ZERO.equals(this.enginePos)"));
        assertTrue(source.contains("Sable.HELPER.getContaining(shaft)"));
        assertTrue(source.contains("Sable.HELPER.getContaining(level, candidateEnginePos) != shaftSubLevel"));
        assertTrue(source.contains("instanceof SteamEngineBlockEntity"));
        assertTrue(source.contains("SteamEngineBlock.getShaftPos"));
        assertTrue(source.contains("SteamEngineBlock.isShaftValid"));
        assertTrue(source.contains("if (!level.isClientSide)"));
        assertTrue(source.indexOf("if (!level.isClientSide)")
                < source.indexOf("this.enginePos = shaft.getBlockPos().subtract(candidateEnginePos)"));
        assertTrue(source.contains("shaft.notifyUpdate()"));
        assertTrue(mixinConfig.contains(
                "compatibility.create.steam_engine.PoweredShaftBlockEntityMixin"));
    }
}
