#if !defined SHADERLOD_SURFACE_REGION_CONTINUITY_INCLUDED
#define SHADERLOD_SURFACE_REGION_CONTINUITY_INCLUDED
// ShaderLOD original helper. SPDX-License-Identifier: MIT
// Camera-relative VIEW positions and normals, distances in blocks. No pack/transport dependency.
struct ShaderlodRegionLimits {
    float plane_absolute_blocks;
    float plane_relative;
    float plane_max_blocks;
    float minimum_normal_cosine;
    float maximum_lightmap_delta;
    float minimum_area_ratio;
    vec2 maximum_ao_shadow_spread;
};
ShaderlodRegionLimits shaderlod_region_default_limits() {
    return ShaderlodRegionLimits(0.015, 0.015, 0.08, 0.995,
        0.003921568627451, 0.0001, vec2(0.10, 0.025));
}
bool shaderlod_region_finite(float x) { return !isnan(x) && !isinf(x); }
bool shaderlod_region_finite(vec2 x) { return !any(isnan(x)) && !any(isinf(x)); }
bool shaderlod_region_finite(vec3 x) { return !any(isnan(x)) && !any(isinf(x)); }
bool shaderlod_region_limits_valid(ShaderlodRegionLimits l) {
    return shaderlod_region_finite(l.plane_absolute_blocks) &&
        shaderlod_region_finite(l.plane_relative) && shaderlod_region_finite(l.plane_max_blocks) &&
        shaderlod_region_finite(l.minimum_normal_cosine) && shaderlod_region_finite(l.maximum_lightmap_delta) &&
        shaderlod_region_finite(l.minimum_area_ratio) && shaderlod_region_finite(l.maximum_ao_shadow_spread) &&
        l.plane_absolute_blocks >= 0.0 && l.plane_absolute_blocks <= l.plane_max_blocks && l.plane_max_blocks <= 2.0 &&
        l.plane_relative >= 0.0 && l.plane_relative <= 0.25 &&
        l.minimum_normal_cosine >= 0.0 && l.minimum_normal_cosine <= 1.0 &&
        l.maximum_lightmap_delta >= 0.0 && l.maximum_lightmap_delta <= 1.0 &&
        l.minimum_area_ratio > 0.0 && l.minimum_area_ratio <= 1.0 &&
        all(greaterThanEqual(l.maximum_ao_shadow_spread, vec2(0.0))) &&
        all(lessThanEqual(l.maximum_ao_shadow_spread, vec2(1.0)));
}

// Active members occupy the first width*width slots, row-major, stride=width.
// Width 1 explicitly means ORIGINAL FULL RATE; width 2/4 may permit sharing.
// ALL active members/pairs/cells are checked; corners alone never license a region.
// protected_members includes sky/hand/invalid-depth/odd-edge/special receivers.
// Optional AO/shadow spread is heuristic, not proof that thin cast shadows are absent.
// Call before representative lighting. False requires ORIGINAL FULL RATE.
bool shaderlod_surface_region_eligible(bool enabled, int region_width,
    vec3 positions_view[16], vec3 normals_view[16], int material_ids[16],
    vec2 lightmaps[16], bool protected_members[16], float near_blocks,
    bool lighting_spread_available, vec2 ao_shadow_spread, ShaderlodRegionLimits limits) {
    if (!enabled || (region_width != 2 && region_width != 4) ||
        !shaderlod_region_limits_valid(limits) || !shaderlod_region_finite(near_blocks) ||
        near_blocks < 0.0 || near_blocks > 65536.0) return false;
    if (lighting_spread_available && (!shaderlod_region_finite(ao_shadow_spread) ||
        any(lessThan(ao_shadow_spread, vec2(0.0))) ||
        any(greaterThan(ao_shadow_spread, limits.maximum_ao_shadow_spread)))) return false;
    int count = region_width * region_width;
    for (int i = 0; i < 16; ++i) {
        if (i >= count) break;
        if (protected_members[i] || material_ids[i] < 0 || material_ids[i] != material_ids[0] ||
            !shaderlod_region_finite(positions_view[i]) ||
            any(greaterThan(abs(positions_view[i]), vec3(65536.0))) ||
            !shaderlod_region_finite(normals_view[i]) ||
            abs(dot(normals_view[i], normals_view[i]) - 1.0) > 0.001 ||
            !shaderlod_region_finite(lightmaps[i]) ||
            any(lessThan(lightmaps[i], vec2(0.0))) || any(greaterThan(lightmaps[i], vec2(1.0))) ||
            length(positions_view[i]) <= near_blocks) return false;
    }
    float footprint_blocks = 1e20;
    for (int y = 0; y < 3; ++y) {
        if (y >= region_width - 1) break;
        for (int x = 0; x < 3; ++x) {
            if (x >= region_width - 1) break;
            int a = y * region_width + x;
            int b = a + 1;
            int c = a + region_width;
            int d = c + 1;
            vec3 ab = positions_view[b] - positions_view[a];
            vec3 ac = positions_view[c] - positions_view[a];
            vec3 dc = positions_view[c] - positions_view[d];
            vec3 db = positions_view[b] - positions_view[d];
            float lab = length(ab), lac = length(ac), ldc = length(dc), ldb = length(db);
            vec3 first_area = cross(ab, ac), second_area = cross(dc, db);
            if (min(min(lab, lac), min(ldc, ldb)) <= 0.000001 ||
                length(first_area) <= limits.minimum_area_ratio * lab * lac ||
                length(second_area) <= limits.minimum_area_ratio * ldc * ldb ||
                dot(first_area, second_area) <= 0.0) return false;
            // Smallest adjacent pixel edge across EVERY cell; a 4x4 region does
            // not gain looser plane tolerance merely by using a larger region.
            footprint_blocks = min(footprint_blocks, min(min(lab, lac), min(ldc, ldb)));
        }
    }
    float plane_tolerance = min(limits.plane_max_blocks,
        limits.plane_absolute_blocks + limits.plane_relative * footprint_blocks);
    for (int i = 0; i < 16; ++i) {
        if (i >= count) break;
        for (int j = i + 1; j < 16; ++j) {
            if (j >= count) break;
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
#endif
