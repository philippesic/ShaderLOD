/* MakeUp - ambientOcclusion.glsl
Based on old Capt Tatsu's ambient occlusion functions.

*/

#include "/lib/shaderlod/adapter.glsl"

float shaderlodMakeupNativeAoAt(vec2 shaderlodMakeupNativeAoUv, float dither) {
    float ambientOcclusion = 0.0;

    float stepsInverse = 1.0 / AOSTEPS;
    vec2 offset;
    float n;
    float ditherSample;

    float linearDepth = texture2DLod(depthtex0, shaderlodMakeupNativeAoUv, 0.0).r;
    float handCheck = linearDepth < 0.56 ? 1024.0 : 1.0;
    linearDepth = ld(linearDepth);

    float sampleLinearDepth = 0.0;
    float angle = 0.0;
    float dist = 0.0;
    float farAndCheck = handCheck * 2.0 * far;
    vec2 scale = vec2(aspectRatioInverse, 1.0) * (fovYInverse / (linearDepth * far));
    vec2 scaleFactor = scale * stepsInverse;
    float checkDepth;

    for (int i = 0; i < AOSTEPS; i++) {
        ditherSample = (i + dither);
        n = fract(ditherSample * 1.6180339887) * 3.141592653589793;
        offset = vec2(cos(n), sin(n)) * ditherSample * scaleFactor;

        sampleLinearDepth = ld(texture2DLod(depthtex0, shaderlodMakeupNativeAoUv + offset, 0.0).r);
        checkDepth = (linearDepth - sampleLinearDepth) * farAndCheck;
        angle = clamp(0.5 - checkDepth, 0.0, 1.0);
        dist = clamp(0.25 * checkDepth - 1.0, 0.0, 1.0);

        sampleLinearDepth = ld(texture2DLod(depthtex0, shaderlodMakeupNativeAoUv - offset, 0.0).r);
        checkDepth = (linearDepth - sampleLinearDepth) * farAndCheck;
        angle += clamp(0.5 - checkDepth, 0.0, 1.0);
        dist += clamp(0.25 * checkDepth - 1.0, 0.0, 1.0);

        ambientOcclusion += clamp(angle + dist, 0.0, 1.0);
    }
    ambientOcclusion /= AOSTEPS;

    return sqrt((ambientOcclusion * AO_STRENGTH) + (1.0 - AO_STRENGTH));
}

