package dev.ryanhcode.sable.forge.platform;

import dev.ryanhcode.sable.platform.SableLoaderPlatform;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;

import java.nio.file.Path;

/** Forge 47 implementation of Sable's loader queries. */
public final class SableLoaderPlatformImpl implements SableLoaderPlatform {

    @Override
    public String getModVersion(final String modId) {
        return LoadingModList.get().getModFileById(modId).versionString();
    }

    @Override
    public Path getGameDirectory() {
        return FMLPaths.GAMEDIR.get();
    }
}
