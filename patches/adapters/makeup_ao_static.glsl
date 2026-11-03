#if !defined(SHADERLOD_MAKEUP_STATIC_AO_INCLUDED)
#define SHADERLOD_MAKEUP_STATIC_AO_INCLUDED
// MakeUp UltraFast 9.5g; LGPL-3.0-or-later. Based on its native dbao kernel
// (old Capt Tatsu ambient occlusion). Only the existing four-of-five budget
// is specialized. Native five-sample work consumes identical prepared inputs.
#if SHADERLOD_REFINEMENT_ADAPTER == 1 && AOSTEPS == 5 && defined SHADERLOD_MAKEUP_AO_HALF_RES && SHADERLOD_MAKEUP_AO_HALF_RES == 0 && defined DEFERRED_SHADER && !defined THE_END && !defined NETHER && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH
#define SHADERLOD_MAKEUP_STATIC_AO_5 1
// The caller supplies the unchanged native center-depth/hand/scale preparation.
// scaleFactor remains scale / AOSTEPS (five), never scale / four.
float shaderlodMakeupStaticAo4(float dither, float linearDepth,
        float farAndCheck, vec2 scaleFactor) {
    float ambientOcclusion = 0.0;
    vec2 offset;
    float n;
    float ditherSample;
    float sampleLinearDepth = 0.0;
    float angle = 0.0;
    float dist = 0.0;
    float checkDepth;

    for (int i = 0; i < 4; i++) {
        ditherSample = float(i) * float(AOSTEPS - 1) / float(4 - 1) + dither;
        n = fract(ditherSample * 1.6180339887) * 3.141592653589793;
        offset = vec2(cos(n), sin(n)) * ditherSample * scaleFactor;

        sampleLinearDepth = ld(texture2DLod(depthtex0, texcoord.xy + offset, 0.0).r);
        checkDepth = (linearDepth - sampleLinearDepth) * farAndCheck;
        angle = clamp(0.5 - checkDepth, 0.0, 1.0);
        dist = clamp(0.25 * checkDepth - 1.0, 0.0, 1.0);

        sampleLinearDepth = ld(texture2DLod(depthtex0, texcoord.xy - offset, 0.0).r);
        checkDepth = (linearDepth - sampleLinearDepth) * farAndCheck;
        angle += clamp(0.5 - checkDepth, 0.0, 1.0);
        dist += clamp(0.25 * checkDepth - 1.0, 0.0, 1.0);

        ambientOcclusion += clamp(angle + dist, 0.0, 1.0);
    }
    ambientOcclusion /= 4;

    return sqrt((ambientOcclusion * AO_STRENGTH) + (1.0 - AO_STRENGTH));
}

// Native five-sample math and positive/negative tap order are unchanged.
// Reuse dbao's identical preparation; do not fetch the center depth again.
float shaderlodMakeupNativeAo5Prepared(float dither, float linearDepth,
        float farAndCheck, vec2 scaleFactor) {
    float ambientOcclusion = 0.0;

    vec2 offset;
    float n;
    float ditherSample;

    float sampleLinearDepth = 0.0;
    float angle = 0.0;
    float dist = 0.0;
    float checkDepth;

    for (int i = 0; i < AOSTEPS; i++) {
        ditherSample = (i + dither);
        n = fract(ditherSample * 1.6180339887) * 3.141592653589793;
        offset = vec2(cos(n), sin(n)) * ditherSample * scaleFactor;

        sampleLinearDepth = ld(texture2DLod(depthtex0, texcoord.xy + offset, 0.0).r);
        checkDepth = (linearDepth - sampleLinearDepth) * farAndCheck;
        angle = clamp(0.5 - checkDepth, 0.0, 1.0);
        dist = clamp(0.25 * checkDepth - 1.0, 0.0, 1.0);

        sampleLinearDepth = ld(texture2DLod(depthtex0, texcoord.xy - offset, 0.0).r);
        checkDepth = (linearDepth - sampleLinearDepth) * farAndCheck;
        angle += clamp(0.5 - checkDepth, 0.0, 1.0);
        dist += clamp(0.25 * checkDepth - 1.0, 0.0, 1.0);

        ambientOcclusion += clamp(angle + dist, 0.0, 1.0);
    }
    ambientOcclusion /= AOSTEPS;

    return sqrt((ambientOcclusion * AO_STRENGTH) + (1.0 - AO_STRENGTH));
}
#endif
#endif
