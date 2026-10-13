#ifndef SHADERLOD_REFINEMENT_ADAPTER_INCLUDED
#define SHADERLOD_REFINEMENT_ADAPTER_INCLUDED
#define SHADERLOD_REFINEMENT_ADAPTER 0 // [0 1]
#if SHADERLOD_REFINEMENT_ADAPTER == 1
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodPreset = -1;
uniform float shaderlodNear;
uniform float shaderlodFar;
uniform float shaderlodTransition;
#include "/include/shaderlod/optimization_flags.glsl"
uniform int shaderlodOptWaterParallaxBudget;
uniform int shaderlodOptWaterWaveBudget;
uniform int shaderlodOptZeroEmissionArithmetic;
uniform int shaderlodOptSsrProjectionArithmetic;
uniform int shaderlodOptReflectionDeferredMetric;
uniform int shaderlodOptSkyAtmosphereOwnership;
uniform int shaderlodOptTemporalShadowFilter;
uniform int shaderlodOptReflectionNextEventRefinement;
#include "/lib/shaderlod/refinement_budget.glsl"
#include "/lib/shaderlod/uniform_sample_budget.glsl"
int shaderlod_complementary_volume_budget(int original, int minimum) {
    if (!shaderlod_optimization_enabled(shaderlodOptVolumetricLightBudget)) return original;
    return shaderlod_uniform_sample_budget(shaderlodBridgeVersion, shaderlodEnabled,
        shaderlodPreset, original, minimum);
}
int shaderlod_reimagined_budget(int flag, int original, int minimum) {
    if (!shaderlod_optimization_enabled(flag)) return original;
    return shaderlod_uniform_sample_budget(shaderlodBridgeVersion, shaderlodEnabled,
        shaderlodPreset, original, minimum);
}
float shaderlod_reimagined_far_weight(int flag, float distanceBlocks) {
    if (!shaderlod_optimization_enabled(flag) ||
        !(shaderlodNear > 0.0 && shaderlodNear < shaderlodFar && shaderlodFar <= 4096.0 &&
          shaderlodTransition >= 0.0 && shaderlodTransition <= shaderlodFar - shaderlodNear &&
          distanceBlocks >= 0.0 && distanceBlocks <= 16777216.0)) return 1.0;
    if (shaderlodTransition == 0.0) return distanceBlocks < shaderlodFar ? 1.0 : 0.0;
    return 1.0 - smoothstep(shaderlodFar - 0.5 * shaderlodTransition,
        shaderlodFar + 0.5 * shaderlodTransition, distanceBlocks);
}
float shaderlod_reimagined_trace_weight(float distanceBlocks) {
    return shaderlod_reimagined_far_weight(shaderlodOptReflectionTraceFade, distanceBlocks);
}
// Native centered projection/inverse specialization, copied from fixed BSL arithmetic source contract.
bool shaderlod_reimagined_centered_projection() {
    mat4 p = gbufferProjection;
    mat4 q = gbufferProjectionInverse;
    return all(lessThanEqual(abs(p[0]), vec4(1e10))) && all(lessThanEqual(abs(p[1]), vec4(1e10))) &&
        all(lessThanEqual(abs(p[2]), vec4(1e10))) && all(lessThanEqual(abs(p[3]), vec4(1e10))) &&
        all(lessThanEqual(abs(q[0]), vec4(1e10))) && all(lessThanEqual(abs(q[1]), vec4(1e10))) &&
        all(lessThanEqual(abs(q[2]), vec4(1e10))) && all(lessThanEqual(abs(q[3]), vec4(1e10))) &&
        all(equal(p[0].yzw, vec3(0.0))) && all(equal(p[1].xzw, vec3(0.0))) &&
        all(equal(p[2].xy, vec2(0.0))) && p[2].w == -1.0 && all(equal(p[3].xyw, vec3(0.0))) &&
        all(equal(q[0].yzw, vec3(0.0))) && all(equal(q[1].xzw, vec3(0.0))) &&
        all(equal(q[2].xyz, vec3(0.0))) && all(equal(q[3].xy, vec2(0.0))) &&
        p[0].x != 0.0 && p[1].y != 0.0 && p[3].z != 0.0 &&
        q[0].x != 0.0 && q[1].y != 0.0 && q[2].w != 0.0 && q[3].z != 0.0;
}
bool shaderlod_reimagined_projected_refinement(vec3 position, vec3 nextPosition, float distanceBlocks) {
    if (!shaderlod_optimization_enabled(shaderlodOptReflectionNextEventRefinement) ||
        !(shaderlodNear > 0.0 && shaderlodNear < shaderlodFar && shaderlodFar <= 4096.0 &&
          distanceBlocks >= shaderlodNear && distanceBlocks <= 16777216.0) ||
        !all(lessThan(abs(position), vec3(1e8))) || !all(lessThan(abs(nextPosition), vec3(1e8)))) return false;
    vec4 a = gbufferProjection * vec4(position, 1.0);
    vec4 b = gbufferProjection * vec4(nextPosition, 1.0);
    if (!(a.w > 0.001 && b.w > 0.001 && a.w < 1e12 && b.w < 1e12) ||
        !(viewWidth >= 16.0 && viewWidth <= 32768.0 && viewHeight >= 16.0 && viewHeight <= 32768.0)) return false;
    vec2 pixels = (a.xy / a.w - b.xy / b.w) * 0.5 * vec2(viewWidth, viewHeight);
    return dot(pixels, pixels) < 0.0625;
}
#endif
#endif
