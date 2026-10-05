#if !defined SHADERLOD_NORMAL_ERROR_BUDGET_INCLUDED
#define SHADERLOD_NORMAL_ERROR_BUDGET_INCLUDED
// Uniform-free quality policy. No sampler, derivative, world-distance or adapter
// assumptions. The caller supplies PROVEN height/slope bounds in its own units.
// For fixed-z reconstruction normalize(vec3(delta_height * influence, h)),
// raw xy-error radius B implies angular error <= 2*atan(B/(2*h)).
// This bounds normals; it does not bound BRDF radiance or reflection hit changes.

bool shaderlod_normal_error_finite(float value) {
    return !isnan(value) && !isinf(value);
}
bool shaderlod_normal_error_finite(vec2 value) {
    return !any(isnan(value)) && !any(isinf(value));
}

// Componentwise height finite-difference error for complete omission of one
// centered wave, replaced by its mean. centered_range must straddle zero.
// wave_lipschitz and modulation_lipschitz are absolute per-coordinate-axis
// derivative bounds; h is the ORIGINAL finite-difference step in those units.
// Amplitude includes ORIGINAL full-count normalization, never reduced-count.
// Modulation is nonnegative, <= modulation_max, and varies by <= modulation_range.
// A negative sentinel means unproved input: retain original expensive work.
vec2 shaderlod_normal_height_error_bound(
    float amplitude, float h, vec2 centered_range, vec2 wave_lipschitz,
    float modulation_max, float modulation_range, vec2 modulation_lipschitz
) {
    if (!shaderlod_normal_error_finite(amplitude) || amplitude < 0.0 ||
        !shaderlod_normal_error_finite(h) || h <= 0.0 ||
        !shaderlod_normal_error_finite(centered_range) ||
        centered_range.x > 0.0 || centered_range.y < 0.0 ||
        !shaderlod_normal_error_finite(wave_lipschitz) || any(lessThan(wave_lipschitz, vec2(0.0))) ||
        !shaderlod_normal_error_finite(modulation_max) || modulation_max < 0.0 ||
        !shaderlod_normal_error_finite(modulation_range) || modulation_range < 0.0 ||
        modulation_range > modulation_max ||
        !shaderlod_normal_error_finite(modulation_lipschitz) ||
        any(lessThan(modulation_lipschitz, vec2(0.0)))) return vec2(-1.0);
    float wave_range = centered_range.y - centered_range.x;
    float centered_absolute = max(-centered_range.x, centered_range.y);
    vec2 difference = modulation_max * min(vec2(wave_range), h * wave_lipschitz) +
        centered_absolute * min(vec2(modulation_range), h * modulation_lipschitz);
    vec2 result = amplitude * min(vec2(modulation_max * wave_range), difference);
    if (!shaderlod_normal_error_finite(result)) return vec2(-1.0);
    return result;
}

// Raw reconstruction-vector units. Keep h unchanged; do not compensate an omitted
// octave by amplifying retained amplitudes. A full normal evaluation is unnecessary.
float shaderlod_normal_error_radius(vec2 height_difference_bound, float influence) {
    if (!shaderlod_normal_error_finite(height_difference_bound) ||
        any(lessThan(height_difference_bound, vec2(0.0))) ||
        !shaderlod_normal_error_finite(influence)) return -1.0;
    float radius = length(height_difference_bound * abs(influence));
    return shaderlod_normal_error_finite(radius) ? radius : -1.0;
}

// Zero budget is an explicit FULL fallback. Angles are radians. view_cosine is
// abs(flat-normal dot view), [0,1]. Protect grazing/unstable/salient effects first.
// The grazing guard blends allowable ERROR (not already computed expensive work)
// from zero at grazing_floor to the full limit at 2*grazing_floor.
float shaderlod_normal_error_budget(
    bool enabled, bool near_or_full_quality, bool unstable_or_salient,
    float fixed_z, float maximum_angle_radians,
    float view_cosine, float grazing_floor
) {
    if (!enabled || near_or_full_quality || unstable_or_salient ||
        !shaderlod_normal_error_finite(fixed_z) || fixed_z <= 0.0 ||
        !shaderlod_normal_error_finite(maximum_angle_radians) ||
        maximum_angle_radians <= 0.0 || maximum_angle_radians >= 3.0 ||
        !shaderlod_normal_error_finite(view_cosine) || view_cosine < 0.0 || view_cosine > 1.0 ||
        !shaderlod_normal_error_finite(grazing_floor) || grazing_floor < 0.0 || grazing_floor > 0.5 ||
        view_cosine <= grazing_floor) return 0.0;
    float confidence = grazing_floor == 0.0 ? 1.0 :
        smoothstep(grazing_floor, 2.0 * grazing_floor, view_cosine);
    // Explicit 1/4096 headroom for positive error-bound accumulation/rounding.
    // Real-arithmetic proof plus host binary32 fixtures, not a driver proof.
    float budget = 2.0 * fixed_z * tan(0.5 * maximum_angle_radians) * confidence *
        (1.0 - 1.0 / 4096.0);
    return shaderlod_normal_error_finite(budget) && budget > 0.0 ? budget : 0.0;
}

// Allocate from smallest-error/highest-frequency octaves if the adapter wants
// useful COMPLETE skips. Partial removal still needs that octave's full evaluation.
// used_radius is the sum of prior accepted radius*removal terms (triangle bound).
// Requested removal is [0,1]; return 0 for near/full/invalid/unsupported policy.
float shaderlod_normal_removal_cap(
    float requested_removal, float used_radius, float octave_radius, float budget
) {
    if (!shaderlod_normal_error_finite(requested_removal) || requested_removal < 0.0 || requested_removal > 1.0 ||
        !shaderlod_normal_error_finite(used_radius) || used_radius < 0.0 ||
        !shaderlod_normal_error_finite(octave_radius) || octave_radius < 0.0 ||
        !shaderlod_normal_error_finite(budget) || budget <= 0.0 || used_radius > budget) return 0.0;
    if (octave_radius == 0.0) return requested_removal;
    float remaining = max(0.0, budget - used_radius) * (1.0 - 1.0 / 4096.0);
    return min(requested_removal, min(1.0, remaining / octave_radius));
}

// Diagnostic certificate; unproved/singular inputs receive the maximal pi bound.
// All returned values are radians, independent of camera/world units.
float shaderlod_normal_angle_bound(float raw_xy_error_radius, float fixed_z) {
    if (!shaderlod_normal_error_finite(raw_xy_error_radius) || raw_xy_error_radius < 0.0 ||
        !shaderlod_normal_error_finite(fixed_z) || fixed_z <= 0.0) return 3.141592654;
    float ratio = 0.5 * (raw_xy_error_radius / fixed_z);
    if (!shaderlod_normal_error_finite(ratio)) return 3.141592654;
    return 2.0 * atan(ratio);
}
#endif
