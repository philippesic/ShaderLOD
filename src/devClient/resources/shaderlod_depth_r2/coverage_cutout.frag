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
    // Native selected RP_MODE1 / AF0 / !IPBR_COMPAT_MODE whole-color replacements.
    // These intervals do not run the full-color bokeh alpha mix.
    if ((mat >= 10040 && mat < 10044) ||
        (mat >= 10404 && mat < 10408) ||
        (mat >= 10456 && mat < 10460)) {
        color = textureLod(tex, texCoord, 0);
    }
    // Pinned Iris Sodium AlphaTests.ONE_TENTH_ALPHA, GREATER, .1f.
    if (!(color.a > 0.1)) discard;
}
