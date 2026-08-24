package dev.ryanhcode.sable.mixin.admitted;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** 1.20.1 server consumer for queued player-freeze teleports. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerFreezeMixin {
    @Shadow
    public ServerPlayer player;

    /*
     * 1.20.1 drives ServerPlayer through ServerGamePacketListenerImpl#tick ->
     * ServerPlayer#doTick, then restores firstGoodX/Y/Z with absMoveTo. Unlike
     * the 1.21.1 upstream target, ServerPlayer#tick is not called and a doTick
     * HEAD injection is overwritten. Consume at the connection tick tail.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void sable$applyQueuedFreeze(final CallbackInfo ci) {
        this.sable$kick();
    }

    private void sable$kick() {
        final PlayerFreezeExtension freeze = (PlayerFreezeExtension) this.player;
        final UUID uuid = freeze.sable$getFrozenToSubLevel();
        if (uuid == null) {
            return;
        }
        final SubLevelContainer container = SubLevelContainer.getContainer(this.player.level());
        final SubLevel subLevel = container == null ? null : container.getSubLevel(uuid);
        if (subLevel != null) {
            ((EntityMovementExtension) this.player).sable$setTrackingSubLevel(subLevel);
            freeze.sable$teleport();
            freeze.sable$freezeTo(null, null);
        }
    }
}
