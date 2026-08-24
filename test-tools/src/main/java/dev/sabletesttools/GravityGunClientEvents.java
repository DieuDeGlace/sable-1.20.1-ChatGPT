package dev.sabletesttools;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SableTestTools.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
final class GravityGunClientEvents {
    private GravityGunClientEvents() {
    }

    @SubscribeEvent
    static void attack(final InputEvent.InteractionKeyMappingTriggered event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (!event.isAttack() || minecraft.player == null
                || minecraft.player.getMainHandItem().getItem() != SableTestTools.GRAVITY_GUN.get()) {
            return;
        }
        TestToolsNetwork.sendLaunch();
        event.setCanceled(true);
    }
}
