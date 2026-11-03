// Photon v1.3b by SixthSurge. Original LICENSE preserved.
// Exact original full-resolution sky body; producer precomputes original LUT/cloud inputs before divergence.
vec3 shaderlod_photon_original_sky(vec3 direction_world, vec3 atmosphere,
    vec4 clouds_and_aurora, float clouds_apparent_distance) {
    vec3 fragment_color;
#if defined WORLD_OVERWORLD
        fragment_color = draw_sky(
            direction_world,
            atmosphere,
            clouds_and_aurora,
            clouds_apparent_distance
        );
#else
        fragment_color = draw_sky(direction_world);
#endif

        // Apply blocky clouds
#if defined WORLD_OVERWORLD && defined BLOCKY_CLOUDS
        fragment_color = fragment_color * blocky_clouds.w + blocky_clouds.xyz;
#endif

        // Apply common fog
        vec4 fog = common_fog(far, true);
        fragment_color = mix(fog.rgb, fragment_color.rgb, fog.a);

        // Apply purkinje shift
        fragment_color = purkinje_shift(fragment_color, vec2(0.0, 1.0));
    return fragment_color;
}
