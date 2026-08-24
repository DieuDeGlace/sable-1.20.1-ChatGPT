package dev.ryanhcode.sable.mixinterface.clip_overwrite;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public interface ClipContextExtension {
    ClipContext.Block sable$getBlockMode();

    ClipContext.Fluid sable$getFluidMode();

    CollisionContext sable$getCollisionContext();

    void sable$setCollisionContext(CollisionContext collisionContext);

    @Nullable SubLevel sable$getIgnoredSubLevel();

    @Nullable Predicate<SubLevel> sable$getSubLevelIgnoring();

    void sable$setIgnoredSubLevel(@Nullable SubLevel sable$ignoredSubLevel);

    void sable$setSubLevelIgnoring(@Nullable Predicate<SubLevel> sable$subLevelIgnoring);

    void sable$setIgnoreMainLevel(boolean ignoreWorld);

    boolean sable$isIgnoreMainLevel();

    void sable$setDoNotProject(boolean doNotProject);

    boolean sable$doNotProject();
}
