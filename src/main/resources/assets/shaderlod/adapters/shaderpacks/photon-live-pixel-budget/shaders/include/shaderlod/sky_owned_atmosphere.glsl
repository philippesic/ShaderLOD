/* Photon Shader by SixthSurge, v1.3b. See unchanged archive LICENSE.
 * Isolated ShaderLOD pilot: exact six-argument helper, locally renamed;
 * only four LUT reads use explicit LOD zero for the existing sky branch.
 * No distance approximation or transition; native quality/math preserved.
 * Requires actual bound single-level sampler verification before acceptance.
 */
#if defined WORLD_OVERWORLD && !defined BORDER_FOG && !defined BLOCKY_CLOUDS && defined ATMOSPHERE_SCATTERING_LUT
vec3 shaderlod_sky_owned_atmosphere(
    vec3 ray_dir,
    vec3 sun_color,
    vec3 sun_dir,
    vec3 moon_color,
    vec3 moon_dir,
    bool use_klein_nishina_phase
) {
    // Calculate nu, mu, mu_s

    float mu = ray_dir.y;

    float nu_sun = dot(ray_dir, sun_dir);
    float nu_moon = dot(ray_dir, moon_dir);

    float mu_sun = sun_dir.y;
    float mu_moon = moon_dir.y;

#ifndef SKY_GROUND
    float horizon_mu = mix(
        -0.01,
        0.03,
        clamp01(smoothstep(-0.05, 0.1, mu_sun) + smoothstep(0.05, 0.1, mu_moon))
    );
    mu = max(mu, horizon_mu);
#endif

    // Improved mapping for nu from Spectrum by Zombye

    float half_range_nu, nu_min, nu_max;

    half_range_nu = sqrt((1.0 - mu * mu) * (1.0 - mu_sun * mu_sun));
    nu_min = mu * mu_sun - half_range_nu;
    nu_max = mu * mu_sun + half_range_nu;

    float u_nu_sun =
        (nu_min == nu_max) ? nu_min : (nu_sun - nu_min) / (nu_max - nu_min);
    u_nu_sun = get_uv_from_unit_range(u_nu_sun, scattering_res.x);

    half_range_nu = sqrt((1.0 - mu * mu) * (1.0 - mu_moon * mu_moon));
    nu_min = mu * mu_moon - half_range_nu;
    nu_max = mu * mu_moon + half_range_nu;

    float u_nu_moon =
        (nu_min == nu_max) ? nu_min : (nu_moon - nu_min) / (nu_max - nu_min);
    u_nu_moon = get_uv_from_unit_range(u_nu_moon, scattering_res.x);

    // Stretch the sky near the horizon upwards (to make it easier to admire the
    // sunset without zooming in)

    if (mu > 0.0) {
        mu *= sqrt(sqrt(mu));
    }

    // Mapping for mu

    const float r = planet_radius; // distance to the planet centre
    const float H = sqrt(
        atmosphere_outer_radius_sq - atmosphere_inner_radius_sq
    ); // distance to the atmosphere upper limit for a horizontal ray at ground
       // level
    const float rho = sqrt(
        max0(planet_radius * planet_radius - atmosphere_inner_radius_sq)
    ); // distance to the horizon

    // Discriminant of the quadratic equation for the intersections of the ray
    // (r, mu) with the ground
    float rmu = r * mu;
    float discriminant = rmu * rmu - r * r + atmosphere_inner_radius_sq;

    float u_mu;
    if (mu < 0.0 && discriminant >= 0.0) { // Ray (r, mu) intersects ground
        // Distance to the ground for the ray (r, mu) and its minimum and
        // maximum values over all mu
        float d = -rmu - sqrt(max0(discriminant));
        float d_min = r - atmosphere_inner_radius;
        float d_max = rho;

        u_mu = d_max == d_min ? 0.0 : (d - d_min) / (d_max - d_min);
        u_mu = get_uv_from_unit_range(u_mu, scattering_res.y / 2);
        u_mu = 0.5 - 0.5 * u_mu;
    } else {
        // Distance to exit the atmosphere outer limit for the ray (r, mu) and
        // its minimum and maximum values over all mu
        float d = -rmu + sqrt(discriminant + H * H);
        float d_min = atmosphere_outer_radius - r;
        float d_max = rho + H;

        u_mu = (d - d_min) / (d_max - d_min);
        u_mu = get_uv_from_unit_range(u_mu, scattering_res.y / 2);
        u_mu = 0.5 + 0.5 * u_mu;
    }

    // Mapping for mu_s

    float d, a;

    const float d_min = atmosphere_thickness;
    const float d_max = H;

    // Distance to the atmosphere upper limit for the ray
    // (atmosphere_inner_radius, min_mu_s)
    float D = intersect_sphere(
                  min_mu_s,
                  atmosphere_inner_radius,
                  atmosphere_outer_radius
    )
                  .y;
    float A = (D - d_min) / (d_max - d_min);

    // Distance to the atmosphere outer limit for the ray
    // (atmosphere_inner_radius, mu_s)
    d = intersect_sphere(
            mu_sun,
            atmosphere_inner_radius,
            atmosphere_outer_radius
    )
            .y;
    a = (d - d_min) / (d_max - d_min);

    // An ad-hoc function equal to 0 for mu_s = min_mu_s (because then d = D and
    // thus a = A, equal to 1 for mu_s = 1 (because then d = d_min and thus a =
    // 0), and with a large slope around mu_s = 0, to get more texture samples
    // near the horizon
    float u_mu_sun =
        get_uv_from_unit_range(max0(1.0 - a / A) / (1.0 + a), scattering_res.z);

    d = intersect_sphere(
            mu_moon,
            atmosphere_inner_radius,
            atmosphere_outer_radius
    )
            .y;
    a = (d - d_min) / (d_max - d_min);

    float u_mu_moon =
        get_uv_from_unit_range(max0(1.0 - a / A) / (1.0 + a), scattering_res.z);

    // Sample atmosphere LUT

    vec3 uv_sc = vec3(
        u_nu_sun * 0.5,
        u_mu,
        u_mu_sun
    ); // Rayleigh + multiple scattering, sunlight
    vec3 uv_sm =
        vec3(u_nu_sun * 0.5 + 0.5, u_mu, u_mu_sun); // Mie scattering, sunlight
    vec3 uv_mc = vec3(
        u_nu_moon * 0.5,
        u_mu,
        u_mu_moon
    ); // Rayleigh + multiple scattering, moonlight
    vec3 uv_mm = vec3(
        u_nu_moon * 0.5 + 0.5,
        u_mu,
        u_mu_moon
    ); // Mie scattering, moonlight

    vec3 scattering_sc = textureLod(ATMOSPHERE_SCATTERING_LUT, uv_sc, 0.0).rgb;
    vec3 scattering_sm = textureLod(ATMOSPHERE_SCATTERING_LUT, uv_sm, 0.0).rgb;
    vec3 scattering_mc = textureLod(ATMOSPHERE_SCATTERING_LUT, uv_mc, 0.0).rgb;
    vec3 scattering_mm = textureLod(ATMOSPHERE_SCATTERING_LUT, uv_mm, 0.0).rgb;

    vec3 mie_phase_sun = atmosphere_mie_phase_sun(nu_sun);
    vec3 mie_phase_moon = atmosphere_mie_phase_moon(nu_moon);

    vec3 atmosphere =
        (scattering_sc + scattering_sm * mie_phase_sun) * sun_color +
        (scattering_mc + scattering_mm * mie_phase_moon) * moon_color;

    return atmosphere_post_processing(atmosphere);
}
#endif // SHADERLOD_SKY_OWNED_ATMOSPHERE
