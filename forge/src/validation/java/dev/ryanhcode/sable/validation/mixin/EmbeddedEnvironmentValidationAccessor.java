package dev.ryanhcode.sable.validation.mixin;

import dev.engine_room.flywheel.backend.engine.embed.EmbeddedEnvironment;
import net.minecraft.core.Vec3i;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the exact CPU transform uploaded for an embedded environment. */
@Mixin(value = EmbeddedEnvironment.class, remap = false)
public interface EmbeddedEnvironmentValidationAccessor {
    @Accessor("poseComposed")
    Matrix4f sable$getPoseComposed();

    @Accessor("renderOrigin")
    Vec3i sable$getRenderOrigin();
}
