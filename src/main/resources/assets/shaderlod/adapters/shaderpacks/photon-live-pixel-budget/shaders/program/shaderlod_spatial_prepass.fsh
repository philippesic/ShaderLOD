/*
--------------------------------------------------------------------------------

  Photon Shader by SixthSurge

  program/d4_deferred_shading:
  Shade terrain and entities, draw sky

--------------------------------------------------------------------------------
*/

#define SHADERLOD_PHOTON_COMPARE_PROGRAM
#include "/include/global.glsl"

layout(location = 0) out vec3 shaderlodSkyColor;
layout(location = 1) out vec4 shaderlodSpatialOutput;
/* RENDERTARGETS: 0,2 */

in vec2 uv;

flat in vec3 ambient_color;
flat in vec3 light_color;

#if defined WORLD_OVERWORLD
flat in vec3 sun_color;
flat in vec3 moon_color;

#include "/include/fog/overworld/parameters.glsl"
flat in OverworldFogParameters fog_params;

#if defined SH_SKYLIGHT
flat in vec3 sky_sh[9];
flat in vec3 skylight_up;
#endif

flat in float rainbow_amount;
#endif

// ------------
//   Uniforms
// ------------

uniform sampler2D noisetex;

uniform sampler2D colortex0; // skytextured output
uniform sampler2D colortex1; // gbuffer 0
uniform sampler2D colortex2; // gbuffer 1
uniform sampler2D colortex4; // sky map
uniform sampler2D colortex5; // previous frame color
uniform sampler2D colortex6; // ambient lighting data
uniform sampler2D colortex7; // previous frame fog scattering
uniform sampler2D colortex11; // clouds history
uniform sampler2D colortex12; // clouds data
uniform sampler2D colortex14; // ambient lighting history data

#ifndef USE_SEPARATE_ENTITY_DRAWS
uniform sampler2D colortex3; // OF damage overlay, armor glint
#endif

#if defined WORLD_OVERWORLD && defined GALAXY
uniform sampler2D colortex13;
#define galaxy_sampler colortex13
#endif

#ifdef CLOUD_SHADOWS
uniform sampler2D colortex8; // cloud shadow map
#endif

uniform sampler3D depthtex0; // atmosphere scattering LUT
uniform sampler2D depthtex1;

#ifdef COLORED_LIGHTS
uniform sampler3D light_sampler_a;
uniform sampler3D light_sampler_b;
#endif

#ifdef BLOCKY_CLOUDS
uniform sampler2D depthtex2; // minecraft cloud texture
#endif

#ifndef WORLD_NETHER
#ifdef SHADOW
uniform sampler2D shadowtex0;
uniform sampler2DShadow shadowtex1;

#ifdef SHADOW_COLOR
uniform sampler2D shadowcolor0;
#endif
#endif
#endif

uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferPreviousModelView;
uniform mat4 gbufferPreviousProjection;

uniform mat4 shadowModelView;
uniform mat4 shadowModelViewInverse;
uniform mat4 shadowProjection;
uniform mat4 shadowProjectionInverse;

uniform vec3 cameraPosition;
uniform vec3 previousCameraPosition;

uniform float eyeAltitude;
uniform float near;
uniform float far;

uniform int worldTime;
uniform int moonPhase;
uniform float sunAngle;
uniform float rainStrength;
uniform float wetness;

uniform int frameCounter;
uniform float frameTimeCounter;

uniform int isEyeInWater;
uniform float blindness;
uniform float nightVision;
uniform float darknessFactor;

uniform vec3 light_dir;
uniform vec3 sun_dir;
uniform vec3 moon_dir;
uniform vec3 view_light_dir;

uniform vec2 view_res;
uniform vec2 view_pixel_size;
uniform vec2 taa_offset;

uniform float biome_cave;
uniform float biome_may_rain;
uniform float biome_may_snow;

uniform float time_sunrise;
uniform float time_noon;
uniform float time_sunset;
uniform float time_midnight;

uniform float world_age;
uniform float eye_skylight;

/*
const bool colortex5MipmapEnabled = true;
*/
#if defined SHADERLOD_LIVE_PIXEL_BUDGET && SHADERLOD_PHOTON_DEAD_MIP_CHAIN == 1 && !defined WORLD_OVERWORLD
/*
const bool colortex11MipmapEnabled = false;
*/
#else
/*
const bool colortex11MipmapEnabled = true;
*/
#endif
/*
*/

// ------------
//   Includes
// ------------

#define ATMOSPHERE_SCATTERING_LUT depthtex0
#define TEMPORAL_REPROJECTION

