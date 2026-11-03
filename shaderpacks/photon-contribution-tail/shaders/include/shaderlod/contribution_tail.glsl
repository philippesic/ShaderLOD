// ShaderLOD contribution-budget experiment; private and default OFF.
// The scattering recurrence derives from Photon v1.3b by Benjamin Stott
// (SixthSurge). Keep the complete original Photon LICENSE in every archive.
// Relative error is bounded for positive pre-tonemap X/Y components only.
#ifndef SHADERLOD_CONTRIBUTION_TAIL_INCLUDED
#define SHADERLOD_CONTRIBUTION_TAIL_INCLUDED
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;

bool shaderlod_tail_finite_nonnegative(float x) {
    return !isnan(x) && !isinf(x) && x >= 0.0 && x <= 1e20;
}

bool shaderlod_tail_valid_inputs(float density, float light_depth,
    float sky_depth, float ground_depth, float step_transmittance,
    float cos_theta, float bounced_light) {
    return shaderlod_tail_finite_nonnegative(density) &&
        shaderlod_tail_finite_nonnegative(light_depth) &&
        shaderlod_tail_finite_nonnegative(sky_depth) &&
        shaderlod_tail_finite_nonnegative(ground_depth) &&
        shaderlod_tail_finite_nonnegative(bounced_light) &&
        shaderlod_tail_finite_nonnegative(step_transmittance) && step_transmittance <= 1.0 &&
        !isnan(cos_theta) && !isinf(cos_theta) && abs(cos_theta) <= 1.0 &&
        shaderlod_tail_finite_nonnegative(clouds_params.l0_scattering_coeff) &&
        clouds_params.l0_scattering_coeff <= 1.0 &&
        shaderlod_tail_finite_nonnegative(clouds_params.l0_extinction_coeff) &&
        clouds_params.l0_extinction_coeff >= 1e-20 &&
        shaderlod_tail_finite_nonnegative(clouds_params.l0_shadow) &&
        clouds_params.l0_shadow <= 1.0 &&
        shaderlod_tail_finite_nonnegative(clouds_params.l0_cumulus_stratus_blend) &&
        clouds_params.l0_cumulus_stratus_blend <= 1.0;
}

bool shaderlod_tail_within_budget(vec2 prefix, float amplitude, float powder,
    float falloff, vec3 g, float extinction, float light_depth, float sky_depth,
    float ground_depth, float shadow, float bounced_light) {
    // Tiny/invalid prefixes and invalid derived state retain all eight orders.
    if (any(isnan(prefix)) || any(isinf(prefix)) || any(lessThan(prefix, vec2(1e-20))) ||
        any(greaterThan(prefix, vec2(1e20))) ||
        !shaderlod_tail_finite_nonnegative(amplitude) ||
        !shaderlod_tail_finite_nonnegative(powder) || powder > 4.0 ||
        !shaderlod_tail_finite_nonnegative(falloff) || falloff > 0.55 ||
        !shaderlod_tail_finite_nonnegative(extinction) ||
        any(isnan(g)) || any(isinf(g)) || any(lessThan(g, vec3(0.0))) ||
        any(greaterThan(g, vec3(0.5)))) return false;

    // Sum A4..A7 with the actual powder recurrence. Powder can exceed one;
    // an infinite geometric series with ratio 0.55 would be incorrect.
    float amplitude_sum = amplitude;
    for (uint j = 0u; j < 3u; ++j) {
        amplitude *= falloff * powder;
        powder = mix(powder, sqrt(powder), 0.5);
        amplitude_sum += amplitude;
    }
    // |g| shrinks in every remaining order, so the order-4 HG maximum is
    // an envelope for all four phases, independent of the current angle.
    vec3 one_minus_g = vec3(1.0) - g;
    vec3 peaks = isotropic_phase * (vec3(1.0) + g) / (one_minus_g * one_minus_g);
    float phase_peak = dot(vec3(0.65, 0.10, 0.25), peaks);
    // E7 is the smallest remaining extinction. Each nonnegative depth's
    // attenuation is consequently at most exp(-E7 * depth), not always 1.
    float last_extinction = extinction * 0.064; // 0.4^3
    vec3 attenuation = exp(-last_extinction * vec3(light_depth, sky_depth, ground_depth));
    vec2 bound = amplitude_sum * vec2(
        attenuation.x * phase_peak * (1.0 - 0.5 * shadow) +
        isotropic_phase * (attenuation.z * bounced_light + attenuation.y * 0.5 * shadow),
        attenuation.y * isotropic_phase);
    if (any(isnan(bound)) || any(isinf(bound)) || any(lessThan(bound, vec2(0.0))) ||
        any(greaterThan(bound, vec2(1e20)))) return false;
    // Outward 1% bound margin and inward 1% prefix margin. This is a
    // float safety margin, not a certified error bound for every GPU driver.
    return all(lessThanEqual(bound * 1.01, prefix * (0.02 * 0.99)));
}

