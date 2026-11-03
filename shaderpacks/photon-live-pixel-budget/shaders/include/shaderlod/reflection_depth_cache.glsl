#if !defined INCLUDE_SHADERLOD_REFLECTION_DEPTH_CACHE
#define INCLUDE_SHADERLOD_REFLECTION_DEPTH_CACHE
// ShaderLOD bounded footprint refinement reference adapter, derived from Photon v1.3b
// by Benjamin Stott (SixthSurge). Original Photon LICENSE remains applicable.
// Included after Photon's unmodified raytracer; reflection callers only.
// Full intersection coverage; bounded refinement savings plus exact texel reuse.
// No new samplers, mip levels, derivative assumptions, or fewer reflection rays.

// Bound the original remaining float32 updates under Photon's pinned project()
// structure. Only 2..7 remaining steps are eligible; larger budgets continue.
// u=2^-24; the 1e-6 homogeneous envelope exceeds the <=7-add gamma bound,
// two NDC conversions and project/delta rounding, including FMA/non-FMA forms.
// Finite/range checks keep products/squares below float overflow; tiny
// terms have an explicit error floor and cannot defeat the world-error reserve.
bool shaderlod_reflection_view_bound(vec3 ray_pos, vec3 ray_step, float extent) {
    vec4 diagonal_projection = vec4(SSRT_PROJECTION_MATRIX_INVERSE[0].x,
        SSRT_PROJECTION_MATRIX_INVERSE[1].y, SSRT_PROJECTION_MATRIX_INVERSE[2].zw);
    vec4 projection_offset = SSRT_PROJECTION_MATRIX_INVERSE[3];
    if (any(isnan(diagonal_projection)) || any(isinf(diagonal_projection)) ||
        any(isnan(projection_offset)) || any(isinf(projection_offset)) ||
        any(isnan(ray_pos)) || any(isinf(ray_pos)) ||
        any(isnan(ray_step)) || any(isinf(ray_step)) ||
        any(greaterThan(abs(diagonal_projection), vec4(1e6))) ||
        any(greaterThan(abs(projection_offset), vec4(1e6))) ||
        any(greaterThan(abs(ray_pos), vec3(4.0))) ||
        any(greaterThan(abs(ray_step), vec3(4.0)))) {
        return false;
    }

    // Necessary scalar view-z bound comes first. No division, x/y inverse work,
    // or three endpoint projections are paid when depth amplification rejects.
    float ndc_z = 2.0 * ray_pos.z - 1.0;
    float delta_z = 2.0 * ray_step.z * extent;
    vec2 h_zw = diagonal_projection.zw * ndc_z + projection_offset.zw;
    vec2 dh_zw = diagonal_projection.zw * delta_z;
    vec2 error_zw = 1e-6 * (abs(diagonal_projection.zw) *
        (2.0 * abs(ray_pos.z) + 2.0 * abs(ray_step.z) + 1.0) +
        2.0 * abs(projection_offset.zw) + abs(h_zw) + abs(dh_zw)) + vec2(1e-30);
    float abs_w = abs(h_zw.y);
    float min_w = abs_w - abs(dh_zw.y) - error_zw.y;
    if (!(abs_w > 1e-6 && min_w > 1e-6)) {
        return false; // no homogeneous pole, including float rounding envelope
    }
    float denominator = (abs_w * min_w) * 0.99999;
    float numerator_z = abs(dh_zw.x * h_zw.y - h_zw.x * dh_zw.y) +
        1e-6 * (abs(dh_zw.x * h_zw.y) + abs(h_zw.x * dh_zw.y)) +
        error_zw.x * abs_w + abs(h_zw.x) * error_zw.y;
    // Account for the two final view-space divisions' float32 rounding without
    // evaluating them: multiply their error bounds by the common denominator.
    numerator_z += 1e-6 * (abs(h_zw.x) * min_w +
        (abs(h_zw.x) + abs(dh_zw.x) + error_zw.x) * abs_w);
    numerator_z *= 1.00001;
    if (!(numerator_z <= 0.049 * denominator)) {
        return false;
    }

    vec2 ndc_xy = 2.0 * ray_pos.xy - 1.0;
    vec2 delta_xy = 2.0 * ray_step.xy * extent;
    vec2 h_xy = diagonal_projection.xy * ndc_xy + projection_offset.xy;
    vec2 dh_xy = diagonal_projection.xy * delta_xy;
    vec2 error_xy = 1e-6 * (abs(diagonal_projection.xy) *
        (2.0 * abs(ray_pos.xy) + 2.0 * abs(ray_step.xy) + 1.0) +
        2.0 * abs(projection_offset.xy) + abs(h_xy) + abs(dh_xy)) + vec2(1e-30);
    vec2 numerator_xy = abs(dh_xy * h_zw.y - h_xy * dh_zw.y) +
        1e-6 * (abs(dh_xy * h_zw.y) + abs(h_xy * dh_zw.y)) +
        error_xy * abs_w + abs(h_xy) * error_zw.y;
    numerator_xy += 1e-6 * (abs(h_xy) * min_w +
        (abs(h_xy) + abs(dh_xy) + error_xy) * abs_w);
    numerator_xy *= 1.00001;
    vec3 numerator = vec3(numerator_xy, numerator_z);
    float numerator_squared = dot(numerator, numerator);
    float denominator_squared = denominator * denominator;
    // Never accept overflow, cancellation-induced NaN, or Inf<=Inf.
    return !isnan(numerator_squared) && !isinf(numerator_squared) &&
        !isnan(denominator_squared) && !isinf(denominator_squared) &&
        denominator_squared > 0.0 &&
        numerator_squared <= 0.002401 * denominator_squared * 0.99999;
}

