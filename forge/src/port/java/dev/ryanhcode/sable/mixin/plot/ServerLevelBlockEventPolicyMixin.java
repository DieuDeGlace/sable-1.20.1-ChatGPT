package dev.ryanhcode.sable.mixin.plot;

import dev.ryanhcode.sable.sublevel.plot.PlotBlockActivityPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets an allocated plot process the block events scheduled by its ticking
 * block entities without widening the level's whole-chunk tick policy.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelBlockEventPolicyMixin {

    @Redirect(
            method = "runBlockEvents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;shouldTickBlocksAt(Lnet/minecraft/core/BlockPos;)Z"
            )
    )
    private boolean sable$shouldProcessPlotBlockEvent(final ServerLevel instance, final BlockPos pos) {
        return PlotBlockActivityPolicy.shouldProcess(instance, pos, instance.shouldTickBlocksAt(pos));
    }
}
