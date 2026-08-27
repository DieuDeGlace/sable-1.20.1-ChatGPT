package dev.ryanhcode.sable.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the Forge level-before-chunk capability initialization contract. */
class ServerChunkCapabilityLifecycleContractTest {

    @Test
    void fallbackChunkIsCreatedAfterServerLevelConstruction() throws IOException {
        final Path root = Path.of(System.getProperty("user.dir")).resolve("..").normalize();
        final String chunkCacheMixin = Files.readString(root.resolve(
                "forge/src/port/java/support/ServerChunkCacheMixin.java"));
        final String levelLifecycleMixin = Files.readString(root.resolve(
                "forge/src/port/java/support/ServerLevelLifecycleMixin.java"));

        assertFalse(chunkCacheMixin.contains("@Inject(method = \"<init>\""),
                "ServerChunkCache construction occurs before Forge initializes ServerLevel capabilities");
        assertTrue(chunkCacheMixin.contains("sable$initializeEmptyChunkAfterLevelCapabilities()"));
        assertTrue(chunkCacheMixin.contains("new EmptyLevelChunk(this.level"));
        assertTrue(chunkCacheMixin.contains("requested before ServerLevel capabilities initialized"));

        assertTrue(levelLifecycleMixin.contains("@Inject(method = \"<init>\", at = @At(\"TAIL\"))"));
        assertTrue(levelLifecycleMixin.contains("sable$initializeEmptyChunkAfterLevelCapabilities()"));
    }
}
