/*
--------------------------------------------------------------------------------

  Photon Shader by SixthSurge

  program/d4_deferred_shading:
  Shade terrain and entities, draw sky

--------------------------------------------------------------------------------
*/

#include "/include/global.glsl"

layout(location = 0) out vec4 shaderlodLightingCache;
/* RENDERTARGETS: 2 */

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
const bool colortex11MipmapEnabled = true;
*/

// ------------
//   Includes
// ------------

#define ATMOSPHERE_SCATTERING_LUT depthtex0
#define TEMPORAL_REPROJECTION

#include "/include/fog/simple_fog.glsl"
#include "/include/lighting/diffuse_lighting.glsl"
#include "/include/lighting/shadows/common.glsl"
#if defined SHADERLOD_LIGHTING_RATE_PROBE && !defined NORMAL_MAPPING && !defined SPECULAR_MAPPING && !defined SHADOW_COLOR && !defined SHADOW_VPS && !defined COLORED_LIGHTS && !defined CLOUD_SHADOWS && !defined SH_SKYLIGHT && !defined DISTANT_HORIZONS && !defined VOXY && !defined TAAU && !defined BLOCKY_CLOUDS && !defined ENVIRONMENT_REFLECTIONS && defined WORLD_OVERWORLD && defined PROGRAM_DEFERRED4
vec2 shaderlodLightingPixel;
#endif // SHADERLOD_LIGHTING_RATE_SCOPE
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

