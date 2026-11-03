// ShaderLOD private reduced mip owner. Native algorithms: EminGT.
#include "/lib/common.glsl"
#ifdef FRAGMENT_SHADER
noperspective in vec2 texCoord;
const int colortex9Format = R11F_G11F_B10F;
const bool colortex9Clear = false;
void main() {
    ivec2 allocation = textureSize(colortex0, 0);
    ivec2 activeSize = ivec2(shaderlodRasterR1Size(vec2(allocation)));
    ivec2 coord = clamp(ivec2(texCoord * vec2(activeSize)), ivec2(0), activeSize - 1);
    /* RENDERTARGETS: 9 */
    gl_FragData[0] = texelFetch(colortex0, coord, 0);
}
#endif
#ifdef VERTEX_SHADER
noperspective out vec2 texCoord;
void main() { gl_Position = ftransform(); texCoord = gl_MultiTexCoord0.xy; }
#endif
