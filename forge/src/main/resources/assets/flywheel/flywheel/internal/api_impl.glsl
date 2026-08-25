struct FlwLightAo {
    vec2 light;
    float ao;
};

/// Get the light at the given world position relative to flw_renderOrigin from the given normal.
/// This may be interpolated for smooth lighting.
bool flw_light(uint scene, vec3 worldPos, vec3 normal, ivec3 renderOrigin, out FlwLightAo light);

/// Fetches the light value at the given block position.
/// Returns false if the light for the given block is not available.
bool flw_lightFetch(uint scene, ivec3 blockPos, out vec2 light);

// Flywheel 1.0.5 and add-ons compiled against it use these signatures.
// Keep them as adapters into Sable's scene-aware light lookup.
bool flw_light(vec3 worldPos, vec3 normal, out FlwLightAo light) {
    return flw_light(0u, worldPos, normal, flw_renderOrigin, light);
}

bool flw_lightFetch(ivec3 blockPos, out vec2 light) {
    return flw_lightFetch(0u, blockPos, light);
}