bool shaderlod_raymarch_depth_cached(
    vec3 screen_pos,
    vec3 view_pos,
    vec3 view_dir,
    float dither,
    uint intersection_step_count,
    uint refinement_step_count,
    out vec3 ray_pos,
    float refinement_tolerance_pixels
) {
    if (view_dir.z > 0.0 && view_dir.z >= -view_pos.z) {
        return false;
    }

    vec3 screen_dir = normalize(
        view_to_screen_space(
            SSRT_PROJECTION_MATRIX,
            view_pos + view_dir,
            true
        ) -
        screen_pos
    );

    float ray_length =
        min_of(abs(sign(screen_dir) - screen_pos) / max(abs(screen_dir), eps));

    if (shaderlod_optimization_enabled(shaderlodOptReflectionTraceBudget))
        intersection_step_count = uint(shaderlod_refinement_budget(shaderlodBridgeVersion,
            shaderlodEnabled, shaderlodPreset, length(view_pos), int(intersection_step_count), 4));
    float step_length = ray_length * rcp(float(intersection_step_count));

    vec3 ray_step = screen_dir * step_length;
    ray_pos =
        screen_pos + dither * ray_step + length(view_pixel_size) * screen_dir;

    float depth_tolerance =
        max(abs(ray_step.z) * 3.0, 0.02 / sqr(view_pos.z)); // from DrDesten <3

    // One immutable depth attachment during this draw; integer coordinates/LOD 0.
    // Out-of-range texels are never cached (upstream fetch behavior retained).
    ivec2 depth_size = textureSize(SSRT_DEPTH_SAMPLER, 0);
    ivec2 cached_texel = ivec2(-1);
    float cached_depth = 0.0;
    bool cache_valid = false;

    bool hit = false;

    // Intersection loop

    for (int i = 0; i < intersection_step_count; ++i, ray_pos += ray_step) {
#ifdef LOD_MOD_ACTIVE
        if (ray_pos.z < 0.0) {
            continue;
        }
#endif
        if (clamp01(ray_pos) != ray_pos) {
            return false;
        }

        ivec2 depth_texel = ivec2(ray_pos.xy * view_res * taau_render_scale);
        bool texel_in_bounds = all(greaterThanEqual(depth_texel, ivec2(0))) &&
            all(lessThan(depth_texel, depth_size));
        float depth;
        if (cache_valid && texel_in_bounds && depth_texel == cached_texel) {
            depth = cached_depth;
        } else {
            depth = texelFetch(SSRT_DEPTH_SAMPLER, depth_texel, 0).x;
            cached_depth = depth;
            cached_texel = depth_texel;
            cache_valid = texel_in_bounds;
        }

        if (depth < ray_pos.z &&
            abs(depth_tolerance - (ray_pos.z - depth)) < depth_tolerance) {
            hit = true;
            break;
        }
    }

    if (!hit) {
        return false;
    }

    if (shaderlod_optimization_enabled(shaderlodOptReflectionRefinement))
        refinement_step_count = uint(shaderlod_refinement_budget(shaderlodBridgeVersion,
            shaderlodEnabled, shaderlodPreset, length(view_pos), int(refinement_step_count), 1));
    // Refinement loop

    float final_depth;

    for (int i = 0; i < refinement_step_count; ++i) {
        ray_step *= 0.5;

        ivec2 depth_texel = ivec2(ray_pos.xy * view_res * taau_render_scale);
        bool texel_in_bounds = all(greaterThanEqual(depth_texel, ivec2(0))) &&
            all(lessThan(depth_texel, depth_size));
        float depth;
        if (cache_valid && texel_in_bounds && depth_texel == cached_texel) {
            depth = cached_depth;
        } else {
            depth = texelFetch(SSRT_DEPTH_SAMPLER, depth_texel, 0).x;
            cached_depth = depth;
            cached_texel = depth_texel;
            cache_valid = texel_in_bounds;
        }

        if (depth < ray_pos.z &&
            abs(depth_tolerance - (ray_pos.z - depth)) < depth_tolerance) {
            ray_pos -= ray_step;
        } else {
            ray_pos += ray_step;
        }

        final_depth = depth;

        // All remaining upstream binary steps fit in this conservative envelope.
        // Only stop when every future fetch would address this same valid texel.
        // Preserve depth/hand/sky eligibility; hit position error is <= tolerance.
        int remaining_steps = int(refinement_step_count) - i - 1;
        if (remaining_steps >= 2 && remaining_steps <= 7 && cache_valid &&
            refinement_tolerance_pixels > 0.0) {
            // Exact binary fractions for this conservative upstream 0..8 subset.
            // Avoid an exp2 accuracy assumption and reuse the same extent below.
            float extent = 1.0 - float(1 << (7 - remaining_steps)) * (1.0 / 128.0);
            vec3 remaining_span = abs(ray_step) * extent;
            vec2 span_pixels = remaining_span.xy * view_res * taau_render_scale;
            if (!(max(span_pixels.x, span_pixels.y) <= refinement_tolerance_pixels &&
                remaining_span.z <= depth_tolerance * 0.0625)) {
                continue; // necessary gates before envelope/inverse work
            }
            vec2 lower_pixels = (ray_pos.xy - remaining_span.xy) * view_res * taau_render_scale;
            vec2 upper_pixels = (ray_pos.xy + remaining_span.xy) * view_res * taau_render_scale;
            // Insets cover conservative float rounding around texel boundaries.
            // Reprojection/material/fog equivalence still needs driver motion QA.
            vec2 rounding_margin = max(vec2(1e-4), abs(upper_pixels) * 2e-6);
            bool one_texel_envelope = all(greaterThanEqual(
                lower_pixels - rounding_margin, vec2(cached_texel))) &&
                all(lessThan(upper_pixels + rounding_margin, vec2(cached_texel + ivec2(1))));
            float z_margin = max(2e-6, depth_tolerance * 1e-4);
            float lower_z = ray_pos.z - remaining_span.z;
            float upper_z = ray_pos.z + remaining_span.z;
            bool uniformly_inside_hit = lower_z > depth + z_margin &&
                upper_z < depth + 2.0 * depth_tolerance - z_margin;
            bool uniformly_outside_hit = upper_z < depth - z_margin ||
                lower_z > depth + 2.0 * depth_tolerance + z_margin;
            if (one_texel_envelope && (uniformly_inside_hit || uniformly_outside_hit) &&
                shaderlod_reflection_view_bound(ray_pos, ray_step, extent)) {
                break; // <=4.9 cm including float envelope; 1 mm cap reserve.
            }
        }
    }

    // No intersection if the binary search landed on a sky pixel or a hand
    // pixel
    return hand_depth < final_depth && final_depth < 1.0;
}

