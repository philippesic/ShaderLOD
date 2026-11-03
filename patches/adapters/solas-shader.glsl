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
uniform int shaderlodOptTemporalShadowFilter;
uniform int shaderlodOptZeroEmissionArithmetic;
uniform int shaderlodOptSsrProjectedFootprintExit;
uniform int shaderlodOptReflectionNextEventRefinement;
bool shaderlodSolasOpaqueRough = false;
uniform int shaderlodOptSsrProjectionArithmetic;
uniform int shaderlodOptRoughReflectionGather;
uniform int shaderlodOptPomHeightCache;
uniform int shaderlodOptPomEmptyHeightPrefix;
uniform int shaderlodSolasAoDepthQualified = 0;
uniform int shaderlodOptReflectionDeferredMetric;
uniform int shaderlodSolasExactDepthPermission = 0;
uniform int shaderlodOptWaterParallaxBudget;
uniform int shaderlodOptSkyAtmosphereOwnership;
uniform int shaderlodOptWaterShadowZero;
#include "/lib/shaderlod/refinement_budget.glsl"
#include "/lib/shaderlod/uniform_sample_budget.glsl"
// Same actual atlas coordinate with the same explicit gradients is the same
// immutable texture query. Do not infer equality from a texel key or UV algebra.
bool shaderlod_solas_same_height_query(float x, float y, float cachedX, float cachedY) {
    return shaderlod_optimization_enabled(shaderlodOptPomHeightCache) &&
        x >= 0.0 && x <= 1.0 && y >= 0.0 && y <= 1.0 &&
        x == cachedX && y == cachedY;
}
int shaderlod_solas_volume_budget(int original, int minimum) {
    if (!shaderlod_optimization_enabled(shaderlodOptAuroraBudget)) return original;
    return shaderlod_uniform_sample_budget(shaderlodBridgeVersion, shaderlodEnabled,
        shaderlodPreset, original, minimum);
}
int shaderlod_solas_budget(int flag, int original, int minimum) {
    if (!shaderlod_optimization_enabled(flag)) return original;
    return shaderlod_uniform_sample_budget(shaderlodBridgeVersion, shaderlodEnabled, shaderlodPreset, original, minimum);
}
int shaderlod_solas_distance_budget(int flag, float distance_blocks, int original, int minimum) {
    if (!shaderlod_optimization_enabled(flag)) return original;
    return shaderlod_refinement_budget(shaderlodBridgeVersion, shaderlodEnabled, shaderlodPreset, distance_blocks, original, minimum);
}
#endif
#endif
