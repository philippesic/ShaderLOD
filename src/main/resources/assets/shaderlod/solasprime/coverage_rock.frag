#version 330 core
// Solas Shader3.7b by Septonious; personal local adaptation, native ancestry retained.
// Only pinned native SOLID polished-stone/deepslate/masonry groups; full native colour follows.
invariant flat in int mat;
invariant in vec2 texCoord;
invariant in vec4 color;
uniform sampler2D tex;
uniform float iris_currentAlphaTest;
void main() {
    // Same native first implicit footprint, before alpha/material divergence.
    vec4 sampled = texture(tex, texCoord);
    float alpha = sampled.a * color.a;
    if (!(alpha > iris_currentAlphaTest)) discard;
    if (mat != 20302 && mat != 20303 && mat != 20317) discard;
}
