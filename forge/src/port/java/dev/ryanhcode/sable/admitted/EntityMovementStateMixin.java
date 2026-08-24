package dev.ryanhcode.sable.mixin.admitted;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.index.SableTags;
import dev.ryanhcode.sable.mixinterface.EntityExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LevelExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.UUID;

/**
 * 1.20.1 adaptation of the upstream entity movement/collision mixin.
 * The only injection-point change is the verified 1.20.1 method name
 * {@code setOnGroundWithKnownMovement}; the collision algorithm remains upstream.
 */
@Mixin(value = Entity.class, priority = 1100)
public abstract class EntityMovementStateMixin implements EntityMovementExtension, EntityExtension {
    @Shadow public boolean horizontalCollision;
    @Shadow public boolean verticalCollision;
    @Shadow public boolean verticalCollisionBelow;
    @Shadow public boolean minorHorizontalCollision;
    @Shadow private Level level;
    @Shadow
    private Vec3 position;
    @Shadow @Nullable private BlockState feetBlockState;
    @Shadow private BlockPos blockPosition;
    @Shadow private AABB bb;

    @Shadow protected abstract Vec3 collide(Vec3 movement);
    @Shadow protected abstract AABB makeBoundingBox();
    @Shadow protected abstract boolean isHorizontalCollisionMinor(Vec3 movement);
    @Shadow public abstract Level level();
    @Shadow public abstract void setPos(Vec3 position);
    @Shadow @Nullable public abstract Entity getVehicle();
    @Shadow public abstract Vec3 getDeltaMovement();
    @Shadow public abstract AABB getBoundingBox();
    @Shadow public abstract EntityType<?> getType();
    @Shadow public abstract void kill();

    @Unique
    private SubLevel sable$trackingSubLevel;
    @Unique
    private UUID sable$lastTrackingSubLevelId;
    @Unique
    private SubLevelEntityCollision.CollisionInfo sable$collisionInfo;
    @Unique
    private BlockPos sable$inBlockStatePos = BlockPos.ZERO;

    @Override
    public void sable$setPosSuperRaw(final Vec3 position) {
        this.position = position;
        this.blockPosition = BlockPos.containing(position);
        this.bb = this.makeBoundingBox();
    }

    @Override
    public Vec3 sable$vanillaCollide(final Vec3 movement) {
        return this.collide(movement);
    }

