package dev.ryanhcode.sable.mixin.entity.entity_leashing;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 1.20.1 keeps elastic leash behaviour in PathfinderMob#tickLeash. */
@Mixin(PathfinderMob.class)
public abstract class LeashableMixin {

    @Redirect(method = "tickLeash", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/PathfinderMob;distanceTo(Lnet/minecraft/world/entity/Entity;)F"))
    private float sable$projectedLeashDistance(final PathfinderMob leashedEntity, final Entity holder) {
        return (float) sable$projectedPosition(leashedEntity).distanceTo(sable$projectedPosition(holder));
    }

    @Redirect(method = "tickLeash", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D"))
    private double sable$projectedLeashX(final Entity entity) {
        return sable$projectedPosition(entity).x;
    }

    @Redirect(method = "tickLeash", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getY()D"))
    private double sable$projectedLeashY(final Entity entity) {
        return sable$projectedPosition(entity).y;
    }

    @Redirect(method = "tickLeash", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D"))
    private double sable$projectedLeashZ(final Entity entity) {
        return sable$projectedPosition(entity).z;
    }

    @Redirect(method = "tickLeash", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/PathfinderMob;getX()D"))
    private double sable$projectedMobX(final PathfinderMob entity) {
        return sable$projectedPosition(entity).x;
    }

    @Redirect(method = "tickLeash", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/PathfinderMob;getY()D"))
    private double sable$projectedMobY(final PathfinderMob entity) {
        return sable$projectedPosition(entity).y;
    }

    @Redirect(method = "tickLeash", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/PathfinderMob;getZ()D"))
    private double sable$projectedMobZ(final PathfinderMob entity) {
        return sable$projectedPosition(entity).z;
    }

    @ModifyArg(method = "tickLeash", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/PathfinderMob;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"), index = 0)
    private Vec3 sable$localLeashImpulse(final Vec3 impulse) {
        final Entity self = (Entity) (Object) this;
        final SubLevel subLevel = Sable.HELPER.getContaining(self);
        return subLevel == null ? impulse : subLevel.logicalPose().transformNormalInverse(impulse);
    }

    private static Vec3 sable$projectedPosition(final Entity entity) {
        return Sable.HELPER.projectOutOfSubLevel(entity.level(), entity.position());
    }
}
