package dev.ryanhcode.sable.mixin.admitted;

import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LevelExtension;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Level.class)
public final class LevelCollisionStateMixin implements LevelExtension {
    @Unique
    private final LevelReusedVectors sable$reusedVectors = new LevelReusedVectors();

    @Override
    public LevelReusedVectors sable$getJOMLSink() {
        return this.sable$reusedVectors;
    }
}
