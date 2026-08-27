package dev.ryanhcode.sable.forge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.backend.Backends;
import dev.engine_room.flywheel.lib.util.ShadersModHelper;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Session-only backend selector used to isolate Flywheel rendering compatibility issues. */
public final class SableFlywheelBackendController {
    public enum Mode {
        CONFIGURED,
        OFF,
        INSTANCING
    }

    private static volatile Mode requestedMode = Mode.CONFIGURED;

    private SableFlywheelBackendController() {
    }

    /** Called only from the BackendManagerImpl selection mixin. */
    public static Backend forcedBackendOrNull() {
        return switch (requestedMode) {
            case CONFIGURED -> null;
            case OFF -> BackendManager.offBackend();
            case INSTANCING -> Backends.INSTANCING.isSupported()
                    ? Backends.INSTANCING
                    : BackendManager.offBackend();
        };
    }

    public static void requestMode(final Mode mode) {
        Minecraft.getInstance().execute(() -> applyModeOnRenderThread(mode));
    }

    private static void applyModeOnRenderThread(final Mode mode) {
        RenderSystem.assertOnRenderThread();
        final String previous = backendName(BackendManager.currentBackend());
        requestedMode = mode;

        // Match Flywheel's own client backend command: the renderer reload
        // reselects the backend and rebuilds visuals through the normal lifecycle.
        Minecraft.getInstance().levelRenderer.allChanged();

        final Backend active = BackendManager.currentBackend();
        final String activeName = backendName(active);
        final boolean unavailable = mode == Mode.INSTANCING && active != Backends.INSTANCING;
        Sable.LOGGER.info("Sable Flywheel backend override: requested={}, previous={}, active={}, instancingSupported={}, shaders={}",
                mode, previous, activeName, Backends.INSTANCING.isSupported(), ShadersModHelper.isShaderPackInUse());
        displayClientMessage(unavailable
                ? "Requested instancing is unsupported in the current shader state; Flywheel is safely off."
                : "Flywheel mode: " + mode.name().toLowerCase() + " (active " + activeName + ")");
    }

    public static Component statusComponent() {
        return Component.literal("Flywheel requested=" + requestedMode.name().toLowerCase()
                + ", active=" + backendName(BackendManager.currentBackend())
                + ", default=" + backendName(BackendManager.defaultBackend())
                + ", instancingSupported=" + Backends.INSTANCING.isSupported()
                + ", shaders=" + ShadersModHelper.isShaderPackInUse());
    }

    private static String backendName(final Backend backend) {
        if (backend == null) {
            return "null";
        }
        final ResourceLocation id = Backend.REGISTRY.getId(backend);
        return id == null ? "[unregistered]" : id.toString();
    }

    private static void displayClientMessage(final String text) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(text), false);
        }
    }
}
