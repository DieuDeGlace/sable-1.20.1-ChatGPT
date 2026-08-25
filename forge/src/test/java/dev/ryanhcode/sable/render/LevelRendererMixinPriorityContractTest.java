package dev.ryanhcode.sable.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps every active Sable renderLevel transformation behind Oculus's
 * priority-999 implicit Iterator modifier.
 */
class LevelRendererMixinPriorityContractTest {

    private static final String AFTER_OCULUS_MIXIN =
            "@Mixin(value = LevelRenderer.class, priority = 900)";

    @Test
    void renderLevelTransformationsApplyAfterOculus() throws IOException {
        final Path forgeRoot = Path.of(System.getProperty("user.dir"));
        final List<Path> sources = List.of(
                forgeRoot.resolve("../common/src/main/java/dev/ryanhcode/sable/mixin/block_decal_render/LevelRendererMixin.java"),
                forgeRoot.resolve("src/main/java/dev/ryanhcode/sable/neoforge/mixin/block_outline_render/LevelRendererMixin.java"),
                forgeRoot.resolve("src/port/java/dev/ryanhcode/sable/forge/adaptermixins/GlobalSubLevelBlockEntityMixin.java"),
                forgeRoot.resolve("src/port/java/dev/ryanhcode/sable/mixin/port/sublevel_render/LevelRendererMixin.java")
        );

        for (final Path source : sources) {
            final String code = Files.readString(source.normalize());
            assertTrue(code.contains(AFTER_OCULUS_MIXIN),
                    () -> source + " must apply after Oculus priority 999");
        }
    }

    @Test
    void renderLevelTransformsRedirectOwnedOperationsWithoutSharedLocals() throws IOException {
        final Path forgeRoot = Path.of(System.getProperty("user.dir"));
        final String decal = Files.readString(forgeRoot.resolve(
                "../common/src/main/java/dev/ryanhcode/sable/mixin/block_decal_render/LevelRendererMixin.java").normalize());
        final String outline = Files.readString(forgeRoot.resolve(
                "src/main/java/dev/ryanhcode/sable/neoforge/mixin/block_outline_render/LevelRendererMixin.java").normalize());
        final String globalBlockEntity = Files.readString(forgeRoot.resolve(
                "src/port/java/dev/ryanhcode/sable/forge/adaptermixins/GlobalSubLevelBlockEntityMixin.java").normalize());

        assertTrue(decal.contains("LevelRenderer;destructionProgress"));
        assertTrue(decal.contains("PoseStack;translate(DDD)V"),
                "the physical transform must replace the destruction loop's own translation");
        assertTrue(decal.contains("BlockRenderDispatcher;renderBreakingTexture"),
                "the slice must end at the decal renderer after the physical transform");
        assertFalse(decal.contains("@Local"));
        assertFalse(decal.contains("@WrapOperation"));

        assertTrue(outline.contains("ForgeHooksClient;onDrawHighlight"));
        assertTrue(outline.contains("LevelRenderer;renderHitOutline"));
        assertFalse(outline.contains("@Share"));
        assertFalse(outline.contains("@WrapOperation"));
        assertFalse(outline.contains("@ModifyArg"));

        assertTrue(globalBlockEntity.contains("PoseStack;pushPose()V"));
        assertFalse(globalBlockEntity.contains("@ModifyVariable"));
        assertFalse(globalBlockEntity.contains("@Inject(method = \"renderLevel\""));
    }
}
