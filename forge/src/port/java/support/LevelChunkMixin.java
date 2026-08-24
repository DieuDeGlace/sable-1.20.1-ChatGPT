package dev.ryanhcode.sable.mixin.plot;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableCommonEvents;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserves the upstream plot bounds, mass and collider block-change pipeline. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Shadow @Final private Level level;
    @Unique private BlockPos sable$blockSet;

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void sable$beforeSetBlockState(final BlockPos pos, final BlockState state,
                                           final boolean moving,
                                           final CallbackInfoReturnable<BlockState> cir) {
        this.sable$blockSet = pos;
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void sable$afterSetBlockState(final BlockPos pos, final BlockState state,
                                          final boolean moving,
                                          final CallbackInfoReturnable<BlockState> cir) {
        if (this.sable$blockSet != null) {
            final SubLevel subLevel = Sable.HELPER.getContaining(this.level, this.sable$blockSet);
            if (subLevel != null) {
                subLevel.getPlot().onBlockChange(this.sable$blockSet, state);
            }
        }
        this.sable$blockSet = null;
    }

    @WrapOperation(method = "setBlockState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState sable$notifyBlockChange(final LevelChunkSection section,
                                               int x, int y, int z,
                                               final BlockState newState,
                                               final Operation<BlockState> original) {
        final BlockState oldState = original.call(section, x, y, z, newState);
        if (this.level instanceof final ServerLevel serverLevel && oldState != newState) {
            x = this.sable$blockSet.getX();
            y = this.sable$blockSet.getY();
            z = this.sable$blockSet.getZ();
            SableCommonEvents.handleBlockChange(serverLevel, (LevelChunk) (Object) this,
                    x, y, z, oldState, newState);
        }
        return oldState;
    }
}
