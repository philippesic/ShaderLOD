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
// Source-backed optional union controls owned by BSL, outside the frozen shared38 contract.
uniform int shaderlodOptZeroEmissionArithmetic;
uniform int shaderlodOptTemporalShadowFilter;
uniform int shaderlodOptReflectedCloudDistanceExit;
uniform int shaderlodOptPomHeightCache;
uniform int shaderlodOptRoughReflectionGather;
uniform int shaderlodOptSsrProjectedFootprintExit;
uniform int shaderlodOptSsrProjectionArithmetic;
uniform int shaderlodOptShadowReceiverFootprint;
uniform int shaderlodOptShadowMinField;
uniform int shaderlodOptShadowRawQuery;
uniform int shaderlodOptPomEmptyHeightPrefix;
uniform int shaderlodOptAoSkyElision;
uniform int shaderlodOptReflectionDeferredMetric;
uniform int shaderlodOptWaterParallaxBudget;
// GLSL120 lacks integer min/max overloads. Preserve integer budget arithmetic
// with local overloads while retaining both shared source helpers byte-for-byte.
int shaderlod_bsl_min(int a, int b) { return a < b ? a : b; }
int shaderlod_bsl_max(int a, int b) { return a > b ? a : b; }
#define min shaderlod_bsl_min
#define max shaderlod_bsl_max
#include "/lib/shaderlod/refinement_budget.glsl"
#include "/lib/shaderlod/uniform_sample_budget.glsl"
#undef min
#undef max
// The sky aurora caller retains its full unit interval and native radiance weighting.
int shaderlod_bsl_volume_budget(int original) {
    if (!shaderlod_optimization_enabled(shaderlodOptAuroraBudget)) return original;
    return shaderlod_uniform_sample_budget(shaderlodBridgeVersion, shaderlodEnabled,
        shaderlodPreset, original, 4);
}
// The returned count preserves native per-effect sample domains, not radiance strength.
int shaderlod_bsl_count(int original, int minimum, float distanceBlocks, int flag) {
    if (!shaderlod_optimization_enabled(flag)) return original;
    return shaderlod_refinement_budget(shaderlodBridgeVersion, shaderlodEnabled,
        shaderlodPreset, distanceBlocks, original, minimum);
}
// Beer transmittance composition: T(coarse)=T(native)^(nativeCount/coarseCount).
// Keep native lighting evaluation on native density; change compositing alpha only.
float shaderlod_bsl_alpha(float alpha, float ratio) {
    if (ratio == 1.0) return alpha;
    return 1.0 - pow(clamp(1.0 - alpha, 0.0, 1.0), ratio);
}
// Existing MakeUp originating-distance fade, preserving near native weight.
float shaderlod_bsl_trace_weight(vec3 viewPos) {
    if (!shaderlod_optimization_enabled(shaderlodOptReflectionTraceFade) ||
        !(shaderlodNear > 0.0 && shaderlodNear < shaderlodFar && shaderlodFar <= 4096.0) ||
        !(dot(viewPos, viewPos) >= 0.0 && dot(viewPos, viewPos) < 1e30)) return 1.0;
    return 1.0 - smoothstep(shaderlodNear, shaderlodFar, length(viewPos));
}
#endif
#endif
