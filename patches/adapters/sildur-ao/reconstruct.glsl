uniform sampler2D colortex10;
#include "/lib/shaderlod/sildur_ao_graph.glsl"
float shaderlodSildurReconstructAo(float noise,vec3 fragpos0,vec3 normal,float depth0) {
 #if defined IS_IRIS && defined MC_GLSL_VERSION && MC_GLSL_VERSION >= 330 && !defined DISTANT_HORIZONS && !defined VOXY
 vec2 fullSize=vec2(viewWidth,viewHeight);
 float blocks=-fragpos0.z;
 if (!shaderlodSildurAoGraphActive(fullSize) || !(depth0>=0.0 && depth0<1.0) || isEyeInWater>=0.9 ||
  !(blocks>shaderlodNear+max(0.05,shaderlodNear*0.001) && blocks<1e6))
  return shaderlodSildurNativeAo(noise,fragpos0,normal,depth0);
 vec2 halfSize=floor(fullSize*0.5),halfPos=(texcoord*fullSize-vec2(1.5))*0.5;
 vec2 base=floor(halfPos),fraction=fract(halfPos);
 float material=texture2DLod(colortex1,texcoord,0.0).b;
 float tolerance=max(0.25,blocks*0.01),sum=0.0,weightSum=0.0,confidence=1.0;
 for(int i=0;i<4;++i) {
  vec2 corner=vec2(float(i-(i/2)*2),float(i/2));
  vec2 cell=clamp(base+corner,vec2(0.0),halfSize-vec2(1.0));
  // Exact cell: no LINEAR mixture can forge frame/depth/material validity.
  vec4 stored=texelFetch(colortex10,ivec2(cell),0);
  vec2 representative=(cell*2.0+vec2(1.5))/fullSize;
  vec3 sampleNormal=utilDecode(texture2DLod(colortex2,representative,0.0).xy);
  float valid=0.0;
  if(stored.r>=0.0 && stored.r<=1.0 && stored.g>shaderlodNear && stored.g<1e6 &&
   stored.a==float(frameCounter) && stored.b==material && dot(sampleNormal,normal)>0.995)
   valid=1.0-smoothstep(0.25,1.0,abs(stored.g-blocks)/tolerance);
  float weight=mix(1.0-fraction.x,fraction.x,corner.x)*mix(1.0-fraction.y,fraction.y,corner.y);
  confidence=min(confidence,valid);
  // Zero confidence cannot recover; keep the post-loop native fallback unchanged.
  if (confidence == 0.0) break;
  if(valid>0.0){sum+=stored.r*weight*valid;weightSum+=weight*valid;}
 }
 if(!(confidence>0.0 && weightSum>0.0 && weightSum<=1.00001))return shaderlodSildurNativeAo(noise,fragpos0,normal,depth0);
 float halfAo=sum/weightSum;
 return confidence<1.0?mix(shaderlodSildurNativeAo(noise,fragpos0,normal,depth0),halfAo,confidence):halfAo;
 #else
 return shaderlodSildurNativeAo(noise,fragpos0,normal,depth0);
 #endif
}
