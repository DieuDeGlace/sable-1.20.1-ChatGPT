package dev.ryanhcode.sable.render;

import foundry.veil.api.client.render.shader.ShaderModificationManager;
import foundry.veil.impl.client.render.shader.modifier.ShaderModification;
import foundry.veil.impl.client.render.shader.transformer.VeilJobParameters;
import foundry.veil.impl.glsl.GlslParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicShadeShaderModifierTest {
    private static final String VANILLA_CHUNK_VERTEX_SHADER = """
            #version 150
            in vec3 Position;
            in vec4 Color;
            in vec2 UV0;
            in ivec2 UV2;
            in vec3 Normal;
            uniform sampler2D Sampler2;
            uniform mat4 ModelViewMat;
            uniform mat4 ProjMat;
            uniform vec3 ChunkOffset;
            out vec4 vertexColor;
            vec4 minecraft_sample_lightmap(sampler2D lightMap, ivec2 uv) {
                return vec4(1.0);
            }
            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position + ChunkOffset, 1.0);
                vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
            }
            """;
    private static final List<String> CHUNK_VERTEX_SHADERS = List.of(
            "rendertype_solid.vsh.txt",
            "rendertype_cutout.vsh.txt",
            "rendertype_cutout_mipped.vsh.txt",
            "rendertype_translucent.vsh.txt",
            "rendertype_tripwire.vsh.txt"
    );

    @Test
    void allVanillaChunkModifiersParseWithVeil120() {
        final Path root = Path.of(System.getProperty("user.dir"))
                .resolve("src/port/resources/assets/sable/pinwheel/shader_modifiers/minecraft/shaders/core")
                .normalize();

        for (final String fileName : CHUNK_VERTEX_SHADERS) {
            final Path file = root.resolve(fileName);
            assertTrue(Files.isRegularFile(file), () -> "Missing shader modifier: " + file);
            assertDoesNotThrow(() -> {
                final ShaderModification modifier = ShaderModification.parse(Files.readString(file), true);
                modifier.inject(GlslParser.parse(VANILLA_CHUNK_VERTEX_SHADER),
                        new VeilJobParameters(new ShaderModificationManager(),
                                new ResourceLocation("minecraft", "shaders/core/" + fileName.replace(".txt", "")),
                                VeilJobParameters.APPLY_VERSION));
            },
                    () -> "Veil 1.20 rejected shader modifier: " + file);
        }
    }
}
