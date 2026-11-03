#ifndef SHADERLOD_SILDUR_PROJECTED_POM_INCLUDED
#define SHADERLOD_SILDUR_PROJECTED_POM_INCLUDED
#include "/include/shaderlod/projected_material_budget.glsl"
int shaderlod_sildur_pom_count(int native_count, float distance_blocks, float alpha,
    vec3 view, vec2 step_uv, vec2 atlas_scale, vec2 atlas_dx, vec2 atlas_dy, vec2 atlas_size) {
    if (!shaderlod_optimization_enabled(shaderlodOptProjectedPom) ||
        !(alpha >= 0.999 && alpha <= 1.0) || !(POM_DIST >= 16.0 && POM_DIST <= 128.0) ||
        !(distance_blocks >= 0.5 * POM_DIST && distance_blocks < POM_DIST) ||
        any(lessThanEqual(atlas_scale,vec2(0.0))) || any(greaterThan(atlas_scale,vec2(1.0)))) return native_count;
    float view2 = dot(view,view);
    if (!(view2 > 1e-8 && view2 < 1e12 && view.z < 0.0 && view.z * view.z > 0.0625 * view2)) return native_count;
    float retained = shaderlodPreset == 0 ? 0.875 : 0.75;
    ShaderlodWorkPolicy policy = ShaderlodWorkPolicy(shaderlodBridgeVersion,2,1,true,2,true,true,
        vec3(0.75,0.90625,0.125) * POM_DIST,vec2(0.5 * (1.0 + retained),retained));
    // mipmap[] is the native global unconditional atlas derivative; no derivative calls in POM branch.
    return shaderlod_projected_material_budget(policy,true,true,false,distance_blocks,native_count,
        step_uv * float(native_count),atlas_dx / atlas_scale,atlas_dy / atlas_scale,atlas_size * atlas_scale,1.0);
}
#endif
