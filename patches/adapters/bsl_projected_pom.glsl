#ifndef SHADERLOD_BSL_PROJECTED_POM_INCLUDED
#define SHADERLOD_BSL_PROJECTED_POM_INCLUDED
// Derived from projected_material_budget.glsl; full primary ray, no texture/mip additions.
int shaderlod_bsl_projected_pom(float fade, bool opaqueMaterial) {
    if (!shaderlod_optimization_enabled(shaderlodOptProjectedPom) || !opaqueMaterial ||
        !(fade >= 0.5 && fade < 1.0) || !(PARALLAX_QUALITY >= 1 && PARALLAX_QUALITY <= 65536)) return PARALLAX_QUALITY;
    float view2 = dot(viewVector, viewVector);
    if (!(view2 > 1e-8 && view2 < 1e12 && viewVector.z < 0.0 &&
        viewVector.z * viewVector.z > 0.0625 * view2) ||
        any(lessThanEqual(vTexCoordAM.pq, vec2(0.0))) || any(greaterThan(vTexCoordAM.pq, vec2(1.0)))) return PARALLAX_QUALITY;
    int selected = shaderlod_bsl_count(PARALLAX_QUALITY, (PARALLAX_QUALITY + 1) / 2, sqrt(view2), shaderlodOptProjectedPom);
    if (selected == PARALLAX_QUALITY) return selected;
    vec2 ray = viewVector.xy * PARALLAX_DEPTH * (1.0 - fade) / -viewVector.z;
    vec2 dx = dcdx / vTexCoordAM.pq, dy = dcdy / vTexCoordAM.pq;
    vec2 size = vec2(atlasSize) * vTexCoordAM.pq;
    if (!all(lessThan(abs(ray), vec2(1e20))) || !all(lessThan(abs(dx), vec2(1e4))) ||
        !all(lessThan(abs(dy), vec2(1e4))) || !all(greaterThanEqual(size, vec2(1.0))) ||
        !all(lessThanEqual(size, vec2(65536.0)))) return PARALLAX_QUALITY;
    float determinant = dx.x * dy.y - dy.x * dx.y;
    float largest = max(dot(dx, dx), dot(dy, dy));
    if (!(largest > 1e-20 && largest < 1e8 && abs(determinant) > 0.01 * largest)) return PARALLAX_QUALITY;
    vec2 pixels = vec2(dy.y * ray.x - dy.x * ray.y, -dx.y * ray.x + dx.x * ray.y) / determinant;
    float floorWork = ceil(max(length(pixels) * 4.0, length(ray * size) * 2.0));
    if (!(floorWork >= 0.0 && floorWork < float(PARALLAX_QUALITY))) return PARALLAX_QUALITY;
    return shaderlod_bsl_min(PARALLAX_QUALITY, shaderlod_bsl_max(selected, int(floorWork)));
}
#endif
