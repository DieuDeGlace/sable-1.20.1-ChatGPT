package dev.ryanhcode.sable.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VeilPostResolveCompatibilityTest {
    private static final Path MIXIN_ROOT = Path.of("src/main/java/dev/ryanhcode/sable/neoforge/mixin/compatibility/veil");

    @Test
    void postCopiesUseExplicitColorMasks() throws IOException {
        assertColorOnly(MIXIN_ROOT.resolve("VeilDeferredRendererMixin.java"));
        assertColorOnly(MIXIN_ROOT.resolve("PostProcessingManagerMixin.java"));
    }

    private static void assertColorOnly(final Path source) throws IOException {
        final String text = Files.readString(source);
        assertTrue(text.contains("GL_COLOR_BUFFER_BIT"), source + " must copy color explicitly");
        assertTrue(text.contains("GL_NEAREST"), source + " must preserve upstream nearest filtering");
        assertTrue(!text.contains("GL_DEPTH_BUFFER_BIT"), source + " must not use the incompatible default depth copy");
    }
}
