package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.kinetic_refresh;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Completes Create 1.20.1 generator reactivation after Sable's bulk move. */
@Mixin(KineticBlock.class)
public abstract class KineticBlockMixin {

    @Inject(method = {"updateIndirectNeighbourShapes", "m_7742_"}, at = @At("TAIL"))
    private void sable$reactivateMovedGenerator(final BlockState state, final LevelAccessor levelAccessor,
                                                final BlockPos pos, final int flags, final int recursionLeft,
                                                final CallbackInfo ci) {
        if (!(levelAccessor instanceof final Level level) || level.isClientSide()) {
            return;
        }
        if (Sable.HELPER.getContaining(level, pos) == null) {
            return;
        }

        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof final GeneratingKineticBlockEntity generator)
                || !generator.needsSpeedUpdate() || generator.getSpeed() != 0.0f) {
            return;
        }

        final float generatedSpeed = generator.getGeneratedSpeed();
        if (generatedSpeed == 0.0f) {
            return;
        }

        generator.updateGeneratedRotation();
    }
}
