#ifndef SHADERLOD_REFINEMENT_ADAPTER_INCLUDED
#define SHADERLOD_REFINEMENT_ADAPTER_INCLUDED
// MakeUp UltraFast9.5g private adapter; LGPL-3.0-or-later, upstream notices retained.
#define SHADERLOD_REFINEMENT_ADAPTER 0 // [0 1]
#if SHADERLOD_REFINEMENT_ADAPTER == 1
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodPreset = -1;
uniform float shaderlodNear;
uniform float shaderlodFar;
uniform float shaderlodTransition;
#include "/include/shaderlod/optimization_flags.glsl"
// Additive source-backed controls beyond frozen shared38; same validated v1 transport.
uniform int shaderlodOptWaterWaveBudget;
uniform int shaderlodOptWaterShadowZero;
uniform int shaderlodOptTemporalShadowFilter;
uniform int shaderlodOptShadowReceiverFootprint;
uniform int shaderlodOptSsrProjectionArithmetic;
uniform int shaderlodOptSsrProjectedFootprintExit;
uniform int shaderlodOptAoSkyElision;
// Exact sampler/filter/comparison/nonmip permission is a pending normal producer join.
uniform int shaderlodMakeupShadowSamplerPermission;
// Independent permission: colored budget never authorizes shared shadow consumers.
uniform int shaderlodMakeupColoredShadowBudgetPermission = 0;
// Historical distance-exit category: MakeUp uses exact native angular cloud support.
uniform int shaderlodOptReflectedCloudDistanceExit;
uniform int shaderlodMakeupCloudSamplerPermission;
// Exact immutable depthtex1 query certificate, qualified at each owned water draw.
uniform int shaderlodMakeupReflectionDepthPermission;
uniform int shaderlodOptReflectionNextEventRefinement;
bool shaderlod_makeup_cloud_support_enabled() {
#if defined WATER_F && !defined THE_END && !defined NETHER && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && V_CLOUDS != 0
    return shaderlod_optimization_enabled(shaderlodOptReflectedCloudDistanceExit) && shaderlodMakeupCloudSamplerPermission == 1;
#else
    return false;
#endif
}
int shaderlod_makeup_min(int a, int b) { return a < b ? a : b; }
int shaderlod_makeup_max(int a, int b) { return a > b ? a : b; }
#define min shaderlod_makeup_min
#define max shaderlod_makeup_max
#include "/lib/shaderlod/refinement_budget.glsl"
#include "/lib/shaderlod/uniform_sample_budget.glsl"
#undef min
#undef max
int shaderlod_makeup_volume_budget(int original) {
    return shaderlod_uniform_sample_budget(shaderlodBridgeVersion, shaderlodEnabled,
        shaderlodPreset, original, 4);
}
bool shaderlod_makeup_opacity_enabled() {
    return shaderlod_optimization_enabled(shaderlodOptCloudOpacityExit);
}
bool shaderlod_makeup_cloud_detail_enabled() {
    #if defined DEFERRED_SHADER
        return shaderlod_optimization_enabled(shaderlodOptCloudPrimaryDetail);
    #elif defined WATER_F
        return shaderlod_optimization_enabled(shaderlodOptReflectionCloudDetail);
    #else
        return false;
    #endif
}
int shaderlod_makeup_cloud_budget(int original) {
    #if defined DEFERRED_SHADER
        if (!shaderlod_optimization_enabled(shaderlodOptCloudPrimaryBudget)) return original;
    #elif defined WATER_F
        if (!shaderlod_optimization_enabled(shaderlodOptReflectionCloudBudget)) return original;
    #else
        return original;
    #endif
    return shaderlod_makeup_volume_budget(original);
}
int shaderlod_makeup_light_budget(int original, int minimum) {
    if (!shaderlod_optimization_enabled(shaderlodOptVolumetricLightBudget)) return original;
    return shaderlod_uniform_sample_budget(shaderlodBridgeVersion, shaderlodEnabled,
        shaderlodPreset, original, minimum);
}
float shaderlod_receiver_reduction_weight_r1(bool eligible,
    vec2 shadowUvDx, vec2 shadowUvDy, vec2 nativeUvHalfExtent,
    float outputPixelsPerRasterPixel) {
    if (!eligible) return 0.0;
    // Ordered comparisons also reject NaN/infinity on legacy GLSL, without
    // depending on a loader's version upgrade for isnan/isinf built-ins.
    if (!all(lessThanEqual(abs(shadowUvDx), vec2(100.0))) ||
        !all(lessThanEqual(abs(shadowUvDy), vec2(100.0))) ||
        !all(greaterThan(nativeUvHalfExtent, vec2(0.0))) ||
        !all(lessThanEqual(nativeUvHalfExtent, vec2(1.0))) ||
        !(outputPixelsPerRasterPixel >= 1.0 && outputPixelsPerRasterPixel <= 16.0))
        return 0.0;

    float determinant = shadowUvDx.x * shadowUvDy.y - shadowUvDy.x * shadowUvDx.y;
    float largestColumn2 = max(dot(shadowUvDx, shadowUvDx), dot(shadowUvDy, shadowUvDy));
    // Relative conditioning refusal; do not turn a grazing/singular receiver
    // into a large inverse or assume it has negligible projected support.
    if (!(largestColumn2 > 1e-20 && largestColumn2 < 1e4 &&
        abs(determinant) > 0.01 * largestColumn2)) return 0.0;

    vec2 outputHalfExtent = vec2(
        dot(abs(vec2(shadowUvDy.y, shadowUvDy.x)), nativeUvHalfExtent),
        dot(abs(vec2(shadowUvDx.y, shadowUvDx.x)), nativeUvHalfExtent)
    ) * (outputPixelsPerRasterPixel / abs(determinant));
    float supportRadius2 = dot(outputHalfExtent, outputHalfExtent);
    if (!(supportRadius2 >= 0.0 && supportRadius2 < 0.25)) return 0.0;
    // Fixed, unsearched hypothesis: full reduction <= 0.25 output pixel;
    // smoothly restore the native quadrature by 0.5 output pixel. Interpolation
    // is in squared radius to avoid a square root. These are not quality claims.
    return 1.0 - smoothstep(0.0625, 0.25, supportRadius2);
}


// Reuse core SSR distance fade: originating camera-relative position and native fallback.
float shaderlod_makeup_trace_weight(vec3 fragpos) {
    if (!shaderlod_optimization_enabled(shaderlodOptReflectionTraceFade) ||
        !(shaderlodNear > 0.0 && shaderlodNear < shaderlodFar && shaderlodFar <= 4096.0) ||
        !(dot(fragpos, fragpos) >= 0.0 && dot(fragpos, fragpos) < 1.0e30)) return 1.0;
    return 1.0 - smoothstep(shaderlodNear, shaderlodFar, length(fragpos));
}
#endif
#endif
