float shaderlod_photon_parallax_height(vec2 coord, vec2 dir, mat2 rot, float t);
#ifndef SHADERLOD_WATER_STATIC_INCLUDED
#define SHADERLOD_WATER_STATIC_INCLUDED

#ifdef SHADERLOD_WATER

// Integer ceil ratios exactly match shaderlod_budget for supported N=1..8.
#define SHADERLOD_WATER_MB ((3 * WATER_WAVE_ITERATIONS + 3) / 4)
#define SHADERLOD_WATER_MP ((WATER_WAVE_ITERATIONS + 1) / 2)
#define SHADERLOD_WATER_MA ((WATER_WAVE_ITERATIONS + 3) / 4)
#define SHADERLOD_WATER_FB ((WATER_WAVE_ITERATIONS + 1) / 2)
#define SHADERLOD_WATER_FP ((WATER_WAVE_ITERATIONS + 3) / 4)
#define SHADERLOD_WATER_FA ((WATER_WAVE_ITERATIONS + 7) / 8)
#define SHADERLOD_WATER_REACHABLE(N) ((N) == SHADERLOD_WATER_MB || (N) == SHADERLOD_WATER_MP || (N) == SHADERLOD_WATER_MA || (N) == SHADERLOD_WATER_FB || (N) == SHADERLOD_WATER_FP || (N) == SHADERLOD_WATER_FA)

int shaderlod_water_octaves(int tier) {
    if (tier == 0) return WATER_WAVE_ITERATIONS;
    int preset = shaderlod_preset();
    if (tier == 1) return preset == 0 ? SHADERLOD_WATER_MB :
        (preset == 1 ? SHADERLOD_WATER_MP : SHADERLOD_WATER_MA);
    return preset == 0 ? SHADERLOD_WATER_FB :
        (preset == 1 ? SHADERLOD_WATER_FP : SHADERLOD_WATER_FA);
}

// Emit only reachable cheap counts. A full-count analytic kernel is needed
// only when that experimental compile-time option is enabled.
#if SHADERLOD_WATER_REACHABLE(1) && (1 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
#define SHADERLOD_WATER_N 1
#define SHADERLOD_WATER_HEIGHT_FN shaderlod_water_height_1
#define SHADERLOD_WATER_GRADIENT_FN shaderlod_water_gradient_1
#define SHADERLOD_WATER_NORMAL_FN shaderlod_water_normal_1
#include "/include/shaderlod/water_height_static.glsl"
#undef SHADERLOD_WATER_N
#undef SHADERLOD_WATER_HEIGHT_FN
#undef SHADERLOD_WATER_GRADIENT_FN
#undef SHADERLOD_WATER_NORMAL_FN
#endif

#if SHADERLOD_WATER_REACHABLE(2) && (2 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
#define SHADERLOD_WATER_N 2
#define SHADERLOD_WATER_HEIGHT_FN shaderlod_water_height_2
#define SHADERLOD_WATER_GRADIENT_FN shaderlod_water_gradient_2
#define SHADERLOD_WATER_NORMAL_FN shaderlod_water_normal_2
#include "/include/shaderlod/water_height_static.glsl"
#undef SHADERLOD_WATER_N
#undef SHADERLOD_WATER_HEIGHT_FN
#undef SHADERLOD_WATER_GRADIENT_FN
#undef SHADERLOD_WATER_NORMAL_FN
#endif

#if SHADERLOD_WATER_REACHABLE(3) && (3 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
#define SHADERLOD_WATER_N 3
#define SHADERLOD_WATER_HEIGHT_FN shaderlod_water_height_3
#define SHADERLOD_WATER_GRADIENT_FN shaderlod_water_gradient_3
#define SHADERLOD_WATER_NORMAL_FN shaderlod_water_normal_3
#include "/include/shaderlod/water_height_static.glsl"
#undef SHADERLOD_WATER_N
#undef SHADERLOD_WATER_HEIGHT_FN
#undef SHADERLOD_WATER_GRADIENT_FN
#undef SHADERLOD_WATER_NORMAL_FN
#endif

#if SHADERLOD_WATER_REACHABLE(4) && (4 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
#define SHADERLOD_WATER_N 4
#define SHADERLOD_WATER_HEIGHT_FN shaderlod_water_height_4
#define SHADERLOD_WATER_GRADIENT_FN shaderlod_water_gradient_4
#define SHADERLOD_WATER_NORMAL_FN shaderlod_water_normal_4
#include "/include/shaderlod/water_height_static.glsl"
#undef SHADERLOD_WATER_N
#undef SHADERLOD_WATER_HEIGHT_FN
#undef SHADERLOD_WATER_GRADIENT_FN
#undef SHADERLOD_WATER_NORMAL_FN
#endif

