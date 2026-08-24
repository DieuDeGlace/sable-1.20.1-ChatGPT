package dev.ryanhcode.sable.mixin.toast;

import dev.ryanhcode.sable.index.SableToasts;
import dev.ryanhcode.sable.mixinterface.toast.SableToastableServer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin implements SableToastableServer {
    @Shadow @Final private Minecraft minecraft;

    @Override
    public void sable$reportSubLevelLoadFailure(final GlobalSavedSubLevelPointer pointer) {
        SableToasts.addOrUpdateLoadFailure(
                this.minecraft.getToasts(),
                Component.translatable("sub_level.toast.loadFailure", Component.literal(pointer.toString())).withStyle(ChatFormatting.RED),
                Component.translatable("sub_level.toast.checkLog")
        );
    }

    @Override
    public void sable$reportSubLevelSaveFailure(final SubLevelData data) {
        SableToasts.addOrUpdateSaveFailure(
                this.minecraft.getToasts(),
                Component.translatable("sub_level.toast.saveFailure", Component.literal(data.toString())).withStyle(ChatFormatting.RED),
                Component.translatable("sub_level.toast.checkLog")
        );
    }

    @Override
    public void sable$reportSubLevelPhysicsFailure(final ServerSubLevel data) {
        SableToasts.addOrUpdatePhysicsFailure(
                this.minecraft.getToasts(),
                Component.translatable("sub_level.toast.physicsFailure", Component.literal(data.toString())).withStyle(ChatFormatting.RED),
                Component.translatable("sub_level.toast.attemptingRecovery")
        );
    }
}
