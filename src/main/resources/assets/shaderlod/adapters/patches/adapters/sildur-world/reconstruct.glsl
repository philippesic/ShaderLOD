#include "/lib/shaderlod/sildur_world_common.glsl"
uniform sampler2D colortex14;
bool shaderlodSildurWorldResolve(out vec4 color){
 if(!shaderlodSildurWorldActive())return false;
 vec3 position,normal;vec4 albedo;vec2 lm;
 if(!shaderlodSildurWorldSurface(gl_FragCoord.xy,position,normal,albedo,lm))return false;
 vec2 low=shaderlodSildurWorldLow(),q=gl_FragCoord.xy/vec2(viewWidth,viewHeight)*low-0.5;
 ivec2 base=ivec2(floor(q));if(any(lessThan(base,ivec2(0)))||any(greaterThanEqual(base+ivec2(1),ivec2(low))))return false;
 vec2 fraction=fract(q);vec3 sum=vec3(0.0);float weightSum=0.0;
 for(int i=0;i<4;i++){
  ivec2 offset=ivec2(i%2,i/2),cell=base+offset;vec2 pixel=shaderlodSildurWorldPixel(cell);vec3 candidatePosition,candidateNormal;vec4 candidateAlbedo;vec2 candidateLm;
  if(!shaderlodSildurWorldSurface(pixel,candidatePosition,candidateNormal,candidateAlbedo,candidateLm))return false;
  // Exact albedo equality protects native texture/color/alpha detail. No tuned blur threshold.
  if(any(notEqual(albedo,candidateAlbedo)) || abs(candidatePosition.z-position.z)>max(0.25,-position.z*0.005) || dot(normal,candidateNormal)<0.999 || length(lm-candidateLm)>1.0/256.0)return false;
  vec4 stored=texelFetch(colortex14,cell,0);if(stored.a!=float(frameCounter)||any(lessThan(stored.rgb,vec3(0.0)))||any(greaterThanEqual(stored.rgb,vec3(1e10))))return false;
  vec2 w=mix(vec2(1.0)-fraction,fraction,vec2(offset));float weight=w.x*w.y;sum+=stored.rgb*weight;weightSum+=weight;
 }
 if(!(weightSum>0.9999&&weightSum<1.0001))return false;color=vec4(sum/weightSum,albedo.a);return true;
}
