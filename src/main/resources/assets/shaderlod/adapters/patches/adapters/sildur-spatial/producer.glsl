#include "/lib/shaderlod/sildur_spatial_option.glsl"
#include "/lib/shaderlod/sildur_spatial_basis.glsl"
uniform int shaderlodSildurSpatialReady;
#include "/lib/shaderlod/sildur_spatial_graph.glsl"
/* RENDERTARGETS:12 */
/*
const int colortex12Format = RGBA32F;
const bool colortex12Clear = true;
const vec4 colortex12ClearColor = vec4(0.0,0.0,0.0,-1.0);
*/
void main(){
 gl_FragData[0]=vec4(0.0,0.0,0.0,-1.0);
 int mode=shaderlodSildurSpatialMode();if(mode==0)return;
 ivec2 pixel=ivec2(gl_FragCoord.xy),origin=mode==2?pixel*2:(pixel/2)*2;
 if(mode==1 && any(notEqual(pixel,origin)))return;
 if(!shaderlodSildurSpatialTile(origin))return;
 shaderlodSildurSpatialFragCoord=vec4(vec2(origin)+0.5,gl_FragCoord.zw);
 vec3 light=shaderlodSildurSpatialEvaluate((vec2(origin)+0.5)/vec2(viewWidth,viewHeight));
 if(all(lessThan(abs(light),vec3(1e10))))gl_FragData[0]=vec4(light,float(frameCounter));
}
