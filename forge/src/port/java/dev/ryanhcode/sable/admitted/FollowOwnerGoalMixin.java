package dev.ryanhcode.sable.mixin.admitted;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** 1.20.1 target adaptation of upstream TamableAnimalMixin teleport validation. */
@Mixin(FollowOwnerGoal.class)
public abstract class FollowOwnerGoalMixin {
    @Shadow
    @Final
    private TamableAnimal tamable;

    @Unique
    private static final BoundingBox3d sable$BOX = new BoundingBox3d();

    @WrapOperation(method = "maybeTeleportTo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/goal/FollowOwnerGoal;canTeleportTo(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean sable$validateTeleportPosition(final FollowOwnerGoal instance,
                                                   final BlockPos blockPos,
                                                   final Operation<Boolean> original) {
        final SubLevel subLevel = Sable.HELPER.getTrackingSubLevel(this.tamable.getOwner());
        if (subLevel != null) {
            final BlockPos localPos = BlockPos.containing(
                    subLevel.logicalPose().transformPositionInverse(blockPos.getCenter()));
            if (original.call(instance, localPos)) {
                final double upright = subLevel.logicalPose().transformNormal(new Vector3d(0.0D, 1.0D, 0.0D))
                        .dot(OrientedBoundingBox3d.UP);
                if (upright > 0.85D) {
                    return true;
                }
            }
        }

        sable$BOX.set(this.tamable.getBoundingBox().move(blockPos.subtract(this.tamable.blockPosition())));
        for (final SubLevel intersecting : Sable.HELPER.getAllIntersecting(this.tamable.level(), sable$BOX)) {
            final Vector3d center = sable$BOX.center();
            final BlockPos localPos = BlockPos.containing(intersecting.logicalPose()
                    .transformPositionInverse(new Vec3(center.x(), center.y(), center.z())));
            if (!this.tamable.level().getBlockState(localPos).isAir()) {
                return false;
            }
        }

        return original.call(instance, blockPos);
    }
}