    @WrapOperation(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setOnGroundWithKnownMovement(ZLnet/minecraft/world/phys/Vec3;)V"))
    private void sable$applySubLevelCollisionFlags(final Entity instance, final boolean onGround,
                                                    final Vec3 movement, final Operation<Void> original) {
        this.horizontalCollision = this.sable$collisionInfo.horizontalCollision;
        this.verticalCollision = this.sable$collisionInfo.verticalCollision;
        this.verticalCollisionBelow = this.sable$collisionInfo.verticalCollisionBelow;
        this.minorHorizontalCollision = this.sable$collisionInfo.minorHorizontalCollision;
        original.call(instance, this.verticalCollisionBelow, movement);
    }

    @WrapOperation(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;updateEntityAfterFallOn(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;)V"))
    private void sable$updateEntityAfterFallOn(final Block block, final BlockGetter getter,
                                                final Entity entity, final Operation<Void> original) {
        if (this.verticalCollision) {
            original.call(block, getter, entity);
        }
    }

    @Redirect(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$collide(final Entity entity, final Vec3 motion) {
        final Entity self = (Entity) (Object) this;
        Vec3 velocity = Vec3.ZERO;
        if (self instanceof LivingEntity livingEntity) {
            velocity = JOMLConversion.toMojang(((LivingEntityMovementExtension) livingEntity).sable$getInheritedVelocity());
        }

        final SubLevel previousTracking = this.sable$trackingSubLevel;
        final Vec3 previousDelta = this.getDeltaMovement();
        this.sable$collisionInfo = SubLevelEntityCollision.collide(
                entity, motion, velocity, ((LevelExtension) this.level).sable$getJOMLSink());
        this.sable$collisionInfo.preTrackingSubLevel = previousTracking;
        this.sable$collisionInfo.preDeltaMovement = previousDelta;

        if (this.sable$collisionInfo.trackingSubLevel != null) {
            if (this.sable$collisionInfo.verticalCollisionBelow) {
                this.sable$trackingSubLevel = this.sable$collisionInfo.trackingSubLevel;
            }
        } else if (!(entity instanceof ServerPlayer)) {
            this.sable$trackingSubLevel = null;
        }

        final Vec3 beforeVanilla = this.sable$collisionInfo.motion;
        final Vec3 afterVanilla = this.collide(beforeVanilla);
        final boolean xCollision = !Mth.equal(beforeVanilla.x, afterVanilla.x);
        final boolean zCollision = !Mth.equal(beforeVanilla.z, afterVanilla.z);
        this.sable$collisionInfo.horizontalCollision |= xCollision || zCollision;

        if (beforeVanilla.y != afterVanilla.y) {
            this.sable$trackingSubLevel = null;
        }
        this.sable$collisionInfo.verticalCollision |= beforeVanilla.y != afterVanilla.y;
        this.sable$collisionInfo.verticalCollisionBelow |= this.sable$collisionInfo.verticalCollision && motion.y < 0.0;
        if (this.horizontalCollision) {
            this.sable$collisionInfo.minorHorizontalCollision = this.isHorizontalCollisionMinor(afterVanilla);
        }
        if (this.sable$trackingSubLevel != null && this.sable$trackingSubLevel.isRemoved()) {
            this.sable$trackingSubLevel = null;
        }
        return afterVanilla;
    }

    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At("TAIL"))
    private void sable$finishMove(final MoverType moverType, final Vec3 movement, final CallbackInfo ci) {
        if (this.sable$collisionInfo != null) {
            this.horizontalCollision |= this.sable$collisionInfo.subLevelHorizontalCollision;
        }
        if (!((Object) this instanceof LivingEntity) && this.sable$collisionInfo != null
                && this.sable$collisionInfo.inheritedMotion != null
                && this.sable$collisionInfo.inheritedMotion.lengthSqr() > Math.pow(0.000001, 2)) {
            this.setPos(this.position.add(((EntityExtension) this).sable$vanillaCollide(this.sable$collisionInfo.inheritedMotion)));
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void sable$maintainTracking(final CallbackInfo ci) {
        final ActiveSableCompanion helper = Sable.HELPER;
        final Entity self = (Entity) (Object) this;
        final Entity vehicle = this.getVehicle();
        final SubLevel containing = helper.getContaining(self);
        if (containing != null) {
            this.sable$trackingSubLevel = null;
        } else if (vehicle != null) {
            final SubLevel vehicleSubLevel = helper.getContaining(vehicle);
            this.sable$trackingSubLevel = vehicleSubLevel != null
                    ? vehicleSubLevel : helper.getTrackingSubLevel(vehicle);
        }
        if (this.sable$trackingSubLevel != null && this.sable$trackingSubLevel.isRemoved()) {
            this.sable$trackingSubLevel = null;
        }
        if (containing != null
                && !this.getBoundingBox().intersects(containing.getPlot().getBoundingBox().toAABB().inflate(1.0))
                && this.getType().is(SableTags.DESTROY_WHEN_LEAVING_PLOT)) {
            this.kill();
        }
    }

    @Override
    public BlockPos sable$getInBlockStatePos() {
        return this.sable$inBlockStatePos;
    }

    /** @author RyanH @reason Take sub-level blocks into account. */
    @Overwrite
    public BlockState getFeetBlockState() {
        final Level currentLevel = this.level();
        if (this.feetBlockState == null || this.sable$trackingSubLevel != null) {
            this.feetBlockState = currentLevel.getBlockState(this.blockPosition);
            this.sable$inBlockStatePos = this.blockPosition;
            final Iterator<SubLevel> intersecting = Sable.HELPER
                    .getAllIntersecting(this.level, new BoundingBox3d(this.blockPosition)).iterator();
            while (this.feetBlockState.isAir() && intersecting.hasNext()) {
                final SubLevel subLevel = intersecting.next();
                final BlockPos localPos = BlockPos.containing(
                        subLevel.logicalPose().transformPositionInverse(this.position.add(0.0, 0.001, 0.0)));
                this.feetBlockState = currentLevel.getBlockState(localPos);
                this.sable$inBlockStatePos = localPos;
            }
        }
        return this.feetBlockState;
    }

    @Override
    public SubLevel sable$getTrackingSubLevel() {
        return this.sable$trackingSubLevel;
    }

    @Override
    public UUID sable$getLastTrackingSubLevelID() {
        return this.sable$lastTrackingSubLevelId;
    }

    @Override
    public void sable$setTrackingSubLevel(final SubLevel subLevel) {
        this.sable$trackingSubLevel = subLevel;
        if (subLevel != null) {
            this.sable$setLastTrackingSubLevelID(subLevel.getUniqueId());
        }
    }

    @Override
    public void sable$setLastTrackingSubLevelID(final UUID uuid) {
        this.sable$lastTrackingSubLevelId = uuid;
    }

    @Override
    public SubLevelEntityCollision.CollisionInfo sable$getCollisionInfo() {
        return this.sable$collisionInfo;
    }

    @Override
    public void sable$setPosField(final Vec3 newPosition) {
        this.position = newPosition;
    }
}
