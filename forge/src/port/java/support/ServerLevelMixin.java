package dev.ryanhcode.sable.mixin.plot.container;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.platform.SableEventPublishPlatform;
import dev.ryanhcode.sable.platform.SablePlatform;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.Supplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level implements SubLevelContainerHolder {
    @Unique
    private final SubLevelContainer sable$plotContainer = this.sable$createPlotContainer();

    protected ServerLevelMixin(final WritableLevelData levelData,
                               final ResourceKey<Level> dimension,
                               final RegistryAccess registryAccess,
                               final Holder<DimensionType> dimensionType,
                               final Supplier<ProfilerFiller> profiler,
                               final boolean clientSide,
                               final boolean debug,
                               final long biomeZoomSeed,
                               final int maxChainedNeighborUpdates) {
        super(levelData, dimension, registryAccess, dimensionType, profiler, clientSide, debug,
                biomeZoomSeed, maxChainedNeighborUpdates);
    }

    @Unique
    private SubLevelContainer sable$createPlotContainer() {
        if (SablePlatform.INSTANCE.isWrappedLevel(this)) {
            return null;
        }
        final ServerSubLevelContainer container = new ServerSubLevelContainer(
                this, SubLevelContainer.DEFAULT_LOG_SIZE_LENGTH, SubLevelContainer.DEFAULT_LOG_PLOT_SIZE,
                SubLevelContainer.DEFAULT_ORIGIN, SubLevelContainer.DEFAULT_ORIGIN);
        Sable.defaultSubLevelContainerInitializer(this, container);
        SableEventPublishPlatform.INSTANCE.onSubLevelContainerReady(this, container);
        return container;
    }

    @Override
    public SubLevelContainer sable$getPlotContainer() {
        return this.sable$plotContainer;
    }
}
