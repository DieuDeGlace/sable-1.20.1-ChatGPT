package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.configuration;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Adapts Create 1.20.1's plot-space configuration-packet range check to the
 * Player interaction check used by Create 1.21.1 and upstream Sable.
 */
@Mixin(BlockEntityConfigurationPacket.class)
public abstract class BlockEntityConfigurationPacketMixin {
    @Redirect(
            method = "lambda$handle$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"
            )
    )
    private boolean sable$validateSubLevelConfigurationRange(final BlockPos targetPos,
                                                              final Vec3i playerBlockPos,
                                                              final double maxRange,
                                                              @Local final ServerPlayer player) {
        final SubLevel subLevel = Sable.HELPER.getContaining(player.level(), targetPos);
        if (subLevel == null) {
            return targetPos.closerThan(playerBlockPos, maxRange);
        }

        // PlayerReachMixin projects the player's eye into this sub-level.
        return player.canReach(targetPos, maxRange);
    }
}
