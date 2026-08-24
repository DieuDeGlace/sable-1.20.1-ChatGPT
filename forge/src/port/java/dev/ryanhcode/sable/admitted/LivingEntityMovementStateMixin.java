package dev.ryanhcode.sable.mixin.admitted;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.mixinterface.EntityExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1 adaptation of the upstream living-entity inherited-motion path.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMovementStateMixin extends Entity implements LivingEntityMovementExtension {
    @Shadow public abstract LivingEntity.Fallsounds getFallSounds();

    @Unique
    private final Vector3d sable$inheritedVelocity = new Vector3d();
    @Unique
    private final Vector3d sable$tempPlayerVelocity = new Vector3d();
    @Unique
    private final Vector3d sable$tempSubLevelVelocity = new Vector3d();

    protected LivingEntityMovementStateMixin(final EntityType<?> type, final Level level) {
        super(type, level);
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void sable$finishTravel(final Vec3 travelVector, final CallbackInfo ci) {
        final SubLevelEntityCollision.CollisionInfo info =
                ((EntityMovementExtension) this).sable$getCollisionInfo();
        if (info != null && info.inheritedMotion != null && !this.isSpectator()) {
            this.setPos(this.position().add(((EntityExtension) this).sable$vanillaCollide(info.inheritedMotion)));
            this.sable$inheritedVelocity.set(
                    info.inheritedMotion.x, info.inheritedMotion.y, info.inheritedMotion.z);
        }

        if (info != null && info.firstCollisions != null && !info.firstCollisions.isEmpty()) {
            for (var firstCollision : info.firstCollisions.entrySet()) {
                final SubLevelEntityCollision.FirstCollisionInfo collision = firstCollision.getValue();
                final SubLevel subLevel = firstCollision.getKey();
                if (collision.horizontal() && subLevel != info.preTrackingSubLevel) {
                    this.sable$computeCollisionEffects(info, subLevel, collision);
                }
            }
        }

        final double threshold = 0.0000001;
        if (this.sable$inheritedVelocity.lengthSquared() <= threshold) {
            this.sable$inheritedVelocity.zero();
        }
        if ((info == null || info.inheritedMotion == null)
                && this.sable$inheritedVelocity.lengthSquared() > threshold) {
            this.sable$applyDrag();
        }
    }

    @Unique
    private void sable$applyDrag() {
        if (this.verticalCollision || this.onGround()) {
            this.sable$inheritedVelocity.mul(0.7, 0.0, 0.7);
        }
        if (this.horizontalCollision) {
            this.sable$inheritedVelocity.mul(0.8, 0.6, 0.8);
        }
        if ((Object) this instanceof Player player && player.getAbilities().flying) {
            this.sable$inheritedVelocity.mul(0.9);
        }
        if (this.wasTouchingWater) {
            this.sable$inheritedVelocity.mul(0.9);
        }
        this.sable$inheritedVelocity.mul(0.99);
        if (Math.abs(this.sable$inheritedVelocity.y) < 0.01) {
            this.sable$inheritedVelocity.y = 0.0;
        }
    }

    @Unique
    private void sable$computeCollisionEffects(final SubLevelEntityCollision.CollisionInfo info,
                                                final SubLevel collidedSubLevel,
                                                final SubLevelEntityCollision.FirstCollisionInfo collision) {
        final Vector3d playerVelocity = JOMLConversion
                .toJOML(info.preDeltaMovement, this.sable$tempPlayerVelocity)
                .add(this.sable$inheritedVelocity);
        final Level level = this.level();
        final Vector3d pointVelocity = Sable.HELPER.getVelocity(
                level, collidedSubLevel, collision.localLocation(), this.sable$tempSubLevelVelocity)
                .mul(1.0 / 20.0);
        final Vector3d relativeVelocity = playerVelocity.sub(pointVelocity).negate();
        final double magnitude = collision.globalDirection().dot(relativeVelocity);
        if (magnitude <= 3.0 / 20.0) {
            return;
        }

        relativeVelocity.set(collision.globalDirection()).mul(-magnitude);
        if (collision.bouncy()) {
            final SoundEvent sound = collision.block().getSoundType().getFallSound();
            level.playSound((Object) this instanceof Player player ? player : null,
                    this.getX(), this.getY(), this.getZ(), sound, SoundSource.BLOCKS, .75f, 1.0f);
            this.addDeltaMovement(JOMLConversion.toMojang(collision.globalDirection())
                    .scale(relativeVelocity.length() * 0.65));
            if (Sable.HELPER.getTrackingSubLevel(this) == null) {
                this.addDeltaMovement(JOMLConversion.toMojang(pointVelocity));
            }
        } else {
            final float damage = (float) (magnitude * 12.0 - 8.0);
            if (damage > 0.0f) {
                this.playSound(damage > 4.0f ? this.getFallSounds().big() : this.getFallSounds().small(), 1.0f, 1.0f);
                this.hurt(this.damageSources().flyIntoWall(), damage);
            }
        }
    }

    @Override
    public Vector3d sable$getInheritedVelocity() {
        return this.sable$inheritedVelocity;
    }
}
