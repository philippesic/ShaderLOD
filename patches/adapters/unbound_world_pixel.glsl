#include "/lib/shaderlod/adapter.glsl"
// Native SOLID RGB + material + normal coarse prepass; native full geometry/depth consumer.
#define SHADERLOD_UNBOUND_PIXEL_GRAPH 0 // [0 1]
#if SHADERLOD_UNBOUND_PIXEL_GRAPH == 1 && SHADERLOD_REFINEMENT_ADAPTER == 1 && defined OVERWORLD && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && RP_MODE == 1 && defined TAA && !defined GENERATED_NORMALS && !defined COATED_TEXTURES && !defined CUSTOM_PBR && !defined DISTANT_HORIZONS && !defined VOXY && COLORED_LIGHTING == 0 && COLORED_LIGHTING_INTERNAL == 0 && RAIN_PUDDLES == 0 && ANISOTROPIC_FILTER == 0 && !defined PIXELATED_SHADOWS && !defined PIXELATED_BLOCKLIGHT && !defined PIXELATED_AO && !defined SNOWY_WORLD && !defined COLOR_CODED_PROGRAMS && SHOW_LIGHT_LEVEL == 0
#define SHADERLOD_UNBOUND_WORLD_PIXEL_SOURCE 1
uniform int shaderlodUnboundWorldPixelPhase;
uniform vec2 shaderlodUnboundWorldPixelSize;
uniform vec2 shaderlodUnboundPixelSize;
uniform sampler2D shaderlodUnboundWorldPixel0,shaderlodUnboundWorldPixel1,shaderlodUnboundWorldPixel2,shaderlodUnboundWorldPixel3,shaderlodUnboundWorldPixel4,shaderlodUnboundWorldPixel5;
vec2 shaderlodUnboundWorldNativeCoord(){return shaderlodUnboundWorldPixelPhase==4?gl_FragCoord.xy*shaderlodUnboundWorldPixelSize/shaderlodUnboundPixelSize:gl_FragCoord.xy;}
#define shaderlodUnboundWorldCoord() vec4(shaderlodUnboundWorldNativeCoord(),gl_FragCoord.zw)
#include "/lib/shaderlod/adapter.glsl"
#include "/lib/shaderlod/unbound_spatial_continuity.glsl"
bool shaderlodUnboundWorldFar(vec3 p){return shaderlod_optimization_enabled(shaderlodOptPixelScale)&&isEyeInWater==0&&shaderlodNear>=1.0&&shaderlodFar>shaderlodNear&&shaderlodFar<=4096.0&&shaderlodTransition>=0.0&&shaderlodTransition<=shaderlodFar-shaderlodNear&&shaderlod_surface_finite(p)&&length(p)>max(256.0,shaderlodFar+0.5*shaderlodTransition);}
bool shaderlodUnboundWorldConsume(vec4 raw,vec3 position,out vec4 c0,out vec4 c1,out vec4 c2){
 c0=c1=c2=vec4(0.0);
 if(shaderlodUnboundWorldPixelPhase!=5||mat!=10128||!all(equal(glColor.rgb,vec3(1.0)))||!shaderlodUnboundWorldFar(position)||!all(equal(shaderlodUnboundWorldPixelSize,vec2(viewWidth,viewHeight)))||any(lessThan(shaderlodUnboundPixelSize,vec2(2.0))))return false;
 vec2 p=gl_FragCoord.xy/shaderlodUnboundWorldPixelSize*shaderlodUnboundPixelSize-0.5,base=floor(p),part=fract(p);
 vec3 positions[4],normals[4];vec2 lm[4];int materials[4];bool protect[4];ivec2 cells[4];vec4 normal0=vec4(0.0),material0=vec4(0.0);vec3 firstColor=vec3(0.0);
 for(int i=0;i<4;++i){cells[i]=ivec2(base)+ivec2(i%2,i/2);if(any(lessThan(cells[i],ivec2(0)))||any(greaterThanEqual(cells[i],ivec2(shaderlodUnboundPixelSize))))return false;
  vec4 pos=texelFetch(shaderlodUnboundWorldPixel3,cells[i],0),data=texelFetch(shaderlodUnboundWorldPixel4,cells[i],0),n=texelFetch(shaderlodUnboundWorldPixel2,cells[i],0),material=texelFetch(shaderlodUnboundWorldPixel1,cells[i],0),color=texelFetch(shaderlodUnboundWorldPixel0,cells[i],0);
  if(!all(equal(raw,texelFetch(shaderlodUnboundWorldPixel5,cells[i],0)))||!all(equal(data.xyz,vec3(lmCoord,glColor.a)))||!shaderlod_surface_finite(n.xyz)||!shaderlod_surface_finite(material.xyz)||!shaderlod_surface_finite(color.rgb))return false;
  if(i==0){normal0=n;material0=material;firstColor=color.rgb;}if(!all(equal(n,normal0))||!all(equal(material.xy,material0.xy))||any(greaterThan(abs(color.rgb-firstColor),vec3(0.025)+abs(firstColor)*0.05)))return false;
  positions[i]=pos.xyz;normals[i]=normalize(mat3(gbufferModelView)*n.xyz);lm[i]=data.xy;materials[i]=int(pos.w);protect[i]=pos.w!=10128.0||!(data.w>=0.56&&data.w<1.0)||!shaderlodUnboundWorldFar(pos.xyz)||dot(normal,normals[i])<0.995;
 }
 if(!shaderlod_surface_tile_eligible(true,positions,normals,materials,lm,protect,256.0,false,vec2(0.0),shaderlod_surface_default_limits()))return false;
 float tolerance=min(0.08,0.015+0.015*min(length(positions[1]-positions[0]),length(positions[2]-positions[0])));if(abs(dot(position-positions[0],normals[0]))>tolerance)return false;
 for(int i=0;i<4;++i){vec2 o=vec2(i%2,i/2);float weight=mix(1.0-part.x,part.x,o.x)*mix(1.0-part.y,part.y,o.y);c0+=texelFetch(shaderlodUnboundWorldPixel0,cells[i],0)*weight;c1+=texelFetch(shaderlodUnboundWorldPixel1,cells[i],0)*weight;}
 c0.a=raw.a;c2=normal0;return true;
}
#else
#define shaderlodUnboundWorldCoord() gl_FragCoord
#endif
