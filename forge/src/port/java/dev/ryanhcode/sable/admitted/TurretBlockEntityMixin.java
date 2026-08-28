package dev.ryanhcode.sable.neoforge.mixin.compatibility.immersiveengineering;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps Immersive Engineering turret target calculations in the coordinate
 * frame of the sub-level containing the turret.
 */
@Pseudo
@Mixin(targets = "blusunrize.immersiveengineering.common.blocks.metal.TurretBlockEntity")
public abstract class TurretBlockEntityMixin {
    @ModifyReturnValue(method = "getTargetVector", at = @At("RETURN"), remap = false)
    private Vec3 sable$targetInTurretFrame(final Vec3 globalTarget) {
        final Pose3dc pose = this.sable$getTurretFramePose();
        if (pose == null) {
            return globalTarget;
        }

        return pose.transformPositionInverse(globalTarget);
    }

    @WrapOperation(
            method = "isValidTarget",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;m_203193_(Lnet/minecraft/core/Position;)D",
                    remap = false
            ),
            remap = false
    )
    private double sable$distanceInTurretFrame(final BlockPos turretPos, final Position globalTarget,
                                                final Operation<Double> original) {
        final Pose3dc pose = this.sable$getTurretFramePose();
        if (pose == null) {
            return original.call(turretPos, globalTarget);
        }

        final Vec3 localTarget = pose.transformPositionInverse(
                new Vec3(globalTarget.x(), globalTarget.y(), globalTarget.z()));
        return original.call(turretPos, localTarget);
    }

    @WrapOperation(
            method = "canShootEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;m_20191_()Lnet/minecraft/world/phys/AABB;",
                    remap = false
            ),
            remap = false
    )
    private AABB sable$boundsInTurretFrame(final LivingEntity entity, final Operation<AABB> original) {
        final AABB globalBounds = original.call(entity);
        final Pose3dc pose = this.sable$getTurretFramePose();
        if (pose == null) {
            return globalBounds;
        }

        return new BoundingBox3d(globalBounds).transformInverse(pose).toMojang();
    }

    @Unique
    private Pose3dc sable$getTurretFramePose() {
        final BlockEntity turret = (BlockEntity) (Object) this;
        final Level level = turret.getLevel();
        if (level == null) {
            return null;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(level, turret.getBlockPos());
        if (subLevel == null) {
            return null;
        }

        if (level instanceof final LevelPoseProviderExtension poseProvider) {
            return poseProvider.sable$getPose(subLevel);
        }
        return subLevel.logicalPose();
    }

}
