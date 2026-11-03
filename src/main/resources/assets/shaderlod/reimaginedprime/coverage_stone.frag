#version 330 core
// Complementary Reimagined r5.9.3 by EminGT; original attribution retained.
// Conservative native terrainIPBR stone group 10080..10083 only.
invariant in vec2 texCoord;
invariant flat in int mat;
uniform sampler2D tex;
void main() {
    // Native implicit footprint before any divergent material or alpha branch.
    vec4 color = texture(tex, texCoord);
    if (!(color.a > 0.5)) discard;
    if (mat < 10080 || mat >= 10084) discard;
}
