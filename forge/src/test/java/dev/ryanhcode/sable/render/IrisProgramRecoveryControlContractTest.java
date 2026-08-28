package dev.ryanhcode.sable.render;

import dev.ryanhcode.sable.compatibility.SableIrisProgramRecovery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisProgramRecoveryControlContractTest {
    @AfterEach
    void restoreDefaults() {
        SableIrisProgramRecovery.setConfiguredEnabled(true);
        SableIrisProgramRecovery.setMode(SableIrisProgramRecovery.Mode.CONFIGURED);
    }

    @Test
    void sessionOverrideDoesNotMutateTheConfiguredValue() {
        SableIrisProgramRecovery.setConfiguredEnabled(false);
        SableIrisProgramRecovery.setMode(SableIrisProgramRecovery.Mode.CONFIGURED);
        assertFalse(SableIrisProgramRecovery.isEnabled());

        SableIrisProgramRecovery.setMode(SableIrisProgramRecovery.Mode.ENABLED);
        assertTrue(SableIrisProgramRecovery.isEnabled());
        assertFalse(SableIrisProgramRecovery.configuredEnabled());

        SableIrisProgramRecovery.setMode(SableIrisProgramRecovery.Mode.DISABLED);
        assertFalse(SableIrisProgramRecovery.isEnabled());
    }

    @Test
    void disabledGatePrecedesEveryOpenGlOperation() throws Exception {
        final Path forgeRoot = Path.of(System.getProperty("user.dir")).normalize();
        final String source = Files.readString(forgeRoot.resolve(
                "src/port/java/dev/ryanhcode/sable/admitted/ExtendedShaderMixin.java"));

        final int gate = source.indexOf("SableIrisProgramRecovery.isEnabled()");
        final int query = source.indexOf("GL20C.glGetInteger");
        final int bind = source.indexOf("ProgramManager.glUseProgram");
        assertTrue(gate >= 0 && gate < query && query < bind);
        assertFalse(source.contains("GlProgramStateTrace"));
        assertFalse(source.contains("glIsProgram"));
        assertFalse(source.contains("PROGRAM_RECOVERY_TRACE"));
    }

    @Test
    void clientCommandIsSessionOnlyAndConfigDefaultsOn() throws Exception {
        final Path forgeRoot = Path.of(System.getProperty("user.dir")).normalize();
        final String command = Files.readString(forgeRoot.resolve(
                "src/port/java/dev/ryanhcode/sable/forge/client/SableIrisProgramRecoveryCommands.java"));
        final String config = Files.readString(forgeRoot.resolve(
                "../common/src/main/java/dev/ryanhcode/sable/SableClientConfig.java"));

        assertTrue(command.contains("literal(\"on\")"));
        assertTrue(command.contains("literal(\"off\")"));
        assertTrue(command.contains("literal(\"configured\")"));
        assertTrue(command.contains("literal(\"status\")"));
        assertFalse(command.contains("allChanged"));
        assertFalse(command.contains("rebuild"));
        assertFalse(command.contains("reload"));
        assertTrue(config.contains("define(\"iris_program_cache_recovery\", true)"));
    }
}
