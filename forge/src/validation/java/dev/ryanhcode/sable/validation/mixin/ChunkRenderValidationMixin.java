package dev.ryanhcode.sable.validation.mixin;

import dev.ryanhcode.sable.validation.SableForgeRenderValidationTelemetry;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VanillaChunkedSubLevelRenderData.class, remap = false)
public abstract class ChunkRenderValidationMixin {
    @Inject(method = "setDirty", at = @At("HEAD"), remap = false)
    private void sable$recordDirty(final int x, final int y, final int z,
                                   final boolean playerChanged, final CallbackInfo ci) {
        SableForgeRenderValidationTelemetry.recordChunkDirty();
    }

    @Inject(method = "renderChunkedSubLevel", at = @At("RETURN"), remap = false)
    private void sable$recordChunkDraws(final RenderType layer, final ShaderInstance shader,
                                        final Matrix4f modelView, final double camX,
                                        final double camY, final double camZ,
                                        final CallbackInfoReturnable<Integer> cir) {
        SableForgeRenderValidationTelemetry.recordChunkLayerDraws(layer, cir.getReturnValueI());
    }
}
