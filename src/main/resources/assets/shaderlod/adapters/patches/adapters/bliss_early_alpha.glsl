// Bliss2.1.2 owned early-alpha interface; original licenses retained.
uniform int shaderlodBlissEarlyAlphaPermission;
uniform float shaderlodBlissEarlyAlphaReference;
bool shaderlodBlissNativeAlphaReject(float finalAlpha) { return false; }
bool shaderlodBlissEarlyAlphaEnabled() {
 return shaderlod_optimization_enabled(shaderlodOptEarlyAlpha) && shaderlodBlissEarlyAlphaPermission == 1 && shaderlodBlissEarlyAlphaReference >= 0.0 && shaderlodBlissEarlyAlphaReference <= 1.0;
}
