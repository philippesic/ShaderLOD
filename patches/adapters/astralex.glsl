#ifndef SHADERLOD_REFINEMENT_ADAPTER_INCLUDED
#define SHADERLOD_REFINEMENT_ADAPTER_INCLUDED
// ShaderLOD private binding; upstream author credits remain in original members.
#define SHADERLOD_REFINEMENT_ADAPTER 0 // [0 1]
#if SHADERLOD_REFINEMENT_ADAPTER == 1
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodPreset = -1;
uniform float shaderlodNear;
uniform float shaderlodFar;
uniform float shaderlodTransition;
#include "/include/shaderlod/optimization_flags.glsl"
uniform int shaderlodOptReflectionDeferredMetric;
uniform int shaderlodOptWaterParallaxBudget;
uniform int shaderlodOptPomHeightCache;
uniform int shaderlodOptRoughReflectionGather;
uniform int shaderlodOptSsrProjectedFootprintExit;
uniform int shaderlodOptSsrProjectionArithmetic;
uniform int shaderlodOptZeroEmissionArithmetic;
uniform int shaderlodOptTemporalShadowFilter;
#include "/lib/shaderlod/refinement_budget.glsl"
#include "/lib/shaderlod/uniform_sample_budget.glsl"
int shaderlod_astralex_ao_groups(int original) {
    if (!shaderlod_optimization_enabled(shaderlodOptAoBudget)) return original;
    return shaderlod_uniform_sample_budget(shaderlodBridgeVersion, shaderlodEnabled,
        shaderlodPreset, original, 4);
}
// Separate effect flags retain the frozen transport and existing ceil policy.
bool shaderlodAstraOpaqueRough = false;
int shaderlod_astralex_count(int original, int minimum, int flag) {
    if (!shaderlod_optimization_enabled(flag)) return original;
    return shaderlod_uniform_sample_budget(shaderlodBridgeVersion, shaderlodEnabled, shaderlodPreset, original, minimum);
}
int shaderlod_astralex_distance_count(int original, int minimum, float distanceBlocks, int flag) {
    if (!shaderlod_optimization_enabled(flag)) return original;
    return shaderlod_refinement_budget(shaderlodBridgeVersion, shaderlodEnabled, shaderlodPreset, distanceBlocks, original, minimum);
}
#endif
#endif