// Camera-relative view-space distances are Minecraft blocks. The transition
// increases allowed refinement error from zero near to 0.25 depth texel far.
// Original coarse traversal and reflection ray accumulation remain unchanged.
bool shaderlod_reflection_depth_trace(
    vec3 screen_pos, vec3 view_pos, vec3 view_dir, float dither,
    uint intersection_step_count, uint refinement_step_count, out vec3 ray_pos
) {
    bool policy_valid = shaderlod_optimization_enabled(shaderlodOptReflectionProjectedRefinement) &&
        !isnan(shaderlodNear) && !isinf(shaderlodNear) &&
        !isnan(shaderlodFar) && !isinf(shaderlodFar) &&
        !isnan(shaderlodTransition) && !isinf(shaderlodTransition) &&
        shaderlodNear >= 0.0 && shaderlodFar > shaderlodNear &&
        shaderlodTransition > 0.0 &&
        shaderlodTransition <= shaderlodFar - shaderlodNear &&
        dither >= 0.0 && dither < 1.0 && refinement_step_count > 0u &&
        intersection_step_count > 0u && intersection_step_count <= 256u &&
        refinement_step_count <= 64u;
    if (policy_valid) {
        float distance_blocks = length(view_pos);
        if (isnan(distance_blocks) || isinf(distance_blocks)) {
            return raymarch_depth_buffer(screen_pos, view_pos, view_dir, dither,
                intersection_step_count, refinement_step_count, ray_pos);
        }
        float near_end = shaderlodNear + 0.5 * shaderlodTransition;
        float far_end = shaderlodFar + 0.5 * shaderlodTransition;
        float reuse_weight = smoothstep(near_end, far_end, distance_blocks);
        if (reuse_weight > 0.0) {
            return shaderlod_raymarch_depth_cached(
                screen_pos, view_pos, view_dir, dither,
                intersection_step_count, refinement_step_count, ray_pos, 0.25 * reuse_weight
            );
        }
    }
    // Absent/unknown/disabled bridge, invalid policy, full near, and unsupported
    // refinement counts execute the literal upstream function and its budgets.
    return raymarch_depth_buffer(
        screen_pos, view_pos, view_dir, dither,
        intersection_step_count, refinement_step_count, ray_pos
    );
}
#endif // INCLUDE_SHADERLOD_REFLECTION_DEPTH_CACHE
