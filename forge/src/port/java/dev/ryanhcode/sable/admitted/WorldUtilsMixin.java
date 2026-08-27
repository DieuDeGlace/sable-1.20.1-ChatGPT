package dev.ryanhcode.sable.neoforge.mixin.compatibility.storagedrawers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps Storage Drawers' eye ray length in the same coordinate frame as the
 * player and the targeted drawer.
 *
 * <p>Storage Drawers computes the ray length before calling {@link Level#clip}.
 * A drawer on a sub-level is stored at a plot coordinate, so vanilla Euclidean
 * distance can turn a normal interaction ray into a multi-million-block ray.
 * Sable's general clip compatibility cannot repair an endpoint that was already
 * constructed from that invalid length.</p>
 */
@Pseudo
@Mixin(
        targets = {
                "com.texelsaurus.minecraft.chameleon.util.WorldUtils",
                "com.jaquadro.minecraft.storagedrawers.util.WorldUtils"
        },
        remap = false
)
public abstract class WorldUtilsMixin {
    @WrapOperation(
            method = "rayTraceEyes",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D",
                    remap = true
            ),
            require = 0
    )
    private static double sable$distanceWithSubLevels(final Vec3 eyePosition, final Vec3 targetPosition,
                                                       final Operation<Double> original,
                                                       @Local(argsOnly = true) final Level level) {
        if (Sable.HELPER.getContaining(level, eyePosition) == null &&
                Sable.HELPER.getContaining(level, targetPosition) == null) {
            return original.call(eyePosition, targetPosition);
        }

        return Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(level, eyePosition, targetPosition));
    }
}
