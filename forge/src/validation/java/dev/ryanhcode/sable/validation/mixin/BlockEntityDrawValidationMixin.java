package dev.ryanhcode.sable.validation.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.validation.SableForgeRenderValidationTelemetry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records entry into the actual target BlockEntityRenderer call path. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityDrawValidationMixin {
    @Inject(method = "setupAndRender", at = @At("HEAD"))
    private static <T extends BlockEntity> void sable$recordSubLevelDraw(
            final BlockEntityRenderer<T> renderer,
            final T blockEntity,
            final float partialTick,
            final PoseStack poseStack,
            final MultiBufferSource bufferSource,
            final CallbackInfo ci
    ) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(blockEntity);
        if (subLevel != null) {
            SableForgeRenderValidationTelemetry.recordBlockEntityDraws(1);
            if (blockEntity instanceof ChestBlockEntity) {
                SableForgeRenderValidationTelemetry.recordChestBlockEntityDraw();
            }
        }
    }
}
