// ShaderLOD G06, LGPL-3.0-or-later. Native kernel and AA dither unchanged.
#include "/lib/config.glsl"
#include "/lib/g06/scope.glsl"
/*
const int colortex8Format = RG32F;
*/
const bool colortex8Clear = false; // Every active texel is overwritten this frame.
#ifdef G06_AO_ACTIVE
uniform sampler2D depthtex0;
uniform float pixelSizeX;
uniform float pixelSizeY;
uniform float near;
uniform float far;
uniform float aspectRatioInverse;
uniform float fovYInverse;
vec2 texcoord;
#include "/lib/depth.glsl"
#include "/lib/dither.glsl"
#include "/lib/ao.glsl"
#endif
void main() {
    vec2 result = vec2(-1.0, 0.0);
    #ifdef G06_AO_ACTIVE
    vec2 fullSize = floor(vec2(1.0 / pixelSizeX, 1.0 / pixelSizeY) + 0.5);
    vec2 nativePixel = floor(gl_FragCoord.xy) * 2.0 + 0.5;
    texcoord = nativePixel / fullSize;
    float z = texture2DLod(depthtex0, texcoord, 0.0).r;
    float blocks = ld(z) * far;
    if (z >= 0.56 && z < 1.0 && blocks > 64.0 && blocks < 1.0e6 &&
        all(equal(mod(fullSize, 2.0), vec2(0.0)))) {
        #if AA_TYPE > 0
            float dither = shiftedSemiblue(nativePixel);
        #else
            float dither = semiblue(nativePixel);
        #endif
        result = vec2(dbao(dither), blocks);
    }
    #endif
    /* RENDERTARGETS: 8 */
    gl_FragData[0] = vec4(result, 0.0, 1.0);
}
