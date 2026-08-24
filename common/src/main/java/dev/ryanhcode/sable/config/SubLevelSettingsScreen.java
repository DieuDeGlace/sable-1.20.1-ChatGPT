package dev.ryanhcode.sable.config;

import dev.ryanhcode.sable.SableServerConfig;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.config.PhysicsConfigData;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.ForgeConfigSpec;

public class SubLevelSettingsScreen extends OptionsSubScreen {
    public static final Component TITLE = Component.translatable("options.sable_menu");
    private OptionsList list;

    public SubLevelSettingsScreen(final Screen optionsScreen, final Options options, final Component component) {
        super(optionsScreen, options, component);
    }

    @Override
    protected void init() {
        final IntegratedServer singleplayerServer = this.minecraft.getSingleplayerServer();

        this.list = new OptionsList(this.minecraft, this.width, this.height, 32, this.height - 32, 25);
        this.list.addBig(new OptionInstance<>(
                "options.physics_steps",
                OptionInstance.cachedConstantTooltip(Component.translatable("options.physics_steps.tooltip")),
                (component, substeps) -> Options.genericValueLabel(component, Component.translatable("options.physics_steps_template", substeps * 20)),
                new OptionInstance.IntRange(1, 10),
                SubLevelContainer.getContainer(singleplayerServer.overworld()).physicsSystem().getConfig().substepsPerTick,
                steps -> {
                    SableServerConfig.SUB_LEVEL_SUBSTEPS_PER_TICK.set(steps);
                    SableServerConfig.SPEC.save();
                    for (final ServerLevel level : singleplayerServer.getAllLevels()) {
                        final SubLevelPhysicsSystem physicsSystem = SubLevelContainer.getContainer(level).physicsSystem();
                        final PhysicsConfigData config = physicsSystem.getConfig();
                        config.updateFromConfig();
                        physicsSystem.getPipeline().updateConfigFrom(config);
                    }
                }
        ));
        this.addWidget(this.list);
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE,
                button -> this.minecraft.setScreen(this.lastScreen))
                .bounds(this.width / 2 - 100, this.height - 27, 200, 20)
                .build());
    }

    @Override
    public void render(final GuiGraphics guiGraphics, final int mouseX, final int mouseY, final float partialTick) {
        this.basicListRender(guiGraphics, this.list, mouseX, mouseY, partialTick);
    }
}
