package dev.ryanhcode.sable.mixin.admitted;

import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.mixinhelpers.voxel_shape_iteration.FastVoxelShapeIterator;
import dev.ryanhcode.sable.mixinterface.voxel_shape_iteration.FastVoxelShapeIterable;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Iterator;

/** Java 17 adaptation of the upstream allocation-free voxel-shape iterator. */
@Mixin(VoxelShape.class)
public abstract class VoxelShapeIterationMixin implements FastVoxelShapeIterable {
    @Unique
    private final Long2ObjectMap<FastVoxelShapeIterator> sable$boxIterators = new Long2ObjectArrayMap<>();
    @Shadow @Final protected DiscreteVoxelShape shape;
    @Shadow public abstract DoubleList getCoords(Direction.Axis axis);

    @Override
    public Iterator<BoundingBox3dc> sable$allBoxes() {
        synchronized (this) {
            final long threadId = Thread.currentThread().getId();
            FastVoxelShapeIterator iterator = this.sable$boxIterators.get(threadId);
            if (iterator == null) {
                iterator = new FastVoxelShapeIterator(this.shape,
                        this.getCoords(Direction.Axis.X).toDoubleArray(),
                        this.getCoords(Direction.Axis.Y).toDoubleArray(),
                        this.getCoords(Direction.Axis.Z).toDoubleArray());
                this.sable$boxIterators.put(threadId, iterator);
            }
            iterator.reset();
            return iterator;
        }
    }
}
