// Four-tap depth-aware AO reconstruction; lower-left half of full-size private RGBA32F.
// Reuses the existing BSL graph mechanism; applies MakeUp final AO factor/ld/hand/dither math.
uniform sampler2D colortex10;
#include "/lib/shaderlod/makeup_ao_graph.glsl"
float shaderlodMakeupReconstructAo(float z, float dither) {
    vec2 fullSize = floor(vec2(1.0 / pixelSizeX, 1.0 / pixelSizeY) + 0.5);
    float blocks = ld(z) * far;
    if (!shaderlodMakeupAoGraphActive(fullSize) || !(z >= 0.56 && z < 1.0 && blocks > 0.0 && blocks < 1.0e6))
        return dbao(dither); // exactly native near/HUD/sky/unknown protocol or target
    float eyeDepth = shaderlodMakeupAoEyeDepth(z, gbufferProjection[2].z, gbufferProjection[3].z, gbufferProjection[2].w, gbufferProjection[3].w);
    if (!(eyeDepth > shaderlodNear + max(0.05, shaderlodNear * 0.001))) return dbao(dither);
    vec2 halfSize = floor(fullSize * 0.5);
    vec2 halfPos = texcoord * halfSize - 0.5;
    vec2 base = floor(halfPos);
    vec2 fraction = fract(halfPos);
    float tolerance = max(0.25, blocks * 0.01);
    float sum = 0.0, weightSum = 0.0, confidence = 1.0;
    for (int i = 0; i < 4; i++) {
        vec2 corner = vec2(float(i - (i / 2) * 2), float(i / 2));
        vec2 cell = clamp(base + corner, vec2(0.0), halfSize - 1.0);
        vec4 stored = texture2DLod(colortex10, (cell + 0.5) / fullSize, 0.0);
        float depthConfidence = 0.0;
        if (stored.r >= 0.0 && stored.r <= 1.0 && stored.g > shaderlodNear && stored.g < 1.0e6 &&
            stored.b == 1.0 && stored.a == float(frameCounter))
            depthConfidence = 1.0 - smoothstep(0.25, 1.0, abs(stored.g - blocks) / tolerance);
        float weight = mix(1.0 - fraction.x, fraction.x, corner.x) * mix(1.0 - fraction.y, fraction.y, corner.y);
        confidence = min(confidence, depthConfidence);
        // Zero confidence cannot recover; keep the post-loop native fallback unchanged.
        if (confidence == 0.0) break;
        if (depthConfidence > 0.0) {
            sum += stored.r * weight * depthConfidence;
            weightSum += weight * depthConfidence;
        }
    }
    if (!(confidence > 0.0 && weightSum > 0.0 && weightSum <= 1.00001)) return dbao(dither);
    float halfAo = sum / weightSum; // native kernel already applied AO_STRENGTH and sqrt exactly once
    if (confidence < 1.0) return mix(dbao(dither), halfAo, confidence);
    return halfAo;
}
