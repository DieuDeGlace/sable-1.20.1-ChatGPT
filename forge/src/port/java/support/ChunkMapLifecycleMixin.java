package dev.ryanhcode.sable.mixin.plot;

import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The 1.20.1 equivalents of the upstream plot-holder persistence/close gates.
 * Plot chunks are serialized by Sable and do not leave the vanilla updating
 * map through the ordinary unload path.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapLifecycleMixin {
    @Inject(method = "saveChunkIfNeeded", at = @At("HEAD"), cancellable = true)
    private void sable$skipVanillaPlotSave(final ChunkHolder holder,
                                           final CallbackInfoReturnable<Boolean> cir) {
        if (holder instanceof PlotChunkHolder) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(method = "hasWork", at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectLinkedOpenHashMap;isEmpty()Z",
            ordinal = 1, remap = false))
    private boolean sable$ignorePlotHoldersDuringClose(
            final Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap) {
        return updatingChunkMap.values().stream().noneMatch(holder -> !(holder instanceof PlotChunkHolder));
    }
}
