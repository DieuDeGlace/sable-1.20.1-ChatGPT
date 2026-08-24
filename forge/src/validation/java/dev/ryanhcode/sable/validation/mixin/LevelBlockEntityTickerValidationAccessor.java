package dev.ryanhcode.sable.validation.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Development-only evidence for the vanilla block-entity ticker registration boundary. */
@Mixin(Level.class)
public interface LevelBlockEntityTickerValidationAccessor {
    @Accessor("blockEntityTickers")
    List<TickingBlockEntity> sable$getBlockEntityTickers();

    @Accessor("pendingBlockEntityTickers")
    List<TickingBlockEntity> sable$getPendingBlockEntityTickers();
}
