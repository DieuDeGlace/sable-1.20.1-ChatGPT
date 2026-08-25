package dev.ryanhcode.sable.render;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywheelShaderAbiTest {
    private static final List<String> COHERENT_SOURCES = List.of(
            "internal/common.vert",
            "internal/api_impl.glsl",
            "internal/light_lut.glsl",
            "internal/instancing/light.glsl",
            "internal/indirect/light.glsl",
            "internal/indirect/matrices.glsl",
            "internal/instancing/main.vert",
            "internal/indirect/main.vert",
            "light/flat.glsl",
            "light/smooth.glsl",
            "light/smooth_when_embedded.glsl"
    );

    @Test
    void coherentShaderAbiIsAvailableUnderSableNamespace() throws Exception {
        final Path root = Path.of(System.getProperty("user.dir"))
                .resolve("src/main/resources/assets/sable/flywheel")
                .normalize();

        for (final String relative : COHERENT_SOURCES) {
            assertTrue(Files.isRegularFile(root.resolve(relative)),
                    () -> "Missing coherent Flywheel shader source: " + relative);
        }

        final String api = Files.readString(root.resolve("internal/api_impl.glsl"));
        assertTrue(api.contains("bool flw_light(uint scene"), "Scene-aware light API is missing");
        assertTrue(api.contains("bool flw_light(vec3 worldPos, vec3 normal, out FlwLightAo light)"),
                "Flywheel 1.0.5 light API adapter is missing");
        assertTrue(api.contains("bool flw_lightFetch(ivec3 blockPos, out vec2 light)"),
                "Flywheel 1.0.5 light-fetch API adapter is missing");

        final String matrices = Files.readString(root.resolve("internal/indirect/matrices.glsl"));
        assertTrue(matrices.contains("mat4 lightingSceneMatrix;"), "Lighting scene matrix is missing");
        assertTrue(matrices.contains("uint sceneID;"), "Lighting scene id is missing");
    }
}
