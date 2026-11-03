#include "/lib/shaderlod/bliss_spatial_graph.glsl"
bool shaderlodBlissSpatialResolve(out vec3 light,out vec3 reflection) {
    ivec2 pixel=ivec2(gl_FragCoord.xy),origin=(pixel/2)*2;
    if(!shaderlodBlissSpatialTile(origin))return false;
    ivec2 address=shaderlodBlissSpatialMode()==2?ivec2(origin.x,origin.y/2):origin;
    vec4 quantity=texelFetch2D(colortex12,address,0),direct=texelFetch2D(colortex12,address+ivec2(1,0),0);
    ivec2 tile=origin/2;int index=tile.x+(int(viewWidth)/2)*tile.y;
    if(!(quantity.a==float(frameCounter) && direct.a==float(index)) ||
        any(isnan(quantity.rgb)) || any(isinf(quantity.rgb)) || any(isnan(direct.rgb)) || any(isinf(direct.rgb)) ||
        any(greaterThan(abs(quantity.rgb),vec3(1e10))) || any(greaterThan(abs(direct.rgb),vec3(1e10))))return false;
    light=quantity.rgb;reflection=direct.rgb;return true;
}
