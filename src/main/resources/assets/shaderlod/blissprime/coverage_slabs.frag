#version 330 core
// Bliss2.1.2 by Xonk; local same-frame native SOLID slab subset.
invariant flat in float blockID;
invariant in vec4 lmtexcoord;
invariant in vec4 color;
invariant in vec4 normalMat;
uniform sampler2D gtexture;
void main() {
    // Native POMfalse/bias0 atlas footprint BEFORE alpha/material divergence.
    float alpha = texture(gtexture, lmtexcoord.xy, 0.0).a * color.a;
    if (!(alpha > 0.5)) discard;
    if (!(normalMat.a >= 1.0 && normalMat.a <= 1.0)) discard;
    if (!(lmtexcoord.w >= 0.0 && lmtexcoord.w <= 1.0)) discard;
    if (blockID != 407.0 && blockID != 408.0) discard;
}
