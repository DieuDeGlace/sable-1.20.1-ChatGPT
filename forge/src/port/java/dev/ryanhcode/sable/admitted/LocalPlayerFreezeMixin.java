package dev.ryanhcode.sable.mixin.admitted;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/** Target-verified upstream client consumer that waits for finalization before teleporting. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerFreezeMixin extends Player implements PlayerFreezeExtension {
    protected LocalPlayerFreezeMixin(final Level level, final BlockPos blockPos, final float yaw,
                                     final GameProfile profile) {
        super(level, blockPos, yaw, profile);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;hasChunkAt(II)Z"))
    private boolean sable$freezeTicking(final Level instance, final int x, final int z) {
        this.sable$tickStopFreezing();
        final UUID uuid = this.sable$getFrozenToSubLevel();
        if (uuid != null) {
            final SubLevelContainer container = SubLevelContainer.getContainer(this.level());
            final ClientSubLevel subLevel = container == null ? null : (ClientSubLevel) container.getSubLevel(uuid);
            if (subLevel == null || !subLevel.isFinalized()) {
                return false;
            }
            this.sable$teleport();
            this.sable$freezeTo(null, null);
        }
        return instance.hasChunkAt(x, z);
    }
}
