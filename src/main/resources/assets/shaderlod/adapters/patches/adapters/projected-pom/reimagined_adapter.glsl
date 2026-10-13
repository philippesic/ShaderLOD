#ifndef SHADERLOD_PROJECTED_POM_ADAPTER_INCLUDED
#define SHADERLOD_PROJECTED_POM_ADAPTER_INCLUDED
#include "/lib/shaderlod/adapter.glsl"
#define SHADERLOD_PROJECTED_POM SHADERLOD_REFINEMENT_ADAPTER
#if SHADERLOD_PROJECTED_POM == 1 && defined GBUFFERS_TERRAIN && !defined GBUFFERS_COLORWHEEL && !defined DISTANT_HORIZONS && !defined VOXY && !defined POM_ALLOW_CUTOUT
#include "/include/shaderlod/projected_material_budget.glsl"

int shaderlod_complementary_pom_count(float distance_blocks, float fade, bool opaque_material) {
    // Global Off, Quality(3), unknown/absent bridge keep the entire upstream ray.
    if (!shaderlod_optimization_enabled(shaderlodOptProjectedPom) ||
        shaderlod_optimization_enabled(shaderlodOptPhysicalRenderScale) ||
        shaderlodBridgeVersion != 2 || shaderlodEnabled != 1 ||
        shaderlodPreset < 0 || shaderlodPreset > 2 || !opaque_material ||
        !(fade >= 0.5 && fade < 1.0) || !(POM_DISTANCE >= 16 && POM_DISTANCE <= 1024))
        return POM_QUALITY;
    float view_length2 = dot(viewVector, viewVector);
    // Protect backfaces and angles >=~75.5 degrees from the tangent normal.
    if (!(view_length2 > 1e-8 && view_length2 < 1e12 && viewVector.z < 0.0 &&
        viewVector.z * viewVector.z > 0.0625 * view_length2) ||
        any(lessThanEqual(vTexCoordAM.pq, vec2(0.0))) ||
        any(greaterThan(vTexCoordAM.pq, vec2(1.0)))) return POM_QUALITY;
    vec2 full_ray = viewVector.xy * (0.25 * POM_DEPTH * (1.0 - fade)) / -viewVector.z;
    float far_ratio = shaderlodPreset == 0 ? 0.75 : 0.5;
    // Private resolved core policy; no claim of an effects-uniform/UI connection.
    // For stock D=32: medium centered25, far29, full width4 blocks.
    ShaderlodWorkPolicy policy = ShaderlodWorkPolicy(shaderlodBridgeVersion, 2, 1,
        true, 2, true, true, vec3(0.78125, 0.90625, 0.125) * float(POM_DISTANCE),
        vec2(far_ratio, far_ratio));
    // Pinned stock archive has native-resolution terrain raster, no scaler overlay.
    // dcdx/dcdy are stock global unconditional texCoord derivatives, not derivatives
    // evaluated here inside the caller's fade/material branches. Preserve ReadNormal.
    return shaderlod_projected_material_budget(policy, true, true, false,
        distance_blocks, POM_QUALITY, full_ray,
        dcdx / vTexCoordAM.pq, dcdy / vTexCoordAM.pq,
        atlasSize * vTexCoordAM.pq, 1.0);
}
#endif
#endif
