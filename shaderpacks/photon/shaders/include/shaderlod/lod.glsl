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
uniform int shaderlodPreset;
#endif

bool shaderlod_use_bridge() {
#ifdef SHADERLOD_USE_MOD_CONFIG
    return shaderlodBridgeVersion == 1 || shaderlodBridgeVersion == 2;
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

int shaderlod_preset() {
#ifdef SHADERLOD_USE_MOD_CONFIG
    if (shaderlodBridgeVersion == 2) return clamp(shaderlodPreset, 0, 2);
#endif
    return SHADERLOD_PRESET;
}

// Preserve low-frequency tree shadow/occlusion structure by default. This
// compile-time switch applies only to PCF/SSRT/GTAO/SSAO, not other effect LOD.
int shaderlod_lighting_tier(int tier) {
#ifdef SHADERLOD_PRESERVE_LIGHTING
    return 0;
#else
    return tier;
#endif
}

// Budget is a cap, never increases upstream work. One path executes per pixel.
int shaderlod_budget(int original, int tier, int minimum) {
    if (tier == 0) return original;
    int preset = shaderlod_preset();
    float scale = preset == 0 ? (tier == 1 ? 0.75 : 0.50) :
                  preset == 1 ? (tier == 1 ? 0.50 : 0.25) :
                                (tier == 1 ? 0.25 : 0.125);
    return min(original, max(minimum, int(ceil(float(original) * scale))));
}

// Interleaved gradient noise; frame rotation lets Photon TAA resolve transitions.
float shaderlod_noise(vec2 pixel, float frame) {
    pixel += mod(frame, 64.0) * vec2(2.17, 0.73);
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

int shaderlod_tier_with_distances(float distance_blocks, float noise, vec3 distances) {
    if (!shaderlod_enabled()) return 0;
    if (distances.z <= 0.0) {
        return distance_blocks < distances.x ? 0 : (distance_blocks < distances.y ? 1 : 2);
    }
    float half_band = 0.5 * distances.z;
    if (distance_blocks <= distances.x - half_band) return 0;
    if (distance_blocks >= distances.y + half_band) return 2;
    if (distance_blocks >= distances.x + half_band && distance_blocks <= distances.y - half_band) return 1;
    float medium = smoothstep(distances.x - half_band, distances.x + half_band, distance_blocks);
    float low = smoothstep(distances.y - half_band, distances.y + half_band, distance_blocks);
    return noise < low ? 2 : (noise < medium ? 1 : 0);
}

int shaderlod_tier(float distance_blocks, float noise) {
    if (!shaderlod_enabled()) return 0;
    return shaderlod_tier_with_distances(distance_blocks, noise, shaderlod_distances());
}

int shaderlod_quality_at(float distance_blocks, vec2 pixel, float frame) {
    if (!shaderlod_enabled()) return 0;
    vec3 distances = shaderlod_distances();
    float half_band = 0.5 * distances.z;
    if (distance_blocks <= distances.x - half_band) return 0;
    if (distance_blocks >= distances.y + half_band) return 2;
    if (distance_blocks >= distances.x + half_band && distance_blocks <= distances.y - half_band) return 1;
    return shaderlod_tier_with_distances(distance_blocks, shaderlod_noise(pixel, frame), distances);
}

// Dedicated getters keep effect ownership explicit; generic tier helpers
// remain used by lighting/debug and cannot be globally repurposed.
int shaderlod_fog_tier(float distance_blocks, float noise) {
#ifdef SHADERLOD_FOG
    return shaderlod_tier(distance_blocks, noise);
#else
    return 0;
#endif
}

int shaderlod_ssr_tier(float distance_blocks, float noise) {
#ifdef SHADERLOD_SSR
    return shaderlod_tier(distance_blocks, noise);
#else
    return 0;
#endif
}

// Cached cloud lighting is generated at a fixed sea-level origin. An explicit
// global-quality option is honest here; it is not distance from the live camera.
int shaderlod_cached_sky_tier() {
#ifndef SHADERLOD_CLOUDS
    return 0;
#else
#ifdef SHADERLOD_CACHED_SKY
    if (shaderlod_enabled()) return shaderlod_preset();
#endif
    return 0;
#endif
}

int shaderlod_cloud_tier(float distance_blocks, float noise) {
#ifndef SHADERLOD_CLOUDS
    return 0;
#else
    float near_cloud = max(SHADERLOD_CLOUD_NEAR, 1.0);
    float far_cloud = max(SHADERLOD_CLOUD_FAR, near_cloud + 1.0);
    float transition_cloud = clamp(SHADERLOD_CLOUD_TRANSITION, 0.0,
        min(2.0 * near_cloud, far_cloud - near_cloud));
    return shaderlod_tier_with_distances(distance_blocks, noise,
        vec3(near_cloud, far_cloud, transition_cloud));
#endif
}

// Water has longer visible geometric detail than material parallax. These
// shader options are effect-specific; the mod still supplies enable/preset.
int shaderlod_water_tier(float distance_blocks, float noise) {
#ifndef SHADERLOD_WATER
    return 0;
#else
    float near_water = max(SHADERLOD_WATER_NEAR, 1.0);
    float far_water = max(SHADERLOD_WATER_FAR, near_water + 1.0);
    float transition_water = clamp(SHADERLOD_WATER_TRANSITION, 0.0,
        min(2.0 * near_water, far_water - near_water));
    return shaderlod_tier_with_distances(distance_blocks, noise,
        vec3(near_water, far_water, transition_water));
#endif
}

int shaderlod_water_quality_at(float distance_blocks, vec2 pixel, float frame) {
#ifndef SHADERLOD_WATER
    return 0;
#else
    if (!shaderlod_enabled()) return 0;
    return shaderlod_water_tier(distance_blocks, shaderlod_noise(pixel, frame));
#endif
}

// Structural work avoidance, independent of distance quality. Terrain uses
// this atmosphere value only for border fog or blocky-cloud compositing.
// The pinned Iris raw 3D LUT has max level/min/max LOD zero, so implicit LOD
// cannot choose a different level when this call moves behind a pixel branch.
bool shaderlod_needs_atmosphere(float depth) {
#if defined BORDER_FOG || defined BLOCKY_CLOUDS
    return true;
#else
    return depth == 1.0 || !shaderlod_enabled();
#endif
}

// POM is already limited to a short range; its relative bands must be inside it.
int shaderlod_material_tier(float distance_blocks, float pom_distance, float noise) {
#ifndef SHADERLOD_MATERIALS
    return 0;
#else
    return shaderlod_tier_with_distances(distance_blocks, noise,
        vec3(0.50 * pom_distance, 0.85 * pom_distance, 0.10 * pom_distance));
#endif
}

vec3 shaderlod_debug_color(int tier) {
    if (tier == 0) return vec3(0.08, 0.8, 0.15);
    if (tier == 1) return vec3(0.95, 0.55, 0.05);
    return vec3(0.95, 0.06, 0.06);
}

#endif
