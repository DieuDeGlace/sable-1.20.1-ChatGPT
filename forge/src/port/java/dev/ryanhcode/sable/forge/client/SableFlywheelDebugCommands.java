package dev.ryanhcode.sable.forge.client;

import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

public final class SableFlywheelDebugCommands {
    private SableFlywheelDebugCommands() {
    }

    public static void register(final RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sable_flywheel")
                .then(Commands.literal("off")
                        .executes(context -> switchMode(SableFlywheelBackendController.Mode.OFF)))
                .then(Commands.literal("instancing")
                        .executes(context -> switchMode(SableFlywheelBackendController.Mode.INSTANCING)))
                .then(Commands.literal("configured")
                        .executes(context -> switchMode(SableFlywheelBackendController.Mode.CONFIGURED)))
                .then(Commands.literal("status")
                        .executes(context -> {
                            context.getSource().sendSystemMessage(SableFlywheelBackendController.statusComponent());
                            return 1;
                        })));
    }

    private static int switchMode(final SableFlywheelBackendController.Mode mode) {
        SableFlywheelBackendController.requestMode(mode);
        return 1;
    }
}
