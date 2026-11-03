#ifndef SHADERLOD_PROJECTED_MATERIAL_BUDGET_INCLUDED
#define SHADERLOD_PROJECTED_MATERIAL_BUDGET_INCLUDED
#include "/include/shaderlod/work_policy.glsl"

// Uniform-free primary height-march policy. Adapter supplies a full [0,1]
// local-tile UV ray and unconditional atlas derivatives in UV / raster pixel.
// distance_blocks is radial Minecraft blocks; output_pixels_per_raster_pixel
// is a dimensionless raster-to-output scale, NOT a block-distance conversion.
// The floor bounds step spacing only. It cannot prove the first height hit.
int shaderlod_projected_material_budget(ShaderlodWorkPolicy policy,
    bool capability_valid, bool quality_valid, bool protected_work,
    float distance_blocks, int upstream_work, vec2 full_ray_tile_uv,
    vec2 tile_uv_dx, vec2 tile_uv_dy, vec2 tile_size_texels,
    float output_pixels_per_raster_pixel) {
    if (upstream_work < 1 || upstream_work > 65536) return upstream_work;
    int half_floor = (upstream_work + 1) / 2;
    ShaderlodWorkDecision decision = shaderlod_work_decide(policy, capability_valid,
        quality_valid, protected_work, distance_blocks, upstream_work, half_floor);
    // Avoid projection ALU for disabled/Quality/protected/near/full-policy work.
    if (decision.realized_work == upstream_work) return upstream_work;
    if (any(isnan(full_ray_tile_uv)) || any(isinf(full_ray_tile_uv)) ||
        any(isnan(tile_uv_dx)) || any(isinf(tile_uv_dx)) ||
        any(isnan(tile_uv_dy)) || any(isinf(tile_uv_dy)) ||
        any(isnan(tile_size_texels)) || any(isinf(tile_size_texels)) ||
        any(lessThan(tile_size_texels, vec2(1.0))) ||
        any(greaterThan(tile_size_texels, vec2(65536.0))) ||
        !(output_pixels_per_raster_pixel >= 1.0 && output_pixels_per_raster_pixel <= 16.0))
        return upstream_work;
    float determinant = tile_uv_dx.x * tile_uv_dy.y - tile_uv_dy.x * tile_uv_dx.y;
    float largest_column2 = max(dot(tile_uv_dx, tile_uv_dx), dot(tile_uv_dy, tile_uv_dy));
    // Relative determinant test rejects singular and ill-conditioned local planes.
    if (!(largest_column2 > 1e-20 && largest_column2 < 1e8 &&
        abs(determinant) > 0.01 * largest_column2)) return upstream_work;
    vec2 ray_raster_pixels = vec2(
        tile_uv_dy.y * full_ray_tile_uv.x - tile_uv_dy.x * full_ray_tile_uv.y,
        -tile_uv_dx.y * full_ray_tile_uv.x + tile_uv_dx.x * full_ray_tile_uv.y) / determinant;
    float full_ray_pixels = length(ray_raster_pixels) * output_pixels_per_raster_pixel;
    float full_ray_texels = length(full_ray_tile_uv * tile_size_texels);
    // <= .25 output pixel and <= .5 base-level texel per nominal depth step.
    // Full range, no material min/max guess, extra sample, pass or mip dependency.
    float spacing_floor = ceil(max(full_ray_pixels * 4.0, full_ray_texels * 2.0));
    if (!(spacing_floor >= 0.0 && spacing_floor < float(upstream_work))) return upstream_work;
    return min(upstream_work, max(decision.realized_work, int(spacing_floor)));
}
#endif
