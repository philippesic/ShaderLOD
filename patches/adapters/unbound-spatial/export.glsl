#include "/lib/shaderlod/unbound_spatial_rate.glsl"
uniform int shaderlodOptSpatialLightingRate;
uniform int shaderlodUnboundSpatialExportReady;
uniform int shaderlodUnboundSpatialSolid;
uniform int shaderlodUnboundSpatialExportMode;
// Private textures occupy output slots3..7; native RENDERTARGETS stays064.
// A scoped native FBO attachment + draw-buffer lease is required before Ready1.
bool shaderlodUnboundSpatialExportAllowed(vec4 color,vec3 normalM,vec3 geoNormal,vec3 shadowMult,vec2 lm,float distanceBlocks,bool noSmooth,bool noDirectional,bool noAO,bool centerBias,int sss,float smoothnessG,float smoothnessD,float emission,float materialMask){
 #ifdef SHADERLOD_UNBOUND_SPATIAL_GRAPH
 bool requested=SHADERLOD_UNBOUND_SPATIAL_REUSE==1?shaderlod_optimization_enabled(shaderlodOptSpatialLightingReuse):shaderlod_optimization_enabled(shaderlodOptSpatialLightingRate);
 return requested&&shaderlodUnboundSpatialExportReady==1&&shaderlodUnboundSpatialSolid==1&&shaderlodUnboundSpatialExportMode>=1&&shaderlodUnboundSpatialExportMode<=3&&
 distanceBlocks>max(256.0,shaderlodNear)&&shaderlod_unbound_rate_weight(distanceBlocks)>=1.0&&distanceBlocks<1e6&&color.a>=.999&&sss==0&&smoothnessG>=0.0&&smoothnessG<=1.0&&smoothnessD>=0.0&&smoothnessD<=1.0&&emission==0.0&&materialMask>=0.0&&materialMask<=1.0&&
 !noSmooth&&!noDirectional&&!noAO&&!centerBias&&all(equal(normalM,geoNormal))&&all(equal(shadowMult,vec3(1.0)))&&geoNdotU<=.99&&frameCounter>=0&&frameCounter<16777216;
 #else
 return false;
 #endif
}
