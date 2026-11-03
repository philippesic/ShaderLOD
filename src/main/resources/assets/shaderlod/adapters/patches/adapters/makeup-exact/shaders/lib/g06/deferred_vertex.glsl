#include "/lib/config.glsl"

/* Color utils */

#ifdef THE_END
    #include "/lib/exactnative/lib/color_utils_end.glsl"
#elif defined NETHER
    #include "/lib/exactnative/lib/color_utils_nether.glsl"
#else
    #include "/lib/exactnative/lib/color_utils.glsl"
#endif
/* Uniforms */

uniform mat4 gbufferModelView;
#if SHADERLOD_G06_AO_ATTENUATION == 1 && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && (defined MC_OS_MAC || (defined MC_OS_LINUX && defined SHADERLOD_G06_ASAHI_RUNTIME_GUARD)) && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && AO == 1 && AOSTEPS == 5 && AA_TYPE == 2 && SHADERLOD_REFINEMENT_ADAPTER == 0 && SHADERLOD_MAKEUP_AO_HALF_RES == 0 && SHADERLOD_MAKEUP_DEPTH_PRIMING == 0 && SHADERLOD_MAKEUP_SPATIAL_REUSE == 0 && SHADERLOD_MAKEUP_SPATIAL_RATE == 0 && SHADERLOD_MAKEUP_WORLD_PIXEL == 0 && (SHADERLOD_MAKEUP_EARLY_ALPHA == 0 || (SHADERLOD_MAKEUP_EARLY_ALPHA == 1 && defined SHADERLOD_COMBINATION_RUNTIME_GUARD)) && (SHADERLOD_MAKEUP_DEAD_FRAME_COPY == 0 || (SHADERLOD_MAKEUP_DEAD_FRAME_COPY == 1 && defined SHADERLOD_COMBINATION_RUNTIME_GUARD)) && (SHADERLOD_G04_TRANSLUCENT_FASTPATH == 0 || (SHADERLOD_G04_TRANSLUCENT_FASTPATH == 1 && defined SHADERLOD_COMBINATION_RUNTIME_GUARD))
uniform float near;
uniform float far;
uniform float aspectRatioInverse;
uniform float fovYInverse;
varying float shaderlodG06FiniteCamera;
#endif


#if (V_CLOUDS != 0 && !defined UNKNOWN_DIM) && !defined NO_CLOUDY_SKY
    uniform float rainStrength;
#endif

/* Ins / Outs */

varying vec2 texcoord;
varying vec3 upVector;

#if (V_CLOUDS != 0 && !defined UNKNOWN_DIM) && !defined NO_CLOUDY_SKY
    varying float umbral;
    varying vec3 cloudColor;
    varying vec3 darkCloudColor;
#endif

#if AO == 1
    varying float fogDensityCoeff;
#endif

/* Utility functions */

#if (V_CLOUDS != 0 && !defined UNKNOWN_DIM) && !defined NO_CLOUDY_SKY
    #include "/lib/exactnative/lib/luma.glsl"
#endif

    // MAIN FUNCTION ------------------

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    texcoord = gl_MultiTexCoord0.xy;
    #if SHADERLOD_G06_AO_ATTENUATION == 1 && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && (defined MC_OS_MAC || (defined MC_OS_LINUX && defined SHADERLOD_G06_ASAHI_RUNTIME_GUARD)) && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH && AO == 1 && AOSTEPS == 5 && AA_TYPE == 2 && SHADERLOD_REFINEMENT_ADAPTER == 0 && SHADERLOD_MAKEUP_AO_HALF_RES == 0 && SHADERLOD_MAKEUP_DEPTH_PRIMING == 0 && SHADERLOD_MAKEUP_SPATIAL_REUSE == 0 && SHADERLOD_MAKEUP_SPATIAL_RATE == 0 && SHADERLOD_MAKEUP_WORLD_PIXEL == 0 && (SHADERLOD_MAKEUP_EARLY_ALPHA == 0 || (SHADERLOD_MAKEUP_EARLY_ALPHA == 1 && defined SHADERLOD_COMBINATION_RUNTIME_GUARD)) && (SHADERLOD_MAKEUP_DEAD_FRAME_COPY == 0 || (SHADERLOD_MAKEUP_DEAD_FRAME_COPY == 1 && defined SHADERLOD_COMBINATION_RUNTIME_GUARD)) && (SHADERLOD_G04_TRANSLUCENT_FASTPATH == 0 || (SHADERLOD_G04_TRANSLUCENT_FASTPATH == 1 && defined SHADERLOD_COMBINATION_RUNTIME_GUARD))
    shaderlodG06FiniteCamera = (near >= 0.01 && near <= 1.0 && far >= 16.0 && far <= 4096.0 && aspectRatioInverse > 0.0 && aspectRatioInverse <= 16.0 && fovYInverse > 0.0 && fovYInverse <= 64.0 && AO_STRENGTH >= 0.0 && AO_STRENGTH <= 1.0) ? 1.0 : 0.0;
    #endif

    upVector = normalize(gbufferModelView[1].xyz);

    #if AO == 1
        #if (VOL_LIGHT == 1 && !defined NETHER) || (VOL_LIGHT == 2 && defined SHADOW_CASTING && !defined NETHER)
            fogDensityCoeff = FOG_DENSITY * FOG_ADJUST;
        #else
            fogDensityCoeff = dayBlendFloat(FOG_SUNSET, FOG_DAY, FOG_NIGHT) * FOG_ADJUST;
        #endif
    #endif

    #if (V_CLOUDS != 0 && !defined UNKNOWN_DIM) && !defined NO_CLOUDY_SKY
        #include "/lib/exactnative/lib/volumetric_clouds_vertex.glsl"
    #endif
}