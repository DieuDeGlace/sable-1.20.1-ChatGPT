package dev.ryanhcode.sable.mixinhelper.clip_overwrite;

import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ClipContextUtil {
    private ClipContextUtil() {
    }

    public static ClipContext copy(final ClipContext source, final Vec3 from, final Vec3 to) {
        final ClipContextExtension sourceExtension = (ClipContextExtension) source;
        final ClipContext copy = new ClipContext(from, to, sourceExtension.sable$getBlockMode(), sourceExtension.sable$getFluidMode(), null);
        final ClipContextExtension copyExtension = (ClipContextExtension) copy;
        copyExtension.sable$setCollisionContext(sourceExtension.sable$getCollisionContext());
        copyExtension.sable$setIgnoredSubLevel(sourceExtension.sable$getIgnoredSubLevel());
        copyExtension.sable$setSubLevelIgnoring(sourceExtension.sable$getSubLevelIgnoring());
        copyExtension.sable$setIgnoreMainLevel(sourceExtension.sable$isIgnoreMainLevel());
        copyExtension.sable$setDoNotProject(sourceExtension.sable$doNotProject());
        return copy;
    }

    public static BlockHitResult originalClip(final BlockGetter level, final ClipContext clipContext) {
        return BlockGetter.traverseBlocks(clipContext.getFrom(), clipContext.getTo(), clipContext, (context, blockPos) -> {
            final BlockState blockState = level.getBlockState(blockPos);
            final FluidState fluidState = level.getFluidState(blockPos);
            final Vec3 from = context.getFrom();
            final Vec3 to = context.getTo();
            final VoxelShape blockShape = context.getBlockShape(blockState, level, blockPos);
            final BlockHitResult blockHit = level.clipWithInteractionOverride(from, to, blockPos, blockShape, blockState);
            final VoxelShape fluidShape = context.getFluidShape(fluidState, level, blockPos);
            final BlockHitResult fluidHit = fluidShape.clip(from, to, blockPos);
            final double blockDistance = blockHit == null ? Double.MAX_VALUE : from.distanceToSqr(blockHit.getLocation());
            final double fluidDistance = fluidHit == null ? Double.MAX_VALUE : from.distanceToSqr(fluidHit.getLocation());
            return blockDistance <= fluidDistance ? blockHit : fluidHit;
        }, context -> {
            final Vec3 difference = context.getFrom().subtract(context.getTo());
            return BlockHitResult.miss(context.getTo(), Direction.getNearest(difference.x, difference.y, difference.z), BlockPos.containing(context.getTo()));
        });
    }
}
