// Private ShaderLOD volume lighting-rate experiment.
// Density, optical-depth and primary-march bodies derive from Photon v1.3b
// by Benjamin Stott (SixthSurge). Retain the original upstream LICENSE.
// General mechanism: reuse a smooth, expensive per-cell lighting result for
// at most one adjacent integration cell; never reduce primary/density work.
// This private adapter is not registered with the mod's extension protocol.
#ifndef SHADERLOD_VOLUME_LIGHT_RATE_HELPER
#define SHADERLOD_VOLUME_LIGHT_RATE_HELPER
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;

#ifdef SHADERLOD_VOLUME_LIGHT_RATE_REUSE
bool shaderlod_rate_reuse_allowed(float distance_blocks, float cell_pixels, float rank,
    float density, float previous_density, float altitude, float previous_altitude) {
    if (isnan(distance_blocks) || isinf(distance_blocks) ||
        isnan(cell_pixels) || isinf(cell_pixels) ||
        isnan(rank) || isinf(rank) || rank < 0.0 || rank >= 1.0 ||
        isnan(density) || isinf(density) || isnan(previous_density) || isinf(previous_density) ||
        isnan(altitude) || isinf(altitude) || isnan(previous_altitude) || isinf(previous_altitude) ||
        distance_blocks <= 512.0 || cell_pixels < 0.0 || cell_pixels >= 48.0 ||
        density < 0.08 || previous_density < 0.08 || altitude < 0.1 || altitude > 0.9 ||
        previous_altitude < 0.1 || previous_altitude > 0.9 || abs(altitude - previous_altitude) > 0.05 ||
        abs(density - previous_density) > 0.1 * max(density, previous_density)) return false;
    float distance_weight = clamp((distance_blocks - 512.0) / 1024.0, 0.0, 1.0);
    float footprint_weight = clamp((48.0 - cell_pixels) / 16.0, 0.0, 1.0);
    return rank < distance_weight * footprint_weight;
}
#endif // SHADERLOD_VOLUME_LIGHT_RATE_REUSE

float shaderlod_rate_local_coverage(vec2 pos) {
    const float wind_angle = CLOUDS_CUMULUS_WIND_ANGLE * degree;
    const vec2 wind_velocity =
        CLOUDS_CUMULUS_WIND_SPEED * vec2(cos(wind_angle), sin(wind_angle));

    pos += cameraPosition.xz * CLOUDS_SCALE;
    pos += wind_velocity * world_age;

    // Sample noise
    vec2 p1 = (0.000002 / CLOUDS_CUMULUS_SIZE) * pos;
    vec2 p2 = (0.000027 / CLOUDS_CUMULUS_SIZE) * pos;
    vec2 noise = vec2(
        textureLod(noisetex, p1, 0.0).x, // cloud coverage
        textureLod(noisetex, p2, 0.0).w // cloud shape
    );

    // Compute cumulus coverage
    float coverage_cu = 0.0, coverage_st = 0.0;

    if (clouds_params.l0_cumulus_stratus_blend < 1.0 - eps) {
        coverage_cu =
            mix(clouds_params.l0_coverage.x,
                clouds_params.l0_coverage.y,
                noise.x);
        coverage_cu = linear_step(1.0 - coverage_cu, 1.0, noise.y);
    }

    // Compute stratus coverage
    if (clouds_params.l0_cumulus_stratus_blend > eps) {
        coverage_st = cubic_smooth(linear_step(
            0.9 - clouds_params.l0_coverage.x,
            1.0,
            2.0 * noise.x * clouds_params.l0_coverage.y
        ));
        coverage_st = 0.5 * coverage_st +
            1.0 * coverage_st * linear_step(0.3, 0.6, noise.y);
        coverage_st = coverage_st / (coverage_st + 1.0);
    }

    return mix(
        coverage_cu,
        coverage_st,
        clouds_params.l0_cumulus_stratus_blend
    );
}

