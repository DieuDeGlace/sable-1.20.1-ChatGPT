package dev.ryanhcode.sable.mixin.dynamic_directional_shading;

import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Adds a hook to disable the directional shading on sub-level AO block faces.
 */
@Mixin(targets = "net.minecraft.client.renderer.block.ModelBlockRenderer$AmbientOcclusionFace")
public class AmbientOcclusionFaceMixin {

    @Redirect(method = "calculate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BlockAndTintGetter;getShade(Lnet/minecraft/core/Direction;Z)F"))
    private float calculate(final BlockAndTintGetter instance, final Direction direction, final boolean shade) {
        final boolean onSubLevel = SableDynamicDirectionalShading.isEnabled()
                && SableDynamicDirectionalShading.isCompilingSubLevel();
        return onSubLevel ? 1.0f : instance.getShade(direction, shade);
    }
}
