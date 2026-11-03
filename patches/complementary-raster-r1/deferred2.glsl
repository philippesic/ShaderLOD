// ShaderLOD private reduced mip owner. Native algorithms: EminGT.
#include "/lib/common.glsl"
#ifdef FRAGMENT_SHADER
noperspective in vec2 texCoord;
const int colortex10Format = RGBA8;
const bool colortex10Clear = false;
void main() {
    ivec2 allocation = textureSize(colortex5, 0);
    ivec2 activeSize = ivec2(shaderlodRasterR1Size(vec2(allocation)));
    ivec2 coord = clamp(ivec2(texCoord * vec2(activeSize)), ivec2(0), activeSize - 1);
    /* RENDERTARGETS: 10 */
    gl_FragData[0] = texelFetch(colortex5, coord, 0);
}
#endif
#ifdef VERTEX_SHADER
noperspective out vec2 texCoord;
void main() { gl_Position = ftransform(); texCoord = gl_MultiTexCoord0.xy; }
#endif
