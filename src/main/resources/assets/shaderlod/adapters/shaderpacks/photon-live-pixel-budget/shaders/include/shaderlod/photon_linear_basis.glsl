// Pinned Photon v1.3b by SixthSurge; original LICENSE preserved.
// Original AO, shadow and diffuse operations; canonical-white diffuse factorization.
bool shaderlod_photon_ao_union_eligible(ivec2 origin) {
    ivec2 ambient_size = textureSize(colortex6, 0);
    ivec2 depth_size = textureSize(colortex14, 0);
    if (any(lessThanEqual(ambient_size, ivec2(0))) || any(notEqual(ambient_size, depth_size)) ||
        any(isnan(view_res)) || any(isinf(view_res)) || any(lessThan(view_res, vec2(2.0))) ||
        any(lessThan(origin, ivec2(0))) || any(greaterThanEqual(origin + ivec2(1), ivec2(view_res))))
        return false;
    ivec2 first_base = ivec2((vec2(origin) + 0.5) * (0.5 / taau_render_scale) - 0.5);
    ivec2 minimum_tap = first_base;
    ivec2 maximum_tap = first_base + ivec2(1);
    for (int member = 1; member < 4; ++member) {
        ivec2 pixel = origin + ivec2(member & 1, member >> 1);
        // Match upstream ivec2 truncation toward zero, including first-row/column edges.
        ivec2 base = ivec2((vec2(pixel) + 0.5) * (0.5 / taau_render_scale) - 0.5);
        minimum_tap = min(minimum_tap, base);
        maximum_tap = max(maximum_tap, base + ivec2(1));
    }
    if (any(lessThan(minimum_tap, ivec2(0))) || any(greaterThanEqual(maximum_tap, ambient_size)) ||
        any(greaterThan(maximum_tap - minimum_tap, ivec2(2)))) return false;
    float minimum_ao = 1.0;
    float maximum_ao = 0.0;
    for (int y = 0; y < 3; ++y) {
        for (int x = 0; x < 3; ++x) {
            ivec2 tap = minimum_tap + ivec2(x, y);
            if (any(greaterThan(tap, maximum_tap))) continue;
            vec4 ambient = texelFetch(colortex6, tap, 0);
            float ambient_depth = texelFetch(colortex14, tap, 0).x;
            if (any(isnan(ambient)) || any(isinf(ambient)) ||
                isnan(ambient_depth) || isinf(ambient_depth) ||
                ambient.x < 0.0 || ambient.x > 1.0 || ambient_depth < 0.0 || ambient_depth > 1.0)
                return false;
            minimum_ao = min(minimum_ao, ambient.x);
            maximum_ao = max(maximum_ao, ambient.x);
        }
    }
    return maximum_ao - minimum_ao <= 0.10;
}

vec4 shaderlod_photon_evaluate(ivec2 texel, vec4 gbuffer_data_0, float depth,
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

        if (!shaderlod_photon_material(material_mask, material)) return vec4(0.0, 0.0, 0.0, -1.0);
        // Canonical white factorization is legal only for the approved zero-SSS/emission nonmetal material.
        material.albedo = vec3(1.0);

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
