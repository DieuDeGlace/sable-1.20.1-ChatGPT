package dev.ryanhcode.sable.forge.client;

import dev.ryanhcode.sable.compatibility.SableIrisProgramRecovery;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

/** Session-only A/B control for the Oculus program-cache compatibility recovery. */
public final class SableIrisProgramRecoveryCommands {
    private SableIrisProgramRecoveryCommands() {
    }

    public static void register(final RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sable_iris_program_recovery")
                .then(Commands.literal("on")
                        .executes(context -> setMode(context.getSource(), SableIrisProgramRecovery.Mode.ENABLED)))
                .then(Commands.literal("off")
                        .executes(context -> setMode(context.getSource(), SableIrisProgramRecovery.Mode.DISABLED)))
                .then(Commands.literal("configured")
                        .executes(context -> setMode(context.getSource(), SableIrisProgramRecovery.Mode.CONFIGURED)))
                .then(Commands.literal("status")
                        .executes(context -> {
                            context.getSource().sendSystemMessage(statusComponent());
                            return 1;
                        })));
    }

    private static int setMode(final net.minecraft.commands.CommandSourceStack source,
                               final SableIrisProgramRecovery.Mode mode) {
        SableIrisProgramRecovery.setMode(mode);
        source.sendSystemMessage(statusComponent());
        return 1;
    }

    private static Component statusComponent() {
        return Component.literal("Oculus program recovery: mode="
                + SableIrisProgramRecovery.mode().name().toLowerCase()
                + ", configured=" + SableIrisProgramRecovery.configuredEnabled()
                + ", effective=" + SableIrisProgramRecovery.isEnabled());
    }
}
