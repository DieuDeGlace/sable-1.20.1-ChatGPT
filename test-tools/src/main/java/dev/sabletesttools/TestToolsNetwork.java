package dev.sabletesttools;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

final class TestToolsNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SableTestTools.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private TestToolsNetwork() {
    }

    static void register() {
        CHANNEL.messageBuilder(LaunchPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buffer) -> { })
                .decoder(buffer -> new LaunchPacket())
                .consumerMainThread(TestToolsNetwork::handleLaunch)
                .add();
    }

    static void sendLaunch() {
        CHANNEL.sendToServer(new LaunchPacket());
    }

    private static void handleLaunch(final LaunchPacket packet, final Supplier<NetworkEvent.Context> supplier) {
        final ServerPlayer sender = supplier.get().getSender();
        if (sender != null && GravityGunController.isHoldingGun(sender)) {
            GravityGunController.launch(sender);
        }
    }

    private record LaunchPacket() {
    }
}
