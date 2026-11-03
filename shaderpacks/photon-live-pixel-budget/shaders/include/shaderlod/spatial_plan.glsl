#if !defined SHADERLOD_SPATIAL_PLAN_INCLUDED
#define SHADERLOD_SPATIAL_PLAN_INCLUDED

// ShaderLOD shared mechanism; include work_policy.glsl first.
// No copied pack algorithm, transport, texture, derivative or projection dependency.
// Distances/centered transition widths are camera-relative Euclidean Minecraft blocks.
// Legal evaluation fractions are exactly 1, 1/4, 1/16 (side lengths 1, 2, 4).
struct ShaderlodSpatialPlan {
    vec3 weights; // Linear reconstruction weights: full, quarter, sixteenth; never radiance scale.
    float requested_ratio; // Shared continuous policy request, independent of executed work.
    float reconstruction_ratio; // Weight-average legal fractions; NOT a physical work cap.
    float evaluation_ratio; // Sum of active tier fractions, before all adapter overhead.
    bool evaluate_full;
    bool evaluate_medium;
    bool evaluate_far;
    int reason; // Existing SHADERLOD_WORK_* diagnostics; no transport/schema change.
};

ShaderlodSpatialPlan shaderlod_spatial_plan_full(float requested_ratio, int reason) {
    return ShaderlodSpatialPlan(vec3(1.0, 0.0, 0.0), requested_ratio, 1.0, 1.0,
        true, false, false, reason);
}

// Exact endpoint legality, not quantization of a continuous request.
int shaderlod_spatial_plan_rate(float ratio) {
    if (ratio == 1.0) return 1;
    if (ratio == 0.25) return 2;
    if (ratio == 0.0625) return 4;
    return 0;
}

float shaderlod_spatial_plan_weight(int tier, int medium_tier, int far_tier,
    float medium_blend, float far_blend) {
    float weight = 0.0;
    if (tier == 1) weight = (1.0 - medium_blend) * (1.0 - far_blend);
    if (tier == medium_tier) weight = weight + medium_blend * (1.0 - far_blend);
    if (tier == far_tier) weight = weight + far_blend;
    return weight;
}

// Call before expensive evaluations. supported_rates is a bit mask: 1/2/4.
// Both policy endpoints must be legal and supported; otherwise retain full at
// every distance. No nearest-tier snapping or interpolation through missing tiers.
// The adapter supplies conservative region distance and protection/quality flags.
ShaderlodSpatialPlan shaderlod_spatial_plan_decide(ShaderlodWorkPolicy policy,
    bool capability_valid, bool quality_valid, bool protected_work,
    float distance_blocks, int supported_rates) {
    // Sixteen virtual members validate/reuse the existing numerical engine only.
    // Its ceil count is deliberately NOT used to select a spatial tier.
    ShaderlodWorkDecision work = shaderlod_work_decide(policy, capability_valid,
        quality_valid, protected_work, distance_blocks, 16, 0);
    if (supported_rates < 1 || supported_rates > 7 || (supported_rates & 1) == 0)
        return shaderlod_spatial_plan_full(work.retained_ratio, SHADERLOD_WORK_INVALID);
    if (work.reason != SHADERLOD_WORK_POLICY)
        return shaderlod_spatial_plan_full(work.retained_ratio, work.reason);
    int medium_tier = shaderlod_spatial_plan_rate(policy.retained_ratios.x);
    int far_tier = shaderlod_spatial_plan_rate(policy.retained_ratios.y);
    if (medium_tier == 0 || far_tier == 0 ||
        (supported_rates & medium_tier) == 0 || (supported_rates & far_tier) == 0)
        return shaderlod_spatial_plan_full(work.retained_ratio, SHADERLOD_WORK_UNSUPPORTED);
    // Bound rounding at smoothstep's endpoints before constructing convex weights.
    float medium_blend = clamp(work.medium_blend, 0.0, 1.0);
    float far_blend = clamp(work.far_blend, 0.0, 1.0);
    float full_weight = shaderlod_spatial_plan_weight(1, medium_tier, far_tier,
        medium_blend, far_blend);
    float medium_weight = shaderlod_spatial_plan_weight(2, medium_tier, far_tier,
        medium_blend, far_blend);
    float far_weight = shaderlod_spatial_plan_weight(4, medium_tier, far_tier,
        medium_blend, far_blend);
    float reconstruction_ratio = full_weight + 0.25 * medium_weight + 0.0625 * far_weight;
    float evaluation_ratio = 0.0;
    if (full_weight > 0.0) evaluation_ratio = evaluation_ratio + 1.0;
    if (medium_weight > 0.0) evaluation_ratio = evaluation_ratio + 0.25;
    if (far_weight > 0.0) evaluation_ratio = evaluation_ratio + 0.0625;
    return ShaderlodSpatialPlan(vec3(full_weight, medium_weight, far_weight),
        work.retained_ratio, reconstruction_ratio, evaluation_ratio,
        full_weight > 0.0, medium_weight > 0.0, far_weight > 0.0, SHADERLOD_WORK_POLICY);
}

// Uniform preflight only: the validated monotone policy reaches its smallest
// retained ratio at the far transition endpoint. If that endpoint requests no
// supported coarse tier, no distance can request one. This does not classify a
// tile, alter transition weights or replace the per-tile all-member decision.
// Invalid/unresolved/disabled/unsupported policies retain original work.
bool shaderlod_spatial_policy_has_coarse_demand(ShaderlodWorkPolicy policy,
    bool capability_valid, bool quality_valid, bool protected_work, int supported_rates) {
    ShaderlodSpatialPlan endpoint = shaderlod_spatial_plan_decide(policy,
        capability_valid, quality_valid, protected_work,
        policy.bands_blocks.y + 0.5 * policy.bands_blocks.z, supported_rates);
    return endpoint.evaluate_medium || endpoint.evaluate_far;
}

#endif // SHADERLOD_SPATIAL_PLAN_INCLUDED
