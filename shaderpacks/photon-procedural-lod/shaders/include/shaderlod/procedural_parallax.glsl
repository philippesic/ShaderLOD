#if !defined SHADERLOD_PROCEDURAL_PARALLAX_INCLUDED
#define SHADERLOD_PROCEDURAL_PARALLAX_INCLUDED
// Full-quality Photon v1.3b water height/parallax derivative, SixthSurge/Belmu.
// Preserves original arithmetic and loops; only qualifies pinned no-mip noise at
// explicit level zero and reports invalid/singular paths for original fallback.
float shaderlod_procedural_full_height(vec2 coord, vec2 wave_dir, mat2 wave_rot, float t) {
    // Parameters

    // Gerstner waves
    const float wave_frequency = 0.7 * WATER_WAVE_FREQUENCY;
    const float persistence = 0.5 * WATER_WAVE_PERSISTENCE;
    const float lacunarity = 1.7 * WATER_WAVE_LACUNARITY;

    // Noise
    const float noise_frequency = 0.007;
    const float noise_strength = 2.0;

    // Height variation
    const float height_variation_frequency = 0.001;
    const float min_height = 0.4;
    const float height_variation_scale = 2.0;
    const float height_variation_offset = -0.5;
    const float height_variation_scroll_speed = 0.1;

    // Reciprical of sum of amplitudes of all waves
    // This is a geometric series with initial value of 1 and common ratio of
    // `persistence`
    const float amplitude_normalization_factor = (1.0 - persistence) /
        (1.0 - pow(persistence, float(WATER_WAVE_ITERATIONS)));

    // Sample noise textures first (latency hiding)

    float[WATER_WAVE_ITERATIONS] wave_noise;
    vec2 noise_coord = (coord + vec2(0.0, 0.25 * t)) * noise_frequency;
    for (uint i = 0u; i < WATER_WAVE_ITERATIONS; ++i) {
        wave_noise[i] = textureLod(noisetex, noise_coord, 0.0).y;
        noise_coord *= 2.5;
    }

#ifdef WATER_WAVES_HEIGHT_VARIATION
    float height_variation_noise =
        textureLod(
            noisetex,
            (coord + vec2(0.0, height_variation_scroll_speed * t)) *
                height_variation_frequency, 0.0
        )
            .y;
#endif

    // Calculate wave height

    float height = 0.0;
    float amplitude_sum = 0.0;

    float wave_length = 1.0;
    float amplitude = 1.0;
    float frequency = wave_frequency;

    for (uint i = 0u; i < WATER_WAVE_ITERATIONS; ++i) {
        height += gerstner_wave(
                      coord * frequency,
                      wave_dir,
                      t,
                      wave_noise[i] * noise_strength,
                      wave_length
                  ) *
            amplitude;

        amplitude *= persistence;
        frequency *= lacunarity;
        wave_length *= 1.5;

        wave_dir *= wave_rot;
    }

#ifdef WATER_WAVES_HEIGHT_VARIATION
    height *=
        max(min_height,
            height_variation_noise * height_variation_scale +
                height_variation_offset);
#endif

    return height * amplitude_normalization_factor;
}

vec2 shaderlod_procedural_full_parallax(
    vec3 tangent_dir,
    vec2 coord,
    vec2 flow_dir,
    bool flowing_water,
    out bool valid
) {
    valid = false;
    // Reject singular and reversed rays; baseline fallback remains in the caller.
    if (tangent_dir.z >= -eps || WATER_WAVE_PERSISTENCE >= 2.0 ||
        any(isnan(tangent_dir)) || any(isinf(tangent_dir))) return coord;
    const int step_count = 4;
    const float parallax_depth = 0.2;

    vec2 wave_dir;
    mat2 wave_rot;
    float t;
    water_waves_setup(flowing_water, flow_dir, wave_dir, wave_rot, t);

    vec2 ray_step = tangent_dir.xy * rcp(-tangent_dir.z) * parallax_depth *
        rcp(float(step_count));

    float depth_value = shaderlod_procedural_full_height(coord, wave_dir, wave_rot, t);
    float depth_march = 0.0;
    float depth_previous = depth_value;
    if (!(depth_value > 0.0) || isnan(depth_value) || isinf(depth_value)) return coord;
    int safety_steps = 0;

    while (depth_march < depth_value) {
        if (++safety_steps > 8) return coord; // height<=1.5 for supported persistence.
        coord += ray_step;
        depth_previous = depth_value;
        depth_value = shaderlod_procedural_full_height(coord, wave_dir, wave_rot, t);
        depth_march += rcp(float(step_count));
    }

    // Interpolation step

    float depth_before = depth_previous - depth_march + rcp(float(step_count));
    float depth_after = depth_value - depth_march;

    float denominator = depth_after - depth_before;
    if (abs(denominator) <= eps || isnan(denominator) || isinf(denominator)) return coord;
    vec2 result = mix(coord, coord - ray_step, depth_after / denominator);
    valid = !any(isnan(result)) && !any(isinf(result));
    return result;
}

#endif