float shaderlod_rate_density(vec3 pos) {
    float r = length(pos);

#if defined CLOUDS_USE_LOCAL_COVERAGE_MAP
    // Get local coverage from precomputed coverage map - saves 1 sample and
    // some maths
    vec2 coverage_map_uv = project_clouds_cumulus_coverage_map(pos);

    if (r < clouds_cumulus_radius || r > clouds_cumulus_top_radius ||
        clamp01(coverage_map_uv) != coverage_map_uv) {
        return 0.0;
    }

    float density = textureLod(colortex8, coverage_map_uv, 0.0).z;
#else
    if (r < clouds_cumulus_radius || r > clouds_cumulus_top_radius) {
        return 0.0;
    }

    float density = shaderlod_rate_local_coverage(pos.xz);
#endif

    // Altitude shaping

    float altitude_fraction =
        (r - clouds_cumulus_radius) * clouds_params.l0_altitude_scale;

    density = clouds_cumulus_altitude_shaping(density, altitude_fraction);

    if (density < eps) {
        return 0.0;
    }

#if !defined PROGRAM_PREPARE
    const float wind_angle = CLOUDS_CUMULUS_WIND_ANGLE * degree;
    const vec2 wind_velocity =
        CLOUDS_CUMULUS_WIND_SPEED * vec2(cos(wind_angle), sin(wind_angle));

    vec3 wind = vec3(wind_velocity * world_age, 0.0).xzy;

    pos.xz += cameraPosition.xz * CLOUDS_SCALE + wind.xz;

    // 3D worley noise for detail
    float worley_0 =
        textureLod(SAMPLER_WORLEY_BUBBLY, (pos + 0.2 * wind) * 0.0009, 0.0).x;
    float worley_1 =
        textureLod(SAMPLER_WORLEY_SWIRLEY, (pos + 0.4 * wind) * 0.005, 0.0).x;
#else
    const float worley_0 = 0.5;
    const float worley_1 = 0.5;
#endif

    float detail_fade = 0.20 * smoothstep(0.85, 1.0, 1.0 - altitude_fraction) -
        0.35 * smoothstep(0.05, 0.5, altitude_fraction) + 0.6;

    density -= clouds_params.l0_detail_weights.x * sqr(worley_0) *
        dampen(clamp01(1.0 - density));
    density -= clouds_params.l0_detail_weights.y * sqr(worley_1) *
        dampen(clamp01(1.0 - density)) * detail_fade;

    // Adjust density so that the clouds are wispy at the bottom and hard at the
    // top
    density = max0(density);
    density = lift(
        density,
        mix(clouds_params.l0_edge_sharpening.x,
            clouds_params.l0_edge_sharpening.y,
            altitude_fraction)
    );
    density *= 0.1 + 0.9 * smoothstep(0.2, 0.7, altitude_fraction);

    return density;
}

float shaderlod_rate_optical_depth(
    vec3 ray_origin,
    vec3 ray_dir,
    float dither,
    const uint step_count
) {
    const float step_growth = 2.0;

    float step_length = 0.1 * clouds_cumulus_thickness / float(step_count); // m

    vec3 ray_pos = ray_origin;
    vec4 ray_step = vec4(ray_dir, 1.0) * step_length;

    float optical_depth = 0.0;

    for (uint i = 0u; i < step_count; ++i, ray_pos += ray_step.xyz) {
        ray_step *= step_growth;
        optical_depth +=
            shaderlod_rate_density(ray_pos + ray_step.xyz * dither) *
            ray_step.w;
    }

    return optical_depth;
}