#include "/include/fog/simple_fog.glsl"
#include "/include/lighting/diffuse_lighting.glsl"
#include "/include/lighting/shadows/common.glsl"
#if defined SHADERLOD_SHARED_SPATIAL && !defined GALAXY && !defined BORDER_FOG && !defined SKY_REFLECTIONS && !defined NORMAL_MAPPING && !defined SPECULAR_MAPPING && !defined SHADOW_COLOR && !defined SHADOW_VPS && !defined COLORED_LIGHTS && !defined CLOUD_SHADOWS && !defined SH_SKYLIGHT && !defined DISTANT_HORIZONS && !defined VOXY && !defined TAAU && !defined BLOCKY_CLOUDS && !defined ENVIRONMENT_REFLECTIONS && defined WORLD_OVERWORLD && defined PROGRAM_DEFERRED4
vec2 shaderlodLightingPixel;
#endif // SHADERLOD_SHARED_SPATIAL_SCOPE
#include "/include/lighting/shadows/pcss.glsl"
#include "/include/lighting/shadows/ssrt.glsl"
#include "/include/lighting/specular_lighting.glsl"
#include "/include/misc/lod_mod_support.glsl"
#include "/include/misc/purkinje_shift.glsl"
#include "/include/sky/sky.glsl"
#include "/include/surface/edge_highlight.glsl"
#include "/include/surface/material.glsl"
#include "/include/surface/rain_puddles.glsl"
#include "/include/utility/bicubic.glsl"
#include "/include/utility/color.glsl"
#include "/include/utility/encoding.glsl"
#include "/include/utility/space_conversion.glsl"

#if defined WORLD_OVERWORLD
#include "/include/sky/clouds/sampling.glsl"
#include "/include/sky/rainbow.glsl"
#if defined BLOCKY_CLOUDS
#include "/include/sky/blocky_clouds.glsl"
#endif
#endif

#if defined CLOUD_SHADOWS
#include "/include/lighting/cloud_shadows.glsl"
#endif

