/* ShaderLOD G05 projected zero-weight water miss, MakeUp Ultra Fast 9.5g.
 * Native trace remains in water.glsl. This helper does not shorten visible rays.
 * Copyright 2026 ShaderLOD contributors; LGPL-3.0-or-later, as derived MakeUp work.
 */
#if SHADERLOD_G05_PROJECTED_MISS == 1 && defined IS_IRIS && MC_VERSION == 12101 && IRIS_VERSION == 10812 && defined GBUFFER_WATER && defined WATER_F && REFLECTION == 1 && SSR_TYPE == 1 && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && RAYMARCH_STEPS == 10
#define SHADERLOD_G05_ACTIVE
#if SHADERLOD_G05_DEBUG == 1
float shaderlodG05SavedSteps = 0.0;
#endif

// Admit the native symmetric perspective matrix only. Native projection is used
// for every executed step; these coefficients are only a rejection certificate.
bool shaderlodG05Projection() {
    return all(equal(gbufferProjection[0].yzw, vec3(0.0))) &&
        all(equal(gbufferProjection[1].xzw, vec3(0.0))) &&
        all(equal(gbufferProjection[2].xy, vec2(0.0))) && gbufferProjection[2].w == -1.0 &&
        all(equal(gbufferProjection[3].xyw, vec3(0.0))) &&
        all(lessThan(abs(gbufferProjection[0]), vec4(1.0e4))) &&
        all(lessThan(abs(gbufferProjection[1]), vec4(1.0e4))) &&
        all(lessThan(abs(gbufferProjection[2]), vec4(1.0e4))) &&
        all(lessThan(abs(gbufferProjection[3]), vec4(1.0e4))) &&
        gbufferProjection[1].y > 1.0e-4;
}

// Before the next coarse step, all later coarse parameters increase. If that
// step starts refinement, sum(halved increments) is less than its coarse span:
// every subsequent point remains forward of the present coarse point. With
// w=-z increasing and (clip.y-w) increasing, every future UV.y
// stays above 1. Native outOfEyeFlag is sticky and native border is zero.
// Bottom exits stay native: pow(negative UV.y, 10) has undefined GLSL semantics.
// The envelope deliberately dwarfs float32 error over ten native recurrences
// (t <= 1023); ambiguous slopes, eye crossings and nonfinite inputs stay native.
bool shaderlodG05ZeroTail(vec3 point, vec3 direction, vec3 errorBound) {
    float py = gbufferProjection[1].y;
    float edgeError = py * errorBound.y + errorBound.z;
    float slopeError = 1.0e-5 * (py * abs(direction.y) + abs(direction.z) + 1.0);
    return point.z < -errorBound.z && direction.z <= 0.0 &&
        (py * point.y + point.z > edgeError && py * direction.y + direction.z > slopeError);
}

// GENERATED_NATIVE_TRACE
#endif
