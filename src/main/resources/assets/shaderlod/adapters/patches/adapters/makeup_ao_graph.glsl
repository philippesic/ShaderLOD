// MakeUp UltraFast9.5g derivative, LGPL-3.0-or-later; native notices retained.
// Dedicated AO only; native lighting/cloud/color buffers remain full size.
#include "/lib/shaderlod/adapter.glsl"
#if SHADERLOD_REFINEMENT_ADAPTER == 1
uniform int shaderlodOptAoRenderScale;
uniform int shaderlodMakeupAoTargetReady;
uniform vec2 shaderlodMakeupAoTargetSize;
#endif
bool shaderlodMakeupAoGraphActive(vec2 fullSize) {
    #if SHADERLOD_MAKEUP_AO_HALF_RES == 1 && SHADERLOD_REFINEMENT_ADAPTER == 1 && AO == 1 && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && !defined UNKNOWN_DIM
    return shaderlod_optimization_enabled(shaderlodOptAoRenderScale) && shaderlodMakeupAoTargetReady == 1 &&
        all(equal(shaderlodMakeupAoTargetSize, fullSize)) &&
        all(greaterThanEqual(fullSize, vec2(2.0))) && all(lessThanEqual(fullSize, vec2(16384.0))) &&
        shaderlodNear > 0.0 && shaderlodNear < shaderlodFar && shaderlodFar <= 4096.0 &&
        frameCounter >= 0 && frameCounter < 16777216 &&
        // Camera-Z inversion requires z/w rows independent of x/y.
        // Off-axis x/y lens offsets remain supported; oblique/deformed depth is native.
        gbufferProjection[0].z == 0.0 && gbufferProjection[1].z == 0.0 &&
        gbufferProjection[0].w == 0.0 && gbufferProjection[1].w == 0.0 &&
        gbufferProjection[2].w == -1.0 && gbufferProjection[3].w == 0.0;
    #else
    return false;
    #endif
}
// Native MakeUp water linearizeDepth projection identity supplies camera-Z blocks.
// Its AO ld()*far is a different native kernel measure, retained in field.g.
float shaderlodMakeupAoEyeDepth(float z, float projection22, float projection32, float projection2w, float projection3w) {
    float denominator = projection22 + 2.0 * z - 1.0;
    if (!(projection2w == -1.0 && projection3w == 0.0 &&
        abs(denominator) > 1e-10 && abs(denominator) < 1e10 && abs(projection32) < 1e10)) return -1.0;
    float result = projection32 / denominator;
    return result >= 0.0 && result < 1e6 ? result : -1.0;
}
