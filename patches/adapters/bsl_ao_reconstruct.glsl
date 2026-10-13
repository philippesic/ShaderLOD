// ShaderLOD local BSL pilot. Four-tap depth-aware AO reconstruction.
// Coordinates are native screen UV; depth is positive camera-space blocks.
// The producer occupies the lower-left half of a full-size private texture.
float shaderlodBslNativeAo(vec2 sampleCoord) {
    shaderlodBslAoCoord = sampleCoord;
    float sampleZ = texture2DLod(depthtex0, sampleCoord, 0.0).r;
    vec2 noisePixel = floor(sampleCoord * vec2(viewWidth, viewHeight)) + 0.5;
    float noise = texture2DLod(noisetex, noisePixel / 512.0, 0.0).b;
    return AmbientOcclusion(sampleZ, depthtex0, gbufferProjectionInverse,
                            near, far, 0.25, noise, false);
}
float shaderlodBslFullAo(float z) {
    // One current-pixel native kernel; no four-kernel filter recomputation.
    // Edge filtering differs from native and requires quality validation.
    return pow(shaderlodBslNativeAo(texCoord), AO_STRENGTH);
}
float shaderlodBslHalfAo(float z) {
    vec2 fullSize = vec2(viewWidth, viewHeight);
    vec2 halfSize = floor(fullSize * 0.5);
    vec2 halfPos = texCoord * halfSize - 0.5;
    vec2 base = floor(halfPos);
    vec2 fraction = fract(halfPos);
    float linearDepth = GetLinearDepth(z, gbufferProjectionInverse);
    float tolerance = max(0.25, linearDepth * 0.01);
    float sum = 0.0, weightSum = 0.0, confidence = 1.0;
    for (int i = 0; i < 4; i++) {
        vec2 corner = vec2(float(i - (i / 2) * 2), float(i / 2));
        vec2 cell = clamp(base + corner, vec2(0.0), halfSize - 1.0);
        vec2 sampleUv = (cell + 0.5) / fullSize;
        vec2 sampleAoDepth = texture2DLod(colortex10, sampleUv, 0.0).rg;
        float relativeDelta = abs(sampleAoDepth.g - linearDepth) / tolerance;
        float depthConfidence = 1.0 - smoothstep(0.25, 1.0, relativeDelta);
        float weight = mix(1.0 - fraction.x, fraction.x, corner.x)
                     * mix(1.0 - fraction.y, fraction.y, corner.y);
        confidence = min(confidence, depthConfidence);
        sum += sampleAoDepth.r * weight * depthConfidence;
        weightSum += weight * depthConfidence;
    }
    float halfAo = pow(sum / max(weightSum, 0.00001), AO_STRENGTH);
    // Only discontinuity regions run the complete current-pixel native AO kernel.
    // Its samples use explicit LOD0, so this branch needs no derivatives.
    if (confidence < 1.0) return mix(shaderlodBslFullAo(z), halfAo, confidence);
    return halfAo;
}
