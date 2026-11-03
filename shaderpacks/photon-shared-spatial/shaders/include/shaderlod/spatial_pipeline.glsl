#if !defined SHADERLOD_SPATIAL_PIPELINE_INCLUDED
#define SHADERLOD_SPATIAL_PIPELINE_INCLUDED
// ShaderLOD original shared mechanism. SPDX-License-Identifier: MIT
// Include work_policy.glsl, spatial_plan.glsl and surface_region_continuity.glsl first.
// This kernel contains no pack decode, sampler, derivative, final-color cache or history.
struct ShaderlodSpatialSurface {
    vec3 position_view_blocks;
    vec3 normal_view;
    int factorization_class;
    vec2 lightmap;
    bool protected_receiver;
};
struct ShaderlodSpatialSpread { bool available; vec2 ao_shadow; };
// quantity_key identifies an adapter-declared LINEAR vec4 reconstruction basis.
// epoch identifies this exact frame, dimensions, source/options and policy snapshot.
struct ShaderlodSpatialCache {
    vec4 linear_quantity;
    bool valid;
    int quantity_key;
    int epoch;
    int side;
    ivec2 tile_coordinate;
    float minimum_distance_blocks;
};
struct ShaderlodSpatialTile {
    bool eligible;
    ivec2 origin;
    ivec2 representative;
    float minimum_distance_blocks;
    ShaderlodSpatialSurface representative_surface;
};
struct ShaderlodSpatialResult {
    bool full_resolution_required;
    vec4 linear_quantity;
    vec3 weights;
};
// Adapter callbacks: surface/probe are cheap validated CURRENT-frame inputs.
// Evaluate must not use implicit derivatives in divergent representative paths.
// Return albedo-independent irradiance or a declared linear shading quantity only.
// Full-resolution albedo/material/specular/reflections/emission/fog stay outside.
// Uniform policy/capability/quality preflight, with no surface sampling.
bool shaderlod_spatial_adapter_has_coarse_demand();
ShaderlodSpatialSurface shaderlod_spatial_adapter_surface(ivec2 pixel);
ShaderlodSpatialSpread shaderlod_spatial_adapter_spread(ivec2 origin, int side);
ShaderlodSpatialPlan shaderlod_spatial_adapter_plan(float distance_blocks);
vec4 shaderlod_spatial_adapter_evaluate_linear(ivec2 pixel, ShaderlodSpatialSurface surface);
ShaderlodSpatialCache shaderlod_spatial_adapter_cache(int side, ivec2 tile_coordinate);

