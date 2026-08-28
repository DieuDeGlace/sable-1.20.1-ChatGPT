package dev.ryanhcode.sable.neoforge.mixin.compatibility.flywheel;

import dev.engine_room.flywheel.backend.glsl.LoadResult;
import dev.engine_room.flywheel.backend.glsl.ShaderSources;
import dev.engine_room.flywheel.backend.glsl.SourceFile;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Mixin(value = ShaderSources.class, remap = false)
public class ShaderSourcesMixin {
    private static final Map<ResourceLocation, ResourceLocation> SABLE$SOURCE_ALIASES = sable$sourceAliases();

    @Shadow
    @Final
    protected Map<ResourceLocation, LoadResult> cache;

    /**
     * Forge 1.20.1's combined mod resource pack does not guarantee that Sable's
     * same-namespace Flywheel source wins over Flywheel's own copy. Redirect every
     * source that participates in Sable's matrix and lighting ABI before program
     * compilation, so entry points, includes and the 192-byte Java matrix stride
     * always come from one coherent source set.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void sable$bindCoherentShaderAbi(final CallbackInfo ci) {
        for (final Map.Entry<ResourceLocation, ResourceLocation> alias : SABLE$SOURCE_ALIASES.entrySet()) {
            final ResourceLocation originalName = alias.getKey();
            final ResourceLocation extendedName = alias.getValue();
            final LoadResult extendedResult = this.cache.get(extendedName);
            if (!(extendedResult instanceof final LoadResult.Success extendedSuccess)) {
                throw new IllegalStateException("Sable's coherent Flywheel shader source was not loaded: "
                        + extendedName);
            }

            final SourceFile extended = extendedSuccess.source();
            final Set<SourceFile> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            for (final LoadResult result : this.cache.values()) {
                if (result instanceof final LoadResult.Success success) {
                    sable$replaceIncludes(success.source(), originalName, extended, visited);
                }
            }
            this.cache.put(originalName, extendedResult);
        }
    }

    private static void sable$replaceIncludes(final SourceFile source, final ResourceLocation originalName,
                                              final SourceFile extended, final Set<SourceFile> visited) {
        if (!visited.add(source)) {
            return;
        }

        for (int i = 0; i < source.included.size(); i++) {
            final SourceFile included = source.included.get(i);
            if (included.name.equals(originalName)) {
                source.included.set(i, extended);
            } else {
                sable$replaceIncludes(included, originalName, extended, visited);
            }
        }
    }

    private static Map<ResourceLocation, ResourceLocation> sable$sourceAliases() {
        final Map<ResourceLocation, ResourceLocation> aliases = new LinkedHashMap<>();
        sable$alias(aliases, "internal/common.vert");
        sable$alias(aliases, "internal/api_impl.glsl");
        sable$alias(aliases, "internal/light_lut.glsl");
        sable$alias(aliases, "internal/instancing/light.glsl");
        sable$alias(aliases, "internal/indirect/light.glsl");
        sable$alias(aliases, "internal/indirect/matrices.glsl");
        sable$alias(aliases, "internal/instancing/main.vert");
        sable$alias(aliases, "internal/indirect/main.vert");
        sable$alias(aliases, "light/flat.glsl");
        sable$alias(aliases, "light/smooth.glsl");
        sable$alias(aliases, "light/smooth_when_embedded.glsl");
        return Collections.unmodifiableMap(aliases);
    }

    private static void sable$alias(final Map<ResourceLocation, ResourceLocation> aliases, final String path) {
        aliases.put(new ResourceLocation("flywheel", path), new ResourceLocation("sable", path));
    }
}
