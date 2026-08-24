package dev.ryanhcode.sable.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Locks the two target-version lifecycle adaptations used when Oculus disables Flywheel. */
class ContraptionShaderFallbackContractTest {

    private static final Path LEVEL_RENDERER_ADAPTER = Path.of(System.getProperty("user.dir"))
            .resolve("src/port/java/dev/ryanhcode/sable/mixin/port/sublevel_render/LevelRendererMixin.java")
            .normalize();
    private static final Path FLYWHEEL_COMPAT = Path.of(System.getProperty("user.dir"))
            .resolve("src/main/java/dev/ryanhcode/sable/neoforge/compatibility/flywheel/FlywheelCompatNeoForge.java")
            .normalize();

    @Test
    void entityCompiledChunkCheckUsesProjectedPosition() throws IOException {
        final String source = Files.readString(LEVEL_RENDERER_ADAPTER);
        final int compiledChunkCheck = source.indexOf("isChunkCompiled(Lnet/minecraft/core/BlockPos;)Z");
        final int containingSubLevel = source.indexOf("getContainingClient(entity)", compiledChunkCheck);
        final int projection = source.indexOf("renderPose(partialTick).transformPosition(entity.position())",
                containingSubLevel);
        final int projectedCheck = source.indexOf("original.call(instance, projectedBlockPosition)", projection);

        assertTrue(compiledChunkCheck >= 0, "The exact 1.20.1 entity compiled-chunk gate is not wrapped");
        assertTrue(containingSubLevel > compiledChunkCheck, "The wrapper does not limit itself to sub-level entities");
        assertTrue(projection > containingSubLevel, "The entity position is not projected out of plot space");
        assertTrue(projectedCheck > projection, "The physical chunk is not passed to the vanilla compiled check");
    }

    @Test
    void flywheelStatesRecoverBeforeBeginFrame() throws IOException {
        final String source = Files.readString(FLYWHEEL_COMPAT);
        final int preFrame = source.indexOf("preVisualizationFrame");
        final int allSubLevels = source.indexOf("container.getAllSubLevels()", preFrame);
        final int stateCreation = source.indexOf("RENDER_POSES.put(plotCoord, renderState)", allSubLevels);

        assertTrue(preFrame >= 0);
        assertTrue(allSubLevels > preFrame,
                "Existing sub-levels are not enumerated when Flywheel recreates its manager");
        assertTrue(stateCreation > allSubLevels,
                "Missing render states are not restored before Flywheel beginFrame");
    }
}
