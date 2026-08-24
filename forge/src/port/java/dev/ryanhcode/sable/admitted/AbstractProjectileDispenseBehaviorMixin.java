package dev.ryanhcode.sable.mixin.admitted;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.AbstractProjectileDispenseBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.20.1 target adaptation of upstream ProjectileDispenseBehaviorMixin. */
@Mixin(AbstractProjectileDispenseBehavior.class)
public final class AbstractProjectileDispenseBehaviorMixin {
    @Inject(method = "execute", at = @At("TAIL"))
    private void sable$applyRecoil(final BlockSource blockSource,
                                   final ItemStack itemStack,
                                   final CallbackInfoReturnable<ItemStack> cir,
                                   @Local final Position position,
                                   @Local final Direction direction) {
        final ServerLevel level = blockSource.getLevel();
        final SubLevel subLevel = Sable.HELPER.getContaining(level, position);
        if (subLevel instanceof final ServerSubLevel serverSubLevel) {
            final Vector3d impulse = new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ())
                    .mul(-1.5D);
            RigidBodyHandle.of(serverSubLevel).applyImpulseAtPoint(JOMLConversion.toJOML(position), impulse);
        }
    }
}
