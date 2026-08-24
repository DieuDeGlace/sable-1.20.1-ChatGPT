package dev.ryanhcode.sable.neoforge.mixin.admitted;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.flywheel.FlywheelBlockEntity;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelReactionWheel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge 1.20.1 signature adaptation of upstream Create FlywheelBlockEntityMixin. */
@Mixin(FlywheelBlockEntity.class)
public abstract class CreateFlywheelBlockEntityMixin extends KineticBlockEntity
        implements BlockEntitySubLevelReactionWheel {
    @Unique private float sable$smoothedSpeed;

    protected CreateFlywheelBlockEntityMixin(final BlockEntityType<?> type, final BlockPos pos,
                                               final BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void sable$tick(final CallbackInfo ci) {
        this.sable$smoothedSpeed += (this.speed - this.sable$smoothedSpeed) / 32.0F;
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void sable$write(final CompoundTag compound, final boolean clientPacket, final CallbackInfo ci) {
        compound.putFloat("SmoothedSpeed", this.sable$smoothedSpeed);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void sable$read(final CompoundTag compound, final boolean clientPacket, final CallbackInfo ci) {
        this.sable$smoothedSpeed = compound.getFloat("SmoothedSpeed");
    }

    @Override
    public void sable$getAngularVelocity(final Vector3d velocity) {
        final Direction.Axis axis = ((IRotate) this.getBlockState().getBlock())
                .getRotationAxis(this.getBlockState());
        final Direction direction = Direction.get(Direction.AxisDirection.NEGATIVE, axis);
        final float angularSpeed = this.sable$smoothedSpeed * (float) (Math.PI * 2.0D) / 60.0F;
        velocity.set(direction.getStepX(), direction.getStepY(), direction.getStepZ()).mul(angularSpeed);
    }
}