#if defined SHADERLOD_SHARED_SPATIAL && !defined GALAXY && !defined BORDER_FOG && !defined SKY_REFLECTIONS && !defined NORMAL_MAPPING && !defined SPECULAR_MAPPING && !defined SHADOW_COLOR && !defined SHADOW_VPS && !defined COLORED_LIGHTS && !defined CLOUD_SHADOWS && !defined SH_SKYLIGHT && !defined DISTANT_HORIZONS && !defined VOXY && !defined TAAU && !defined BLOCKY_CLOUDS && !defined ENVIRONMENT_REFLECTIONS && defined WORLD_OVERWORLD && defined PROGRAM_DEFERRED4
#define SHADERLOD_PHOTON_PRODUCER
#include "/include/shaderlod/photon_spatial_adapter.glsl"
#include "/include/shaderlod/photon_original_sky.glsl"
#endif
void main() {
    // Preserve original skytextured input in unsupported dimensions; logical0 uses Iris ping-pong.
    shaderlodSkyColor = texelFetch(colortex0, ivec2(gl_FragCoord.xy), 0).rgb;
    shaderlodSpatialOutput = vec4(0.0, 0.0, 0.0, -1.0);
#if defined SHADERLOD_SHARED_SPATIAL && !defined GALAXY && !defined BORDER_FOG && !defined SKY_REFLECTIONS && !defined NORMAL_MAPPING && !defined SPECULAR_MAPPING && !defined SHADOW_COLOR && !defined SHADOW_VPS && !defined COLORED_LIGHTS && !defined CLOUD_SHADOWS && !defined SH_SKYLIGHT && !defined DISTANT_HORIZONS && !defined VOXY && !defined TAAU && !defined BLOCKY_CLOUDS && !defined ENVIRONMENT_REFLECTIONS && defined WORLD_OVERWORLD && defined PROGRAM_DEFERRED4
    // Exact original prelude before ANY depth/tile divergence; implicit LUT/cloud derivatives retain upstream scope.
    ivec2 texel = ivec2(gl_FragCoord.xy);

    // Sample textures

    float depth = texelFetch(combined_depth_tex, texel, 0).x;
    vec4 gbuffer_data_0 = texelFetch(colortex1, texel, 0);
#if defined NORMAL_MAPPING || defined SPECULAR_MAPPING
    vec4 gbuffer_data_1 = texelFetch(colortex2, texel, 0);
#endif
#if !defined USE_SEPARATE_ENTITY_DRAWS
    vec4 overlays = texelFetch(colortex3, texel, 0);
#endif

    // Check for LoD terrain

#ifdef LOD_MOD_ACTIVE
    float depth_mc = texelFetch(depthtex1, texel, 0).x;
    float depth_lod = texelFetch(lod_depth_tex, texel, 0).x;
    bool is_lod = is_lod_terrain(depth_mc, depth_lod);
#else
    const bool is_lod = false;
#define depth_mc depth
#endif

    // Space conversions

    bool is_hand;
    fix_hand_depth(depth_mc, is_hand);

    vec3 position_view = screen_to_view_space(
        combined_projection_matrix_inverse,
        vec3(uv, depth),
        true
    );
    vec3 position_scene = view_to_scene_space(position_view);
    vec3 position_world = position_scene + cameraPosition;
    vec3 direction_world =
        normalize(position_scene - gbufferModelViewInverse[3].xyz);

#if defined WORLD_OVERWORLD
    // Atmosphere

    vec3 atmosphere = atmosphere_scattering(
        direction_world,
        sun_color,
        sun_dir,
        moon_color,
        moon_dir,
        /* use_klein_nishina_phase */ depth == 1.0
    );

    // Read clouds/aurora/crepuscular rays

    float clouds_apparent_distance;
    vec4 clouds_and_aurora =
        read_clouds_and_aurora(uv, clouds_apparent_distance);

    // Blocky clouds

#ifdef BLOCKY_CLOUDS
    vec3 world_start_pos = gbufferModelViewInverse[3].xyz + cameraPosition;
    vec3 world_end_pos = position_world;

    float dither = texelFetch(noisetex, texel & 511, 0).b;
    dither = r1(frameCounter, dither);

    vec4 blocky_clouds = raymarch_blocky_clouds(
        world_start_pos,
        world_end_pos,
        depth == 1.0,
        blocky_clouds_altitude_l0,
        dither
    );

#ifdef BLOCKY_CLOUDS_LAYER_2
    float visibility = pow4(blocky_clouds.a);
    vec4 blocky_clouds_l2 = raymarch_blocky_clouds(
        world_start_pos,
        world_end_pos,
        depth == 1.0,
        blocky_clouds_altitude_l1,
        dither
    );
    blocky_clouds.rgb += blocky_clouds_l2.xyz * visibility;
    blocky_clouds.a *= mix(1.0, blocky_clouds_l2.a, visibility);
#endif

    float new_alpha = sqr(sqr(blocky_clouds.a));
    blocky_clouds.rgb +=
        atmosphere * (1.0 - new_alpha) * (blocky_clouds.a - new_alpha);
    blocky_clouds.a = new_alpha;
#endif
#endif

    // Full-resolution original sky, independent of mod policy/cache readiness.
    if (depth == 1.0)
        shaderlodSkyColor = shaderlod_photon_original_sky(direction_world, atmosphere,
            clouds_and_aurora, clouds_apparent_distance);
    // Keep original sky/prelude above this uniform idle-work gate.
    if (!shaderlod_spatial_adapter_has_coarse_demand()) return;
    if (!shaderlod_photon_cache_contract()) return;
    ivec2 pixel = ivec2(gl_FragCoord.xy); ivec2 tile_coordinate = pixel / 2;
    ivec2 lane = pixel % 2;
    if (lane.y != 0) return;
    if (lane.x == 0) {
        ShaderlodSpatialCache cache = shaderlod_spatial_produce(true, true, 2, tile_coordinate,
            ivec2(view_res), shaderlod_photon_near_guard(), shaderlod_region_default_limits(),
            SHADERLOD_PHOTON_QUANTITY, frameCounter);
        if (cache.valid) shaderlodSpatialOutput = cache.linear_quantity;
    } else {
        // Independent metadata lane: same immutable inputs, same shared all-member guard/plan.
        // No visibility assertion; quantity lane's negative alpha additionally certifies finite evaluation.
        ShaderlodSpatialTile tile = shaderlod_spatial_prepare(2, tile_coordinate, ivec2(view_res),
            shaderlod_photon_near_guard(), shaderlod_region_default_limits());
        if (!tile.eligible) return;
        ShaderlodSpatialPlan plan = shaderlod_spatial_adapter_plan(tile.minimum_distance_blocks);
        if (!shaderlod_spatial_plan_valid(plan) || !plan.evaluate_medium) return;
        shaderlodSpatialOutput = vec4(float(frameCounter), 258.0,
            float(tile_coordinate.x + shaderlod_photon_tile_width() * tile_coordinate.y),
            tile.minimum_distance_blocks);
    }
#endif
}
