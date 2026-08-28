package dev.ryanhcode.sable.validation.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Development-only evidence that a plot bell received its initialization block event. */
@Mixin(BellBlockEntity.class)
public interface BellBlockEntityValidationAccessor {
    @Accessor("nearbyEntities")
    List<LivingEntity> sable$getNearbyEntities();
}
