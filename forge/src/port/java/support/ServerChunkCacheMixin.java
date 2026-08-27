package dev.ryanhcode.sable.mixin.plot;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.ServerChunkCacheLifecycleExtension;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Routes 1.20.1 server chunk lookups and block notifications into Sable plots. */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin implements ServerChunkCacheLifecycleExtension {
    @Unique private static final Logger sable$LOGGER = LogUtils.getLogger();
    @Shadow @Final ServerLevel level;
    @Unique private EmptyLevelChunk sable$emptyChunk;
    @Unique private boolean sable$loggedEmptyFallback;

    @Override
    public void sable$initializeEmptyChunkAfterLevelCapabilities() {
        if (this.sable$emptyChunk != null) {
            return;
        }

        this.sable$emptyChunk = new EmptyLevelChunk(this.level, new ChunkPos(0, 0),
                this.level.registryAccess().registryOrThrow(Registries.BIOME)
                        .getHolderOrThrow(Biomes.PLAINS));
    }

    private SubLevelContainer sable$container() {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container == null) {
            throw new IllegalStateException("Sable plot container not found in server level");
        }
        return container;
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void sable$getChunkNow(final int x, final int z,
                                   final CallbackInfoReturnable<LevelChunk> cir) {
        final SubLevelContainer container = this.sable$container();
        if (container.inBounds(x, z)) {
            cir.setReturnValue(container.getChunk(new ChunkPos(x, z)));
        }
    }

    @Inject(method = "getChunkFutureMainThread", at = @At("HEAD"), cancellable = true)
    private void sable$getChunkFutureMainThread(final int x, final int z,
                                                final ChunkStatus status, final boolean load,
                                                final CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess,
                                                        ChunkHolder.ChunkLoadingFailure>>> cir) {
        final SubLevelContainer container = this.sable$container();
        if (container.inBounds(x, z)) {
            final LevelChunk chunk = container.getChunk(new ChunkPos(x, z));
            if (chunk != null) {
                cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
            } else {
                if (this.sable$emptyChunk == null) {
                    throw new IllegalStateException("Sable empty plot chunk requested before ServerLevel capabilities initialized");
                }
                if (!this.sable$loggedEmptyFallback) {
                    this.sable$loggedEmptyFallback = true;
                    dev.ryanhcode.sable.util.DiagnosticLogLimiter.info(sable$LOGGER, "SABLE-PLOT-EMPTY-FALLBACK: routed unallocated plot-grid neighbor "
                            + "({}, {}) to EmptyLevelChunk in {}; collision/raycast queries may safely continue",
                            x, z, this.level.dimension().location());
                }
                cir.setReturnValue(CompletableFuture.completedFuture(Either.left(this.sable$emptyChunk)));
            }
        }
    }

    @Inject(method = "hasChunk", at = @At("HEAD"), cancellable = true)
    private void sable$hasChunk(final int x, final int z,
                                final CallbackInfoReturnable<Boolean> cir) {
        final SubLevelContainer container = this.sable$container();
        if (container.inBounds(x, z)) {
            cir.setReturnValue(container.getChunk(new ChunkPos(x, z)) != null);
        }
    }

    @Inject(method = "getChunkForLighting", at = @At("HEAD"), cancellable = true)
    private void sable$getChunkForLighting(final int x, final int z,
                                            final CallbackInfoReturnable<LightChunk> cir) {
        final SubLevelContainer container = this.sable$container();
        if (container.inBounds(x, z)) {
            cir.setReturnValue(container.getChunk(new ChunkPos(x, z)));
        }
    }

    @Inject(method = "isPositionTicking", at = @At("HEAD"), cancellable = true)
    private void sable$isPositionTicking(final long packedChunkPos,
                                         final CallbackInfoReturnable<Boolean> cir) {
        final int x = ChunkPos.getX(packedChunkPos);
        final int z = ChunkPos.getZ(packedChunkPos);
        final SubLevelContainer container = this.sable$container();
        if (container.inBounds(x, z)) {
            cir.setReturnValue(container.getChunk(new ChunkPos(packedChunkPos)) != null);
        }
    }

    @Inject(method = "getFullChunk", at = @At("HEAD"), cancellable = true)
    private void sable$getFullChunk(final long packedChunkPos,
                                    final Consumer<LevelChunk> consumer,
                                    final CallbackInfo ci) {
        final int x = ChunkPos.getX(packedChunkPos);
        final int z = ChunkPos.getZ(packedChunkPos);
        final SubLevelContainer container = this.sable$container();
        if (container.inBounds(x, z)) {
            final LevelChunk chunk = container.getChunk(new ChunkPos(packedChunkPos));
            if (chunk != null) {
                consumer.accept(chunk);
            }
            ci.cancel();
        }
    }

    @Inject(method = "getVisibleChunkIfPresent", at = @At("HEAD"), cancellable = true)
    private void sable$getVisibleChunkIfPresent(final long packedChunkPos,
                                                 final CallbackInfoReturnable<ChunkHolder> cir) {
        final int x = ChunkPos.getX(packedChunkPos);
        final int z = ChunkPos.getZ(packedChunkPos);
        final SubLevelContainer container = this.sable$container();
        if (container.inBounds(x, z)) {
            cir.setReturnValue(container.getChunkHolder(new ChunkPos(x, z)));
        }
    }

    @Inject(method = "addRegionTicket", at = @At("HEAD"), cancellable = true)
    private <T> void sable$addRegionTicket(final TicketType<T> type,
                                           final ChunkPos pos,
                                           final int distance,
                                           final T value,
                                           final CallbackInfo ci) {
        if (this.sable$container().inBounds(pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "blockChanged", at = @At("HEAD"), cancellable = true)
    private void sable$blockChanged(final BlockPos blockPos, final CallbackInfo ci) {
        final SubLevelContainer container = this.sable$container();
        final ChunkPos chunkPos = new ChunkPos(blockPos);
        if (container.inBounds(chunkPos)) {
            final PlotChunkHolder holder = container.getChunkHolder(chunkPos);
            if (holder == null) {
                throw new IllegalStateException("Cannot change a block in an unallocated Sable plot chunk");
            }
            holder.blockChanged(blockPos);
            ci.cancel();
        }
    }
}
