package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.steam_engine;

import com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Repairs the impossible zero engine offset left on powered shafts moved into a
 * Sable sub-level. Create stores this field as {@code shaftPos - enginePos}; a
 * valid steam engine is always two blocks away, so zero can never represent a
 * real owner.
 */
@Mixin(PoweredShaftBlockEntity.class)
public abstract class PoweredShaftBlockEntityMixin {

    @Shadow
    public BlockPos enginePos;

    @Shadow
    public int initialTicks;

    @Inject(method = "canBePoweredBy", at = @At("RETURN"), cancellable = true)
    private void sable$repairZeroEngineOffset(final BlockPos candidateEnginePos,
                                              final CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || this.initialTicks != 0 || !BlockPos.ZERO.equals(this.enginePos)) {
            return;
        }

        final PoweredShaftBlockEntity shaft = (PoweredShaftBlockEntity) (Object) this;
        final Level level = shaft.getLevel();
        if (level == null) {
            return;
        }

        final SubLevel shaftSubLevel = Sable.HELPER.getContaining(shaft);
        if (shaftSubLevel == null || Sable.HELPER.getContaining(level, candidateEnginePos) != shaftSubLevel) {
            return;
        }

        final BlockState engineState = level.getBlockState(candidateEnginePos);
        if (!(engineState.getBlock() instanceof SteamEngineBlock)
                || !(level.getBlockEntity(candidateEnginePos) instanceof SteamEngineBlockEntity)
                || !SteamEngineBlock.getShaftPos(engineState, candidateEnginePos).equals(shaft.getBlockPos())
                || !SteamEngineBlock.isShaftValid(engineState, shaft.getBlockState())) {
            return;
        }

        if (!level.isClientSide) {
            this.enginePos = shaft.getBlockPos().subtract(candidateEnginePos);
            shaft.notifyUpdate();
        }
        cir.setReturnValue(true);
    }
}
