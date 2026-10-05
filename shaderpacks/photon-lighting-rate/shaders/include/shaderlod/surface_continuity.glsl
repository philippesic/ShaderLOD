#if !defined SHADERLOD_SURFACE_CONTINUITY_INCLUDED
#define SHADERLOD_SURFACE_CONTINUITY_INCLUDED

// ShaderLOD original helper, independent of any upstream shader implementation.
// All positions/normals share CAMERA-RELATIVE VIEW space; distances are blocks.
struct ShaderlodSurfaceLimits {
    float plane_absolute_blocks;
    float plane_relative;
    float plane_max_blocks;
    float minimum_normal_cosine;
    float maximum_lightmap_delta;
    float minimum_area_ratio;
    vec2 maximum_ao_shadow_spread;
};

ShaderlodSurfaceLimits shaderlod_surface_default_limits() {
    return ShaderlodSurfaceLimits(0.015, 0.015, 0.08, 0.995,
        0.003921568627451, 0.0001, vec2(0.10, 0.025));
}

bool shaderlod_surface_finite(float x) { return !isnan(x) && !isinf(x); }
bool shaderlod_surface_finite(vec2 x) { return !any(isnan(x)) && !any(isinf(x)); }
bool shaderlod_surface_finite(vec3 x) { return !any(isnan(x)) && !any(isinf(x)); }

bool shaderlod_surface_limits_valid(ShaderlodSurfaceLimits l) {
    return shaderlod_surface_finite(l.plane_absolute_blocks) &&
        shaderlod_surface_finite(l.plane_relative) &&
        shaderlod_surface_finite(l.plane_max_blocks) &&
        shaderlod_surface_finite(l.minimum_normal_cosine) &&
        shaderlod_surface_finite(l.maximum_lightmap_delta) &&
        shaderlod_surface_finite(l.minimum_area_ratio) &&
        shaderlod_surface_finite(l.maximum_ao_shadow_spread) &&
        l.plane_absolute_blocks >= 0.0 && l.plane_absolute_blocks <= l.plane_max_blocks &&
        l.plane_max_blocks <= 2.0 && l.plane_relative >= 0.0 && l.plane_relative <= 0.25 &&
        l.minimum_normal_cosine >= 0.0 && l.minimum_normal_cosine <= 1.0 &&
        l.maximum_lightmap_delta >= 0.0 && l.maximum_lightmap_delta <= 1.0 &&
        l.minimum_area_ratio > 0.0 && l.minimum_area_ratio <= 1.0 &&
        all(greaterThanEqual(l.maximum_ao_shadow_spread, vec2(0.0))) &&
        all(lessThanEqual(l.maximum_ao_shadow_spread, vec2(1.0)));
}

// Row-major 2x2: 0=(0,0), 1=(1,0), 2=(0,1), 3=(1,1).
// protected_members includes sky, hand, invalid depth/material and special receivers.
// Optional spreads are already available normalized [0,1] AO/shadow max-minus-min;
// unavailable spreads do NOT establish that cast-shadow edges are absent.
// Call before expensive representative shading. False requires ORIGINAL FULL RATE.
bool shaderlod_surface_tile_eligible(bool enabled, vec3 positions_view[4],
    vec3 normals_view[4], int material_ids[4], vec2 lightmaps[4],
    bool protected_members[4], float near_blocks, bool lighting_spread_available,
    vec2 ao_shadow_spread, ShaderlodSurfaceLimits limits) {
    if (!enabled || !shaderlod_surface_limits_valid(limits) ||
        !shaderlod_surface_finite(near_blocks) || near_blocks < 0.0 || near_blocks > 65536.0)
        return false;
    if (lighting_spread_available &&
        (!shaderlod_surface_finite(ao_shadow_spread) ||
        any(lessThan(ao_shadow_spread, vec2(0.0))) ||
        any(greaterThan(ao_shadow_spread, limits.maximum_ao_shadow_spread))))
        return false;
    for (int i = 0; i < 4; ++i) {
        if (protected_members[i] || material_ids[i] < 0 || material_ids[i] != material_ids[0] ||
            !shaderlod_surface_finite(positions_view[i]) ||
            any(greaterThan(abs(positions_view[i]), vec3(65536.0))) ||
            !shaderlod_surface_finite(normals_view[i]) ||
            abs(dot(normals_view[i], normals_view[i]) - 1.0) > 0.001 ||
            !shaderlod_surface_finite(lightmaps[i]) ||
            any(lessThan(lightmaps[i], vec2(0.0))) || any(greaterThan(lightmaps[i], vec2(1.0))) ||
            length(positions_view[i]) <= near_blocks)
            return false;
    }
    vec3 edge_x = positions_view[1] - positions_view[0];
    vec3 edge_y = positions_view[2] - positions_view[0];
    float length_x = length(edge_x);
    float length_y = length(edge_y);
    float area = length(cross(edge_x, edge_y));
    // Reject collapsed/singular geometry before division; no derivatives involved.
    if (length_x <= 0.000001 || length_y <= 0.000001 ||
        area <= limits.minimum_area_ratio * length_x * length_y)
        return false;
    // Tangential tile size, capped tolerance: distance alone never licenses a depth edge.
    float plane_tolerance = min(limits.plane_max_blocks,
        limits.plane_absolute_blocks + limits.plane_relative * min(length_x, length_y));
    for (int i = 0; i < 4; ++i) {
        for (int j = i + 1; j < 4; ++j) {
            vec3 delta = positions_view[j] - positions_view[i];
            if (length(delta) <= 0.000001 ||
                dot(normals_view[i], normals_view[j]) < limits.minimum_normal_cosine ||
                abs(dot(delta, normals_view[i])) > plane_tolerance ||
                abs(dot(delta, normals_view[j])) > plane_tolerance ||
                any(greaterThan(abs(lightmaps[j] - lightmaps[i]), vec2(limits.maximum_lightmap_delta))))
                return false;
        }
    }
    return true;
}

#endif // SHADERLOD_SURFACE_CONTINUITY_INCLUDED
