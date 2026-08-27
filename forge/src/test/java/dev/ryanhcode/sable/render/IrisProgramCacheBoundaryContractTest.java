package dev.ryanhcode.sable.render;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisProgramCacheBoundaryContractTest {
    @Test
    void extendedShaderNoLongerForcesOrPollsGlobalGlStateOnEveryApply() throws Exception {
        final Path forgeRoot = Path.of(System.getProperty("user.dir")).normalize();
        final String mixin = Files.readString(forgeRoot.resolve(
                "src/port/java/dev/ryanhcode/sable/admitted/ExtendedShaderMixin.java"));

        assertTrue(mixin.contains("sable$refreshModelMatrices"));
        assertFalse(mixin.contains("m_173363_"));
        assertFalse(mixin.contains("glUseProgram"));
        assertFalse(mixin.contains("GL_CURRENT_PROGRAM"));
        assertFalse(mixin.contains("glIsProgram"));
    }
}
