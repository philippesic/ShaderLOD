#ifndef SHADERLOD_EFFECT_ADAPTER_INCLUDED
#define SHADERLOD_EFFECT_ADAPTER_INCLUDED
// Private local binding. Original authors retain all upstream attribution.
#define SHADERLOD_EFFECT_ADAPTER 0 // [0 1]
#define SHADERLOD_SILDUR_SHADOW_QUERY_GRAPH 0 // [0 1]
#if SHADERLOD_EFFECT_ADAPTER == 1
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodPreset = -1;
// Additional union IDs declared locally; frozen38 shared transport stays exact.
uniform int shaderlodOptWaterShadowZero;
uniform int shaderlodOptWaterWaveBudget;
uniform int shaderlodOptZeroEmissionArithmetic;
uniform int shaderlodOptTemporalShadowFilter;
uniform int shaderlodOptPomHeightCache;
uniform int shaderlodOptShadowReceiverFootprint;
uniform int shaderlodSildurReceiverPermission;
uniform int shaderlodOptAoSkyElision;
uniform int shaderlodOptShadowMinField;
uniform int shaderlodOptShadowRawQuery;
// Default-zero producer/API permissions require parent normal supplier qualification.
uniform int shaderlodSildurShadowSamplerVersion;
uniform int shaderlodSildurShadowSamplerQualified;
uniform int shaderlodSildurShadowArithmeticQualified;
uniform int shaderlodSildurShadowFieldQualified;
uniform int shaderlodSildurShadowFieldFrame = -1;
uniform float shaderlodSildurShadowWeightSteps;
uniform int frameCounter; // Native Iris PER_FRAME uniform, not a private counter supplier.
#include "/include/shaderlod/optimization_flags.glsl"
#include "/lib/shaderlod/uniform_sample_budget.glsl"
uniform float shaderlodNear;
uniform float shaderlodFar;
uniform float shaderlodTransition;
#include "/lib/shaderlod/refinement_budget.glsl"
int shaderlod_effect_budget(int original, int minimum) {
    if (!shaderlod_optimization_enabled(shaderlodOptVolumetricLightBudget)) return original;
    return shaderlod_uniform_sample_budget(shaderlodBridgeVersion, shaderlodEnabled,
        shaderlodPreset, original, minimum);
}
int shaderlod_sildur_primary_cloud_count(int original, bool reflection, float nearestLayerGap) {
    // Slab vertical gap is a conservative lower bound on every reached cloud point distance.
    // Keep native integration for near clouds/camera inside cloud layer and unknown bands.
    if (!(shaderlodNear > 0.0 && shaderlodNear < shaderlodFar && shaderlodFar <= 4096.0 &&
        shaderlodTransition >= 0.0 && shaderlodTransition <= shaderlodFar - shaderlodNear &&
        shaderlodTransition * 0.5 <= shaderlodNear &&
        nearestLayerGap >= shaderlodFar + 0.5 * shaderlodTransition && nearestLayerGap <= 16777216.0)) return original;
    if (reflection) {
        // Native reflection3 stays3 in Balanced; Performance2 covers the same native span.
        if (!shaderlod_optimization_enabled(shaderlodOptReflectionCloudBudget) || shaderlodPreset == 0) return original;
        return max(2, min(original, int(ceil(float(original) * (2.0 / 3.0)))));
    }
    if (!shaderlod_optimization_enabled(shaderlodOptCloudPrimaryBudget)) return original;
    return shaderlod_uniform_sample_budget(shaderlodBridgeVersion, shaderlodEnabled,
        shaderlodPreset, original, 6);
}
int shaderlod_sildur_distance_count(int flag, int original, int minimum, float distance_blocks) {
    if (!shaderlod_optimization_enabled(flag)) return original;
    return shaderlod_refinement_budget(shaderlodBridgeVersion, shaderlodEnabled,
        shaderlodPreset, distance_blocks, original, minimum);
}
#endif
#endif
