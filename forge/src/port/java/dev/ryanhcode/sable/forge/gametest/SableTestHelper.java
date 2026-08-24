package dev.ryanhcode.sable.forge.gametest;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CommonLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.function.Consumer;

final class SableTestHelper {
    private SableTestHelper() {
    }

    static ServerSubLevel spawnSubLevel(final SubLevelContainer container, final Vector3dc position,
                                        final Consumer<CommonLevelAccessor> setter) {
        final Pose3d pose = new Pose3d();
        pose.position().set(position);
        final SubLevel subLevel = container.allocateNewSubLevel(pose);
        final LevelPlot plot = subLevel.getPlot();
        final ChunkPos center = plot.getCenterChunk();
        plot.newEmptyChunk(center);
        setter.accept(plot.getEmbeddedLevelAccessor());
        subLevel.updateLastPose();
        return (ServerSubLevel) subLevel;
    }

    static ServerSubLevel spawnSingleBlockSubLevel(final SubLevelContainer container, final Vector3dc position,
                                                   final BlockState state) {
        return spawnSubLevel(container, position,
                accessor -> accessor.setBlock(BlockPos.ZERO, state, 3));
    }

    static Vector3d absolutePosition(final net.minecraft.gametest.framework.GameTestHelper helper,
                                     final Vector3dc localPosition) {
        final Vec3 result = helper.absoluteVec(new Vec3(
                localPosition.x(), localPosition.y(), localPosition.z()));
        return new Vector3d(result.x, result.y, result.z);
    }

    static Vector3d absoluteDirection(final net.minecraft.gametest.framework.GameTestHelper helper,
                                      final Vector3dc localDirection) {
        final Vec3 origin = helper.absoluteVec(Vec3.ZERO);
        final Vec3 target = helper.absoluteVec(new Vec3(
                localDirection.x(), localDirection.y(), localDirection.z()));
        return new Vector3d(target.x - origin.x, target.y - origin.y, target.z - origin.z);
    }

    static Vector3d localPosition(final net.minecraft.gametest.framework.GameTestHelper helper,
                                  final Vector3dc globalPosition) {
        final Vec3 result = helper.relativeVec(new Vec3(
                globalPosition.x(), globalPosition.y(), globalPosition.z()));
        return new Vector3d(result.x, result.y, result.z);
    }

    static void removeSubLevel(final SubLevelContainer container, final ServerSubLevel subLevel) {
        final LevelPlot plot = subLevel.getPlot();
        final Vector2i origin = container.getOrigin();
        container.removeSubLevel(plot.plotPos.x - origin.x, plot.plotPos.z - origin.y,
                SubLevelRemovalReason.REMOVED);
    }
}
