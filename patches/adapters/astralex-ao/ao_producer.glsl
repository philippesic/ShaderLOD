// Private AO-only pass; native scene/materials/reflections/AA stay in full deferred1.
#include "/lib/util/functions.glsl"
#include "/settings/globalSettings.glsl"
#if SHADERLOD_ASTRALEX_AO_HALF_RES == 1 && (!defined OVERWORLD || !defined AO || AO_QUALITY < 1 || AO_QUALITY > 2 || AO_FOVSCALE < 1 || AO_FOVSCALE > 5 || defined DISTANT_HORIZONS || defined VOXY)
#error ShaderLOD Astra half-AO unsupported native graph; restore private AO option0.
#endif
varying vec2 texCoord;
uniform sampler2D noisetex, depthtex0;
uniform int frameCounter;
uniform float near, far, viewWidth, viewHeight, aspectRatio;
uniform mat4 gbufferProjection;
#include "/lib/util/dither.glsl"
#include "/lib/shaderlod/astra_ao_graph.glsl"
#ifdef AO
#include "/lib/shaderlod/astra_ao_kernel.glsl"
#endif
/*
const int colortex15Format = RGBA32F;
*/
const bool colortex15Clear = true;
const vec4 colortex15ClearColor = vec4(1.0, 0.0, 0.0, -1.0);
void main() {
    vec2 fullSize = vec2(viewWidth, viewHeight);
    vec2 halfSize = floor(fullSize * 0.5);
    vec2 sourceUv = (floor(gl_FragCoord.xy) * 2.0 + vec2(1.5)) / fullSize;
    float z = texture2DLod(depthtex0, sourceUv, 0.0).r;
    float ao = 1.0, valid = 0.0;
    float eyeDepth = shaderlodAstraAoEyeDepth(z, gbufferProjection[2].z, gbufferProjection[3].z, gbufferProjection[2].w, gbufferProjection[3].w);
#ifdef AO
    if (shaderlodAstraAoGraphActive(fullSize) && z >= 0.56 && z < 1.0 &&
        eyeDepth > shaderlodNear + max(0.05, shaderlodNear * 0.001) && eyeDepth < 1e6) {
        float dither = animateDither(BlueNoise(sourceUv * fullSize));
        ao = shaderlodAstraAoKernel(sourceUv, z, GetLinearDepth(z), dither);
        if (ao >= 0.0 && ao <= 1.0) valid = 1.0;
    }
#endif
    /* RENDERTARGETS:15 */
    gl_FragData[0] = vec4(ao, eyeDepth, valid, float(frameCounter));
}
