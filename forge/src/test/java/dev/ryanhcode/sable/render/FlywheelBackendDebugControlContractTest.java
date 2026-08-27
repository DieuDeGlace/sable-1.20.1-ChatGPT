package dev.ryanhcode.sable.render;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywheelBackendDebugControlContractTest {
    @Test
    void runtimeSwitchUsesTheUpstreamBackendCommandLifecycle() throws Exception {
        final Path forgeRoot = Path.of(System.getProperty("user.dir")).normalize();
        final Path controller = forgeRoot.resolve(
                "src/port/java/dev/ryanhcode/sable/forge/client/SableFlywheelBackendController.java");
        final String source = Files.readString(controller);

        assertTrue(source.contains("levelRenderer.allChanged()"));
        assertTrue(source.contains("Mode.CONFIGURED"));
        assertTrue(source.contains("Backends.INSTANCING.isSupported()"));
        assertFalse(source.contains("BackendManagerImpl.onEndClientResourceReload(false)"));
        assertFalse(source.contains("SubLevelRenderDispatcher.rebuild"));
    }

    @Test
    void commandKeepsOnlyTheLowCostBackendControls() throws Exception {
        final Path forgeRoot = Path.of(System.getProperty("user.dir")).normalize();
        final Path commands = forgeRoot.resolve(
                "src/port/java/dev/ryanhcode/sable/forge/client/SableFlywheelDebugCommands.java");
        final String source = Files.readString(commands);

        assertTrue(source.contains("literal(\"off\")"));
        assertTrue(source.contains("literal(\"instancing\")"));
        assertTrue(source.contains("literal(\"configured\")"));
        assertTrue(source.contains("literal(\"status\")"));
        assertFalse(source.contains("probe"));
    }
}
