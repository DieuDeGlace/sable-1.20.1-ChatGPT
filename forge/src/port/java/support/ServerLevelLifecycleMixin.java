package dev.ryanhcode.sable.mixin.plot;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SablePlatform;
import dev.ryanhcode.sable.sublevel.storage.SubLevelOccupancySavedData;
import dev.ryanhcode.sable.sublevel.storage.SubLevelTicketsSavedData;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Forge 1.20.1 port of the upstream server-container lifecycle hooks.
 *
 * <p>The source boundary intentionally excludes the upstream mixin tree, so the
 * lifecycle portion is admitted here explicitly. Saving and plot-specific
 * vanilla tick policy remain separate migration gates.</p>
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelLifecycleMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void sable$initializeSubLevelContainer(final CallbackInfo ci) {
        final ServerLevel level = (ServerLevel) (Object) this;
        if (!SablePlatform.INSTANCE.isWrappedLevel(level)) {
            SubLevelOccupancySavedData.getOrLoad(level);
            SubLevelTicketsSavedData.getOrLoad(level);
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            container.initialize();
        }
    }

    /**
     * Keep the upstream ordering: Sable's region/storage files and ticket
     * pointers must be flushed before vanilla writes the level SavedData.
     */
    @Inject(method = "save", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;saveLevelData()V",
            shift = At.Shift.BEFORE))
    private void sable$saveSubLevels(final ProgressListener progressListener, final boolean flush,
                                     final boolean skipSave, final CallbackInfo ci) {
        final ServerLevel level = (ServerLevel) (Object) this;
        if (progressListener != null) {
            progressListener.progressStartNoAbort(Component.translatable("menu.savingSubLevels"));
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IllegalStateException("Sable server sub-level container is missing during level save");
        }
        final SubLevelHoldingChunkMap holdingChunkMap = container.getHoldingChunkMap();
        holdingChunkMap.saveAll();
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void sable$tickSubLevelContainer(final BooleanSupplier hasTimeLeft, final CallbackInfo ci) {
        final ServerSubLevelContainer container =
                SubLevelContainer.getContainer((ServerLevel) (Object) this);
        if (container == null) {
            throw new IllegalStateException("Sable server sub-level container is missing during level tick");
        }
        container.tick();
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void sable$closeSubLevelContainer(final CallbackInfo ci) {
        final ServerSubLevelContainer container =
                SubLevelContainer.getContainer((ServerLevel) (Object) this);
        if (container != null) {
            container.close();
        }
    }
}
