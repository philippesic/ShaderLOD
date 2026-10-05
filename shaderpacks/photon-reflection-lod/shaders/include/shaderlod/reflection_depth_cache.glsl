#if !defined INCLUDE_SHADERLOD_REFLECTION_DEPTH_CACHE
#define INCLUDE_SHADERLOD_REFLECTION_DEPTH_CACHE
// ShaderLOD bounded footprint refinement reference adapter, derived from Photon v1.3b
// by Benjamin Stott (SixthSurge). Original Photon LICENSE remains applicable.
// Included after Photon's unmodified raytracer; reflection callers only.
// Full intersection coverage; bounded refinement savings plus exact texel reuse.
// No new samplers, mip levels, derivative assumptions, or fewer reflection rays.
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform float shaderlodNear;
uniform float shaderlodFar;
uniform float shaderlodTransition;

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
        if (remaining_steps > 0 && cache_valid && refinement_tolerance_pixels > 0.0) {
            vec3 remaining_span = abs(ray_step) *
                (1.0 - exp2(-float(remaining_steps)));
            vec2 span_pixels = remaining_span.xy * view_res * taau_render_scale;
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
                max(span_pixels.x, span_pixels.y) <= refinement_tolerance_pixels &&
                remaining_span.z <= depth_tolerance * 0.0625) {
                // Screen depth is nonlinear. Also bound the actual possible hit
                // displacement to 5 cm in camera-relative Minecraft view space.
                // Future positions lie on this line segment; each projected
                // coordinate is monotone provided homogeneous W has no pole.
                float extent = 1.0 - exp2(-float(remaining_steps));
                vec4 center_h = project(SSRT_PROJECTION_MATRIX_INVERSE, 2.0 * ray_pos - 1.0);
                vec4 lower_h = project(SSRT_PROJECTION_MATRIX_INVERSE,
                    2.0 * (ray_pos - ray_step * extent) - 1.0);
                vec4 upper_h = project(SSRT_PROJECTION_MATRIX_INVERSE,
                    2.0 * (ray_pos + ray_step * extent) - 1.0);
                bool safe_w = abs(center_h.w) > 1e-6 && abs(lower_h.w) > 1e-6 &&
                    abs(upper_h.w) > 1e-6 && lower_h.w * upper_h.w > 0.0;
                if (safe_w) {
                    vec3 center_view = center_h.xyz / center_h.w;
                    vec3 max_view_error = max(abs(lower_h.xyz / lower_h.w - center_view),
                        abs(upper_h.xyz / upper_h.w - center_view));
                    if (dot(max_view_error, max_view_error) <= 0.002401) {
                        break; // 1 mm reserved for float rounding in a 5 cm cap.
                    }
                }
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
    float distance_blocks = length(view_pos);
    bool policy_valid = shaderlodBridgeVersion == 2 && shaderlodEnabled == 1 &&
        !isnan(shaderlodNear) && !isinf(shaderlodNear) &&
        !isnan(shaderlodFar) && !isinf(shaderlodFar) &&
        !isnan(shaderlodTransition) && !isinf(shaderlodTransition) &&
        shaderlodNear >= 0.0 && shaderlodFar > shaderlodNear &&
        shaderlodTransition > 0.0 &&
        shaderlodTransition <= shaderlodFar - shaderlodNear &&
        !isnan(distance_blocks) && !isinf(distance_blocks) &&
        dither >= 0.0 && dither < 1.0 && refinement_step_count > 0u &&
        intersection_step_count > 0u && intersection_step_count <= 256u &&
        refinement_step_count <= 64u;
    if (policy_valid) {
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
