// ShaderLOD existing-pass shadow tail budget.
// PCF integration derives from Photon v1.3b by Benjamin Stott (SixthSurge).
// Retain upstream LICENSE in every derivative; this helper does not replace it.
// General policy: preserve cheap certainty probes; spend fewer samples on the
// remaining annulus only when distant and its projected blur radius is small.

int shaderlod_shadow_budget(int original_steps, vec3 scene_pos, float filter_radius, float sss_amount) {
    // Unknown bridge and every protected optical path retain original work.
    if (!shaderlod_optimization_enabled(shaderlodOptShadowSampleBudget) ||
        original_steps <= 6 || sss_amount != 0.0) {
        return original_steps;
    }
#if defined SHADOW_VPS || defined SHADOW_COLOR || defined PIXELATED_SHADOWS
    return original_steps;
#else
    float distance_blocks = length(scene_pos); // camera-relative world blocks
    if (isnan(distance_blocks) || isinf(distance_blocks) || distance_blocks <= 64.0) return original_steps;
    float view_depth = -(mat3(gbufferModelView) * scene_pos).z;
    float projection_scale = abs(shadowProjection[0].x);
    if (isnan(view_depth) || isinf(view_depth) || view_depth <= 0.0 ||
        isnan(projection_scale) || isinf(projection_scale) || projection_scale <= 0.0) return original_steps;
    // Convert the unchanged shadow clip-space disc radius to world blocks,
    // then to a conservative screen radius in full-resolution output pixels.
    float world_radius = filter_radius / projection_scale;
    float pixel_scale = 0.5 * max(abs(gbufferProjection[0].x) * view_res.x,
                                  abs(gbufferProjection[1].y) * view_res.y);
    float radius_pixels = world_radius * pixel_scale / view_depth;
    if (isnan(radius_pixels) || isinf(radius_pixels) || radius_pixels >= 2.0) return original_steps;
    float distance_mix = clamp((distance_blocks - 64.0) / 48.0, 0.0, 1.0);
    float footprint_mix = clamp(2.0 - radius_pixels, 0.0, 1.0);
    int tail_steps = original_steps - 4;
    int kept_tail = int(ceil(float(tail_steps) * (1.0 - 0.5 * distance_mix * footprint_mix)));
    return 4 + max(2, kept_tail);
#endif
}
