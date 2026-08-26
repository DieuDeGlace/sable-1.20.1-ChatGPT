package dev.ryanhcode.sable.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the upstream renderer-selection and isolated-buffer ownership contract. */
class EmbeddiumReachAroundContractTest {

    @Test
    void embeddiumSelectsDedicatedReachAroundDispatcher() throws IOException {
        final Path root = Path.of(System.getProperty("user.dir")).resolve("..").normalize();
        final String selection = Files.readString(root.resolve(
                "common/src/main/java/dev/ryanhcode/sable/sublevel/render/SubLevelRenderer.java"));
        final String dispatcher = Files.readString(root.resolve(
                "forge/src/port/java/dev/ryanhcode/sable/sublevel/render/dispatcher/ReachAroundSubLevelRenderDispatcher.java"));
        final String bridge = Files.readString(root.resolve(
                "forge/src/port/java/dev/ryanhcode/sable/mixin/sublevel_render/impl/embeddium/EmbeddiumDirtyBridgeMixin.java"));

        assertTrue(selection.contains("Veil.platform().isModLoaded(\"embeddium\")"));
        assertTrue(selection.contains("return new ReachAroundSubLevelRenderDispatcher()"));
        assertTrue(dispatcher.contains("this.fixedBufferPack = new ChunkBufferBuilderPack()"));
        assertTrue(dispatcher.contains("this.fixedBufferPack, this.asynchronousBuilderCount"));
        assertTrue(dispatcher.contains("sectionRenderDispatcher.uploadAllPendingUploads()"));
        assertTrue(bridge.contains("dispatcher.preRenderChunks(camera)"));
        assertTrue(bridge.contains("subLevel.getRenderData().compileSections"));
        assertTrue(bridge.contains("@Mixin(targets = \"me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer\", remap = false)"));
        assertTrue(bridge.contains("renderType.clearRenderState()"));
    }
}
