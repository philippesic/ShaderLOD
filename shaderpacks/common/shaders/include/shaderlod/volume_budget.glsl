#if !defined SHADERLOD_VOLUME_BUDGET_INCLUDED
#define SHADERLOD_VOLUME_BUDGET_INCLUDED

// Shared work_policy.glsl supplies distance, versions, modes and retained ratios.
// This helper only validates volume inputs and refines its continuous request.
// All physical distances/footprints are in blocks; no derivatives or texture work.
bool shaderlod_volume_finite(float v) { return !isnan(v) && !isinf(v); }

// footprint_blocks is the smallest final-pixel footprint at the volume entry;
// resolved_feature_blocks is an adapter-proven feature scale, not a derivative.
// protected_weight smoothly restores overhead/other salient views to full work.
// Original count and caller floor remain operation inputs, not quality presets.
float shaderlod_volume_work_target(ShaderlodWorkPolicy policy, bool capability_valid,
    uint original, uint minimum, float entry_blocks, float interval_blocks,
    float footprint_blocks, float resolved_feature_blocks, float protected_weight) {
    float full = float(original);
    // Preserve the existing volume count domain before any uint -> int cast.
    if (original < minimum || minimum < 1u || original > 65536u) return full;
    bool quality_valid =
        shaderlod_volume_finite(interval_blocks) && interval_blocks > 0.0 && interval_blocks <= 16777216.0 &&
        shaderlod_volume_finite(footprint_blocks) && footprint_blocks > 0.0 &&
        shaderlod_volume_finite(resolved_feature_blocks) && resolved_feature_blocks > 0.0 &&
        shaderlod_volume_finite(protected_weight) && protected_weight >= 0.0 && protected_weight <= 1.0;
    ShaderlodWorkDecision work = shaderlod_work_decide(policy, capability_valid,
        quality_valid, protected_weight == 1.0, entry_blocks, int(original), int(minimum));
    if (work.reason != SHADERLOD_WORK_POLICY) return full;
    float footprint_weight = smoothstep(resolved_feature_blocks, 2.0 * resolved_feature_blocks, footprint_blocks);
    // Refine BEFORE applying the floor. Rounding to realized_work here would
    // discard continuous transitions and the march's existing partial last cell.
    float target = mix(full, work.continuous_requested_work,
        footprint_weight * (1.0 - protected_weight));
    return clamp(target, float(minimum), full);
}

// Continuous budget -> ceil(target) cells: all but the last have equal width;
// the last contains precisely the remaining interval. Integer targets recover
// ordinary equal-width integration. No stochastic count or frame-changing selector.
// Caller validates a finite positive interval and target >= 1 before these calls.
float shaderlod_volume_cell_length(float interval_length, float target, uint cell) {
    float step_length = interval_length / target;
    return max(0.0, min(step_length, interval_length - float(cell) * step_length));
}

float shaderlod_volume_cell_sample(float interval_length, float target, uint cell, float jitter) {
    return float(cell) * (interval_length / target) +
        clamp(jitter, 0.0, 1.0) * shaderlod_volume_cell_length(interval_length, target, cell);
}

#endif // SHADERLOD_VOLUME_BUDGET_INCLUDED
