#if !defined SHADERLOD_VOLUME_BUDGET_INCLUDED
#define SHADERLOD_VOLUME_BUDGET_INCLUDED

// Uniform-free policy for explicit volumetric adapters. All distances are blocks.
// The adapter supplies the real ray interval, native density and quality floor.
// A work cap changes quadrature, never extinction, scattering or density strength.
bool shaderlod_volume_finite(float v) { return !isnan(v) && !isinf(v); }

bool shaderlod_volume_bands_valid(vec3 bands, vec2 retained) {
    return !any(isnan(bands)) && !any(isinf(bands)) &&
        !any(isnan(retained)) && !any(isinf(retained)) &&
        bands.x > 0.0 && bands.y > bands.x && bands.y <= 4096.0 &&
        bands.z >= 0.0 && bands.z <= 512.0 &&
        (bands.z == 0.0 || bands.z >= 0.0009765625) &&
        bands.z <= min(2.0 * bands.x, bands.y - bands.x) &&
        retained.y >= 0.0 && retained.y <= retained.x && retained.x <= 1.0;
}

float shaderlod_volume_blend(float distance_blocks, float boundary, float width) {
    if (width == 0.0) return float(distance_blocks >= boundary);
    return smoothstep(boundary - 0.5 * width, boundary + 0.5 * width, distance_blocks);
}

// footprint_blocks is the smallest final-pixel footprint at the volume entry;
// resolved_feature_blocks is an adapter-proven spatial feature scale, not a derivative.
// protected_weight smoothly restores overhead/other salient views to full work.
// Invalid input, empty intervals and budgets below the floor retain original work.
float shaderlod_volume_work_target(uint original, uint minimum, bool enabled,
    float entry_blocks, float interval_blocks, float footprint_blocks,
    float resolved_feature_blocks, float protected_weight, vec3 bands, vec2 retained) {
    float full = float(original);
    if (!enabled || original < minimum || minimum < 1u || original > 65536u ||
        !shaderlod_volume_bands_valid(bands, retained) ||
        !shaderlod_volume_finite(entry_blocks) || entry_blocks < 0.0 || entry_blocks > 16777216.0 ||
        !shaderlod_volume_finite(interval_blocks) || interval_blocks <= 0.0 || interval_blocks > 16777216.0 ||
        !shaderlod_volume_finite(footprint_blocks) || footprint_blocks <= 0.0 ||
        !shaderlod_volume_finite(resolved_feature_blocks) || resolved_feature_blocks <= 0.0 ||
        !shaderlod_volume_finite(protected_weight) || protected_weight < 0.0 || protected_weight > 1.0)
        return full;
    float medium = shaderlod_volume_blend(entry_blocks, bands.x, bands.z);
    float far_weight = shaderlod_volume_blend(entry_blocks, bands.y, bands.z);
    float ratio = mix(mix(1.0, retained.x, medium), retained.y, far_weight);
    float footprint_weight = smoothstep(resolved_feature_blocks, 2.0 * resolved_feature_blocks, footprint_blocks);
    ratio = mix(1.0, ratio, footprint_weight * (1.0 - protected_weight));
    return clamp(full * ratio, float(minimum), full);
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
