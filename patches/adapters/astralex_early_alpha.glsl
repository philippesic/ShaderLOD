// AstraLex93 owned early-alpha interface; original licenses retained.
uniform int shaderlodAstraEarlyAlphaPermission;
uniform float shaderlodAstraEarlyAlphaReference;
bool shaderlodAstraNativeAlphaReject(float finalAlpha) { return false; }
bool shaderlodAstraEarlyAlphaEnabled() {
 return shaderlod_optimization_enabled(shaderlodOptEarlyAlpha) && shaderlodAstraEarlyAlphaPermission == 1 && shaderlodAstraEarlyAlphaReference >= 0.0 && shaderlodAstraEarlyAlphaReference <= 1.0;
}
vec4 shaderlodAstraSample2D(sampler2D s,vec2 uv) {
 if (shaderlodAstraEarlyAlphaEnabled()) return textureLod(s,uv,0.0);
 return texture2D(s,uv);
}
vec4 shaderlodAstraDepth(sampler2DShadow s,vec3 p) {
 if (shaderlodAstraEarlyAlphaEnabled()) return shadow2DLod(s,p,0.0);
 return shadow2D(s,p);
}
