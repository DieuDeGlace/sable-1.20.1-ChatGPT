package dev.ryanhcode.sable.forge.platform;

import dev.ryanhcode.sable.platform.SablePlatform;
import net.createmod.catnip.levelWrappers.WrappedServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** General Forge 47 behavior needed by Sable common code. */
public final class SablePlatformImpl implements SablePlatform {

    @Override
    public boolean isWrappedLevel(@Nullable final Level level) {
        return ModList.get().isLoaded("create") && level instanceof WrappedServerLevel;
    }

    @Override
    public boolean isBlockstateLadder(final BlockState state,
                                      final Level level,
                                      final BlockPos pos,
                                      final LivingEntity entity) {
        return ForgeHooks.isLivingOnLadder(state, level, pos, entity).isPresent();
    }
}
