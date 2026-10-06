#if !defined SHADERLOD_SHADING_RATE_INCLUDED
#define SHADERLOD_SHADING_RATE_INCLUDED

// Include work_policy.glsl first. Spatial rates are side lengths: 1, 2, 4.
// supported_rates is a bit mask: 1 full, 2 one per 2x2, 4 one per 4x4.
// A region retains full-resolution materials/textures; only declared evaluations
// are shared. Rate requests are exact fractions 1, 1/4, 1/16. No implicit snapping.
struct ShaderlodShadingRateDecision {
    int requested_rate; // 0 means continuous request is not a legal discrete rate.
    int realized_rate; // Always legal 1/2/4; 1 means original per-member work.
    int requested_work; // Unmodified numerical ceil/floor request from shared policy.
    int realized_work; // Negotiated evaluations, not measured physical GPU work.
    float requested_ratio;
    float realized_ratio;
    int reason;
};

ShaderlodShadingRateDecision shaderlod_shading_rate_decide(ShaderlodWorkDecision work,
    int region_members, int supported_rates, bool region_valid, bool protected_region) {
    int requested_rate = 0;
    if (work.retained_ratio == 1.0) requested_rate = 1;
    if (work.retained_ratio == 0.25) requested_rate = 2;
    if (work.retained_ratio == 0.0625) requested_rate = 4;
    ShaderlodShadingRateDecision result = ShaderlodShadingRateDecision(requested_rate,
        1, work.realized_work, region_members, work.retained_ratio, 1.0, work.reason);
    // Full region membership, valid numerical result and mandatory full capability.
    // A decision is a caller-owned value, so validate it again before rate lowering.
    if ((region_members != 1 && region_members != 4 && region_members != 16) ||
        work.upstream_work != region_members || work.requested_work < 0 ||
        work.realized_work < work.requested_work || work.realized_work > region_members ||
        work.reason < SHADERLOD_WORK_POLICY || work.reason > SHADERLOD_WORK_QUALITY_FALLBACK ||
        !shaderlod_effect_finite(work.retained_ratio) || work.retained_ratio < 0.0 ||
        work.retained_ratio > 1.0 || supported_rates < 1 || supported_rates > 7 ||
        (supported_rates & 1) == 0) {
        result.reason = SHADERLOD_WORK_INVALID;
        return result;
    }
    if (work.requested_work != int(ceil(float(region_members) * work.retained_ratio))) {
        result.reason = SHADERLOD_WORK_INVALID;
        return result;
    }
    if (work.reason != SHADERLOD_WORK_POLICY) return result;
    if (protected_region) { result.reason = SHADERLOD_WORK_PROTECTED; return result; }
    if (!region_valid) { result.reason = SHADERLOD_WORK_QUALITY_FALLBACK; return result; }
    if (requested_rate == 0 || (supported_rates & requested_rate) == 0 ||
        region_members < requested_rate * requested_rate ||
        work.realized_work != region_members / (requested_rate * requested_rate)) {
        result.reason = SHADERLOD_WORK_UNSUPPORTED;
        return result;
    }
    result.realized_rate = requested_rate;
    result.realized_work = work.realized_work;
    result.realized_ratio = work.retained_ratio;
    return result;
}

#endif // SHADERLOD_SHADING_RATE_INCLUDED
