#include "/lib/shaderlod/reimagined_spatial_rate.glsl"
// Four native lighting-input RGBA32F exports; fifth is exact prelighting RGB plus native smoothnessG; scene0 is RGB-only. Never Iris colortex16+ or a live native target.
uniform sampler2D shaderlodReimaginedSpatialInput0;
uniform sampler2D shaderlodReimaginedSpatialInput1;
uniform sampler2D shaderlodReimaginedSpatialInput2;
uniform sampler2D shaderlodReimaginedSpatialInput3;
uniform sampler2D shaderlodReimaginedSpatialInput4;
uniform int shaderlodReimaginedSpatialResources;
uniform int shaderlodReimaginedSpatialMode;
bool shaderlodReimaginedSpatialFinite(vec4 v){return !any(isnan(v))&&!any(isinf(v))&&all(lessThan(abs(v),vec4(1e10)));}
// Source alpha eligibility/latealpha uses original color unchanged. Only this private alpha stores G.
bool shaderlodReimaginedSpatialPayload(ivec2 p,out vec4 value){
 #if SHADERLOD_REFINEMENT_ADAPTER == 1
 value=texelFetch(shaderlodReimaginedSpatialInput4,p,0);
 return shaderlodReimaginedSpatialFinite(value)&&value.a>=0.0&&value.a<=1.0;
 #else
 value=vec4(0.0);return false;
 #endif
}
bool shaderlodReimaginedSpatialInput(ivec2 p,out vec4 a,out vec4 b,out vec4 c,out vec4 d){
 #if SHADERLOD_REFINEMENT_ADAPTER == 1
 if(any(lessThan(p,ivec2(0)))||any(greaterThanEqual(p,ivec2(viewWidth,viewHeight))))return false;
 a=texelFetch(shaderlodReimaginedSpatialInput0,p,0);b=texelFetch(shaderlodReimaginedSpatialInput1,p,0);
 c=texelFetch(shaderlodReimaginedSpatialInput2,p,0);d=texelFetch(shaderlodReimaginedSpatialInput3,p,0);
 return shaderlodReimaginedSpatialResources==1&&shaderlodReimaginedSpatialFinite(a)&&shaderlodReimaginedSpatialFinite(b)&&shaderlodReimaginedSpatialFinite(c)&&shaderlodReimaginedSpatialFinite(d)&&
 d.w==float(frameCounter)&&d.z>=.56&&d.z<1.0&&length(c.xyz)>max(3.0,shaderlodNear)&&
 a.w>=0.0&&a.w<=1.0&&dot(a.xyz,a.xyz)>.99&&dot(a.xyz,a.xyz)<1.01&&
 b.w>=0.0&&b.w<=1.0&&c.w>=0.0&&c.w<=1.0&&d.x>=0.0&&d.x<1000.0&&d.y>0.0&&d.y<16777216.0;
 #else
 a=vec4(0.0);b=vec4(0.0);c=vec4(0.0);d=vec4(0.0);return false;
 #endif
}
// Same working Photon/Bliss geometry classifier, native input quantities retained.
bool shaderlodReimaginedSpatialTile(ivec2 p){
 #if SHADERLOD_REFINEMENT_ADAPTER == 1
 vec3 positions[4],normals[4];int materials[4];vec2 lightmaps[4];bool protectedMembers[4];
 vec4 a0,b0,c0,d0,payload0;if(!shaderlodReimaginedSpatialInput(p,a0,b0,c0,d0)||!shaderlodReimaginedSpatialPayload(p,payload0))return false;
 for(int i=0;i<4;i++){
  vec4 a,b,c,d,payload;if(!shaderlodReimaginedSpatialInput(p+ivec2(i%2,i/2),a,b,c,d)||!shaderlodReimaginedSpatialPayload(p+ivec2(i%2,i/2),payload)||payload.a!=payload0.a)return false;
  positions[i]=c.xyz;normals[i]=a.xyz;materials[i]=int(d.y);lightmaps[i]=vec2(b.w,c.w);
  protectedMembers[i]=dot(a.xyz,normalize(gbufferModelView[1].xyz))>.99||shaderlod_reimagined_rate_weight(length(c.xyz))<1.0;
  if(any(notEqual(a,a0))||b.w!=b0.w||c.w!=c0.w||d.x!=d0.x)return false;
 }
 // No claim that the native positional shadow field is constant across a tile.
 return shaderlod_surface_tile_eligible(true,positions,normals,materials,lightmaps,protectedMembers,max(256.0,shaderlodNear),false,vec2(0.0),shaderlod_surface_default_limits());
 #else
 return false;
 #endif
}
