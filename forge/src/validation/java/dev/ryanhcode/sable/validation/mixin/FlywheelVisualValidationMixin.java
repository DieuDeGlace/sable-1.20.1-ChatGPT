package dev.ryanhcode.sable.validation.mixin;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.validation.SableForgeRenderValidationTelemetry;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Confirms that Flywheel constructed a real visual inside a Sable embedding. */
@Mixin(value = BlockEntityStorage.class, remap = false)
public abstract class FlywheelVisualValidationMixin {
    @Inject(
            method = "createRaw(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;Lnet/minecraft/world/level/block/entity/BlockEntity;F)Ldev/engine_room/flywheel/api/visual/BlockEntityVisual;",
            at = @At("RETURN"),
            remap = false
    )
    private void sable$recordSubLevelVisual(
            final VisualizationContext visualizationContext,
            final BlockEntity blockEntity,
            final float partialTick,
            final CallbackInfoReturnable<BlockEntityVisual<?>> cir
    ) {
        if (cir.getReturnValue() != null && Sable.HELPER.getContainingClient(blockEntity) != null) {
            SableForgeRenderValidationTelemetry.recordFlywheelSubLevelVisual();
        }
    }
}
