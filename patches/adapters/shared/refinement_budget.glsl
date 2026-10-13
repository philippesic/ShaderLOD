#ifndef SHADERLOD_REFINEMENT_BUDGET_INCLUDED
#define SHADERLOD_REFINEMENT_BUDGET_INCLUDED
// Reusable hit-refinement budget. Camera-relative Euclidean distance in blocks.
// Coarse traversal, ray geometry, hit validity, radiance and fallback stay upstream.
// Off, QUALITY, absent/invalid transport, bands or distances restore full work.
// Existing core bands: full transition width centered on each near/far boundary.
float shaderlod_refinement_blend(float distance_blocks, float boundary) {
    if (shaderlodTransition == 0.0) return distance_blocks < boundary ? 0.0 : 1.0;
    return smoothstep(boundary - 0.5 * shaderlodTransition,
        boundary + 0.5 * shaderlodTransition, distance_blocks);
}
int shaderlod_refinement_budget(int bridge, int enabled, int preset,
    float distance_blocks, int original, int minimum) {
    if (bridge != 2 || enabled != 1 || preset < 0 || preset > 2 ||
        original < minimum || minimum < 1 || original > 65536 ||
        !(distance_blocks >= 0.0 && distance_blocks <= 16777216.0) ||
        !(shaderlodNear > 0.0 && shaderlodNear < shaderlodFar && shaderlodFar <= 4096.0 &&
          shaderlodTransition >= 0.0 && shaderlodTransition <= shaderlodFar - shaderlodNear &&
          shaderlodTransition * 0.5 <= shaderlodNear)) return original;
    float retained = preset == 0 ? 0.875 : 0.75;
    float medium_blend = shaderlod_refinement_blend(distance_blocks, shaderlodNear);
    float far_blend = shaderlod_refinement_blend(distance_blocks, shaderlodFar);
    // Preserve existing far retention; near starts a gentler intermediate tier.
    float medium_retained = 0.5 * (1.0 + retained);
    float ratio = mix(mix(1.0, medium_retained, medium_blend), retained, far_blend);
    return max(minimum, min(original,
        int(ceil(float(original) * ratio))));
}
#endif
