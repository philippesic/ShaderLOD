#ifndef SHADERLOD_HELPER_INCLUDED
#define SHADERLOD_HELPER_INCLUDED

// ShaderLOD's explicit Photon integration. Distance is camera/view-space blocks.
// An unregistered uniform is zero: sentinel prevents absent-mod defaults from
// overriding usable shader options. Iris properties expressions are not a mod API.
#ifdef SHADERLOD_USE_MOD_CONFIG
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform float shaderlodNear;
uniform float shaderlodFar;
uniform float shaderlodTransition;
uniform int shaderlodDebug;
#endif

bool shaderlod_use_bridge() {
#ifdef SHADERLOD_USE_MOD_CONFIG
    return shaderlodBridgeVersion == 1;
#else
    return false;
#endif
}

bool shaderlod_enabled() {
#ifdef SHADERLOD_USE_MOD_CONFIG
    if (shaderlod_use_bridge()) return shaderlodEnabled != 0;
#endif
#ifdef SHADERLOD_ENABLED
    return true;
#else
    return false;
#endif
}

bool shaderlod_debug_enabled() {
#ifdef SHADERLOD_USE_MOD_CONFIG
    if (shaderlod_use_bridge()) return shaderlodDebug != 0;
#endif
#ifdef SHADERLOD_DEBUG
    return true;
#else
    return false;
#endif
}

vec3 shaderlod_distances() {
    vec3 values = vec3(SHADERLOD_NEAR, SHADERLOD_FAR, SHADERLOD_TRANSITION);
#ifdef SHADERLOD_USE_MOD_CONFIG
    if (shaderlod_use_bridge()) {
        values = vec3(shaderlodNear, shaderlodFar, shaderlodTransition);
    }
#endif
    values.x = max(values.x, 1.0);
    values.y = max(values.y, values.x + 1.0);
    // Prevent overlapping transition bands even with independently chosen options.
    values.z = clamp(values.z, 0.0, values.y - values.x);
    return values;
}

// Interleaved gradient noise; frame rotation lets Photon TAA resolve transitions.
float shaderlod_noise(vec2 pixel, float frame) {
    pixel += mod(frame, 64.0) * vec2(2.17, 0.73);
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

int shaderlod_tier(float distance_blocks, float noise) {
    if (!shaderlod_enabled()) return 0;
    vec3 distances = shaderlod_distances();
    if (distances.z <= 0.0) {
        return distance_blocks < distances.x ? 0 : (distance_blocks < distances.y ? 1 : 2);
    }
    float half_band = 0.5 * distances.z;
    float medium = smoothstep(distances.x - half_band, distances.x + half_band, distance_blocks);
    float low = smoothstep(distances.y - half_band, distances.y + half_band, distance_blocks);
    // Select one quality path per pixel, so transitions do not evaluate both paths.
    return noise < low ? 2 : (noise < medium ? 1 : 0);
}

vec3 shaderlod_debug_color(int tier) {
    if (tier == 0) return vec3(0.08, 0.8, 0.15);
    if (tier == 1) return vec3(0.95, 0.55, 0.05);
    return vec3(0.95, 0.06, 0.06);
}

#endif
