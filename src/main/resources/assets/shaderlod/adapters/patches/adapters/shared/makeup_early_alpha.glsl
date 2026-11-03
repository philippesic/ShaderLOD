// MakeUp9.5g exact terrain coverage rejection; LGPL-3.0-or-later upstream retained.
#if defined GBUFFER_TERRAIN && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH
#include "/lib/shaderlod/adapter.glsl"
#endif
// Singleton native path uses only the existing early-alpha transport.
#if SHADERLOD_REFINEMENT_ADAPTER == 0 && SHADERLOD_MAKEUP_EARLY_ALPHA == 1 && defined SHADERLOD_EARLY_ALPHA_RUNTIME_GUARD && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined MC_OS_MAC && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && SHADERLOD_MAKEUP_DEAD_FRAME_COPY == 0 && SHADERLOD_MAKEUP_AO_HALF_RES == 0 && SHADERLOD_MAKEUP_DEPTH_PRIMING == 0 && SHADERLOD_MAKEUP_SPATIAL_REUSE == 0 && SHADERLOD_MAKEUP_SPATIAL_RATE == 0 && SHADERLOD_MAKEUP_WORLD_PIXEL == 0 && defined GBUFFER_TERRAIN
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodPreset = -1;
uniform int shaderlodOptimizationFlagsVersion;
uniform int shaderlodOptEarlyAlpha;
bool shaderlod_optimization_enabled(int flag) {
    return shaderlodOptimizationFlagsVersion == 1 && flag == 1 &&
        shaderlodBridgeVersion == 2 && shaderlodEnabled == 1 &&
        shaderlodPreset >= 0 && shaderlodPreset <= 2;
}
#endif
#if (SHADERLOD_REFINEMENT_ADAPTER == 1 || (SHADERLOD_REFINEMENT_ADAPTER == 0 && SHADERLOD_MAKEUP_EARLY_ALPHA == 1 && defined SHADERLOD_EARLY_ALPHA_RUNTIME_GUARD && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined MC_OS_MAC && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && SHADERLOD_MAKEUP_DEAD_FRAME_COPY == 0 && SHADERLOD_MAKEUP_AO_HALF_RES == 0 && SHADERLOD_MAKEUP_DEPTH_PRIMING == 0 && SHADERLOD_MAKEUP_SPATIAL_REUSE == 0 && SHADERLOD_MAKEUP_SPATIAL_RATE == 0 && SHADERLOD_MAKEUP_WORLD_PIXEL == 0)) && defined GBUFFER_TERRAIN && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH
uniform int shaderlodMakeupEarlyAlphaPermission;
uniform float shaderlodMakeupEarlyAlphaReference;
bool shaderlodMakeupEarlyAlphaActive() {
#if SHADERLOD_MAKEUP_EARLY_ALPHA == 1
    return shaderlod_optimization_enabled(shaderlodOptEarlyAlpha) && shaderlodPreset != 2 && shaderlodMakeupEarlyAlphaPermission == 1;
#else
    return false;
#endif
}
// Prepared from exact native AlphaTest BEFORE native Iris pruning; late native test remains.
bool shaderlodMakeupNativeAlphaReject(float finalAlpha) { return false; }
#if defined SHADOW_CASTING
vec4 shaderlodMakeupShadow1(vec3 p) { if (shaderlodMakeupEarlyAlphaActive()) return shadow2DLod(shadowtex1,p,0.0); return shadow2D(shadowtex1,p); }
#if defined COLORED_SHADOW
vec4 shaderlodMakeupShadow0(vec3 p) { if (shaderlodMakeupEarlyAlphaActive()) return shadow2DLod(shadowtex0,p,0.0); return shadow2D(shadowtex0,p); }
vec4 shaderlodMakeupShadowColor0(vec2 p) { if (shaderlodMakeupEarlyAlphaActive()) return texture2DLod(shadowcolor0,p,0.0); return texture2D(shadowcolor0,p); }
#endif
#endif
vec4 shaderlodMakeupFog(vec2 p) { if (shaderlodMakeupEarlyAlphaActive()) return texture2DLod(gaux4,p,0.0); return texture2D(gaux4,p); }
#else
#define shaderlodMakeupShadow1(p) shadow2D(shadowtex1,p)
#define shaderlodMakeupShadow0(p) shadow2D(shadowtex0,p)
#define shaderlodMakeupShadowColor0(p) texture2D(shadowcolor0,p)
#define shaderlodMakeupFog(p) texture2D(gaux4,p)
#endif
