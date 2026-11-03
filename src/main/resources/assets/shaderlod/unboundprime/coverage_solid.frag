#version 330 core
// ShaderLOD B9 private coverage stage derived from pinned Complementary
// Unbound r5.9.3 by EminGT. Original attribution/license preserved in stage.
// Same invariant native vertex stage and current native tex sampler required.
invariant in vec2 texCoord;
invariant flat in int mat;
uniform sampler2D tex;
void main() {
    // Native initial implicit footprint and alpha test stay ahead of divergence.
    vec4 color = texture(tex, texCoord);
    if (color.a <= 0.5) discard;
    // Conservative subset only; native color still shades every snow material.
    if (mat >= 10952 && mat < 10956) discard;
}
