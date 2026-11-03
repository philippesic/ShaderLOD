// Native final alpha unchanged by gbuffersLighting; all prior material/floodfill queries preserved.
uniform int shaderlodSolasEarlyAlphaPermission;
uniform float shaderlodSolasEarlyAlphaReference;
bool shaderlodSolasNativeAlphaReject(float finalAlpha) { return false; }
bool shaderlodSolasEarlyAlphaEnabled() {
 return shaderlod_optimization_enabled(shaderlodOptEarlyAlpha) && shaderlodSolasEarlyAlphaPermission == 1
  && shaderlodSolasEarlyAlphaReference >= 0.0 && shaderlodSolasEarlyAlphaReference <= 1.0;
}
