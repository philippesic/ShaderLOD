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
uniform int shaderlodOptZeroEmissionArithmetic;
uniform int shaderlodOptWaterParallaxBudget;
uniform int shaderlodOptWaterWaveBudget;
uniform int shaderlodOptSkyAtmosphereOwnership;
uniform int shaderlodOptReflectionDeferredMetric;
uniform int shaderlodOptSsrProjectionArithmetic;
uniform int shaderlodUnboundExactDepthPermission;
uniform int shaderlodOptSsrProjectedFootprintExit;
uniform int shaderlodOptReflectionNextEventRefinement;
// Real depth sampler/ownership qualification supplied only by the normal resource owner.
// Missing/unbound uniform remains0 and uses the exact native implicit calls.
uniform int shaderlodUnboundAODepthSamplingQualified;

#include "/lib/shaderlod/refinement_budget.glsl"
#include "/lib/shaderlod/uniform_sample_budget.glsl"
bool shaderlodUnboundProjectionShape() {
    mat4 p = gbufferProjection;
    mat4 q = gbufferProjectionInverse;
    return all(lessThanEqual(abs(p[0]), vec4(1e10))) && all(lessThanEqual(abs(p[1]), vec4(1e10)))
        && all(lessThanEqual(abs(p[2]), vec4(1e10))) && all(lessThanEqual(abs(p[3]), vec4(1e10)))
        && all(lessThanEqual(abs(q[0]), vec4(1e10))) && all(lessThanEqual(abs(q[1]), vec4(1e10)))
        && all(lessThanEqual(abs(q[2]), vec4(1e10))) && all(lessThanEqual(abs(q[3]), vec4(1e10)))
        && all(equal(p[0].yzw, vec3(0.0))) && all(equal(p[1].xzw, vec3(0.0)))
        && all(equal(p[2].xy, vec2(0.0))) && p[2].w == -1.0 && all(equal(p[3].xyw, vec3(0.0)))
        && all(equal(q[0].yzw, vec3(0.0))) && all(equal(q[1].xzw, vec3(0.0)))
        && all(equal(q[2].xyz, vec3(0.0))) && all(equal(q[3].xy, vec2(0.0)))
        && p[0].x != 0.0 && p[1].y != 0.0 && p[3].z != 0.0
        && q[0].x != 0.0 && q[1].y != 0.0 && q[2].w != 0.0 && q[3].z != 0.0;
}

// Photon native stochastic ray rejection, adapted to Unbound's existing dither.
// Only the calling ordinary-water/native SSR graph may use this predicate.
bool shaderlod_unbound_skip_reflection_trace(float distanceBlocks, float dither) {
    if (!shaderlod_optimization_enabled(shaderlodOptReflectionTraceFade) ||
        !(shaderlodNear > 0.0 && shaderlodFar > shaderlodNear && shaderlodFar <= 4096.0 &&
          shaderlodTransition >= 0.0 && shaderlodTransition <= shaderlodFar - shaderlodNear &&
          shaderlodTransition * 0.5 <= shaderlodNear &&
          distanceBlocks >= 0.0 && distanceBlocks <= 16777216.0 && dither >= 0.0 && dither < 1.0)) return false;
    return dither < shaderlod_refinement_blend(distanceBlocks, shaderlodFar);
}

float shaderlod_unbound_water_wave_weight(int flag, float distanceBlocks) {
    if (!shaderlod_optimization_enabled(flag) ||
        !(shaderlodNear > 0.0 && shaderlodNear < shaderlodFar && shaderlodFar <= 4096.0 &&
          shaderlodTransition >= 0.0 && shaderlodTransition <= shaderlodFar - shaderlodNear &&
          distanceBlocks >= 0.0 && distanceBlocks <= 16777216.0)) return 1.0;
    if (shaderlodTransition == 0.0) return distanceBlocks < shaderlodFar ? 1.0 : 0.0;
    return 1.0 - smoothstep(shaderlodFar - 0.5 * shaderlodTransition,
        shaderlodFar + 0.5 * shaderlodTransition, distanceBlocks);
}

int shaderlod_complementary_volume_budget(int original, int minimum) {
    if (!shaderlod_optimization_enabled(shaderlodOptVolumetricLightBudget)) return original;
    return shaderlod_uniform_sample_budget(shaderlodBridgeVersion, shaderlodEnabled,
        shaderlodPreset, original, minimum);
}
#endif
#endif
