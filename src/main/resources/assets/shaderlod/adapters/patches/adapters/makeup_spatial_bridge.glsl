// Original family-specific bridge, native MakeUp9.5g material/AA retained.
#if (SHADERLOD_MAKEUP_SPATIAL_REUSE == 1 || SHADERLOD_MAKEUP_SPATIAL_RATE == 1 || SHADERLOD_MAKEUP_WORLD_PIXEL == 1) && SHADERLOD_REFINEMENT_ADAPTER == 1 && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined GBUFFER_TERRAIN && defined FOLIAGE_V && defined MATERIAL_GLOSS && defined SHADOW_CASTING && SHADOW_LOCK == 0 && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH
#define SHADERLOD_MAKEUP_SPATIAL_SOURCE 1
// The normal derived-program builder changes only this exact constant and main export call.
const bool shaderlodMakeupSpatialExportStage=false;
const bool shaderlodMakeupSpatialRateStage=false;

uniform int shaderlodMakeupSpatialReady;
uniform int shaderlodOptSpatialLightingRate;
uniform vec2 shaderlodMakeupSpatialSize;
#if SHADERLOD_MAKEUP_WORLD_PIXEL == 1
const bool shaderlodMakeupWorldPixelStage=false;
uniform sampler2D colortex9,colortex11;
vec4 shaderlodMakeupWorldFog(vec2 p){if(shaderlodMakeupSpatialReady==1)return texture2DLod(gaux4,p,0.0);return shaderlodMakeupFog(p);}
#define shaderlodMakeupFog(p) shaderlodMakeupWorldFog(p)
#endif
uniform sampler2D colortex8,colortex10,colortex12,colortex13,colortex14,colortex15;
uniform int shaderlodEffectsVersion,shaderlodOpaqueLightingMode;
uniform float shaderlodOpaqueLightingNear,shaderlodOpaqueLightingFar,shaderlodOpaqueLightingTransition;
uniform float shaderlodOpaqueLightingMediumWorkRatio,shaderlodOpaqueLightingFarWorkRatio;
// Existing alpha transport remains the literal fallback. Sparse lighting is explicitLOD0
// only after the normal current bound-sampler parity proof; the exporter is never issued without it.
vec4 shaderlodMakeupSpatialShadow1(vec3 p){if(shaderlodMakeupSpatialExportStage || shaderlodMakeupSpatialReady==1)return vec4(textureLod(shadowtex1,p,0.0));return shaderlodMakeupShadow1(p);}
#define shaderlodMakeupShadow1(p) shaderlodMakeupSpatialShadow1(p)
#ifdef COLORED_SHADOW
vec4 shaderlodMakeupSpatialShadow0(vec3 p){if(shaderlodMakeupSpatialExportStage || shaderlodMakeupSpatialReady==1)return vec4(textureLod(shadowtex0,p,0.0));return shaderlodMakeupShadow0(p);}
vec4 shaderlodMakeupSpatialShadowColor0(vec2 p){if(shaderlodMakeupSpatialExportStage || shaderlodMakeupSpatialReady==1)return texture2DLod(shadowcolor0,p,0.0);return shaderlodMakeupShadowColor0(p);}
#define shaderlodMakeupShadow0(p) shaderlodMakeupSpatialShadow0(p)
#define shaderlodMakeupShadowColor0(p) shaderlodMakeupSpatialShadowColor0(p)
#endif
#if SHADERLOD_MAKEUP_WORLD_PIXEL == 1
#define SHADERLOD_MAKEUP_REAL_LIGHT_DECL vec3
#else
#define SHADERLOD_MAKEUP_REAL_LIGHT_DECL
#endif
#else
#define SHADERLOD_MAKEUP_SPATIAL_SOURCE 0
#define SHADERLOD_MAKEUP_REAL_LIGHT_DECL vec3
#endif
