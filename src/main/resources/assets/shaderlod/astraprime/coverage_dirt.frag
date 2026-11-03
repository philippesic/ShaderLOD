#version 330 core
// AstraLex93.0 by LexBoosT; local same-frame native SOLID dirt subset.
// Raw mc_Entity10003 is exclusively dirt in pinned block.properties; native colour follows.
invariant flat in int shaderlodAstraDepthMaterial;
invariant in vec2 texCoord;
uniform sampler2D gtexture;
void main() {
    // Match native implicit footprint before any divergent material/alpha branch.
    float nativeAlpha = texture(gtexture, texCoord).a;
    if (!(nativeAlpha > 0.5)) discard;
    if (shaderlodAstraDepthMaterial != 10003) discard;
}