bool shaderlod_spatial_inside(ivec2 pixel, ivec2 dimensions) {
    return all(greaterThanEqual(pixel, ivec2(0))) && all(lessThan(pixel, dimensions));
}
ivec2 shaderlod_spatial_cache_dimensions(ivec2 dimensions, int side) {
    // Avoid dimension+side overflow. Incomplete edge tiles have invalid cache entries.
    if ((side != 2 && side != 4) || any(lessThanEqual(dimensions, ivec2(0)))) return ivec2(0);
    return dimensions / side + ivec2(notEqual(dimensions % side, ivec2(0)));
}
ShaderlodSpatialSurface shaderlod_spatial_empty_surface() {
    return ShaderlodSpatialSurface(vec3(0.0), vec3(0.0, 0.0, 1.0), -1, vec2(0.0), true);
}
ShaderlodSpatialCache shaderlod_spatial_invalid_cache(int quantity_key, int epoch) {
    return ShaderlodSpatialCache(vec4(0.0), false, quantity_key, epoch, 0, ivec2(-1), 0.0);
}
ShaderlodSpatialResult shaderlod_spatial_full() {
    // Caller executes its untouched original full-resolution path, not zero shading.
    return ShaderlodSpatialResult(true, vec4(0.0), vec3(1.0, 0.0, 0.0));
}
bool shaderlod_spatial_plan_valid(ShaderlodSpatialPlan plan) {
    return shaderlod_region_finite(plan.weights) &&
        all(greaterThanEqual(plan.weights, vec3(0.0))) &&
        all(lessThanEqual(plan.weights, vec3(1.0))) &&
        abs(plan.weights.x + plan.weights.y + plan.weights.z - 1.0) <= 0.000001 &&
        plan.evaluate_full == (plan.weights.x > 0.0) &&
        plan.evaluate_medium == (plan.weights.y > 0.0) &&
        plan.evaluate_far == (plan.weights.z > 0.0) &&
        plan.reason >= SHADERLOD_WORK_POLICY && plan.reason <= SHADERLOD_WORK_QUALITY_FALLBACK &&
        (plan.reason == SHADERLOD_WORK_POLICY || (!plan.evaluate_medium && !plan.evaluate_far));
}
ShaderlodSpatialTile shaderlod_spatial_prepare(int side, ivec2 tile_coordinate,
    ivec2 dimensions, float near_blocks, ShaderlodRegionLimits limits) {
    ShaderlodSpatialTile tile = ShaderlodSpatialTile(false, ivec2(0), ivec2(0),
        0.0, shaderlod_spatial_empty_surface());
    // Covers both quantity production and independent certificate preparation.
    if (!shaderlod_spatial_adapter_has_coarse_demand()) return tile;
    ivec2 cache_dimensions = shaderlod_spatial_cache_dimensions(dimensions, side);
    if (!shaderlod_spatial_inside(tile_coordinate, cache_dimensions) ||
        !shaderlod_region_limits_valid(limits) || !shaderlod_region_finite(near_blocks) ||
        near_blocks < 0.0 || near_blocks > 65536.0) return tile;
    tile.origin = tile_coordinate * side;
    // Bounds BEFORE callbacks. No clamp-to-edge duplicate receiver or OOB fetch.
    if (any(lessThan(dimensions - tile.origin, ivec2(side)))) return tile;
    tile.representative = tile.origin + ivec2((side - 1) / 2);
    vec3 positions[16]; vec3 normals[16]; int materials[16];
    vec2 lightmaps[16]; bool protected_members[16];
    tile.minimum_distance_blocks = 1e20;
    for (int i = 0; i < 16; ++i) {
        positions[i] = vec3(0.0); normals[i] = vec3(0.0, 0.0, 1.0);
        materials[i] = -1; lightmaps[i] = vec2(0.0); protected_members[i] = true;
        if (i < side * side) {
            ivec2 pixel = tile.origin + ivec2(i % side, i / side);
            ShaderlodSpatialSurface surface = shaderlod_spatial_adapter_surface(pixel);
            positions[i] = surface.position_view_blocks; normals[i] = surface.normal_view;
            materials[i] = surface.factorization_class; lightmaps[i] = surface.lightmap;
            protected_members[i] = surface.protected_receiver;
            tile.minimum_distance_blocks = min(tile.minimum_distance_blocks, length(positions[i]));
            if (all(equal(pixel, tile.representative))) tile.representative_surface = surface;
        }
    }
    ShaderlodSpatialSpread spread = shaderlod_spatial_adapter_spread(tile.origin, side);
    tile.eligible = shaderlod_surface_region_eligible(true, side, positions, normals,
        materials, lightmaps, protected_members, near_blocks, spread.available,
        spread.ao_shadow, limits);
    return tile;
}
ShaderlodSpatialCache shaderlod_spatial_produce(bool enabled, bool cache_contract_valid, int side,
    ivec2 tile_coordinate, ivec2 dimensions, float near_blocks,
    ShaderlodRegionLimits limits, int quantity_key, int epoch) {
    ShaderlodSpatialCache result = shaderlod_spatial_invalid_cache(quantity_key, epoch);
    if (!enabled || !cache_contract_valid) return result;
    ShaderlodSpatialTile tile = shaderlod_spatial_prepare(side, tile_coordinate, dimensions, near_blocks, limits);
    if (!tile.eligible) return result;
    ShaderlodSpatialPlan plan = shaderlod_spatial_adapter_plan(tile.minimum_distance_blocks);
    if (!shaderlod_spatial_plan_valid(plan)) return result;
    if ((side == 2 && !plan.evaluate_medium) || (side == 4 && !plan.evaluate_far)) return result;
    // All eligibility and demand checks precede expensive evaluation.
    vec4 quantity = shaderlod_spatial_adapter_evaluate_linear(tile.representative, tile.representative_surface);
    if (any(isnan(quantity)) || any(isinf(quantity))) return result;
    result.linear_quantity = quantity; result.valid = true;
    result.side = side; result.tile_coordinate = tile_coordinate;
    result.minimum_distance_blocks = tile.minimum_distance_blocks;
    return result;
}
// A certificate is meaningful only under adapter-proved exact storage and an
// immutable current surface/policy snapshot with completed producer writes.
bool shaderlod_spatial_cache_valid(ShaderlodSpatialCache cache, int quantity_key,
    int epoch, int side, ivec2 tile_coordinate, float near_blocks) {
    return cache.valid && cache.quantity_key == quantity_key && cache.epoch == epoch &&
        cache.side == side && all(equal(cache.tile_coordinate, tile_coordinate)) &&
        shaderlod_region_finite(cache.minimum_distance_blocks) &&
        cache.minimum_distance_blocks > near_blocks && cache.minimum_distance_blocks <= 16777216.0 &&
        !any(isnan(cache.linear_quantity)) && !any(isinf(cache.linear_quantity));
}
bool shaderlod_spatial_complete_tile(int side, ivec2 coordinate, ivec2 dimensions) {
    if (!shaderlod_spatial_inside(coordinate, shaderlod_spatial_cache_dimensions(dimensions, side))) return false;
    return all(greaterThanEqual(dimensions - coordinate * side, ivec2(side)));
}
ShaderlodSpatialResult shaderlod_spatial_resolve(bool cache_contract_valid,
    ivec2 pixel, ivec2 dimensions, ShaderlodSpatialPlan initial_plan,
    float near_blocks, ShaderlodRegionLimits limits, int quantity_key, int epoch) {
    ShaderlodSpatialResult result = shaderlod_spatial_full();
    if (!cache_contract_valid || !shaderlod_spatial_inside(pixel, dimensions) ||
        !shaderlod_spatial_plan_valid(initial_plan) ||
        !shaderlod_region_limits_valid(limits) || !shaderlod_region_finite(near_blocks) ||
        near_blocks < 0.0 || near_blocks > 65536.0 ||
        (!initial_plan.evaluate_medium && !initial_plan.evaluate_far)) return result;
    ShaderlodSpatialCache cache_medium = shaderlod_spatial_invalid_cache(quantity_key, epoch);
    ShaderlodSpatialCache cache_far = shaderlod_spatial_invalid_cache(quantity_key, epoch);
    float distance_blocks = 1e20;
    if (initial_plan.evaluate_medium) {
        if (!shaderlod_spatial_complete_tile(2, pixel / 2, dimensions)) return result;
        cache_medium = shaderlod_spatial_adapter_cache(2, pixel / 2);
        if (!shaderlod_spatial_cache_valid(cache_medium, quantity_key, epoch, 2, pixel / 2, near_blocks)) return result;
        distance_blocks = min(distance_blocks, cache_medium.minimum_distance_blocks);
    }
    if (initial_plan.evaluate_far) {
        if (!shaderlod_spatial_complete_tile(4, pixel / 4, dimensions)) return result;
        cache_far = shaderlod_spatial_adapter_cache(4, pixel / 4);
        if (!shaderlod_spatial_cache_valid(cache_far, quantity_key, epoch, 4, pixel / 4, near_blocks)) return result;
        distance_blocks = min(distance_blocks, cache_far.minimum_distance_blocks);
    }
    // Every member governed the producer's certificate. Replanning may remove
    // demand but must never introduce an absent tier; ambiguous agreement falls full.
    ShaderlodSpatialPlan plan = shaderlod_spatial_adapter_plan(distance_blocks);
    if (!shaderlod_spatial_plan_valid(plan) ||
        (plan.evaluate_medium && !initial_plan.evaluate_medium) ||
        (plan.evaluate_far && !initial_plan.evaluate_far) ||
        (!plan.evaluate_medium && !plan.evaluate_far)) return result;
    // No recipient region reads/classifier. Only the full basis required by a
    // transition reads this pixel; any invalid/protected receiver falls original.
    vec4 full = vec4(0.0);
    if (plan.evaluate_full) {
        ShaderlodSpatialSurface surface = shaderlod_spatial_adapter_surface(pixel);
        if (surface.protected_receiver || surface.factorization_class < 0 ||
            !shaderlod_region_finite(surface.position_view_blocks) ||
            !shaderlod_region_finite(surface.normal_view) ||
            !shaderlod_region_finite(surface.lightmap)) return result;
        full = shaderlod_spatial_adapter_evaluate_linear(pixel, surface);
        if (any(isnan(full)) || any(isinf(full))) return result;
    }
    result.full_resolution_required = false;
    result.weights = plan.weights;
    result.linear_quantity = full * plan.weights.x + cache_medium.linear_quantity * plan.weights.y +
        cache_far.linear_quantity * plan.weights.z;
    if (any(isnan(result.linear_quantity)) || any(isinf(result.linear_quantity))) return shaderlod_spatial_full();
    return result;
}
#endif
