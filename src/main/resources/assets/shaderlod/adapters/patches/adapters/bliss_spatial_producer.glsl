#include "/lib/shaderlod/bliss_spatial_native.glsl"
#include "/lib/shaderlod/bliss_spatial_graph.glsl"
/* RENDERTARGETS:12 */
void main(){
    gl_FragData[0]=vec4(0.0,0.0,0.0,-1.0);
    ivec2 pixel=ivec2(gl_FragCoord.xy);int mode=shaderlodBlissSpatialMode();
    ivec2 origin=mode==2?ivec2((pixel.x/2)*2,pixel.y*2):(pixel/2)*2;
    if(mode==1 && pixel.y!=origin.y)return;
    if(!shaderlodBlissSpatialTile(origin))return;
    // Both atlas cells use identical native representative coordinates/noise/jitter.
    shaderlodBlissSpatialFragCoord=vec4(vec2(origin)+0.5,gl_FragCoord.zw);
    vec3 light,reflection;shaderlodBlissSpatialEvaluate(light,reflection);
    if(any(isnan(light)) || any(isinf(light)) || any(isnan(reflection)) || any(isinf(reflection)) ||
        any(greaterThan(abs(light),vec3(1e10))) || any(greaterThan(abs(reflection),vec3(1e10))))return;
    int tileWidth=int(viewWidth)/2;ivec2 tile=origin/2;
    // Deliberate duplicate native evaluation:2/eligible4 receivers, full32bit separate RGB fields.
    gl_FragData[0]=pixel.x==origin.x?vec4(light,float(frameCounter)):vec4(reflection,float(tile.x+tileWidth*tile.y));
}
