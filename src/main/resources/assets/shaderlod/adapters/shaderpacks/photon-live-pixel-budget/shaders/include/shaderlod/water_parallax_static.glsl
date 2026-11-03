// ShaderLOD fixed-count Photon v1.3b derivative. Original attribution/license
// are retained in the assembled pack. Repeated include: names/count supplied
// by water_lod.glsl; no include guard is intentional.

vec2 SHADERLOD_WATER_PARALLAX_FN(
    vec3 tangent_dir,
    vec2 coord,
    vec2 flow_dir,
    bool flowing_water
) {
    const int step_count = SHADERLOD_WATER_PARALLAX_STEPS;
    const float parallax_depth = 0.2;

    vec2 wave_dir;
    mat2 wave_rot;
    float t;
    water_waves_setup(flowing_water, flow_dir, wave_dir, wave_rot, t);

    vec2 ray_step = tangent_dir.xy * rcp(-tangent_dir.z) * parallax_depth *
        rcp(float(step_count));

    float depth_value = shaderlod_photon_parallax_height(coord, wave_dir, wave_rot, t);
    float depth_march = 0.0;
    float depth_previous;

    while (depth_march < depth_value) {
        coord += ray_step;
        depth_previous = depth_value;
        depth_value = shaderlod_photon_parallax_height(coord, wave_dir, wave_rot, t);
        depth_march += rcp(float(step_count));
    }

    // Interpolation step

    float depth_before = depth_previous - depth_march + rcp(float(step_count));
    float depth_after = depth_value - depth_march;

    return mix(
        coord,
        coord - ray_step,
        depth_after / (depth_after - depth_before)
    );
}
