package dev.ryanhcode.sable.neoforge.mixin.compatibility.legendarysurvivaloverhaul;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps Legendary Survival Overhaul's looked-at entity ray length in one
 * coordinate frame when the preceding block pick hit a sub-level.
 */
@Pseudo
@Mixin(targets = "sfiomn.legendarysurvivaloverhaul.util.WorldUtil", remap = false)
public abstract class LegendarySurvivalOverhaulWorldUtilMixin {
    @WrapOperation(
            method = "getEntityLookedAt(Lnet/minecraft/world/entity/player/Player;D)Lnet/minecraft/world/entity/Entity;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D",
                    remap = true
            ),
            require = 0
    )
    private static double sable$distanceWithSubLevels(final Vec3 hitPosition, final Vec3 eyePosition,
                                                       final Operation<Double> original,
                                                       @Local(argsOnly = true) final Player player) {
        final Level level = player.level();
        if (Sable.HELPER.getContaining(level, hitPosition) == null &&
                Sable.HELPER.getContaining(level, eyePosition) == null) {
            return original.call(hitPosition, eyePosition);
        }

        return Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(level, hitPosition, eyePosition));
    }
}
