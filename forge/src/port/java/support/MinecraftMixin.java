package dev.ryanhcode.sable.mixin.plot;

import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ticks the client plot container once per Minecraft client tick, matching upstream Sable. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow public ClientLevel level;
    @Shadow private volatile boolean pause;

    @Inject(method = "tick", at = @At("HEAD"))
    private void sable$tickPlotContainer(final CallbackInfo ci) {
        if (this.level == null) {
            SableClient.NETWORK_EVENT_LOOP.clear();
            return;
        }

        SableClient.NETWORK_EVENT_LOOP.runAllTasks();
        if (!this.pause && this.level instanceof final SubLevelContainerHolder holder
                && holder.sable$getPlotContainer() != null) {
            holder.sable$getPlotContainer().tick();
        }
    }
}
