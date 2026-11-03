#include "/lib/shaderlod/sildur_world_native.glsl"
#include "/lib/shaderlod/sildur_world_common.glsl"
/* RENDERTARGETS:14 */
/*
const int colortex14Format = RGBA32F;
const bool colortex14Clear = true;
const vec4 colortex14ClearColor = vec4(0.0,0.0,0.0,-1.0);
*/
void main(){
 gl_FragData[0]=vec4(0.0,0.0,0.0,-1.0);
 if(!shaderlodSildurWorldActive())return;
 ivec2 cell=ivec2(gl_FragCoord.xy);vec2 pixel=shaderlodSildurWorldPixel(cell);vec3 position,normal;vec4 albedo;vec2 lm;
 if(!shaderlodSildurWorldSurface(pixel,position,normal,albedo,lm))return;
 shaderlodSildurWorldFragCoord=vec4(pixel,gl_FragCoord.zw);
 vec4 color=shaderlodSildurWorldEvaluate(pixel/vec2(viewWidth,viewHeight));
 if(all(greaterThanEqual(color,vec4(0.0))) && all(lessThan(color,vec4(1e10))))gl_FragData[0]=vec4(color.rgb,float(frameCounter));
}
