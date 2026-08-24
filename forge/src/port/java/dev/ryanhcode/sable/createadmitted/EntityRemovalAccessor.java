package dev.ryanhcode.sable.neoforge.mixin.admitted;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityRemovalAccessor {
    @Accessor("removalReason")
    void sable$setRemovalReason(@Nullable Entity.RemovalReason removalReason);
}
