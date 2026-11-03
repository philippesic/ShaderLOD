#include "/lib/shaderlod/unbound_spatial_lighting.glsl"
/* RENDERTARGETS:11 */
/*
const int colortex11Format = RGBA8;
*/
const bool colortex11Clear=true;
const vec4 colortex11ClearColor=vec4(0.0,0.0,0.0,0.0);
void main(){
 gl_FragData[0]=vec4(0.0,0.0,0.0,-1.0);gl_FragData[1]=vec4(0.0,0.0,0.0,-1.0);
 ivec2 p=ivec2(gl_FragCoord.xy);ivec2 source=shaderlodUnboundSpatialMode==2?p*2:p;
 if(shaderlodUnboundSpatialMode==1&&any(notEqual(p,(p/2)*2)))return;
 if(shaderlodUnboundSpatialMode!=3&&!shaderlodUnboundSpatialTile(source))return;
 vec3 d,h;if(!shaderlodUnboundSpatialEvaluate(source,d,h))return;
 if(any(isnan(d))||any(isinf(d))||any(isnan(h))||any(isinf(h))||any(greaterThanEqual(abs(d),vec3(1e10)))||any(greaterThanEqual(abs(h),vec3(1e10))))return;
 gl_FragData[0]=vec4(d,float(frameCounter));gl_FragData[1]=vec4(h,float(source.x+int(viewWidth)*source.y));
}
