// BSL v10.1.8 owned early-alpha interface; original Capt Tatsu licensing retained.
// Native atlas alpha is preserved to output in this exact no-PBR terrain path.
uniform int shaderlodBslEarlyAlphaPermission;
uniform float shaderlodBslEarlyAlphaReference;
bool shaderlodBslNativeAlphaReject(float finalAlpha) { return false; }
bool shaderlodBslEarlyAlphaEnabled() {
#if defined IS_IRIS && defined MC_VERSION && defined IRIS_VERSION && ((MC_VERSION == 12101 && (IRIS_VERSION == 10808 || IRIS_VERSION == 10812)) || (MC_VERSION == 12111 && IRIS_VERSION == 11008) || (MC_VERSION == 260102 && IRIS_VERSION == 11104) || (MC_VERSION == 260200 && IRIS_VERSION == 11104) || (MC_VERSION == 260300 && IRIS_VERSION == 11107))
 return shaderlod_optimization_enabled(shaderlodOptEarlyAlpha) && shaderlodBslEarlyAlphaPermission == 1 && shaderlodBslEarlyAlphaReference >= 0.0 && shaderlodBslEarlyAlphaReference <= 1.0;
#else
 return false;
#endif
}
