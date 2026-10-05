#ifndef SHADERLOD_EFFECT_POLICY_INCLUDED
#define SHADERLOD_EFFECT_POLICY_INCLUDED

// Uniform-free work policy for explicit adapters. Camera-relative Euclidean blocks.
// Resolve extension version/mode/capability and original-path fallback in the adapter.
// These finite bounds match current transport distances and bound float-to-int casts.
// Positive widths below 1/1024 block cannot reliably resolve centered endpoints at 4096.
#define SHADERLOD_EFFECT_MAX_BAND_BLOCKS 4096.0
#define SHADERLOD_EFFECT_MAX_WIDTH_BLOCKS 512.0
#define SHADERLOD_EFFECT_MIN_WIDTH_BLOCKS 0.0009765625
#define SHADERLOD_EFFECT_MAX_DISTANCE_BLOCKS 16777216.0
#define SHADERLOD_EFFECT_MAX_EXACT_WORK 16777216

bool shaderlod_effect_finite(float value) {
    return !isnan(value) && !isinf(value);
}

bool shaderlod_effect_valid(float distance_blocks, float near_blocks,
    float far_blocks, float transition_blocks, float medium_ratio, float far_ratio) {
    return shaderlod_effect_finite(distance_blocks) && distance_blocks >= 0.0 &&
        distance_blocks <= SHADERLOD_EFFECT_MAX_DISTANCE_BLOCKS &&
        shaderlod_effect_finite(near_blocks) && shaderlod_effect_finite(far_blocks) &&
        shaderlod_effect_finite(transition_blocks) && near_blocks > 0.0 &&
        far_blocks > near_blocks && far_blocks <= SHADERLOD_EFFECT_MAX_BAND_BLOCKS &&
        transition_blocks >= 0.0 && transition_blocks <= SHADERLOD_EFFECT_MAX_WIDTH_BLOCKS &&
        (transition_blocks == 0.0 || transition_blocks >= SHADERLOD_EFFECT_MIN_WIDTH_BLOCKS) &&
        0.5 * transition_blocks <= near_blocks && transition_blocks <= far_blocks - near_blocks &&
        shaderlod_effect_finite(medium_ratio) && shaderlod_effect_finite(far_ratio) &&
        far_ratio >= 0.0 && far_ratio <= medium_ratio && medium_ratio <= 1.0;
}

// Internal: callers first validate the entire policy. Width is the full centered band.
float shaderlod_effect_blend(float distance_blocks, float boundary, float width) {
    if (width == 0.0 && distance_blocks < boundary) return 0.0;
    if (width == 0.0) return 1.0;
    float start = boundary - 0.5 * width;
    if (distance_blocks <= start) return 0.0;
    if (distance_blocks >= boundary + 0.5 * width) return 1.0;
    float t = clamp((distance_blocks - start) / width, 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

// protected_lighting is the resolved preserve-lighting && salient-effect guard.
// FULL or effect-disabled means original work; INHERIT is resolved outside this helper.
// Protection applies to quality budgets, not independent exact work reuse.
float shaderlod_effect_retained_ratio(bool global_enabled, bool effect_enabled,
    bool full_quality, bool protected_lighting, float distance_blocks,
    float near_blocks, float far_blocks, float transition_blocks,
    float medium_ratio, float far_ratio) {
    if (!shaderlod_effect_valid(distance_blocks, near_blocks, far_blocks,
        transition_blocks, medium_ratio, far_ratio)) return 1.0;
    if (!global_enabled || !effect_enabled || full_quality || protected_lighting) return 1.0;
    float medium_weight = shaderlod_effect_blend(distance_blocks, near_blocks, transition_blocks);
    float far_weight = shaderlod_effect_blend(distance_blocks, far_blocks, transition_blocks);
    float medium = (1.0 - medium_weight) + medium_ratio * medium_weight;
    return clamp(medium * (1.0 - far_weight) + far_ratio * far_weight, far_ratio, 1.0);
}

// Cap an actual upstream work count; zero minimum explicitly permits skipping.
// Round the float product upward and preserve the adapter's legal retained floor.
// Binary32 arithmetic can differ from the Java double oracle at integer thresholds;
// this is not a bit-exact Java port. Counts above the exact-integer bound fail open.
int shaderlod_effect_work_cap(int upstream_work, int minimum_work,
    bool global_enabled, bool effect_enabled, bool full_quality, bool protected_lighting,
    float distance_blocks, float near_blocks, float far_blocks, float transition_blocks,
    float medium_ratio, float far_ratio) {
    if (upstream_work < 0 || upstream_work > SHADERLOD_EFFECT_MAX_EXACT_WORK ||
        minimum_work < 0 || minimum_work > upstream_work) return upstream_work;
    float ratio = shaderlod_effect_retained_ratio(global_enabled, effect_enabled,
        full_quality, protected_lighting, distance_blocks, near_blocks, far_blocks,
        transition_blocks, medium_ratio, far_ratio);
    int requested = int(ceil(float(upstream_work) * ratio));
    return min(upstream_work, max(minimum_work, requested));
}

#endif // SHADERLOD_EFFECT_POLICY_INCLUDED