float shaderlodMakeupAoKernel(vec2 shaderlodMakeupNativeAoUv, float dither) {
    #if SHADERLOD_REFINEMENT_ADAPTER == 1 && !defined NETHER && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && defined DEFERRED_SHADER
    if (!shaderlod_optimization_enabled(shaderlodOptAoBudget) && !shaderlod_optimization_enabled(shaderlodOptAoDepthReuse))
        return shaderlodMakeupNativeAoAt(shaderlodMakeupNativeAoUv, dither);
    #else
    return shaderlodMakeupNativeAoAt(shaderlodMakeupNativeAoUv, dither);
    #endif

    float ambientOcclusion = 0.0;

    float stepsInverse = 1.0 / AOSTEPS;
    vec2 offset;
    float n;
    float ditherSample;

    float linearDepth = texture2DLod(depthtex0, shaderlodMakeupNativeAoUv, 0.0).r;
    float handCheck = linearDepth < 0.56 ? 1024.0 : 1.0;
    linearDepth = ld(linearDepth);

    float sampleLinearDepth = 0.0;
    float angle = 0.0;
    float dist = 0.0;
    float farAndCheck = handCheck * 2.0 * far;
    vec2 scale = vec2(aspectRatioInverse, 1.0) * (fovYInverse / (linearDepth * far));
    vec2 scaleFactor = scale * stepsInverse;
    float checkDepth;

    bool shaderlodAoDepthCache = false;
    vec2 shaderlodAoCachedCoord = vec2(0.0);
    float shaderlodAoCachedDepth = 0.0;
    #if SHADERLOD_REFINEMENT_ADAPTER == 1 && !defined NETHER && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && defined DEFERRED_SHADER
    shaderlodAoDepthCache = shaderlod_optimization_enabled(shaderlodOptAoDepthReuse);
    #endif
    bool shaderlodAoCacheValid = false;
    int shaderlodAoSamples = AOSTEPS;
    #if SHADERLOD_REFINEMENT_ADAPTER == 1 && !defined NETHER && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && defined DEFERRED_SHADER
    if (handCheck == 1.0 && shaderlod_optimization_enabled(shaderlodOptAoBudget))
        shaderlodAoSamples = shaderlod_refinement_budget(shaderlodBridgeVersion, shaderlodEnabled,
            shaderlodPreset, linearDepth * far, AOSTEPS, 2);
    #endif
    for (int i = 0; i < shaderlodAoSamples; i++) {
        ditherSample = (i + dither);
        if (shaderlodAoSamples < AOSTEPS)
            ditherSample = float(i) * float(AOSTEPS - 1) / float(shaderlodAoSamples - 1) + dither;
        n = fract(ditherSample * 1.6180339887) * 3.141592653589793;
        offset = vec2(cos(n), sin(n)) * ditherSample * scaleFactor;

        {
            vec2 shaderlodAoCoord = shaderlodMakeupNativeAoUv + offset;
            bool shaderlodAoCoordValid = all(greaterThanEqual(shaderlodAoCoord, vec2(0.0))) && all(lessThanEqual(shaderlodAoCoord, vec2(1.0)));
            float shaderlodAoDepth;
            if (shaderlodAoDepthCache && shaderlodAoCacheValid && shaderlodAoCoordValid && all(equal(shaderlodAoCoord, shaderlodAoCachedCoord)))
                shaderlodAoDepth = shaderlodAoCachedDepth;
            else
                shaderlodAoDepth = texture2DLod(depthtex0, shaderlodAoCoord, 0.0).r;
            shaderlodAoCachedCoord = shaderlodAoCoord;
            shaderlodAoCachedDepth = shaderlodAoDepth;
            shaderlodAoCacheValid = shaderlodAoCoordValid;
            sampleLinearDepth = ld(shaderlodAoDepth);
        }
        checkDepth = (linearDepth - sampleLinearDepth) * farAndCheck;
        angle = clamp(0.5 - checkDepth, 0.0, 1.0);
        dist = clamp(0.25 * checkDepth - 1.0, 0.0, 1.0);

        {
            vec2 shaderlodAoCoord = shaderlodMakeupNativeAoUv - offset;
            bool shaderlodAoCoordValid = all(greaterThanEqual(shaderlodAoCoord, vec2(0.0))) && all(lessThanEqual(shaderlodAoCoord, vec2(1.0)));
            float shaderlodAoDepth;
            if (shaderlodAoDepthCache && shaderlodAoCacheValid && shaderlodAoCoordValid && all(equal(shaderlodAoCoord, shaderlodAoCachedCoord)))
                shaderlodAoDepth = shaderlodAoCachedDepth;
            else
                shaderlodAoDepth = texture2DLod(depthtex0, shaderlodAoCoord, 0.0).r;
            shaderlodAoCachedCoord = shaderlodAoCoord;
            shaderlodAoCachedDepth = shaderlodAoDepth;
            shaderlodAoCacheValid = shaderlodAoCoordValid;
            sampleLinearDepth = ld(shaderlodAoDepth);
        }
        checkDepth = (linearDepth - sampleLinearDepth) * farAndCheck;
        angle += clamp(0.5 - checkDepth, 0.0, 1.0);
        dist += clamp(0.25 * checkDepth - 1.0, 0.0, 1.0);

        ambientOcclusion += clamp(angle + dist, 0.0, 1.0);
    }
    ambientOcclusion /= shaderlodAoSamples;

    return sqrt((ambientOcclusion * AO_STRENGTH) + (1.0 - AO_STRENGTH));
}
