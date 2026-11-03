// Sildur2.02 exact native nMap0 alpha is preserved to final output.
uniform int shaderlodSildurEarlyAlphaPermission;
uniform float shaderlodSildurEarlyAlphaReference;
bool shaderlodSildurNativeAlphaReject(float finalAlpha) { return false; }
bool shaderlodSildurEarlyAlphaEnabled() {
 return shaderlod_optimization_enabled(shaderlodOptEarlyAlpha) && shaderlodSildurEarlyAlphaPermission == 1
  && shaderlodSildurEarlyAlphaReference >= 0.0 && shaderlodSildurEarlyAlphaReference <= 1.0;
}
