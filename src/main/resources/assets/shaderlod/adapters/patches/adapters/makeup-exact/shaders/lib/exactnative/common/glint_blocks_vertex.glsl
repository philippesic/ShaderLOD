#include "/lib/config.glsl"

/* Uniforms */

uniform sampler2D gaux3;
uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferProjectionInverse;

/* Ins / Outs */

varying vec2 texcoord;
varying vec4 tintColor;
varying float exposure;

#if AA_TYPE > 0
    #include "/lib/exactnative/src/taa_offset.glsl"
#endif

// MAIN FUNCTION ------------------

void main() {
    #include "/lib/exactnative/src/basiccoords_vertex.glsl"
    #include "/lib/exactnative/src/position_vertex.glsl"

    exposure = texture2D(gaux3, vec2(0.5)).r;

    tintColor = gl_Color;
}
