package dev.ryanhcode.sable.forge.platform;

import dev.ryanhcode.sable.platform.SableAssemblyPlatform;
import net.minecraft.world.level.Level;

/** Forge 47 implementation of the block-snapshot assembly guard. */
public final class SableAssemblyPlatformImpl implements SableAssemblyPlatform {

    @Override
    public void setIgnoreOnPlace(final Level level, final boolean ignore) {
        level.captureBlockSnapshots = ignore;
    }
}
