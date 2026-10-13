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
#include "/lib/shaderlod/refinement_budget.glsl"
#include "/lib/shaderlod/uniform_sample_budget.glsl"
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
// Reuse core SSR distance fade: originating camera-relative position and native fallback.
float shaderlod_makeup_trace_weight(vec3 fragpos) {
    if (!shaderlod_optimization_enabled(shaderlodOptReflectionTraceFade) ||
        !(shaderlodNear > 0.0 && shaderlodNear < shaderlodFar && shaderlodFar <= 4096.0) ||
        !(dot(fragpos, fragpos) >= 0.0 && dot(fragpos, fragpos) < 1.0e30)) return 1.0;
    return 1.0 - smoothstep(shaderlodNear, shaderlodFar, length(fragpos));
}
#endif
#endif
