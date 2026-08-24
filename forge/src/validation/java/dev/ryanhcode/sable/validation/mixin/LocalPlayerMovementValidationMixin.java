package dev.ryanhcode.sable.validation.mixin;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.validation.SableForgeMovementValidationProbe;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LocalPlayer.class, priority = 900)
public abstract class LocalPlayerMovementValidationMixin extends Entity {
    @Unique private Vec3 sable$validationGlobal;
    @Unique private SubLevel sable$validationOldTracking;
    @Unique private boolean sable$validationArmed;

    protected LocalPlayerMovementValidationMixin(final EntityType<?> type, final Level level) {
        super(type, level);
    }

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void sable$prepareValidation(final CallbackInfo ci) {
        if (!Boolean.getBoolean("sable.movementValidation")) return;
        this.sable$validationGlobal = this.position();
        final EntityMovementExtension movement = (EntityMovementExtension) this;
        this.sable$validationOldTracking = movement.sable$getTrackingSubLevel();
        final SubLevel armed = SableForgeMovementValidationProbe.takeClientPacketArm();
        this.sable$validationArmed = armed != null;
        if (armed != null) {
            movement.sable$setTrackingSubLevel(armed);
            SableForgeMovementValidationProbe.beginClientPacket(this.sable$validationGlobal, armed);
        }
    }

    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void sable$finishValidation(final CallbackInfo ci) {
        if (this.sable$validationArmed) {
            SableForgeMovementValidationProbe.finishClientPacket(this.position());
            ((EntityMovementExtension) this).sable$setTrackingSubLevel(this.sable$validationOldTracking);
        }
        this.sable$validationGlobal = null;
        this.sable$validationOldTracking = null;
        this.sable$validationArmed = false;
    }
}
