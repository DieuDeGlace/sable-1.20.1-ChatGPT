package dev.ryanhcode.sable.forge;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ForceGroupRegistryVerifierTest {
    private static final Set<ResourceLocation> BUILT_INS = Set.of(
            id("gravity"),
            id("drag"),
            id("levitation"),
            id("balloon_lift"),
            id("propulsion"),
            id("lift"),
            id("magnetic_force"));

    @Test
    void acceptsTheCompleteBuiltInRegistry() {
        assertDoesNotThrow(() -> ForceGroupRegistryVerifier.requireBuiltIns(BUILT_INS, BUILT_INS));
    }

    @Test
    void acceptsAddonForceGroupsWithoutSpecialCasingTheirIds() {
        final Set<ResourceLocation> registeredIds = new HashSet<>(BUILT_INS);
        registeredIds.add(ResourceLocation.fromNamespaceAndPath("example_addon", "buoyancy"));

        assertDoesNotThrow(() -> ForceGroupRegistryVerifier.requireBuiltIns(registeredIds, BUILT_INS));
    }

    @Test
    void rejectsARegistryMissingAnyBuiltInForceGroup() {
        final Set<ResourceLocation> registeredIds = new HashSet<>(BUILT_INS);
        registeredIds.remove(id("gravity"));

        assertThrows(IllegalStateException.class,
                () -> ForceGroupRegistryVerifier.requireBuiltIns(registeredIds, BUILT_INS));
    }

    private static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath("sable", path);
    }
}
