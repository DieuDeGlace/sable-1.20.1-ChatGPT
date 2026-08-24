package dev.ryanhcode.sable.mixin.plot;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableChunkEventPlatform;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/** Routes 1.20.1 client chunk packets and lookups into Sable plots. */
@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {
    @Shadow @Final private ClientLevel level;
    @Shadow @Final private LevelChunk emptyChunk;

    @Shadow
    private static boolean isValidChunk(final LevelChunk chunk, final int x, final int z) {
        return false;
    }

    private SubLevelContainer sable$container() {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container == null) {
            throw new IllegalStateException("Sable plot container not found in client level");
        }
        return container;
    }

    @Inject(method = "getChunk",
            at = @At("HEAD"), cancellable = true)
    private void sable$getChunk(final int x, final int z, final ChunkStatus status,
                                final boolean create, final CallbackInfoReturnable<LevelChunk> cir) {
        final SubLevelContainer container = this.sable$container();
        if (container.inBounds(x, z)) {
            final LevelChunk chunk = container.getChunk(new ChunkPos(x, z));
            cir.setReturnValue(chunk != null ? chunk : this.emptyChunk);
        }
    }

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
    private void sable$replaceWithPacketData(final int x, final int z,
                                             final FriendlyByteBuf buffer, final CompoundTag tag,
                                             final Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer,
                                             final CallbackInfoReturnable<LevelChunk> cir) {
        final SubLevelContainer container = this.sable$container();
        if (!container.inBounds(x, z)) {
            return;
        }

        final ChunkPos chunkPos = new ChunkPos(x, z);
        LevelChunk chunk = container.getChunk(chunkPos);
        if (!isValidChunk(chunk, x, z)) {
            if (chunk != null) {
                SableChunkEventPlatform.INSTANCE.onOldChunkInvalid(chunk);
                this.level.unload(chunk);
            }
            chunk = new LevelChunk(this.level, chunkPos);
            chunk.replaceWithPacketData(buffer, tag, consumer);
            container.newPopulatedChunk(chunkPos, chunk);
        } else {
            chunk.replaceWithPacketData(buffer, tag, consumer);
        }

        this.level.onChunkLoaded(chunkPos);
        this.level.getLightEngine().setLightEnabled(chunkPos, true);
        SableChunkEventPlatform.INSTANCE.onClientChunkPacketReplaced(chunk);
        cir.setReturnValue(chunk);
    }
}