#if SHADERLOD_WATER_REACHABLE(5) && (5 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
#define SHADERLOD_WATER_N 5
#define SHADERLOD_WATER_HEIGHT_FN shaderlod_water_height_5
#define SHADERLOD_WATER_GRADIENT_FN shaderlod_water_gradient_5
#define SHADERLOD_WATER_NORMAL_FN shaderlod_water_normal_5
#include "/include/shaderlod/water_height_static.glsl"
#undef SHADERLOD_WATER_N
#undef SHADERLOD_WATER_HEIGHT_FN
#undef SHADERLOD_WATER_GRADIENT_FN
#undef SHADERLOD_WATER_NORMAL_FN
#endif

#if SHADERLOD_WATER_REACHABLE(6) && (6 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
#define SHADERLOD_WATER_N 6
#define SHADERLOD_WATER_HEIGHT_FN shaderlod_water_height_6
#define SHADERLOD_WATER_GRADIENT_FN shaderlod_water_gradient_6
#define SHADERLOD_WATER_NORMAL_FN shaderlod_water_normal_6
#include "/include/shaderlod/water_height_static.glsl"
#undef SHADERLOD_WATER_N
#undef SHADERLOD_WATER_HEIGHT_FN
#undef SHADERLOD_WATER_GRADIENT_FN
#undef SHADERLOD_WATER_NORMAL_FN
#endif

#if SHADERLOD_WATER_REACHABLE(7) && (7 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
#define SHADERLOD_WATER_N 7
#define SHADERLOD_WATER_HEIGHT_FN shaderlod_water_height_7
#define SHADERLOD_WATER_GRADIENT_FN shaderlod_water_gradient_7
#define SHADERLOD_WATER_NORMAL_FN shaderlod_water_normal_7
#include "/include/shaderlod/water_height_static.glsl"
#undef SHADERLOD_WATER_N
#undef SHADERLOD_WATER_HEIGHT_FN
#undef SHADERLOD_WATER_GRADIENT_FN
#undef SHADERLOD_WATER_NORMAL_FN
#endif

#if SHADERLOD_WATER_REACHABLE(8) && (8 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
#define SHADERLOD_WATER_N 8
#define SHADERLOD_WATER_HEIGHT_FN shaderlod_water_height_8
#define SHADERLOD_WATER_GRADIENT_FN shaderlod_water_gradient_8
#define SHADERLOD_WATER_NORMAL_FN shaderlod_water_normal_8
#include "/include/shaderlod/water_height_static.glsl"
#undef SHADERLOD_WATER_N
#undef SHADERLOD_WATER_HEIGHT_FN
#undef SHADERLOD_WATER_GRADIENT_FN
#undef SHADERLOD_WATER_NORMAL_FN
#endif

// Parallax requires only three medium kernels, one per uniform preset.
// Each kernel calls either original full height or a literal cheap-height case.
#if SHADERLOD_WATER_MB == WATER_WAVE_ITERATIONS
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN get_water_height
#elif SHADERLOD_WATER_MB == 1
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_1
#elif SHADERLOD_WATER_MB == 2
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_2
#elif SHADERLOD_WATER_MB == 3
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_3
#elif SHADERLOD_WATER_MB == 4
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_4
#elif SHADERLOD_WATER_MB == 5
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_5
#elif SHADERLOD_WATER_MB == 6
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_6
#elif SHADERLOD_WATER_MB == 7
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_7
#elif SHADERLOD_WATER_MB == 8
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_8
#endif
#define SHADERLOD_WATER_PARALLAX_FN shaderlod_water_parallax_balanced
#define SHADERLOD_WATER_PARALLAX_STEPS 3
#include "/include/shaderlod/water_parallax_static.glsl"
#undef SHADERLOD_WATER_PARALLAX_FN
#undef SHADERLOD_WATER_PARALLAX_HEIGHT_FN
#undef SHADERLOD_WATER_PARALLAX_STEPS

