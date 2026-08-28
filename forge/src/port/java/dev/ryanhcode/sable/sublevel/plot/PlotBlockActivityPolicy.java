package dev.ryanhcode.sable.sublevel.plot;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Shares the narrow 1.20.1 admission rule used by plot block-entity ticks and
 * the block events those block entities schedule.
 *
 * <p>This policy must only wrap a specific vanilla activity gate. It does not
 * replace {@link ServerLevel#shouldTickBlocksAt(BlockPos)} globally, because
 * doing so would also admit remote storage chunks to weather and random ticks.</p>
 */
public final class PlotBlockActivityPolicy {
    private PlotBlockActivityPolicy() {
    }

    public static boolean shouldProcess(final Level level, final BlockPos pos, final boolean original) {
        if (original || !(level instanceof final ServerLevel serverLevel)) {
            return original;
        }
        final SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        return container != null && container.getPlot(new ChunkPos(pos)) != null;
    }
}
