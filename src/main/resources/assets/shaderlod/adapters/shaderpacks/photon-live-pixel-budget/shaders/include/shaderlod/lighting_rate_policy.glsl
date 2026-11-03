// ShaderLOD explicit Photon spatial lighting adapter. Original Photon LICENSE applies.
#if !defined SHADERLOD_LIGHTING_RATE_POLICY_INCLUDED
#define SHADERLOD_LIGHTING_RATE_POLICY_INCLUDED
#include "/include/shaderlod/surface_continuity.glsl"
uniform int shaderlodDebug;
uniform int shaderlodEffectsVersion;
uniform int shaderlodOpaqueLightingMode;
uniform float shaderlodOpaqueLightingNear;
uniform float shaderlodOpaqueLightingFar;
uniform float shaderlodOpaqueLightingTransition;
uniform float shaderlodOpaqueLightingMediumWorkRatio;
uniform float shaderlodOpaqueLightingFarWorkRatio;

bool shaderlod_rate_finite(float x) { return !isnan(x) && !isinf(x); }
bool shaderlod_rate_ratio(float x) {
    // Legal realized rates are K4/full and K1/four-pixel tile. K2/K3/zero unsupported.
    return shaderlod_rate_finite(x) && (x == 1.0 || (x > 0.0 && x <= 0.25));
}
bool shaderlod_rate_parameters(out vec3 bands, out vec2 ratios) {
    bands = vec3(shaderlodNear, shaderlodFar, shaderlodTransition);
    ratios = vec2(1.0, 0.25); // Explicit private-pack INHERIT policy; public option defaults off.
    if (shaderlodPhotonLightingResources != 1 || !shaderlod_optimization_enabled(shaderlodOptSpatialLightingRate) || shaderlodEffectsVersion != 2)
        return false;
    if (shaderlodOpaqueLightingMode == 1) return false; // FULL
    if (shaderlodOpaqueLightingMode == 2) {
        bands = vec3(shaderlodOpaqueLightingNear, shaderlodOpaqueLightingFar, shaderlodOpaqueLightingTransition);
        ratios = vec2(shaderlodOpaqueLightingMediumWorkRatio, shaderlodOpaqueLightingFarWorkRatio);
    } else if (shaderlodOpaqueLightingMode != 0) return false;
    return !any(isnan(bands)) && !any(isinf(bands)) && bands.x >= 1.0 && bands.y > bands.x &&
        bands.z >= 0.0 && bands.z <= min(2.0 * bands.x, bands.y - bands.x) &&
        shaderlod_rate_finite(bands.y + 0.5 * bands.z) &&
        shaderlod_rate_ratio(ratios.x) && shaderlod_rate_ratio(ratios.y) && ratios.y <= ratios.x;
}
float shaderlod_rate_weight(float distance_blocks) {
    vec3 bands; vec2 ratios;
    if (!shaderlod_rate_parameters(bands, ratios) || !shaderlod_rate_finite(distance_blocks)) return 0.0;
    float half_width = 0.5 * bands.z;
    float medium = bands.z == 0.0 ? float(distance_blocks >= bands.x) :
        smoothstep(bands.x - half_width, bands.x + half_width, distance_blocks);
    float far_weight = bands.z == 0.0 ? float(distance_blocks >= bands.y) :
        smoothstep(bands.y - half_width, bands.y + half_width, distance_blocks);
    return ratios.x <= 0.25 ? medium : (ratios.y <= 0.25 ? far_weight : 0.0);
}
bool shaderlod_rate_material(uint mask, Material material) {
    return mask == 0u && !material.is_metal && !material.is_hardcoded_metal &&
        material.sss_amount == 0.0 && material.sheen_amount == 0.0 && material.porosity == 0.0 &&
        material.ssr_multiplier == 0.0 && all(equal(material.emission, vec3(0.0))) &&
        material.roughness >= 0.75 && material.roughness <= 1.0 &&
        !any(isnan(material.albedo)) && !any(isinf(material.albedo)) && max_of(material.f0) <= 0.04;
}
bool shaderlod_rate_environment() {
    return isEyeInWater == 0 && shaderlod_rate_finite(wetness) && wetness == 0.0;
}
#endif