#if SHADERLOD_WATER_MP == WATER_WAVE_ITERATIONS
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN get_water_height
#elif SHADERLOD_WATER_MP == 1
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_1
#elif SHADERLOD_WATER_MP == 2
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_2
#elif SHADERLOD_WATER_MP == 3
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_3
#elif SHADERLOD_WATER_MP == 4
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_4
#elif SHADERLOD_WATER_MP == 5
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_5
#elif SHADERLOD_WATER_MP == 6
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_6
#elif SHADERLOD_WATER_MP == 7
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_7
#elif SHADERLOD_WATER_MP == 8
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_8
#endif
#define SHADERLOD_WATER_PARALLAX_FN shaderlod_water_parallax_performance
#define SHADERLOD_WATER_PARALLAX_STEPS 2
#include "/include/shaderlod/water_parallax_static.glsl"
#undef SHADERLOD_WATER_PARALLAX_FN
#undef SHADERLOD_WATER_PARALLAX_HEIGHT_FN
#undef SHADERLOD_WATER_PARALLAX_STEPS

#if SHADERLOD_WATER_MA == WATER_WAVE_ITERATIONS
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN get_water_height
#elif SHADERLOD_WATER_MA == 1
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_1
#elif SHADERLOD_WATER_MA == 2
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_2
#elif SHADERLOD_WATER_MA == 3
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_3
#elif SHADERLOD_WATER_MA == 4
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_4
#elif SHADERLOD_WATER_MA == 5
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_5
#elif SHADERLOD_WATER_MA == 6
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_6
#elif SHADERLOD_WATER_MA == 7
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_7
#elif SHADERLOD_WATER_MA == 8
#define SHADERLOD_WATER_PARALLAX_HEIGHT_FN shaderlod_water_height_8
#endif
#define SHADERLOD_WATER_PARALLAX_FN shaderlod_water_parallax_aggressive
#define SHADERLOD_WATER_PARALLAX_STEPS 1
#include "/include/shaderlod/water_parallax_static.glsl"
#undef SHADERLOD_WATER_PARALLAX_FN
#undef SHADERLOD_WATER_PARALLAX_HEIGHT_FN
#undef SHADERLOD_WATER_PARALLAX_STEPS

#endif // SHADERLOD_WATER kernels
float shaderlod_photon_parallax_height(vec2 coord, vec2 dir, mat2 rot, float t) {
    if (!shaderlod_optimization_enabled(shaderlodOptWaterWaveBudget)) return get_water_height(coord,dir,rot,t);
#if SHADERLOD_WATER_REACHABLE(1) && (1 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
    if (shaderlod_photon_parallax_wave_count == 1) return shaderlod_water_height_1(coord,dir,rot,t);
#endif
#if SHADERLOD_WATER_REACHABLE(2) && (2 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
    if (shaderlod_photon_parallax_wave_count == 2) return shaderlod_water_height_2(coord,dir,rot,t);
#endif
#if SHADERLOD_WATER_REACHABLE(3) && (3 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
    if (shaderlod_photon_parallax_wave_count == 3) return shaderlod_water_height_3(coord,dir,rot,t);
#endif
#if SHADERLOD_WATER_REACHABLE(4) && (4 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
    if (shaderlod_photon_parallax_wave_count == 4) return shaderlod_water_height_4(coord,dir,rot,t);
#endif
#if SHADERLOD_WATER_REACHABLE(5) && (5 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
    if (shaderlod_photon_parallax_wave_count == 5) return shaderlod_water_height_5(coord,dir,rot,t);
#endif
#if SHADERLOD_WATER_REACHABLE(6) && (6 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
    if (shaderlod_photon_parallax_wave_count == 6) return shaderlod_water_height_6(coord,dir,rot,t);
#endif
#if SHADERLOD_WATER_REACHABLE(7) && (7 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
    if (shaderlod_photon_parallax_wave_count == 7) return shaderlod_water_height_7(coord,dir,rot,t);
#endif
#if SHADERLOD_WATER_REACHABLE(8) && (8 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
    if (shaderlod_photon_parallax_wave_count == 8) return shaderlod_water_height_8(coord,dir,rot,t);
#endif
    return get_water_height(coord,dir,rot,t);
}
#define SHADERLOD_WATER_PARALLAX_FN shaderlod_water_parallax_full_steps
#define SHADERLOD_WATER_PARALLAX_STEPS 4
#include "/include/shaderlod/water_parallax_static.glsl"
#undef SHADERLOD_WATER_PARALLAX_FN
#undef SHADERLOD_WATER_PARALLAX_STEPS


