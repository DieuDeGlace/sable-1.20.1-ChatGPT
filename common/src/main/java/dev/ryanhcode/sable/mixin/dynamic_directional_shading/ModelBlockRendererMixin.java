package dev.ryanhcode.sable.mixin.dynamic_directional_shading;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;
import dev.ryanhcode.sable.render.dynamic_shade.SubLevelVertexConsumer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {

    @ModifyVariable(method = "putQuadData", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private VertexConsumer sable$modifyConsumer(final VertexConsumer value) {
        return SableDynamicDirectionalShading.isEnabled() && SableDynamicDirectionalShading.isCompilingSubLevel()
                ? new SubLevelVertexConsumer(value) : value;
    }

    @Redirect(method = "renderModelFaceFlat", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BlockAndTintGetter;getShade(Lnet/minecraft/core/Direction;Z)F"))
    public float getShade(final BlockAndTintGetter instance, final Direction direction, final boolean shade) {
        final boolean onSubLevel = SableDynamicDirectionalShading.isEnabled()
                && SableDynamicDirectionalShading.isCompilingSubLevel();
        return onSubLevel ? 1.0f : instance.getShade(direction, shade);
    }
}
