#if !defined SHADERLOD_PHOTON_SPATIAL_ADAPTER_INCLUDED
#define SHADERLOD_PHOTON_SPATIAL_ADAPTER_INCLUDED
// Explicit Photon v1.3b input adapter; Photon LICENSE retained in assembled archive.
// All tier selection, all-member geometry classification, validation/reconstruction are common.
#include "/include/shaderlod/work_policy.glsl"
#include "/include/shaderlod/spatial_plan.glsl"
#include "/include/shaderlod/surface_region_continuity.glsl"
#include "/include/shaderlod/spatial_pipeline.glsl"
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodEffectsVersion;
uniform int shaderlodCapabilitiesVersion;
uniform int shaderlodOpaqueSpatialAvailable;
uniform int shaderlodOpaqueLightingMode;
uniform float shaderlodOpaqueLightingNear;
uniform float shaderlodOpaqueLightingFar;
uniform float shaderlodOpaqueLightingTransition;
uniform float shaderlodOpaqueLightingMediumWorkRatio;
uniform float shaderlodOpaqueLightingFarWorkRatio;
const int SHADERLOD_PHOTON_QUANTITY = 1;

bool shaderlod_photon_material(uint mask, Material material) {
    return mask == 0u && !material.is_metal && !material.is_hardcoded_metal &&
        material.sss_amount == 0.0 && material.sheen_amount == 0.0 && material.porosity == 0.0 &&
        material.ssr_multiplier == 0.0 && all(equal(material.emission, vec3(0.0))) &&
        material.roughness >= 0.75 && material.roughness <= 1.0 &&
        !any(isnan(material.albedo)) && !any(isinf(material.albedo)) && max_of(material.f0) <= 0.04;
}
bool shaderlod_photon_environment() {
    return isEyeInWater == 0 && shaderlod_region_finite(wetness) && wetness == 0.0;
}
int shaderlod_photon_tile_width() { return (int(view_res.x) + 1) / 2; }
bool shaderlod_photon_cache_contract() {
    // RGBA32F required by frozen graph. Float integers are exact only in this bounded domain.
    // Same frameCounter epoch, dimensions, source/options/policy snapshot across both draws.
    return shaderlod_region_finite(view_res) && all(greaterThanEqual(view_res, vec2(2.0))) &&
        all(lessThanEqual(view_res, vec2(8192.0))) && all(equal(view_res, floor(view_res))) &&
        #if defined SHADERLOD_PHOTON_PRODUCER
        all(equal(textureSize(colortex1, 0), ivec2(view_res))) &&
#else
        all(equal(textureSize(colortex2, 0), ivec2(view_res))) &&
#endif
        frameCounter >= 0 && frameCounter <= 16777216;
}
float shaderlod_photon_near_guard() {
    return max(0.001, shaderlodOpaqueLightingNear - 0.5 * shaderlodOpaqueLightingTransition);
}
ShaderlodWorkPolicy shaderlod_photon_work_policy() {
    // INHERIT deliberately unresolved/full; no private distances/preset substitution.
    return ShaderlodWorkPolicy(shaderlodBridgeVersion,
        shaderlodEffectsVersion, 2, shaderlodEnabled == 1, shaderlodOpaqueLightingMode,
        false, true, vec3(shaderlodOpaqueLightingNear, shaderlodOpaqueLightingFar,
            shaderlodOpaqueLightingTransition),
        vec2(shaderlodOpaqueLightingMediumWorkRatio, shaderlodOpaqueLightingFarWorkRatio));
}
bool shaderlod_spatial_adapter_has_coarse_demand() {
    return shaderlod_spatial_policy_has_coarse_demand(shaderlod_photon_work_policy(),
        shaderlodCapabilitiesVersion == 1 && shaderlodOpaqueSpatialAvailable == 1,
        shaderlod_photon_environment(), false, 3);
}
ShaderlodSpatialPlan shaderlod_spatial_adapter_plan(float distance_blocks) {
    ShaderlodWorkPolicy policy = shaderlod_photon_work_policy();
    return shaderlod_spatial_plan_decide(policy,
        shaderlodCapabilitiesVersion == 1 && shaderlodOpaqueSpatialAvailable == 1,
        shaderlod_photon_environment(), false, distance_blocks, 3); // rates1 and1/4 only
}
ShaderlodSpatialSurface shaderlod_spatial_adapter_surface(ivec2 pixel) {
    ShaderlodSpatialSurface result = shaderlod_spatial_empty_surface();
    float depth = texelFetch(depthtex1, pixel, 0).x;
    bool hand; fix_hand_depth(depth, hand);
    if (hand || !shaderlod_region_finite(depth) || depth <= 0.0 || depth >= 1.0) return result;
    vec4 packed = texelFetch(colortex1, pixel, 0);
    if (any(isnan(packed)) || any(isinf(packed))) return result;
    vec2 a = unpack_unorm_2x8(packed.x); vec2 b = unpack_unorm_2x8(packed.y);
    uint mask = uint(255.0 * b.y);
    if (mask != 0u) return result;
#if !defined USE_SEPARATE_ENTITY_DRAWS
    if (texelFetch(colortex3, pixel, 0) != vec4(0.0)) return result;
#endif
    vec3 flat_normal = decode_unit_vector(unpack_unorm_2x8(packed.z));
    result.position_view_blocks = screen_to_view_space(combined_projection_matrix_inverse,
        vec3((vec2(pixel) + 0.5) * view_pixel_size, depth), true);
    result.normal_view = mat3(gbufferModelView) * flat_normal;
    result.lightmap = unpack_unorm_2x8(packed.w);
    Material material = material_from(vec3(a, b.x), mask,
        view_to_scene_space(result.position_view_blocks) + cameraPosition, flat_normal, result.lightmap);
    result.factorization_class = 0;
    result.protected_receiver = !shaderlod_photon_environment() || !shaderlod_photon_material(mask, material);
    return result;
}
#include "/include/shaderlod/photon_linear_basis.glsl"
ShaderlodSpatialSpread shaderlod_spatial_adapter_spread(ivec2 origin, int side) {
    // Original AO reconstruction union checked; NO cast-shadow continuity certificate.
    // Failed AO guard rejects quantity before expensive evaluation; sentinel invalidates split metadata.
    return ShaderlodSpatialSpread(false, vec2(0.0));
}
vec4 shaderlod_spatial_adapter_evaluate_linear(ivec2 pixel, ShaderlodSpatialSurface surface) {
    // Guard precedes original expensive evaluation. NaN forces shared producer/resolve fallback.
    if (!shaderlod_photon_ao_union_eligible((pixel / 2) * 2)) return vec4(uintBitsToFloat(0x7fc00000u));
    shaderlodLightingPixel = vec2(pixel) + 0.5;
    float depth = texelFetch(depthtex1, pixel, 0).x;
    vec3 scene = view_to_scene_space(surface.position_view_blocks);
    vec4 quantity = shaderlod_photon_evaluate(pixel, texelFetch(colortex1, pixel, 0), depth,
        surface.position_view_blocks, scene, scene + cameraPosition,
        normalize(scene - gbufferModelViewInverse[3].xyz));
    // Declared alpha basis domain [0,1] reserves -1 as a split-lane invalid sentinel.
    if (quantity.a < 0.0 || quantity.a > 1.0) return vec4(uintBitsToFloat(0x7fc00000u));
    return quantity;
}
ShaderlodSpatialCache shaderlod_spatial_adapter_cache(int side, ivec2 tile_coordinate) {
    ShaderlodSpatialCache result = shaderlod_spatial_invalid_cache(SHADERLOD_PHOTON_QUANTITY, frameCounter);
    if (side != 2 || !shaderlod_photon_cache_contract()) return result;
    ivec2 origin = 2 * tile_coordinate;
    vec4 quantity = texelFetch(colortex2, origin, 0);
    vec4 certificate = texelFetch(colortex2, origin + ivec2(1, 0), 0);
    int index = tile_coordinate.x + shaderlod_photon_tile_width() * tile_coordinate.y;
    if (any(isnan(certificate)) || any(isinf(certificate)) ||
        certificate.x != float(frameCounter) || certificate.y != 258.0 ||
        certificate.z != float(index) || quantity.a < 0.0 || quantity.a > 1.0) return result;
    // All integer fields recover exactly from bounded binary32 values. Kernel validates the certificate.
    result.linear_quantity = quantity; result.valid = true;
    result.quantity_key = 1; result.epoch = int(certificate.x); result.side = 2;
    result.tile_coordinate = ivec2(int(certificate.z) % shaderlod_photon_tile_width(),
        int(certificate.z) / shaderlod_photon_tile_width());
    result.minimum_distance_blocks = certificate.w;
    return result;
}
#endif
