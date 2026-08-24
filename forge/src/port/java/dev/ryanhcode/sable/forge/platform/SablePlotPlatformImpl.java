package dev.ryanhcode.sable.forge.platform;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.platform.SablePlotPlatform;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkDataEvent;
import org.slf4j.Logger;

/** Forge 47.4.0 chunk persistence adapter. */
@SuppressWarnings("deprecation")
public final class SablePlotPlatformImpl implements SablePlotPlatform {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FORGE_CAPS_KEY = "ForgeCaps";

    @Override
    public void readLightData(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk) {
        // Forge 47.4.0 has no LevelChunk auxiliary-light manager or auxiliary-light NBT key.
    }

    @Override
    public void readChunkAttachments(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk) {
        if (tag.contains(FORGE_CAPS_KEY, Tag.TAG_COMPOUND)) {
            chunk.readCapsFromNBT(tag.getCompound(FORGE_CAPS_KEY));
        }
    }

    @Override
    public void postLoad(final CompoundTag tag, final LevelChunk chunk) {
        MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Load(chunk, tag, ChunkStatus.ChunkType.LEVELCHUNK));
    }

    @Override
    public void writeLightData(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk) {
        // Forge 47.4.0 has no auxiliary-light state to serialize.
    }

    @Override
    public void writeChunkAttachments(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk) {
        try {
            final CompoundTag capabilities = chunk.writeCapsToNBT();
            if (capabilities != null) {
                tag.put(FORGE_CAPS_KEY, capabilities);
            }
        } catch (final Exception exception) {
            LOGGER.error("Failed to write chunk capabilities; provider state will not persist", exception);
        }
    }
}
