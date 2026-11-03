#if !defined SHADERLOD_PHOTON_WATER_POLICY_INCLUDED
#define SHADERLOD_PHOTON_WATER_POLICY_INCLUDED
#define SHADERLOD_WATER
int shaderlod_preset() { return shaderlodPreset; }
int shaderlod_water_quality_at(float dist, vec2 pixel, float frame) {
    if (!(shaderlod_optimization_enabled(shaderlodOptWaterWaveBudget) ||
        shaderlod_optimization_enabled(shaderlodOptWaterParallaxBudget)) ||
        !(dist >= 0.0 && dist <= 16777216.0)) return 0;
    // Legacy dedicated water bands, centered full32-block transitions.
    if (dist <= 80.0) return 0;
    if (dist >= 272.0) return 2;
    if (dist >= 112.0 && dist <= 240.0) return 1;
    pixel += mod(frame, 64.0) * vec2(2.17, 0.73);
    float noise = fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
    float medium = smoothstep(80.0, 112.0, dist);
    float low = smoothstep(240.0, 272.0, dist);
    return noise < low ? 2 : (noise < medium ? 1 : 0);
}
#endif