#if defined SHADERLOD_LIGHTING_RATE_PROBE && !defined NORMAL_MAPPING && !defined SPECULAR_MAPPING && !defined SHADOW_COLOR && !defined SHADOW_VPS && !defined COLORED_LIGHTS && !defined CLOUD_SHADOWS && !defined SH_SKYLIGHT && !defined DISTANT_HORIZONS && !defined VOXY && !defined TAAU && !defined BLOCKY_CLOUDS && !defined ENVIRONMENT_REFLECTIONS && defined WORLD_OVERWORLD && defined PROGRAM_DEFERRED4
#include "/include/shaderlod/lighting_rate_policy.glsl"
#endif // SHADERLOD_LIGHTING_RATE_SCOPE
#if defined SHADERLOD_LIGHTING_RATE_PROBE && !defined NORMAL_MAPPING && !defined SPECULAR_MAPPING && !defined SHADOW_COLOR && !defined SHADOW_VPS && !defined COLORED_LIGHTS && !defined CLOUD_SHADOWS && !defined SH_SKYLIGHT && !defined DISTANT_HORIZONS && !defined VOXY && !defined TAAU && !defined BLOCKY_CLOUDS && !defined ENVIRONMENT_REFLECTIONS && defined WORLD_OVERWORLD && defined PROGRAM_DEFERRED4
vec4 shaderlod_evaluate_representative(ivec2 texel, vec4 gbuffer_data_0, float depth,
    vec3 position_view, vec3 position_scene, vec3 position_world, vec3 direction_world) {
    vec2 uv = (vec2(texel) + 0.5) * view_pixel_size;
    bool is_hand = false;
    bool is_lod = false;
    vec3 fragment_color;
#if !defined USE_SEPARATE_ENTITY_DRAWS
    vec4 overlays = texelFetch(colortex3, texel, 0);
#endif
        // Sample ambient occlusion a while before using it (latency hiding)

        vec2 half_res_pos = shaderlodLightingPixel * (0.5 / taau_render_scale) - 0.5;

        ivec2 i = ivec2(half_res_pos);
        vec2 f = fract(half_res_pos);

        ivec2 p10 = i + ivec2(1, 0);
        ivec2 p01 = i + ivec2(0, 1);
        ivec2 p11 = i + ivec2(1, 1);

        vec4 ambient_00 = texelFetch(colortex6, i, 0);
        vec4 ambient_10 = texelFetch(colortex6, p10, 0);
        vec4 ambient_01 = texelFetch(colortex6, p01, 0);
        vec4 ambient_11 = texelFetch(colortex6, p11, 0);
        float ambient_depth_00 = texelFetch(colortex14, i, 0).x;
        float ambient_depth_10 = texelFetch(colortex14, p10, 0).x;
        float ambient_depth_01 = texelFetch(colortex14, p01, 0).x;
        float ambient_depth_11 = texelFetch(colortex14, p11, 0).x;

        // Unpack gbuffer data

        mat4x2 data = mat4x2(
            unpack_unorm_2x8(gbuffer_data_0.x),
            unpack_unorm_2x8(gbuffer_data_0.y),
            unpack_unorm_2x8(gbuffer_data_0.z),
            unpack_unorm_2x8(gbuffer_data_0.w)
        );

        vec3 albedo = vec3(data[0], data[1].x);
        uint material_mask = uint(255.0 * data[1].y);
        vec3 flat_normal = decode_unit_vector(data[2]);
        vec2 light_levels = data[3];

#if !defined USE_SEPARATE_ENTITY_DRAWS
        uint overlay_id = uint(255.0 * overlays.a);
        albedo = overlay_id == 0u ? albedo + overlays.rgb
                                  : albedo; // enchantment glint
        albedo = overlay_id == 1u && !is_hand
            ? 2.0 * albedo * overlays.rgb
            : albedo; // damage overlay
#endif

        // Get material and normal

        Material material = material_from(
            albedo,
            material_mask,
            position_world,
            flat_normal,
            light_levels
        );

        vec3 normal = flat_normal;
        bool parallax_shadow = false;

#ifdef LOD_MOD_ACTIVE
        if (!is_lod) {
#endif

#ifdef NORMAL_MAPPING
            normal = decode_unit_vector(gbuffer_data_1.xy);
#endif

#ifdef SPECULAR_MAPPING
            vec4 specular_map = vec4(
                unpack_unorm_2x8(gbuffer_data_1.z),
                unpack_unorm_2x8(gbuffer_data_1.w)
            );
            decode_specular_map(specular_map, material, parallax_shadow);
#elif defined NORMAL_MAPPING
        parallax_shadow = gbuffer_data_1.z >= 0.5;
#endif

#ifdef LOD_MOD_ACTIVE
        }
#endif

        // Rain puddles

#if defined WORLD_OVERWORLD && defined RAIN_PUDDLES
        if (wetness > eps && biome_may_rain > eps) {
            bool puddle = get_rain_puddles(
                position_world,
                flat_normal,
                light_levels,
                material.porosity,
                material_mask,
                normal,
                material.albedo,
                material.f0,
                material.roughness,
                material.ssr_multiplier
            );
        }
#endif

        if (!shaderlod_rate_material(material_mask, material)) return vec4(0.0, 0.0, 0.0, -1.0);
        // Canonical white factorization is legal only for the approved zero-SSS/emission nonmetal material.
        material.albedo = vec3(1.0);
        float ao_span = max(max(ambient_00.x, ambient_10.x), max(ambient_01.x, ambient_11.x)) -
            min(min(ambient_00.x, ambient_10.x), min(ambient_01.x, ambient_11.x));
        if (ao_span > 0.10) return vec4(0.0, 0.0, 0.0, -1.0);

        // Upscale ambient occlusion

        float lin_z = screen_to_view_space_depth(
            combined_projection_matrix_inverse,
            depth
        );

#define depth_weight(reversed_depth) \
    exp2( \
        -10.0 * \
        abs(screen_to_view_space_depth( \
                combined_projection_matrix_inverse, \
                1.0 - reversed_depth \
            ) - \
            lin_z) \
    )
        float w00 = depth_weight(ambient_depth_00) * (1.0 - f.x) * (1.0 - f.y);
        float w10 = depth_weight(ambient_depth_10) * (f.x - f.x * f.y);
        float w01 = depth_weight(ambient_depth_01) * (f.y - f.x * f.y);
        float w11 = depth_weight(ambient_depth_11) * (f.x * f.y);
#undef depth_weight

        vec4 ambient_upscaled;
        float weight_sum = w00 + w10 + w01 + w11;

        if (abs(weight_sum) > eps) {
            ambient_upscaled = ambient_00 * w00 + ambient_10 * w10 +
                ambient_01 * w01 + ambient_11 * w11;

            ambient_upscaled *= rcp(weight_sum);
        } else {
            ambient_upscaled = ambient_00;
        }

        float ao = ambient_upscaled.x;
        float ambient_sss = ambient_upscaled.y;

        vec3 bent_normal;
        bent_normal.xy = ambient_upscaled.zw * 2.0 - 1.0;
        bent_normal.z =
            sqrt(clamp01(1.0 - dot(bent_normal.xy, bent_normal.xy)));
        bent_normal = mat3(gbufferModelViewInverse) * bent_normal;

        // Sense check bent normal
        if (dot(bent_normal, normal) < eps) {
            bent_normal = normal;
        }

        // No AO/bent normal on hand
        if (is_hand) {
            ao = 1.0;
            bent_normal = normal;
        }

        // Calculate lighting dot products

        float NoL = dot(normal, light_dir);
        float NoV = clamp01(dot(normal, -direction_world));
        float LoV = dot(light_dir, -direction_world);
        float halfway_norm = inversesqrt(2.0 * LoV + 2.0);
        float NoH = (NoL + NoV) * halfway_norm;
        float LoH = LoV * halfway_norm + halfway_norm;

        // Cloud shadows

#if defined WORLD_OVERWORLD && defined CLOUD_SHADOWS
        float cloud_shadows = get_cloud_shadows(colortex8, position_scene);
#else
        const float cloud_shadows = 1.0;
#endif

        // Shadows

#if defined WORLD_OVERWORLD || defined WORLD_END
        vec3 shadows = vec3(0.0);
        float shadow_distance_fade = 1.0;
        float sss_depth = 0.0;

        if (NoL > 1e-3 || material.sss_amount > 1e-3) {
            // Calculate near shadows
            vec3 shadow_near = vec3(0.0);
            float shadow_distant = 0.0;
            float sss_depth_near = 0.0;
            float sss_depth_distant = 0.0;

#ifdef SHADOW
            shadow_near = get_filtered_shadows(
                position_scene,
                flat_normal,
                light_levels.y,
                cloud_shadows,
                material.sss_amount,
                shadow_distance_fade,
                sss_depth_near
            );
#endif

            // Calculate distant shadows
            if (shadow_distance_fade >= eps) {
#ifdef SHADOW_SSRT
                shadow_distant = get_screen_space_shadows(
                    uv,
                    position_view,
                    depth,
#ifdef LOD_MOD_ACTIVE
                    depth_lod,
#endif
                    light_levels.y,
                    material.sss_amount > eps,
                    sss_depth_distant
                );
#else
                shadow_distant = get_lightmap_shadows(light_levels.y);
#endif
            }

            shadows =
                mix(shadow_near,
                    vec3(shadow_distant),
                    clamp01(shadow_distance_fade));

            sss_depth =
                mix(sss_depth_near,
                    sss_depth_distant,
                    clamp01(shadow_distance_fade));

            // Apply parallax shadow
#if defined POM && defined POM_SHADOW && \
    (defined SPECULAR_MAPPING || defined NORMAL_MAPPING)
            shadows *= float(!parallax_shadow);
#endif
        }
#else
        const vec3 shadows = vec3(1.0);
        const float shadow_distance_fade = 1.0;
        const float sss_depth = 0.0;
#endif

        // Diffuse lighting

        fragment_color = get_diffuse_lighting(
            material,
            position_scene,
            normal,
            flat_normal,
            bent_normal,
            shadows,
            light_levels,
            ao,
            ambient_sss,
            sss_depth,
#ifdef CLOUD_SHADOWS
            cloud_shadows,
#endif
#ifdef SHADOW_SSRT
            0.0,
#else
            shadow_distance_fade,
#endif
            NoL,
            NoV,
            NoH,
            LoV
        );

    return vec4(fragment_color, clamp01(shadows.x * ao));
}
#endif // SHADERLOD_LIGHTING_RATE_SCOPE
void main() {
    shaderlodLightingCache = vec4(0.0, 0.0, 0.0, -1.0);
#if defined SHADERLOD_LIGHTING_RATE_PROBE && !defined NORMAL_MAPPING && !defined SPECULAR_MAPPING && !defined SHADOW_COLOR && !defined SHADOW_VPS && !defined COLORED_LIGHTS && !defined CLOUD_SHADOWS && !defined SH_SKYLIGHT && !defined DISTANT_HORIZONS && !defined VOXY && !defined TAAU && !defined BLOCKY_CLOUDS && !defined ENVIRONMENT_REFLECTIONS && defined WORLD_OVERWORLD && defined PROGRAM_DEFERRED4
    vec3 bands; vec2 ratios;
    if (!shaderlod_rate_parameters(bands, ratios) || !shaderlod_rate_environment()) return;
    ivec2 tile = ivec2(gl_FragCoord.xy);
    ivec2 origin = 2 * tile;
    if (any(greaterThanEqual(origin + ivec2(1), ivec2(view_res)))) return;
    vec3 positions[4]; vec3 normals[4]; vec4 packed[4]; float depths[4]; vec2 lightmaps[4];
    vec3 normals_view[4]; int material_ids[4]; bool protected_members[4];
    float nearest_distance = 1e20;
    for (int i = 0; i < 4; ++i) {
        ivec2 p = origin + ivec2(i & 1, i >> 1);
        depths[i] = texelFetch(depthtex1, p, 0).x;
        bool hand; fix_hand_depth(depths[i], hand);
        if (hand || depths[i] >= 1.0 || depths[i] <= 0.0) return;
        packed[i] = texelFetch(colortex1, p, 0);
        vec2 a = unpack_unorm_2x8(packed[i].x);
        vec2 b = unpack_unorm_2x8(packed[i].y);
        if (uint(255.0 * b.y) != 0u) return;
#if !defined USE_SEPARATE_ENTITY_DRAWS
        if (texelFetch(colortex3, p, 0) != vec4(0.0)) return;
#endif
        // Pinned rgb_to_hsl().z is max RGB (despite its name), matching material_from mask0 exactly.
#ifdef HARDCODED_SPECULAR
        float roughness = sqr(1.0 - 0.33 * smoothstep(0.2, 0.6, max(max(a.x, a.y), b.x)));
        if (roughness < 0.75) return;
#endif
        normals[i] = decode_unit_vector(unpack_unorm_2x8(packed[i].z));
        lightmaps[i] = unpack_unorm_2x8(packed[i].w);
        normals_view[i] = mat3(gbufferModelView) * normals[i];
        material_ids[i] = 0; protected_members[i] = false;
        positions[i] = screen_to_view_space(combined_projection_matrix_inverse,
            vec3((vec2(p) + 0.5) * view_pixel_size, depths[i]), true);
        if (any(isnan(positions[i])) || any(isinf(positions[i]))) return;
        nearest_distance = min(nearest_distance, length(positions[i]));
    }
    if (shaderlod_rate_weight(nearest_distance) <= 0.0) return;
    // Frozen shared helper; view-space normals match position-space plane residuals.
    if (!shaderlod_surface_tile_eligible(true, positions, normals_view, material_ids,
        lightmaps, protected_members, max(0.001, bands.x - 0.5 * bands.z),
        false, vec2(0.0), shaderlod_surface_default_limits())) return;
    shaderlodLightingPixel = vec2(origin) + 0.5; // Static representative, upstream shadow jitter at actual pixel.
    vec3 scene = view_to_scene_space(positions[0]);
    vec3 world = scene + cameraPosition;
    vec3 direction = normalize(scene - gbufferModelViewInverse[3].xyz);
    shaderlodLightingCache = shaderlod_evaluate_representative(origin, packed[0], depths[0], positions[0], scene, world, direction);
#endif // SHADERLOD_LIGHTING_RATE_SCOPE
}