CloudsResult shaderlod_rate_draw_cumulus_clouds(
    vec3 air_viewer_pos,
    vec3 ray_dir,
    vec3 clear_sky,
    float distance_to_terrain,
    float dither
) {
    // ---------------------
    //   Raymarching Setup
    // ---------------------

#if defined PROGRAM_DEFERRED0
    const uint primary_steps_horizon = CLOUDS_CUMULUS_PRIMARY_STEPS_H / 2;
    const uint primary_steps_zenith = CLOUDS_CUMULUS_PRIMARY_STEPS_Z / 2;
#else
    const uint primary_steps_horizon = CLOUDS_CUMULUS_PRIMARY_STEPS_H;
    const uint primary_steps_zenith = CLOUDS_CUMULUS_PRIMARY_STEPS_Z;
#endif
    const uint lighting_steps = CLOUDS_CUMULUS_LIGHTING_STEPS;
    const uint ambient_steps = CLOUDS_CUMULUS_AMBIENT_STEPS;
    const float max_ray_length = 2e4;
    const float min_transmittance = 0.075;
    const float planet_albedo = 0.4;
    const vec3 sky_dir = vec3(0.0, 1.0, 0.0);

    // Early exit if coverage is 0
    if (clouds_params.l0_coverage.y < eps) {
        return clouds_not_hit;
    }

    uint primary_steps =
        uint(mix(primary_steps_horizon, primary_steps_zenith, abs(ray_dir.y)));

    float r = length(air_viewer_pos);

    vec2 dists = intersect_spherical_shell(
        air_viewer_pos,
        ray_dir,
        clouds_cumulus_radius,
        clouds_cumulus_top_radius
    );
    bool planet_intersected =
        intersect_sphere(air_viewer_pos, ray_dir, min(r - 10.0, planet_radius))
            .y >= 0.0;
    bool terrain_intersected = distance_to_terrain >= 0.0 &&
        r < clouds_cumulus_radius && distance_to_terrain < dists.x;

    if (
        dists.y < 0.0 // volume not intersected
        || planet_intersected &&
            r < clouds_cumulus_radius // planet blocking clouds
        || terrain_intersected // terrain blocking clouds
    ) {
        return clouds_not_hit;
    }

    float ray_length =
        (distance_to_terrain >= 0.0) ? distance_to_terrain : dists.y;
    ray_length = clamp(ray_length - dists.x, 0.0, max_ray_length);
    float step_length = ray_length * rcp(float(primary_steps));

    vec3 ray_step = ray_dir * step_length;
    vec3 ray_origin =
        air_viewer_pos + ray_dir * (dists.x + step_length * dither);

    vec2 scattering = vec2(0.0); // x: direct light, y: skylight
    float transmittance = 1.0;

    float distance_sum = 0.0;
    float distance_weight_sum = 0.0;

    // ------------------
    //   Lighting Setup
    // ------------------

    bool moonlit = sun_dir.y < -0.04;
    vec3 light_dir = moonlit ? moon_dir : sun_dir;
    float cos_theta = dot(ray_dir, light_dir);
    float bounced_light = planet_albedo * light_dir.y * rcp_pi;

#ifdef SHADERLOD_VOLUME_LIGHT_RATE_REUSE
    // Allocated only in the private reuse body, after original geometry exits.
    bool cached_valid = false;
    bool cached_smooth = false;
    vec2 cached_depths = vec2(0.0);
    float cached_density = 0.0;
    float cached_altitude = 0.0;
    float pixel_scale = 0.5 * max(abs(gbufferProjection[0].x) * view_res.x,
                                  abs(gbufferProjection[1].y) * view_res.y);
    float view_cosine = -(mat3(gbufferModelView) * ray_dir).z;
    // Fixed screen rank: transition changes workload before the kernels.
    float rate_rank = fract(dot(floor(gl_FragCoord.xy), vec2(0.754877666, 0.569840296)));
    bool ray_rate_allowed = !moonlit && light_dir.y > 0.2 && cos_theta < 0.8 &&
        !isnan(pixel_scale) && !isinf(pixel_scale) && pixel_scale > 0.0 &&
        !isnan(view_cosine) && !isinf(view_cosine) && view_cosine > 0.0 &&
        !isnan(step_length) && !isinf(step_length) && step_length > 0.0 &&
        !isnan(clouds_params.l0_extinction_coeff) && !isinf(clouds_params.l0_extinction_coeff) &&
        clouds_params.l0_extinction_coeff > 0.0;
#endif // SHADERLOD_VOLUME_LIGHT_RATE_REUSE

    // --------------------
    //   Raymarching Loop
    // --------------------

    for (uint i = 0u; i < primary_steps; ++i) {
        if (transmittance < min_transmittance) {
            break;
        }

        vec3 ray_pos = ray_origin + ray_step * i;

        float altitude_fraction = (length(ray_pos) - clouds_cumulus_radius) *
            rcp(clouds_cumulus_thickness);

        float density = shaderlod_rate_density(ray_pos);

        if (density < eps) {
#ifdef SHADERLOD_VOLUME_LIGHT_RATE_REUSE
            cached_valid = false; // An empty gap forbids bridging cloud boundaries.
            cached_smooth = false;
#endif // SHADERLOD_VOLUME_LIGHT_RATE_REUSE
            continue;
        }

        // Fade away in the distance to hide the max ray length cutoff
        float distance_to_sample = distance(ray_origin, ray_pos);
        density *=
            smoothstep(1.0, 0.95, distance_to_sample * rcp(max_ray_length));

#if defined CLOUDS_USE_LOCAL_COVERAGE_MAP
        // Fade away at the edges of the local coverage map
        density *= smoothstep(
            1.0,
            0.9,
            length(ray_pos.xz) * rcp(0.5 * clouds_cumulus_coverage_map_scale)
        );
#endif

        float step_optical_depth =
            density * clouds_params.l0_extinction_coeff * step_length;
        float step_transmittance = exp(-step_optical_depth);

#ifdef SHADERLOD_VOLUME_LIGHT_RATE_REUSE
        float sample_distance_blocks = max(0.0, dists.x + step_length * (float(i) + dither)) / CLOUDS_SCALE;
        // Worst-orientation bound on a primary cell's screen extent, rather
        // than a derivative of a divergent density field. It is a heuristic,
        // not a certificate of cloud-shadow continuity.
        float projected_cell_pixels = (step_length / CLOUDS_SCALE) * pixel_scale /
            max(sample_distance_blocks * view_cosine, 0.0001);
        bool reuse_lighting = ray_rate_allowed && (i & 1u) == 1u && cached_valid && cached_smooth &&
            shaderlod_rate_reuse_allowed(sample_distance_blocks, projected_cell_pixels, rate_rank,
                density, cached_density, altitude_fraction, cached_altitude);
        float light_optical_depth;
        float sky_optical_depth;
        if (reuse_lighting) {
            light_optical_depth = cached_depths.x;
            sky_optical_depth = cached_depths.y;
        } else {
            // All unchanged secondary samples are evaluated before updating the
            // coherence flag. A reuse never evaluates then blends fresh work.
            vec2 hash = hash2(fract(ray_pos));
            light_optical_depth = shaderlod_rate_optical_depth(ray_pos, light_dir, hash.x, lighting_steps);
            sky_optical_depth = shaderlod_rate_optical_depth(ray_pos, sky_dir, hash.y, ambient_steps);
            vec2 fresh_depths = vec2(light_optical_depth, sky_optical_depth);
            vec2 depth_delta = abs(fresh_depths - cached_depths) * clouds_params.l0_extinction_coeff;
            cached_smooth = cached_valid && !any(isnan(depth_delta)) && !any(isinf(depth_delta)) &&
                max(depth_delta.x, depth_delta.y) <= 0.1;
            cached_depths = fresh_depths;
            cached_density = density;
            cached_altitude = altitude_fraction;
            cached_valid = !any(isnan(fresh_depths)) && !any(isinf(fresh_depths));
        }
#else // SHADERLOD_VOLUME_LIGHT_RATE_REUSE
#if defined PROGRAM_DEFERRED0
        vec2 hash = vec2(0.0);
#else
        vec2 hash = hash2(fract(ray_pos)); // used to dither the light rays
#endif

        float light_optical_depth = shaderlod_rate_optical_depth(
            ray_pos,
            light_dir,
            hash.x,
            lighting_steps
        );
        float sky_optical_depth = shaderlod_rate_optical_depth(
            ray_pos,
            sky_dir,
            hash.y,
            ambient_steps
        );
#endif // SHADERLOD_VOLUME_LIGHT_RATE_REUSE
        // guess optical depth to the ground using altitude fraction and density
        // from this sample
        float ground_optical_depth =
            mix(density, 1.0, clamp01(altitude_fraction * 2.0 - 1.0)) *
            altitude_fraction * clouds_cumulus_thickness;

        scattering +=
            clouds_cumulus_scattering(
                density,
                light_optical_depth,
                sky_optical_depth,
                ground_optical_depth,
                step_transmittance,
                cos_theta,
                bounced_light
            ) *
            transmittance;

        transmittance *= step_transmittance;

        // Update distance to cloud
        distance_sum += distance_to_sample * density;
        distance_weight_sum += density;
    }

    // Get main light color for this layer
    vec3 light_color =
        sunlight_color * atmosphere_transmittance(ray_origin, light_dir);
    light_color = atmosphere_post_processing(light_color);
    light_color *= moonlit ? moon_color : sun_color;

    // Remap the transmittance so that min_transmittance is 0
    float clouds_transmittance =
        linear_step(min_transmittance, 1.0, transmittance);

    vec3 clouds_scattering =
        scattering.x * light_color + scattering.y * sky_color;
    clouds_scattering = clouds_aerial_perspective(
        clouds_scattering,
        clouds_transmittance,
        air_viewer_pos,
        ray_origin,
        ray_dir,
        clear_sky
    );

    float apparent_distance = (distance_weight_sum == 0.0)
        ? 1e6
        : (distance_sum / distance_weight_sum) +
            distance(air_viewer_pos, ray_origin);

    return CloudsResult(
        vec4(clouds_scattering, scattering.y),
        clouds_transmittance,
        apparent_distance
    );
}

CloudsResult draw_cumulus_clouds(vec3 air_viewer_pos, vec3 ray_dir, vec3 clear_sky,
    float distance_to_terrain, float dither) {
    // Uniform selection precedes private geometry/cache setup. Unknown protocol
    // and global OFF execute the literal upstream body and original samplers.
    if (shaderlodBridgeVersion != 2 || shaderlodEnabled != 1) {
        return shaderlod_rate_original_draw_cumulus_clouds(air_viewer_pos, ray_dir, clear_sky,
            distance_to_terrain, dither);
    }
    return shaderlod_rate_draw_cumulus_clouds(air_viewer_pos, ray_dir, clear_sky,
        distance_to_terrain, dither);
}
#endif // SHADERLOD_VOLUME_LIGHT_RATE_HELPER
