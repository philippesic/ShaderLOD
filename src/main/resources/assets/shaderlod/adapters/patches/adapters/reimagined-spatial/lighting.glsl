#define GBUFFERS_TERRAIN
#include "/lib/common.glsl"
#include "/lib/shaderlod/adapter.glsl"
#include "/lib/shaderlod/reimagined_spatial_inputs.glsl"
vec3 upVec=normalize(gbufferModelView[1].xyz),eastVec=normalize(gbufferModelView[0].xyz),northVec=normalize(gbufferModelView[2].xyz);
vec3 sunVec=GetSunVector();
vec3 normal=vec3(0.0);
vec2 signMidCoordPos=vec2(0.0); // Native read guarded by subsurfaceMode2, excluded here.
float NdotU=0.0,geoNdotU=0.0,NdotUmax0=0.0;
float SdotU=dot(sunVec,upVec);
float sunFactor=SdotU<0.0?clamp(SdotU+.375,0.0,.75)/.75:clamp(SdotU+.03125,0.0,.0625)/.0625;
float sunVisibility=clamp(SdotU+.0625,0.0,.125)/.125,sunVisibility2=sunVisibility*sunVisibility;
float shadowTimeVar1=abs(sunVisibility-.5)*2.0,shadowTimeVar2=shadowTimeVar1*shadowTimeVar1,shadowTime=shadowTimeVar2*shadowTimeVar2;
vec3 lightVec=sunVec*((timeAngle<.5325||timeAngle>.9675)?1.0:-1.0);
vec4 glColor=vec4(1.0),shaderlodReimaginedSpatialFragCoord=vec4(0.0);
#include "/lib/util/spaceConversion.glsl"
#include "/lib/shaderlod/reimagined_spatial_native.glsl"
#include "/lib/util/dither.glsl"
bool shaderlodReimaginedSpatialEvaluate(ivec2 p,out vec3 diffuse,out vec3 highlight){
 vec4 a,b,c,d,payload;if(!shaderlodReimaginedSpatialInput(p,a,b,c,d)||!shaderlodReimaginedSpatialPayload(p,payload))return false;
 normal=a.xyz;NdotU=dot(a.xyz,upVec);geoNdotU=NdotU;NdotUmax0=max(NdotU,0.0);glColor=vec4(1.0,1.0,1.0,a.w);
 shaderlodReimaginedSpatialFragCoord=vec4(vec2(p)+.5,d.z,1.0);
 float dither=Bayer64(shaderlodReimaginedSpatialFragCoord.xy);
 #ifdef TAA
 dither=fract(dither+goldenRatio*mod(float(frameCounter),3600.0));
 #endif
 vec4 color=vec4(1.0);vec3 shadowMult=vec3(1.0);
 shaderlodReimaginedSpatialNative(color,shadowMult,b.xyz,c.xyz,length(c.xyz),a.xyz,a.xyz,dither,normalize(ViewToPlayer(a.xyz*10000.0)),vec2(b.w,c.w),false,false,false,false,0,payload.a,d.x,0.0,diffuse,highlight);
 return true; // Native fallback retains even native nonfinite output/conversion semantics.
}
