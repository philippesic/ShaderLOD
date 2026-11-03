#ifndef SHADERLOD_EFFECT_ADAPTER_INCLUDED
#define SHADERLOD_EFFECT_ADAPTER_INCLUDED
// Private local binding. Original authors retain all upstream attribution.
#define SHADERLOD_EFFECT_ADAPTER 0 // [0 1]
#define SHADERLOD_BLISS_AO_HALF_RES 0 // [0 1]
#include "/lib/shaderlod/bliss_spatial_option.glsl"
#if SHADERLOD_EFFECT_ADAPTER == 1
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodPreset = -1;
uniform float shaderlodNear;
uniform float shaderlodFar;
uniform float shaderlodTransition;
#include "/include/shaderlod/optimization_flags.glsl"
uniform int shaderlodOptReflectionDeferredMetric;
uniform int shaderlodOptSsrProjectionArithmetic;
uniform int shaderlodOptZeroEmissionArithmetic;
uniform int shaderlodOptAoSkyElision;
uniform int shaderlodOptWaterWaveBudget;
uniform int shaderlodOptCloudSecondaryExtentCache;
#include "/lib/shaderlod/uniform_sample_budget.glsl"
#include "/lib/shaderlod/refinement_budget.glsl"
#include "/include/shaderlod/work_policy.glsl"
int shaderlod_effect_budget(int flag, int original, int minimum) {
    if (!shaderlod_optimization_enabled(flag)) return original;
    return shaderlod_uniform_sample_budget(shaderlodBridgeVersion, shaderlodEnabled,
        shaderlodPreset, original, minimum);
}

int shaderlod_bliss_distance_budget(int flag, float distance_blocks, int original, int minimum) {
    if (!shaderlod_optimization_enabled(flag)) return original;
    return shaderlod_refinement_budget(shaderlodBridgeVersion, shaderlodEnabled,
        shaderlodPreset, distance_blocks, original, minimum);
}
#endif
#endif
