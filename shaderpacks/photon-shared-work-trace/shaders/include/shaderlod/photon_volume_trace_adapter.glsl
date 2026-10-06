#if !defined SHADERLOD_PHOTON_VOLUME_ADAPTER_INCLUDED
#define SHADERLOD_PHOTON_VOLUME_ADAPTER_INCLUDED

// Explicit derivative of Photon v1.3b by Benjamin Stott (SixthSurge).
// Density, secondary lighting, phase functions and upstream early exit retained.
// Photon LICENSE is retained in each assembled archive; do not remove attribution.
#include "/include/shaderlod/work_policy.glsl"
#include "/include/shaderlod/volume_budget.glsl"

uniform int shaderlodCapabilitiesVersion;
uniform int shaderlodVolumeIntegrationAvailable;
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform float shaderlodNear;
uniform float shaderlodFar;
uniform float shaderlodTransition;
uniform int shaderlodEffectsVersion;
uniform int shaderlodVolumetricsMode;
uniform float shaderlodVolumetricsNear;
uniform float shaderlodVolumetricsFar;
uniform float shaderlodVolumetricsTransition;
uniform float shaderlodVolumetricsMediumWorkRatio;
uniform float shaderlodVolumetricsFarWorkRatio;

float shaderlod_volume_local_coverage(vec2 pos) {
    const float wind_angle = CLOUDS_CUMULUS_WIND_ANGLE * degree;
    const vec2 wind_velocity =
        CLOUDS_CUMULUS_WIND_SPEED * vec2(cos(wind_angle), sin(wind_angle));

    pos += cameraPosition.xz * CLOUDS_SCALE;
    pos += wind_velocity * world_age;

    // Sample noise
    vec2 p1 = (0.000002 / CLOUDS_CUMULUS_SIZE) * pos;
    vec2 p2 = (0.000027 / CLOUDS_CUMULUS_SIZE) * pos;
#ifdef SHADERLOD_WORK_TRACE
#if defined PROGRAM_DEFERRED1 && defined WORLD_OVERWORLD && !defined ATMOSPHERE_TRANSMITTANCE_LUT
    SHADERLOD_TRACE_TEXTURE(photon_trace);
    SHADERLOD_TRACE_TEXTURE(photon_trace);
#endif // SHADERLOD_TRACE_STAGE
#endif // SHADERLOD_WORK_TRACE
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

float shaderlod_volume_density(vec3 pos) {
#ifdef SHADERLOD_WORK_TRACE
#if defined PROGRAM_DEFERRED1 && defined WORLD_OVERWORLD && !defined ATMOSPHERE_TRANSMITTANCE_LUT
    SHADERLOD_TRACE_DENSITY(photon_trace);
#endif // SHADERLOD_TRACE_STAGE
#endif // SHADERLOD_WORK_TRACE
    float r = length(pos);

#if defined CLOUDS_USE_LOCAL_COVERAGE_MAP
    // Get local coverage from precomputed coverage map - saves 1 sample and
    // some maths
    vec2 coverage_map_uv = project_clouds_cumulus_coverage_map(pos);

    if (r < clouds_cumulus_radius || r > clouds_cumulus_top_radius ||
        clamp01(coverage_map_uv) != coverage_map_uv) {
        return 0.0;
    }

#ifdef SHADERLOD_WORK_TRACE
#if defined PROGRAM_DEFERRED1 && defined WORLD_OVERWORLD && !defined ATMOSPHERE_TRANSMITTANCE_LUT
    SHADERLOD_TRACE_TEXTURE(photon_trace);
#endif // SHADERLOD_TRACE_STAGE
#endif // SHADERLOD_WORK_TRACE
    float density = textureLod(colortex8, coverage_map_uv, 0.0).z;
#else
    if (r < clouds_cumulus_radius || r > clouds_cumulus_top_radius) {
        return 0.0;
    }

    float density = shaderlod_volume_local_coverage(pos.xz);
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
#ifdef SHADERLOD_WORK_TRACE
#if defined PROGRAM_DEFERRED1 && defined WORLD_OVERWORLD && !defined ATMOSPHERE_TRANSMITTANCE_LUT
    SHADERLOD_TRACE_TEXTURE(photon_trace);
#endif // SHADERLOD_TRACE_STAGE
#endif // SHADERLOD_WORK_TRACE
    float worley_0 =
        textureLod(SAMPLER_WORLEY_BUBBLY, (pos + 0.2 * wind) * 0.0009, 0.0).x;
#ifdef SHADERLOD_WORK_TRACE
#if defined PROGRAM_DEFERRED1 && defined WORLD_OVERWORLD && !defined ATMOSPHERE_TRANSMITTANCE_LUT
    SHADERLOD_TRACE_TEXTURE(photon_trace);
#endif // SHADERLOD_TRACE_STAGE
#endif // SHADERLOD_WORK_TRACE
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

float shaderlod_volume_optical_depth(
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
#ifdef SHADERLOD_WORK_TRACE
#if defined PROGRAM_DEFERRED1 && defined WORLD_OVERWORLD && !defined ATMOSPHERE_TRANSMITTANCE_LUT
        SHADERLOD_TRACE_SECONDARY(photon_trace);
#endif // SHADERLOD_TRACE_STAGE
#endif // SHADERLOD_WORK_TRACE
        ray_step *= step_growth;
        optical_depth +=
            shaderlod_volume_density(ray_pos + ray_step.xyz * dither) *
            ray_step.w;
    }

    return optical_depth;
}

CloudsResult shaderlod_volume_draw_cumulus(
    vec3 air_viewer_pos,
    vec3 ray_dir,
    vec3 clear_sky,
    float distance_to_terrain,
    float dither,
    float work_target
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

#ifdef SHADERLOD_WORK_TRACE
#if defined PROGRAM_DEFERRED1 && defined WORLD_OVERWORLD && !defined ATMOSPHERE_TRANSMITTANCE_LUT
    photon_trace.path_tag = SHADERLOD_TRACE_PATH_ADAPTED;
#endif // SHADERLOD_TRACE_STAGE
#endif // SHADERLOD_WORK_TRACE
    // Early exit if coverage is 0
    if (clouds_params.l0_coverage.y < eps) {
        return clouds_not_hit;
    }

    uint primary_steps =
        uint(mix(primary_steps_horizon, primary_steps_zenith, abs(ray_dir.y)));
#ifdef SHADERLOD_WORK_TRACE
#if defined PROGRAM_DEFERRED1 && defined WORLD_OVERWORLD && !defined ATMOSPHERE_TRANSMITTANCE_LUT
    SHADERLOD_TRACE_SELECT(photon_trace, primary_steps, work_target, work_target, SHADERLOD_TRACE_PATH_ADAPTED, 0u);
#endif // SHADERLOD_TRACE_STAGE
#endif // SHADERLOD_WORK_TRACE

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
    primary_steps = uint(ceil(work_target));
    float step_length = ray_length * rcp(work_target);

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

    // --------------------
    //   Raymarching Loop
    // --------------------

    for (uint i = 0u; i < primary_steps; ++i) {
        if (transmittance < min_transmittance) {
            break;
        }
#ifdef SHADERLOD_WORK_TRACE
#if defined PROGRAM_DEFERRED1 && defined WORLD_OVERWORLD && !defined ATMOSPHERE_TRANSMITTANCE_LUT
        SHADERLOD_TRACE_PRIMARY(photon_trace);
#endif // SHADERLOD_TRACE_STAGE
#endif // SHADERLOD_WORK_TRACE

        // The final partial cell closes the original interval exactly; do not
        // stretch or truncate optical depth when changing the sample count.
        float cell_length = shaderlod_volume_cell_length(ray_length, work_target, i);
        if (cell_length <= 0.0) continue;
        vec3 ray_pos = air_viewer_pos + ray_dir * (dists.x +
            shaderlod_volume_cell_sample(ray_length, work_target, i, dither));

        float altitude_fraction = (length(ray_pos) - clouds_cumulus_radius) *
            rcp(clouds_cumulus_thickness);

        float density = shaderlod_volume_density(ray_pos);

        if (density < eps) {
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
            density * clouds_params.l0_extinction_coeff * cell_length;
        float step_transmittance = exp(-step_optical_depth);

#if defined PROGRAM_DEFERRED0
        vec2 hash = vec2(0.0);
#else
        vec2 hash = hash2(fract(ray_pos)); // used to dither the light rays
#endif

        float light_optical_depth = shaderlod_volume_optical_depth(
            ray_pos,
            light_dir,
            hash.x,
            lighting_steps
        );
        float sky_optical_depth = shaderlod_volume_optical_depth(
            ray_pos,
            sky_dir,
            hash.y,
            ambient_steps
        );
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
        distance_sum += distance_to_sample * density * cell_length;
        distance_weight_sum += density * cell_length;
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

CloudsResult draw_cumulus_clouds(vec3 viewer, vec3 direction, vec3 clear_sky,
    float terrain_distance, float dither) {
    bool capability_valid = shaderlodCapabilitiesVersion == 1 &&
        shaderlodVolumeIntegrationAvailable == 1;
    if (!capability_valid || any(isnan(viewer)) || any(isinf(viewer)) ||
        any(isnan(direction)) || any(isinf(direction)) ||
        abs(dot(direction, direction) - 1.0) > 0.001 ||
        !shaderlod_volume_finite(terrain_distance) || !shaderlod_volume_finite(dither) ||
        dither < 0.0 || dither > 1.0 || !shaderlod_volume_finite(CLOUDS_SCALE) || CLOUDS_SCALE <= 0.0)
        return shaderlod_original_draw_cumulus_clouds(viewer, direction, clear_sky, terrain_distance, dither);
    // INHERIT resolves the general system's safe default: unity retained work.
    // Core bands provide units/transition semantics; there is no private ratio tuning.
    vec3 bands = vec3(shaderlodNear, shaderlodFar, shaderlodTransition);
    vec2 retained = vec2(1.0);
    if (shaderlodVolumetricsMode == 2) {
        bands = vec3(shaderlodVolumetricsNear, shaderlodVolumetricsFar, shaderlodVolumetricsTransition);
        retained = vec2(shaderlodVolumetricsMediumWorkRatio, shaderlodVolumetricsFarWorkRatio);
    }
    ShaderlodWorkPolicy policy = ShaderlodWorkPolicy(shaderlodBridgeVersion,
        shaderlodEffectsVersion, 1, shaderlodEnabled == 1, shaderlodVolumetricsMode,
        true, true, bands, retained);
    uint full = uint(mix(float(CLOUDS_CUMULUS_PRIMARY_STEPS_H),
        float(CLOUDS_CUMULUS_PRIMARY_STEPS_Z), abs(direction.y)));
    if (full < 8u || full > 65536u)
        return shaderlod_original_draw_cumulus_clouds(viewer, direction, clear_sky, terrain_distance, dither);
    // Shared preflight rejects disabled/full/invalid controls before intersections,
    // footprint calculations or textureSize. Final distance/quality are supplied below.
    ShaderlodWorkDecision preflight = shaderlod_work_decide(policy, capability_valid,
        true, false, 0.0, int(full), 8);
    if (preflight.reason != SHADERLOD_WORK_POLICY || all(equal(retained, vec2(1.0))))
        return shaderlod_original_draw_cumulus_clouds(viewer, direction, clear_sky, terrain_distance, dither);
    // Guard inside and above the physical cloud shell, including boundary rounding.
    float radius = length(viewer);
    if (!shaderlod_volume_finite(radius) || radius >= clouds_cumulus_radius - 1.0 ||
        !shaderlod_volume_finite(clouds_cumulus_top_radius) ||
        clouds_cumulus_top_radius <= clouds_cumulus_radius ||
        any(isnan(view_res)) || any(isinf(view_res)) || min(view_res.x, view_res.y) <= 0.0)
        return shaderlod_original_draw_cumulus_clouds(viewer, direction, clear_sky, terrain_distance, dither);
    // Full overhead sample density. Smoothly restore it before |direction.y|=0.65.
    float overhead_weight = smoothstep(0.35, 0.65, abs(direction.y));
    if (overhead_weight == 1.0)
        return shaderlod_original_draw_cumulus_clouds(viewer, direction, clear_sky, terrain_distance, dither);
    vec2 dists = intersect_spherical_shell(viewer, direction, clouds_cumulus_radius, clouds_cumulus_top_radius);
    float ray_length = (terrain_distance >= 0.0) ? terrain_distance : dists.y;
    ray_length = clamp(ray_length - dists.x, 0.0, 2e4);
    if (any(isnan(dists)) || any(isinf(dists)) || dists.x < 0.0 || dists.y < 0.0 ||
        ray_length <= 0.0 || clouds_params.l0_coverage.y < eps)
        return shaderlod_original_draw_cumulus_clouds(viewer, direction, clear_sky, terrain_distance, dither);
    // Use final-resolution pixels, even though cloud rendering is checkerboarded.
    // Analytic pinhole cone width, no fragment derivatives or temporal selector.
    vec2 focal_pixels = abs(vec2(gbufferProjection[0].x, gbufferProjection[1].y)) * view_res;
    float focal = max(focal_pixels.x, focal_pixels.y);
    ivec3 noise_size = textureSize(SAMPLER_WORLEY_SWIRLEY, 0);
    if (any(isnan(focal_pixels)) || any(isinf(focal_pixels)) || min(focal_pixels.x, focal_pixels.y) <= 0.0 ||
        min(noise_size.x, min(noise_size.y, noise_size.z)) < 1)
        return shaderlod_original_draw_cumulus_clouds(viewer, direction, clear_sky, terrain_distance, dither);
    vec3 view_direction = mat3(gbufferModelView) * direction;
    float forward = -view_direction.z;
    if (any(isnan(view_direction)) || any(isinf(view_direction)) || forward <= 0.0 ||
        abs(dot(view_direction, view_direction) - 1.0) > 0.001)
        return shaderlod_original_draw_cumulus_clouds(viewer, direction, clear_sky, terrain_distance, dither);
    // The atmosphere viewer includes a planet/origin shift. Subtract it
    // analytically: ray displacement from that same viewer is camera-relative.
    vec3 camera_relative_entry_blocks = direction * (dists.x / CLOUDS_SCALE);
    float entry_blocks = length(camera_relative_entry_blocks);
    // Conservative finite-pixel cone bound, including off-axis foreshortening.
    // For a half-pixel screen displacement delta >= 1/focal, the sine of
    // its ray angle is >= delta * forward^2 / (1 + delta * forward).
    // The projection's finest axis bounds every screen-plane orientation.
    float half_pixel = 1.0 / focal;
    float footprint_blocks = 2.0 * entry_blocks * half_pixel * forward * forward /
        (1.0 + half_pixel * forward);
    // Finest voxel of pinned fine-noise field; a conservative eligibility heuristic,
    // not proof of a band-limited density or perceptual parity.
    float feature_blocks = 1.0 / (0.005 * float(max(noise_size.x, max(noise_size.y, noise_size.z))) * CLOUDS_SCALE);
    float target = shaderlod_volume_work_target(policy, capability_valid, full, 8u,
        entry_blocks, ray_length / CLOUDS_SCALE, footprint_blocks, feature_blocks, overhead_weight);
    if (target >= float(full))
        return shaderlod_original_draw_cumulus_clouds(viewer, direction, clear_sky, terrain_distance, dither);
    return shaderlod_volume_draw_cumulus(viewer, direction, clear_sky, terrain_distance, dither, target);
}

#endif // SHADERLOD_PHOTON_VOLUME_ADAPTER_INCLUDED
