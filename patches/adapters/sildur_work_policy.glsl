#if !defined SHADERLOD_WORK_POLICY_INCLUDED
#define SHADERLOD_WORK_POLICY_INCLUDED

#include "/include/shaderlod/effect_policy.glsl"

// ShaderLOD shared mechanism. No pack transport, sampling or projection dependencies.
// Bands are radial camera-relative Euclidean distances in Minecraft blocks.
// Width is the full centered transition width, as in DistanceBands/EffectPolicy.
const int SHADERLOD_WORK_POLICY = 0;
const int SHADERLOD_WORK_INVALID = 1;
const int SHADERLOD_WORK_GLOBAL_DISABLED = 2;
const int SHADERLOD_WORK_FULL = 3;
const int SHADERLOD_WORK_UNRESOLVED_INHERIT = 4;
const int SHADERLOD_WORK_EFFECT_DISABLED = 5;
const int SHADERLOD_WORK_PROTECTED = 6;
const int SHADERLOD_WORK_UNSUPPORTED = 7;
const int SHADERLOD_WORK_QUALITY_FALLBACK = 8;

struct ShaderlodWorkPolicy {
    int core_version;
    int effects_version;
    int minimum_effects_version; // 1 for original groups; 2 for OpaqueLighting.
    bool global_enabled;
    int mode; // 0 INHERIT, 1 FULL, 2 ADAPTIVE. Unknown modes retain original work.
    bool inherited_policy_resolved; // Mode 0 uses the explicitly supplied inherited policy.
    bool policy_enabled;
    vec3 bands_blocks; // near, far, full transition width. No preset distances here.
    vec2 retained_ratios; // medium, far; work fractions, never effect strength.
};

struct ShaderlodWorkDecision {
    int upstream_work;
    int requested_work; // ceil(binary32 upstream * retained_ratio), before caller floor.
    int realized_work; // requested count after legal floor; not measured physical work.
    float retained_ratio;
    float continuous_requested_work; // Binary32 upstream * ratio, before legal floor.
    float continuous_work; // Continuous request clamped to caller floor/upstream.
    float medium_blend;
    float far_blend;
    int reason;
};

bool shaderlod_work_protocol_valid(ShaderlodWorkPolicy policy) {
    return policy.core_version == 2 &&
        (policy.minimum_effects_version == 1 || policy.minimum_effects_version == 2) &&
        policy.effects_version >= policy.minimum_effects_version && policy.effects_version <= 2 &&
        policy.mode >= 0 && policy.mode <= 2;
}

ShaderlodWorkDecision shaderlod_work_full(int upstream, int reason) {
    return ShaderlodWorkDecision(upstream, upstream, upstream, 1.0, float(upstream), float(upstream), 0.0, 0.0, reason);
}

// Call before expensive work. Consumer supplies capability/revision/distance-source
// validity, quality/region validity, and resolved salient/near/member protection.
// false guards preserve the original pack path; no implicit derivatives or fetches.
ShaderlodWorkDecision shaderlod_work_decide(ShaderlodWorkPolicy policy,
    bool capability_valid, bool quality_valid, bool protected_work,
    float distance_blocks, int upstream_work, int minimum_work) {
    if (!shaderlod_work_protocol_valid(policy) ||
        !shaderlod_effect_valid(distance_blocks, policy.bands_blocks.x, policy.bands_blocks.y,
            policy.bands_blocks.z, policy.retained_ratios.x, policy.retained_ratios.y) ||
        upstream_work < 0 || upstream_work > SHADERLOD_EFFECT_MAX_EXACT_WORK || minimum_work < 0 || minimum_work > upstream_work)
        return shaderlod_work_full(upstream_work, SHADERLOD_WORK_INVALID);
    if (!policy.global_enabled) return shaderlod_work_full(upstream_work, SHADERLOD_WORK_GLOBAL_DISABLED);
    if (policy.mode == 1) return shaderlod_work_full(upstream_work, SHADERLOD_WORK_FULL);
    if (!capability_valid) return shaderlod_work_full(upstream_work, SHADERLOD_WORK_UNSUPPORTED);
    if (policy.mode == 0 && !policy.inherited_policy_resolved)
        return shaderlod_work_full(upstream_work, SHADERLOD_WORK_UNRESOLVED_INHERIT);
    if (!policy.policy_enabled) return shaderlod_work_full(upstream_work, SHADERLOD_WORK_EFFECT_DISABLED);
    if (protected_work) return shaderlod_work_full(upstream_work, SHADERLOD_WORK_PROTECTED);
    if (!quality_valid) return shaderlod_work_full(upstream_work, SHADERLOD_WORK_QUALITY_FALLBACK);
    float medium_weight = shaderlod_effect_blend(distance_blocks, policy.bands_blocks.x, policy.bands_blocks.z);
    float far_weight = shaderlod_effect_blend(distance_blocks, policy.bands_blocks.y, policy.bands_blocks.z);
    float ratio = shaderlod_effect_retained_ratio(true, true, false, false, distance_blocks,
        policy.bands_blocks.x, policy.bands_blocks.y, policy.bands_blocks.z,
        policy.retained_ratios.x, policy.retained_ratios.y);
    float continuous_requested = float(upstream_work) * ratio;
    float continuous = clamp(continuous_requested, float(minimum_work), float(upstream_work));
    // Same ceil/floor quantization as shaderlod_effect_work_cap, using the already
    // validated ratio so the ordinary path does not evaluate the policy twice.
    int requested = int(ceil(continuous_requested));
    int realized = min(upstream_work, max(minimum_work, requested));
    return ShaderlodWorkDecision(upstream_work, requested, realized, ratio, continuous_requested,
        continuous, medium_weight, far_weight, SHADERLOD_WORK_POLICY);
}

#endif // SHADERLOD_WORK_POLICY_INCLUDED
