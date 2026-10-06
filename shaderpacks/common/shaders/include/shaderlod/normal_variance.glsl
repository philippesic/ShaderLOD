#if !defined SHADERLOD_NORMAL_VARIANCE_INCLUDED
#define SHADERLOD_NORMAL_VARIANCE_INCLUDED
// Conditional filtered-normal covariance policy; no sampler/derivative/uniform.
// Slopes are tangent normal.xy/normal.z; covariance has dimensionless slope^2.
// vec3 covariance packs (xx,xy,yy) in one VERIFIED common orthonormal frame.
// This does not estimate moments or guarantee BRDF energy/reflection equivalence.
// On rejection, the adapter must RETAIN ORIGINAL NORMAL WORK before skipping it.

bool shaderlod_normal_variance_finite(float x) {
    return !isnan(x) && !isinf(x);
}
bool shaderlod_normal_variance_finite(vec3 x) {
    return !any(isnan(x)) && !any(isinf(x));
}

bool shaderlod_normal_variance_covariance_valid(vec3 covariance) {
    if (!shaderlod_normal_variance_finite(covariance) || covariance.x < 0.0 || covariance.z < 0.0 ||
        covariance.x > 0.01 || covariance.z > 0.01 || abs(covariance.y) > 0.01) return false;
    return covariance.y * covariance.y <= covariance.x * covariance.z;
}

// Valid ONLY for an independent centered contribution of known covariance.
// Removed field variance is (1-w*w)*C, not the error-field variance (1-w)^2*C.
// Cross-octave covariance must be included separately or independence proven.
vec3 shaderlod_normal_variance_removed(vec3 covariance, float retained_weight) {
    if (!shaderlod_normal_variance_covariance_valid(covariance) ||
        !shaderlod_normal_variance_finite(retained_weight) || retained_weight < 0.0 || retained_weight > 1.0)
        return vec3(-1.0);
    return (1.0 - retained_weight * retained_weight) * covariance;
}

// mode1: Gaussian/Beckmann slope moment addition (scale2).
// mode2: experimental radial-truncated isotropic GGX moment match. Full GGX slope
// variance diverges; radial_mass is the chosen finite-domain mass, NOT a proof of
// full GGX convolution. Conditional variance coefficient is
// (-log(1-q)-q)/(2*q); different q gives a different alpha mapping.
float shaderlod_normal_variance_alpha_scale(int mode, bool allow_ggx_approximation, float radial_mass) {
    if (mode == 1) return 2.0;
    if (mode != 2 || !allow_ggx_approximation || !shaderlod_normal_variance_finite(radial_mass) ||
        radial_mass < 0.5 || radial_mass > 0.999) return 0.0;
    float coefficient = (-log(1.0 - radial_mass) - radial_mass) / (2.0 * radial_mass);
    return 1.0 / coefficient;
}

// upstream_alpha is the NDF width alpha, NOT perceptual roughness or alpha^2.
// Covariance must be the validated nonnegative C_full-C_retained; no guessed
// octave amplitude, upper error bound or unobserved phase statistic is accepted.
// A scalar isotropic alpha cannot reproduce strongly anisotropic removed detail.
// Limits are eligibility bounds: reject rather than silently clamp compensation.
float shaderlod_normal_variance_alpha(
    float upstream_alpha, vec3 removed_covariance, int mode,
    bool enabled, bool near_or_full, bool moments_verified,
    bool mean_slope_preserved, bool material_supported, bool unstable_frame,
    bool allow_ggx_approximation, float radial_mass,
    float view_cosine, float light_cosine, float grazing_floor,
    float maximum_axis_variance, float maximum_anisotropy,
    float maximum_alpha_delta, out bool accepted
) {
    accepted = false;
    if (!enabled || near_or_full || !moments_verified || !mean_slope_preserved ||
        !material_supported || unstable_frame ||
        !shaderlod_normal_variance_finite(upstream_alpha) || upstream_alpha < 0.0001 || upstream_alpha > 1.0 ||
        !shaderlod_normal_variance_covariance_valid(removed_covariance) ||
        !shaderlod_normal_variance_finite(view_cosine) || view_cosine < 0.0 || view_cosine > 1.0 ||
        !shaderlod_normal_variance_finite(light_cosine) || light_cosine < 0.0 || light_cosine > 1.0 ||
        !shaderlod_normal_variance_finite(grazing_floor) || grazing_floor < 0.0 || grazing_floor > 0.5 ||
        view_cosine <= grazing_floor || light_cosine <= grazing_floor ||
        !shaderlod_normal_variance_finite(maximum_axis_variance) || maximum_axis_variance < 0.0 || maximum_axis_variance > 0.01 ||
        removed_covariance.x > maximum_axis_variance || removed_covariance.z > maximum_axis_variance ||
        !shaderlod_normal_variance_finite(maximum_anisotropy) || maximum_anisotropy < 0.0 || maximum_anisotropy > 1.0 ||
        !shaderlod_normal_variance_finite(maximum_alpha_delta) || maximum_alpha_delta < 0.0 || maximum_alpha_delta > 1.0)
        return upstream_alpha;
    float trace = removed_covariance.x + removed_covariance.z;
    float discriminant = length(vec2(removed_covariance.x - removed_covariance.z, 2.0 * removed_covariance.y));
    if (trace > 0.0 && discriminant > maximum_anisotropy * trace) return upstream_alpha;
    float scale = shaderlod_normal_variance_alpha_scale(mode, allow_ggx_approximation, radial_mass);
    if (!(scale > 0.0)) return upstream_alpha;
    float result = sqrt(upstream_alpha * upstream_alpha + scale * 0.5 * trace);
    if (!shaderlod_normal_variance_finite(result) || result > 1.0 || result - upstream_alpha > maximum_alpha_delta)
        return upstream_alpha;
    accepted = true;
    return result;
}
#endif
