package dev.ryanhcode.sable.mixin.admitted;

import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.mixinterface.respawn_point.ServerPlayerRespawnExtension;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.UUID;

/** Maps the 1.21 ServerPlayer respawn hook to PlayerList#respawn in 1.20.1. */
@Mixin(PlayerList.class)
public class PlayerListRespawnMixin {

    @Redirect(method = "respawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;findRespawnPositionAndUseSpawnBlock(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;FZZ)Ljava/util/Optional;"))
    private Optional<Vec3> sable$findTrackedRespawn(final ServerLevel level, final BlockPos blockPos,
                                                    final float angle, final boolean forced,
                                                    final boolean consumeAnchorCharge,
                                                    final ServerPlayer oldPlayer,
                                                    final boolean keepEverything) {
        final ServerPlayerRespawnExtension extension = (ServerPlayerRespawnExtension) oldPlayer;
        final UUID respawnPoint = extension.sable$getRespawnPoint();
        if (respawnPoint == null) {
            return Player.findRespawnPositionAndUseSpawnBlock(level, blockPos, angle, forced, consumeAnchorCharge);
        }

        final SubLevelTrackingPointSavedData.TakenLoginPoint point =
                SubLevelTrackingPointSavedData.getOrLoad(level).take(respawnPoint, false);
        if (point == null) {
            return Optional.empty();
        }
        if (point.subLevelId() != null) {
            extension.sable$setQueuedFreeze(Pair.of(point.subLevelId(), point.localAnchor()));
        }
        return Optional.of(JOMLConversion.toMojang(point.position()));
    }
}
