package dev.ryanhcode.sable.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the vanilla synchronous chunk-build ownership boundary used by sub-level rendering. */
class ChunkCompileUploadDrainContractTest {

    @Test
    void synchronousSubLevelBuildsDrainUploadsBeforeDirtyQueueIsCleared() throws IOException {
        final Path source = Path.of(System.getProperty("user.dir"))
                .resolve("../common/src/main/java/dev/ryanhcode/sable/sublevel/render/vanilla/VanillaChunkedSubLevelRenderData.java")
                .normalize();
        final String code = Files.readString(source);

        final int rebuild = code.indexOf("rebuildChunkSync(renderSection, renderRegionCache)");
        final int drain = code.indexOf("sectionRenderDispatcher.uploadAllPendingUploads()");
        final int clear = code.indexOf("dirtyRenderSections.clear()", drain);

        assertTrue(rebuild >= 0, "synchronous sub-level rebuild call is missing");
        assertTrue(drain > rebuild, "upload queue must be drained after synchronous rebuilds");
        assertTrue(clear > drain, "upload queue must be drained before the dirty list is cleared");
        assertTrue(code.contains("if (synchronousBuilds > 0)"),
                "the shared dispatcher should only be drained when this pass built synchronously");
    }
}