vec3 get_water_normal_lod(
    vec3 world_pos, vec3 flat_normal, vec2 coord, vec2 flow_dir,
    float skylight, bool flowing_water
) {
#if defined PROGRAM_SHADOW || !defined SHADERLOD_WATER
    return get_water_normal(world_pos, flat_normal, coord, flow_dir, skylight, flowing_water);
#else
    if (!shaderlod_optimization_enabled(shaderlodOptWaterWaveBudget) || WATER_WAVE_PERSISTENCE >= 2.0)
        return get_water_normal(world_pos, flat_normal, coord, flow_dir, skylight, flowing_water);
    int quality = shaderlod_water_quality_at(distance(world_pos, cameraPosition),
        gl_FragCoord.xy, float(frameCounter));
    // Preserve the untouched original function, including constant loops.
    if (quality == 0) return get_water_normal(world_pos, flat_normal, coord,
        flow_dir, skylight, flowing_water);
    int count = shaderlod_water_octaves(quality);
#if SHADERLOD_WATER_REACHABLE(1) && (1 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
    if (count == 1) return shaderlod_water_normal_1(world_pos, flat_normal,
        coord, flow_dir, skylight, flowing_water);
#endif
#if SHADERLOD_WATER_REACHABLE(2) && (2 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
    if (count == 2) return shaderlod_water_normal_2(world_pos, flat_normal,
        coord, flow_dir, skylight, flowing_water);
#endif
#if SHADERLOD_WATER_REACHABLE(3) && (3 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
    if (count == 3) return shaderlod_water_normal_3(world_pos, flat_normal,
        coord, flow_dir, skylight, flowing_water);
#endif
#if SHADERLOD_WATER_REACHABLE(4) && (4 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
    if (count == 4) return shaderlod_water_normal_4(world_pos, flat_normal,
        coord, flow_dir, skylight, flowing_water);
#endif
#if SHADERLOD_WATER_REACHABLE(5) && (5 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
    if (count == 5) return shaderlod_water_normal_5(world_pos, flat_normal,
        coord, flow_dir, skylight, flowing_water);
#endif
#if SHADERLOD_WATER_REACHABLE(6) && (6 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
    if (count == 6) return shaderlod_water_normal_6(world_pos, flat_normal,
        coord, flow_dir, skylight, flowing_water);
#endif
#if SHADERLOD_WATER_REACHABLE(7) && (7 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
    if (count == 7) return shaderlod_water_normal_7(world_pos, flat_normal,
        coord, flow_dir, skylight, flowing_water);
#endif
#if SHADERLOD_WATER_REACHABLE(8) && (8 < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
    if (count == 8) return shaderlod_water_normal_8(world_pos, flat_normal,
        coord, flow_dir, skylight, flowing_water);
#endif
    return get_water_normal(world_pos, flat_normal, coord, flow_dir, skylight, flowing_water);
#endif
}

vec2 get_water_parallax_coord_lod(
    vec3 world_pos, vec3 tangent_dir, vec2 coord, vec2 flow_dir, bool flowing_water
) {
#if defined PROGRAM_SHADOW || !defined SHADERLOD_WATER
    return get_water_parallax_coord(tangent_dir, coord, flow_dir, flowing_water);
#else
    shaderlod_photon_parallax_wave_count = WATER_WAVE_ITERATIONS;
    if (WATER_WAVE_PERSISTENCE >= 2.0) return get_water_parallax_coord(tangent_dir, coord, flow_dir, flowing_water);
    int quality = shaderlod_water_quality_at(distance(world_pos, cameraPosition),
        gl_FragCoord.xy, float(frameCounter));
    if (quality == 0) return get_water_parallax_coord(tangent_dir, coord, flow_dir, flowing_water);
    if (shaderlod_optimization_enabled(shaderlodOptWaterWaveBudget)) shaderlod_photon_parallax_wave_count = shaderlod_water_octaves(quality);
    if (!shaderlod_optimization_enabled(shaderlodOptWaterParallaxBudget)) return shaderlod_water_parallax_full_steps(tangent_dir, coord, flow_dir, flowing_water);
    if (quality == 2) return coord;
    int preset = shaderlod_preset();
    if (preset == 0) return shaderlod_water_parallax_balanced(tangent_dir, coord, flow_dir, flowing_water);
    if (preset == 1) return shaderlod_water_parallax_performance(tangent_dir, coord, flow_dir, flowing_water);
    return shaderlod_water_parallax_aggressive(tangent_dir, coord, flow_dir, flowing_water);
#endif
}

#endif // SHADERLOD_WATER_STATIC_INCLUDED
