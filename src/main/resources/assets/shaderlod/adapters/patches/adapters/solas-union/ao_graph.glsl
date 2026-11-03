#include "/lib/shaderlod/adapter.glsl"
uniform int shaderlodOptAoRenderScale;
uniform int shaderlodSolasAoTargetReady;
uniform vec2 shaderlodSolasAoTargetSize;
bool shaderlodSolasAoGraphActive() {
#if SHADERLOD_SOLAS_AO_HALF_RES == 1 && SHADERLOD_REFINEMENT_ADAPTER == 1 && defined IS_IRIS && MC_VERSION == 12101 && IRIS_VERSION == 10812 && defined OVERWORLD && defined SSAO && !defined DISTANT_HORIZONS && !defined VOXY
    return shaderlod_optimization_enabled(shaderlodOptAoRenderScale) && shaderlodSolasAoTargetReady == 1 &&
        all(equal(shaderlodSolasAoTargetSize,vec2(viewWidth,viewHeight))) &&
        viewWidth >= 2.0 && viewHeight >= 2.0 && viewWidth <= 16384.0 && viewHeight <= 16384.0 && frameCounter >= 0 && frameCounter < 16777216;
#else
    return false;
#endif
}
