// ShaderLOD fixed-count Photon v1.3b derivative. Original attribution/license
// are retained in the assembled pack. Repeated include: names/count supplied
// by water_lod.glsl; no include guard is intentional.

float SHADERLOD_WATER_HEIGHT_FN(vec2 coord, vec2 wave_dir, mat2 wave_rot, float t) {
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
        (1.0 - pow(persistence, float(SHADERLOD_WATER_N)));

    // Sample noise textures first (latency hiding)

    float[SHADERLOD_WATER_N] wave_noise;
    vec2 noise_coord = (coord + vec2(0.0, 0.25 * t)) * noise_frequency;
    for (uint i = 0u; i < SHADERLOD_WATER_N; ++i) {
        wave_noise[i] = texture(noisetex, noise_coord).y;
        noise_coord *= 2.5;
    }

#ifdef WATER_WAVES_HEIGHT_VARIATION
    float height_variation_noise =
        texture(
            noisetex,
            (coord + vec2(0.0, height_variation_scroll_speed * t)) *
                height_variation_frequency
        )
            .y;
#endif

    // Calculate wave height

    float height = 0.0;
    float amplitude_sum = 0.0;

    float wave_length = 1.0;
    float amplitude = 1.0;
    float frequency = wave_frequency;

    for (uint i = 0u; i < SHADERLOD_WATER_N; ++i) {
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

#ifdef SHADERLOD_WATER_ANALYTIC
vec2 SHADERLOD_WATER_GRADIENT_FN(vec2 coord, vec2 wave_dir, mat2 wave_rot, float t) {
    const float wave_frequency = 0.7 * WATER_WAVE_FREQUENCY;
    const float persistence = 0.5 * WATER_WAVE_PERSISTENCE;
    const float lacunarity = 1.7 * WATER_WAVE_LACUNARITY;
    const float noise_frequency = 0.007;
    const float noise_strength = 2.0;
    float normalization = (1.0 - persistence) /
        (1.0 - pow(persistence, float(SHADERLOD_WATER_N)));
    vec2 noise_coord = (coord + vec2(0.0, 0.25 * t)) * noise_frequency;
    vec2 gradient = vec2(0.0);
    float wavelength = 1.0;
    float amplitude = 1.0;
    float frequency = wave_frequency;
    for (int i = 0; i < SHADERLOD_WATER_N; ++i) {
        float noise = texture(noisetex, noise_coord).y * noise_strength;
        float k = tau / wavelength;
        float phase = sqrt(9.8 * k) * t - k * (dot(wave_dir, coord * frequency) + noise);
        float wave = sin(phase) * 0.5 + 0.5;
        gradient += (-k * frequency * wave * cos(phase) * amplitude) * wave_dir;
        amplitude *= persistence;
        frequency *= lacunarity;
        wavelength *= 1.5;
        noise_coord *= 2.5;
        wave_dir *= wave_rot;
    }
#ifdef WATER_WAVES_HEIGHT_VARIATION
    float height_noise = texture(noisetex,
        (coord + vec2(0.0, 0.1 * t)) * 0.001).y;
    gradient *= max(0.4, height_noise * 2.0 - 0.5);
#endif
    return gradient * normalization;
}
#endif

vec3 SHADERLOD_WATER_NORMAL_FN(
    vec3 world_pos,
    vec3 flat_normal,
    vec2 coord,
    vec2 flow_dir,
    float skylight,
    bool flowing_water
) {
    vec2 wave_dir;
    mat2 wave_rot;
    float t;
    water_waves_setup(flowing_water, flow_dir, wave_dir, wave_rot, t);

    const float h = 0.1;
#ifdef SHADERLOD_WATER_ANALYTIC
    // Experimental derivative approximation remains a separately compiled arm.
    vec2 wave_delta = h * SHADERLOD_WATER_GRADIENT_FN(coord, wave_dir, wave_rot, t);
#else
    float wave0 = SHADERLOD_WATER_HEIGHT_FN(coord, wave_dir, wave_rot, t);
    float wave1 = SHADERLOD_WATER_HEIGHT_FN(coord + vec2(h, 0.0), wave_dir, wave_rot, t);
    float wave2 = SHADERLOD_WATER_HEIGHT_FN(coord + vec2(0.0, h), wave_dir, wave_rot, t);
    vec2 wave_delta = vec2(wave1 - wave0, wave2 - wave0);
#endif

#if defined WORLD_OVERWORLD
    float normal_influence = flowing_water
        ? 0.1
        : mix(0.01, 0.04 + 0.15 * rainStrength, dampen(skylight));
#else
    float normal_influence = 0.04;
#endif
    normal_influence *= smoothstep(
        0.0,
        0.15,
        abs(dot(flat_normal, normalize(world_pos - cameraPosition)))
    ); // prevent noise when looking horizontally
    normal_influence *= WATER_WAVE_STRENGTH;

    vec3 normal = vec3(wave_delta, h);
    normal.xy *= normal_influence;

    return normalize(normal);
}
