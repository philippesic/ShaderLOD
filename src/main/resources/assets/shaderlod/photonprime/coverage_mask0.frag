#version 400 core
// Photon v1.3b by SixthSurge. Conservative mask0 native terrain subset.
invariant in vec2 uv;
invariant in vec4 tint;
invariant flat in uint material_mask;
uniform sampler2D gtexture;
const float lod_bias = log2(1.0);
void main() {
    // Same native implicit footprint before any divergent material/alpha branch.
    vec4 base_color = texture(gtexture, uv, lod_bias) * tint;
    if (base_color.a < 0.1) discard;
    if (material_mask != 0u) discard;
}
