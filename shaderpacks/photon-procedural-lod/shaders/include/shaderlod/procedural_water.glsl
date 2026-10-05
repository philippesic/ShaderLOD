#if !defined SHADERLOD_PROCEDURAL_WATER_INCLUDED
#define SHADERLOD_PROCEDURAL_WATER_INCLUDED
// ShaderLOD experimental Photon v1.3b derivative, Benjamin Stott (SixthSurge).
// Original Photon LICENSE and Belmu Gerstner attribution remain in the pack.
#include "/include/shaderlod/procedural_footprint.glsl"
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;

// Compute footprint weights once, shared by all three finite differences.
void shaderlod_procedural_water_weights(
    vec2 wave_dir, mat2 wave_rot, vec2 dx, vec2 dy, float distance_weight,
    out float octave_weights[WATER_WAVE_ITERATIONS]
) {
    float frequency = 0.7 * WATER_WAVE_FREQUENCY;
    float noise_frequency = 0.007;
    float wavelength = 1.0;
    float noise_gradient_bound = length(vec2(textureSize(noisetex, 0)));
    for (int i = 0; i < WATER_WAVE_ITERATIONS; ++i) {
        float phase_cycles = shaderlod_procedural_cycles(dx, dy,
            wave_dir * frequency / wavelength,
            2.0 * noise_frequency * noise_gradient_bound / wavelength);
        // Keep full coarse slope/height energy and full-N normalization.
        octave_weights[i] = i == 0 ? 1.0 : mix(1.0,
            shaderlod_procedural_weight(phase_cycles), distance_weight);
        frequency *= 1.7 * WATER_WAVE_LACUNARITY;
        wavelength *= 1.5;
        noise_frequency *= 2.5;
        wave_dir *= wave_rot;
    }
}

float shaderlod_procedural_height(
    vec2 coord, vec2 wave_dir, mat2 wave_rot, float t,
    vec2 dx, vec2 dy, float octave_weights[WATER_WAVE_ITERATIONS]
) {
    const float persistence = 0.5 * WATER_WAVE_PERSISTENCE;
    const float lacunarity = 1.7 * WATER_WAVE_LACUNARITY;
    const float normalization = (1.0 - persistence) /
        (1.0 - pow(persistence, float(WATER_WAVE_ITERATIONS)));
    float frequency = 0.7 * WATER_WAVE_FREQUENCY;
    float noise_frequency = 0.007;
    float wavelength = 1.0;
    float amplitude = 1.0;
    float height = 0.0;
    // Pinned noise.png is normalized, LINEAR, repeat, level zero.
    for (int i = 0; i < WATER_WAVE_ITERATIONS; ++i) {
        float weight = octave_weights[i];
        float wave = 0.375; // E[(0.5 sin(x) + 0.5)^2] for uniform phase.
        // Skip before texture and trigonometry; no hidden full-wave evaluation.
        if (weight > 0.0) {
            float noise = textureGrad(noisetex,
                (coord + vec2(0.0, 0.25 * t)) * noise_frequency,
                dx * noise_frequency, dy * noise_frequency).y;
            wave = mix(0.375, gerstner_wave(coord * frequency, wave_dir,
                t, noise * 2.0, wavelength), weight);
        }
        height += wave * amplitude;
        amplitude *= persistence;
        frequency *= lacunarity;
        wavelength *= 1.5;
        noise_frequency *= 2.5;
        wave_dir *= wave_rot;
    }
#ifdef WATER_WAVES_HEIGHT_VARIATION
    float height_noise = textureGrad(noisetex,
        (coord + vec2(0.0, 0.1 * t)) * 0.001,
        dx * 0.001, dy * 0.001).y;
    height *= max(0.4, height_noise * 2.0 - 0.5);
#endif
    return height * normalization;
}

vec3 shaderlod_procedural_normal(
    vec3 world_pos, vec3 flat_normal, vec2 coord, vec2 flow_dir,
    float skylight, bool flowing_water, vec2 dx, vec2 dy, bool footprint_valid, float footprint_confidence
) {
    float dist = distance(world_pos, cameraPosition); // Minecraft blocks.
    if (!footprint_valid || footprint_confidence <= 0.0 || shaderlodBridgeVersion != 2 || shaderlodEnabled != 1 || dist <= 32.0 ||
        any(isnan(dx)) || any(isnan(dy)) || any(isinf(dx)) || any(isinf(dy))) {
        return get_water_normal(world_pos, flat_normal, coord, flow_dir,
            skylight, flowing_water);
    }
    // Do not alter slope energy when the source geometric sum is singular.
    if (WATER_WAVE_PERSISTENCE >= 2.0) {
        return get_water_normal(world_pos, flat_normal, coord, flow_dir,
            skylight, flowing_water);
    }
    float distance_weight = smoothstep(32.0, 48.0, dist) * footprint_confidence;
    vec2 wave_dir;
    mat2 wave_rot;
    float t;
    water_waves_setup(flowing_water, flow_dir, wave_dir, wave_rot, t);
    float octave_weights[WATER_WAVE_ITERATIONS];
    shaderlod_procedural_water_weights(wave_dir, wave_rot, dx, dy,
        distance_weight, octave_weights);
    const float h = 0.1;
    float wave0 = shaderlod_procedural_height(coord, wave_dir, wave_rot, t,
        dx, dy, octave_weights);
    float wave1 = shaderlod_procedural_height(coord + vec2(h, 0.0), wave_dir,
        wave_rot, t, dx, dy, octave_weights);
    float wave2 = shaderlod_procedural_height(coord + vec2(0.0, h), wave_dir,
        wave_rot, t, dx, dy, octave_weights);
#if defined WORLD_OVERWORLD
    float normal_influence = flowing_water ? 0.1 :
        mix(0.01, 0.04 + 0.15 * rainStrength, dampen(skylight));
#else
    float normal_influence = 0.04;
#endif
    normal_influence *= smoothstep(0.0, 0.15,
        abs(dot(flat_normal, normalize(world_pos - cameraPosition))));
    normal_influence *= WATER_WAVE_STRENGTH;
    vec3 normal = vec3(wave1 - wave0, wave2 - wave0, h);
    normal.xy *= normal_influence;
    return normalize(normal);
}
#endif
