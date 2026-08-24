package dev.ryanhcode.sable.neoforge.mixin.compatibility.flywheel;

import com.mojang.logging.LogUtils;
import dev.engine_room.flywheel.backend.glsl.LoadResult;
import dev.engine_room.flywheel.backend.glsl.ShaderSources;
import dev.engine_room.flywheel.backend.glsl.SourceFile;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

@Mixin(value = ShaderSources.class, remap = false)
public class ShaderSourcesMixin {
    private static final Logger SABLE$LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SABLE$FLYWHEEL_MATRICES = new ResourceLocation(
            "flywheel", "internal/indirect/matrices.glsl");
    private static final ResourceLocation SABLE$EXTENDED_MATRICES = new ResourceLocation(
            "sable", "internal/indirect/matrices.glsl");

    @Shadow
    @Final
    protected Map<ResourceLocation, LoadResult> cache;

    /**
     * Forge 1.20.1's combined mod resource pack does not guarantee that Sable's
     * same-namespace Flywheel source wins over Flywheel's own copy.  Redirect the
     * already-parsed include graph before program compilation so the 192-byte Java
     * matrix stride and the shader struct are unconditionally the same definition.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void sable$bindExtendedMatrixSource(final CallbackInfo ci) {
        final LoadResult extendedResult = this.cache.get(SABLE$EXTENDED_MATRICES);
        if (!(extendedResult instanceof final LoadResult.Success extendedSuccess)) {
            throw new IllegalStateException("Sable's extended Flywheel matrix shader source was not loaded: "
                    + SABLE$EXTENDED_MATRICES);
        }

        final SourceFile extended = extendedSuccess.source();
        final Set<SourceFile> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int replacements = 0;
        for (final LoadResult result : this.cache.values()) {
            if (result instanceof final LoadResult.Success success) {
                replacements += sable$replaceMatrixIncludes(success.source(), extended, visited);
            }
        }

        if (replacements == 0) {
            throw new IllegalStateException("No Flywheel shader include referenced the matrix source "
                    + SABLE$FLYWHEEL_MATRICES);
        }
        this.cache.put(SABLE$FLYWHEEL_MATRICES, extendedResult);
        SABLE$LOGGER.info("Sable bound {} Flywheel shader include(s) to the extended 192-byte matrix layout",
                replacements);
    }

    private static int sable$replaceMatrixIncludes(final SourceFile source, final SourceFile extended,
                                                    final Set<SourceFile> visited) {
        if (!visited.add(source)) {
            return 0;
        }

        int replacements = 0;
        for (int i = 0; i < source.included.size(); i++) {
            final SourceFile included = source.included.get(i);
            if (included.name.equals(SABLE$FLYWHEEL_MATRICES)) {
                source.included.set(i, extended);
                replacements++;
            } else {
                replacements += sable$replaceMatrixIncludes(included, extended, visited);
            }
        }
        return replacements;
    }
}