vec2 shaderlod_tail_scattering(
    float density,
    float light_optical_depth,
    float sky_optical_depth,
    float ground_optical_depth,
    float step_transmittance,
    float cos_theta,
    float bounced_light
) {
    vec2 scattering = vec2(0.0);

    float scatter_amount = clouds_params.l0_scattering_coeff;
    float extinct_amount = clouds_params.l0_extinction_coeff;

    float scattering_integral_times_density =
        (1.0 - step_transmittance) / clouds_params.l0_extinction_coeff;

    float powder_effect = clouds_powder_effect(
        density + density * clouds_params.l0_cumulus_stratus_blend,
        cos_theta
    );
    float scattering_falloff = 0.55 *
        mix(lift(clamp01(clouds_params.l0_scattering_coeff / 0.1), 0.33),
            1.0,
            cos_theta * 0.5 + 0.5);

    float phase = clouds_phase_single(cos_theta);
    vec3 phase_g = pow(vec3(0.6, 0.9, 0.3), vec3(1.0 + light_optical_depth));

    for (uint i = 0u; i < 8u; ++i) {
        scattering.x += scatter_amount *
            exp(-extinct_amount * light_optical_depth) * phase *
            (1.0 - 0.5 * clouds_params.l0_shadow);
        scattering.x += scatter_amount *
            exp(-extinct_amount * ground_optical_depth) * isotropic_phase *
            bounced_light;
        scattering.x += scatter_amount *
            exp(-extinct_amount * sky_optical_depth) * isotropic_phase *
            clouds_params.l0_shadow *
            0.5; // fake bounced lighting from the layer above
        scattering.y += scatter_amount *
            exp(-extinct_amount * sky_optical_depth) * isotropic_phase;

        scatter_amount *= scattering_falloff * powder_effect;
        extinct_amount *= 0.4;
        phase_g *= 0.8;

        powder_effect = mix(powder_effect, sqrt(powder_effect), 0.5);

        // State now describes order i+1. Decide before evaluating its phase.
        if (i == 3u && shaderlod_tail_within_budget(
            scattering, scatter_amount, powder_effect, scattering_falloff,
            phase_g, extinct_amount, light_optical_depth, sky_optical_depth,
            ground_optical_depth, clouds_params.l0_shadow, bounced_light)) break;

        phase = clouds_phase_multi(cos_theta, phase_g);
    }

    return scattering * scattering_integral_times_density;
}

vec2 clouds_cumulus_scattering(float density, float light_optical_depth,
    float sky_optical_depth, float ground_optical_depth, float step_transmittance,
    float cos_theta, float bounced_light) {
    // Fallback occurs before any clone/bound work. Unknown bridge/global,
    // unsupported inputs and original shader quality all retain upstream work.
    if (shaderlodBridgeVersion != 2 || shaderlodEnabled != 1 ||
        !shaderlod_tail_valid_inputs(density, light_optical_depth, sky_optical_depth,
            ground_optical_depth, step_transmittance, cos_theta, bounced_light)) {
        return shaderlod_tail_original_scattering(density, light_optical_depth,
            sky_optical_depth, ground_optical_depth, step_transmittance,
            cos_theta, bounced_light);
    }
    return shaderlod_tail_scattering(density, light_optical_depth, sky_optical_depth,
        ground_optical_depth, step_transmittance, cos_theta, bounced_light);
}
#endif // SHADERLOD_CONTRIBUTION_TAIL_INCLUDED
