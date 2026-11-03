// MakeUp dedicated AO producer, adapted from native dbao paired-depth kernel.
// LGPL-3.0-or-later. ShaderLOD half-rectangle/reconstruction mechanism reused from BSL pilot.
#include "/lib/config.glsl"
uniform sampler2D depthtex0;
uniform float near;
uniform float far;
uniform float aspectRatioInverse;
uniform float fovYInverse;
uniform float pixelSizeX;
uniform float pixelSizeY;
uniform int frameCounter;
uniform mat4 gbufferProjection;
#include "/lib/depth.glsl"
#include "/lib/dither.glsl"
#include "/lib/shaderlod/makeup_ao_graph.glsl"
#include "/lib/shaderlod/makeup_ao_kernel.glsl"
/*
const int colortex10Format = RGBA32F;
*/
const bool colortex10Clear = true;
const vec4 colortex10ClearColor = vec4(1.0, 0.0, 0.0, -1.0);
void main() {
    vec2 fullSize = floor(vec2(1.0 / pixelSizeX, 1.0 / pixelSizeY) + 0.5);
    vec2 halfSize = floor(fullSize * 0.5);
    vec2 sourceUv = gl_FragCoord.xy / max(halfSize, vec2(1.0));
    float z = texture2DLod(depthtex0, sourceUv, 0.0).r;
    float blocks = ld(z) * far;
    float ao = 1.0;
    float valid = 0.0;
    float eyeDepth = shaderlodMakeupAoEyeDepth(z, gbufferProjection[2].z, gbufferProjection[3].z, gbufferProjection[2].w, gbufferProjection[3].w);
    if (shaderlodMakeupAoGraphActive(fullSize) && z >= 0.56 && z < 1.0 &&
        eyeDepth > shaderlodNear + max(0.05, shaderlodNear * 0.001) && blocks > 0.0 && blocks < 1.0e6) {
        vec2 nativePixel = floor(sourceUv * fullSize) + 0.5;
        #if AA_TYPE > 0
        float dither = shiftedSemiblue(nativePixel);
        #else
        float dither = semiblue(nativePixel);
        #endif
        ao = shaderlodMakeupAoKernel(sourceUv, dither);
        if (ao >= 0.0 && ao <= 1.0) valid = 1.0;
    }
    /* RENDERTARGETS:10 */
    gl_FragData[0] = vec4(ao, blocks, valid, float(frameCounter));
}
