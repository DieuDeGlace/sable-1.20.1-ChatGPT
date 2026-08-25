package dev.ryanhcode.sable;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderer;
import foundry.veil.Veil;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

import java.util.Arrays;

public final class SableClientConfig {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ATTEMPT_UDP_NETWORKING;
    public static final ForgeConfigSpec.BooleanValue SUB_LEVEL_DYNAMIC_SHADING;
    public static final ForgeConfigSpec.BooleanValue SUB_LEVEL_WATER_OCCLUSION;
    public static final ForgeConfigSpec.BooleanValue SUB_LEVEL_SKYLIGHT_SHADOWS;
    public static final ForgeConfigSpec.BooleanValue DEBUG_DRAW_LOADED_CHUNKS;
    public static final ForgeConfigSpec.DoubleValue INTERPOLATION_DELAY;
    public static final ForgeConfigSpec.EnumValue<SubLevelRenderer.SelectedRenderer> SELECTED_RENDERER;
    public static final ForgeConfigSpec.DoubleValue ZOOM_SENSITIVITY;

    static {
        final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();


        SUB_LEVEL_DYNAMIC_SHADING = builder
                .comment("Whether sub-levels should apply block shading dynamically")
                .define("sub_level_dynamic_shading", true);
        SUB_LEVEL_WATER_OCCLUSION = builder
                .comment("Whether sub-levels can occlude the water surface")
                .define("sub_level_water_occlusion", false);
        SUB_LEVEL_SKYLIGHT_SHADOWS = builder
                .comment("Whether sub-levels should cast a shadow on the world")
                .define("sub_level_skylight_shadows", false);
        DEBUG_DRAW_LOADED_CHUNKS = builder
                .comment("Whether to draw loaded chunks on the client in the chunk debug renderer")
                .define("debug_draw_loaded_chunks", Veil.platform().isDevelopmentEnvironment());
        INTERPOLATION_DELAY = builder
                .comment("The distance back in game-ticks that the snapshot interpolation should operate")
                .defineInRange("sub_level_snapshot_interpolation_delay_ticks", 1.5, 0.0, 100.0);
        SELECTED_RENDERER = builder
                .comment("The renderer to use for sub-levels")
                .defineEnum("sub_level_renderer", SubLevelRenderer.DEFAULT, Arrays.stream(SubLevelRenderer.SelectedRenderer.values())
                        .filter(SubLevelRenderer.SelectedRenderer::isSupported)
                        .toArray(SubLevelRenderer.SelectedRenderer[]::new));
        ZOOM_SENSITIVITY = builder
                .comment("The zoom sensitivity for sub-level camera types")
                .defineInRange("sub_level_zoom_sensitivity", 0.2, 0.0, 100.0);
        ATTEMPT_UDP_NETWORKING = builder
                .comment("If Sable should attempt to establish a UDP connection with the server, to receive sub-level movement data over a UDP channel")
                .define("attempt_udp_networking", true);

        SPEC = builder.build();
    }

    @ApiStatus.Internal
    public static void onUpdate(final boolean notify) {
        final boolean requestedDynamicShading = SUB_LEVEL_DYNAMIC_SHADING.get();
        final boolean shadingChanged = SableDynamicDirectionalShading.isEnabled() != requestedDynamicShading;
        SableDynamicDirectionalShading.setIsEnabled(requestedDynamicShading);
        dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(LOGGER, "[SABLE-DYNAMIC-SHADE-CONFIG] enabled={} changed={} notify={}",
                requestedDynamicShading, shadingChanged, notify);

        Minecraft.getInstance().execute(() -> {
            SubLevelRenderer.setImpl(SableClientConfig.SELECTED_RENDERER.get());
            if (!notify || !shadingChanged) {
                return;
            }

            final ClientSubLevelContainer container = Minecraft.getInstance().level == null
                    ? null : SubLevelContainer.getContainer(Minecraft.getInstance().level);
            if (container != null) {
                container.getAllSubLevels().forEach(subLevel -> subLevel.getRenderData().rebuild());
                dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(LOGGER, "[SABLE-DYNAMIC-SHADE-REBUILD] rebuilt {} loaded sub-level renderers",
                        container.getAllSubLevels().size());
            }
        });
    }
}
