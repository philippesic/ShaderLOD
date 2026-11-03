// Private Unbound AO-only producer. Native clouds/lighting/AA stay in deferred1.
#include "/lib/common.glsl"
#include "/lib/shaderlod/unbound_ao_option.glsl"
#include "/lib/shaderlod/unbound_ao_graph.glsl"
float farMinusNear = far - near;
#include "/lib/shaderlod/unbound_ao_kernel.glsl"
/*
const int colortex10Format = RGBA32F;
*/
const bool colortex10Clear = true;
const vec4 colortex10ClearColor = vec4(1.0, 0.0, 0.0, -1.0);
void main() {
    vec2 fullSize = vec2(viewWidth, viewHeight);
    vec2 halfSize = floor(fullSize * 0.5);
    vec2 sourceUv = (floor(gl_FragCoord.xy) * 2.0 + vec2(1.5)) / fullSize;
    float z = texture2DLod(depthtex0, sourceUv, 0.0).r;
    float ao = 1.0;
    float valid = 0.0;
    float eyeDepth = shaderlodUnboundAoEyeDepth(z, gbufferProjection[2].z, gbufferProjection[3].z, gbufferProjection[2].w, gbufferProjection[3].w);
#if SSAO_QUALI > 0
    if (shaderlodUnboundAoGraphActive(fullSize) && z >= 0.56 && z < 1.0 &&
        eyeDepth > shaderlodNear + max(0.05, shaderlodNear * 0.001) && eyeDepth < 1e6) {
        float dither = texture2DLod(noisetex, sourceUv * fullSize / 128.0, 0.0).b;
        #ifdef TAA
            dither = fract(dither + goldenRatio * mod(float(frameCounter), 3600.0));
        #endif
        ao = shaderlodUnboundAoKernel(sourceUv, depthtex0, z, GetLinearDepth(z), dither, far, near, far - near);
        if (ao >= 0.0 && ao <= 1.0) valid = 1.0;
    }
#endif
    /* RENDERTARGETS:10 */
    gl_FragData[0] = vec4(ao, eyeDepth, valid, float(frameCounter));
}
