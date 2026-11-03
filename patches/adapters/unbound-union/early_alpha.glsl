// Complementary Unbound r5.9.3, EminGT; upstream license/ancestry retained.
// Native material alpha can be resampled at LOD0 after the initial atlas0.5 discard.
#include "/lib/shaderlod/adapter.glsl"
uniform int shaderlodUnboundEarlyAlphaPermission;
uniform float shaderlodUnboundEarlyAlphaReference;
bool shaderlodUnboundNativeAlphaReject(float finalAlpha){return false;}
bool shaderlodUnboundEarlyAlphaEnabled(){
#if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined GBUFFERS_TERRAIN && defined FRAGMENT_SHADER && defined OVERWORLD && defined IS_IRIS && ((MC_VERSION == 12101 && (IRIS_VERSION == 10808 || IRIS_VERSION == 10812)) || (MC_VERSION == 12111 && IRIS_VERSION == 11008 && SHADERLOD_UNBOUND_DEPTH_PRIMING == 0) || ((MC_VERSION == 260102 || MC_VERSION == 260200) && IRIS_VERSION == 11104 && SHADERLOD_UNBOUND_DEPTH_PRIMING == 0)) && RP_MODE == 1 && ANISOTROPIC_FILTER == 0 && !defined CUSTOM_PBR && !defined GENERATED_NORMALS && !defined COATED_TEXTURES && !defined CLOUD_SHADOWS && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && COLORED_LIGHTING_INTERNAL == 0 && !defined GBUFFERS_COLORWHEEL
 return shaderlod_optimization_enabled(shaderlodOptEarlyAlpha) && shaderlodUnboundEarlyAlphaPermission==1 && shaderlodUnboundEarlyAlphaReference>=0.0 && shaderlodUnboundEarlyAlphaReference<=1.0;
#else
 return false;
#endif
}
