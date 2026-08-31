package dev.ryanhcode.sable.forge;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

final class ForceGroupRegistryVerifier {
    private ForceGroupRegistryVerifier() {
    }

    static void requireBuiltIns(final Set<ResourceLocation> registeredIds,
                                final Set<ResourceLocation> builtInIds) {
        if (!registeredIds.containsAll(builtInIds)) {
            final Set<ResourceLocation> missingIds = new java.util.HashSet<>(builtInIds);
            missingIds.removeAll(registeredIds);
            throw new IllegalStateException("Sable force-group registry is missing built-in entries: "
                    + missingIds);
        }
    }
}
