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
uniform int shaderlodOptReflectionNextEventRefinement;
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
// Photon next-event policy adapted to the native Astra view-space recurrence.
// Positive threshold authorizes a Euclidean quarter-output-pixel next event,
// never an interval/sample-budget change. Missing controls/near/invalid retain native.
float shaderlod_astralex_next_event_threshold(float distanceBlocks, float width, float height, bool opaqueRough) {
    if (!shaderlod_optimization_enabled(shaderlodOptReflectionNextEventRefinement) || !opaqueRough ||
        !(distanceBlocks >= 0.0 && distanceBlocks <= 1e6) ||
        !(width >= 16.0 && width <= 32768.0 && height >= 16.0 && height <= 32768.0) ||
        !(shaderlodNear > 0.0 && shaderlodFar > shaderlodNear && shaderlodFar <= 4096.0 &&
          shaderlodTransition >= 0.0 && shaderlodTransition <= shaderlodFar - shaderlodNear &&
          shaderlodTransition * 0.5 <= shaderlodNear)) return 0.0;
    return 0.25 * smoothstep(shaderlodNear, shaderlodFar + shaderlodTransition, distanceBlocks);
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
