// ShaderLOD exact-zero receiver experiment; private, default OFF.
// Photon v1.3b by Benjamin Stott (SixthSurge); retain its complete LICENSE.
#ifndef SHADERLOD_WATER_SHADOW_ZERO_INCLUDED
#define SHADERLOD_WATER_SHADOW_ZERO_INCLUDED

bool shaderlod_water_shadow_known_enabled() {
    return shaderlod_optimization_enabled(shaderlodOptWaterShadowZero);
}

bool shaderlod_water_shadow_zero(bool is_water, vec3 albedo, float NoL) {
    // Use the existing consumers' exact cutoffs. NaN/Inf never authorize skip.
    // Eligibility is local and instantaneous; no distance or history is used.
    return shaderlod_water_shadow_known_enabled() && is_water &&
        !any(isnan(albedo)) && !any(isinf(albedo)) &&
        !isnan(NoL) && !isinf(NoL) &&
        max_of(albedo) < eps && NoL <= eps;
}

float shaderlod_water_shadow_sample(vec3 coord) {
    // The active per-fragment gate can diverge. Explicit LOD avoids implicit
    // derivatives. Pinned Iris/Photon shadowtex1 has no mipmaps, base LOD 0.
    // The bridge test is draw-uniform. Unknown/global OFF executes original
    // implicit sampling with precisely its original filter-control topology.
    if (shaderlod_water_shadow_known_enabled()) {
        return textureLod(shadowtex1, coord, 0.0);
    }
    return texture(shadowtex1, coord);
}
#endif
