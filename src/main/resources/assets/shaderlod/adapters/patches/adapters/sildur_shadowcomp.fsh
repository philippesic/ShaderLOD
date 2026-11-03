#version 330 compatibility
/* DRAWBUFFERS:2 */
#include "/lib/shaderlod/adapter.glsl"
const int shadowcolor2Format=RGBA32F;
const bool shadowcolor2Clear=true;
const vec4 shadowcolor2ClearColor=vec4(0.0,0.0,-1.0,0.0);
uniform sampler2D shadowtex0;
uniform sampler2D shadowtex1;
void main(){
    vec4 field=vec4(0.0,0.0,-1.0,0.0);
#if SHADERLOD_EFFECT_ADAPTER ==1 && SHADERLOD_SILDUR_SHADOW_QUERY_GRAPH ==1
    if(shaderlod_optimization_enabled(shaderlodOptShadowMinField) &&
        shaderlodSildurShadowSamplerVersion==1 && shaderlodSildurShadowSamplerQualified==1 &&
        frameCounter>=0 && frameCounter<16777216 &&
        all(equal(textureSize(shadowtex0,0),ivec2(3072))) && all(equal(textureSize(shadowtex1,0),ivec2(3072)))) {
        ivec2 cell=ivec2(gl_FragCoord.xy);
        if(all(greaterThanEqual(cell,ivec2(0))) && all(lessThan(cell,ivec2(128)))) {
            vec2 minDepth=vec2(1.0);
            for(int y=0;y<24;++y) for(int x=0;x<24;++x) {
                ivec2 p=cell*24+ivec2(x,y);
                vec2 z=vec2(texelFetch(shadowtex0,p,0).x,texelFetch(shadowtex1,p,0).x);
                if(!all(greaterThanEqual(z,vec2(0.0))) || !all(lessThanEqual(z,vec2(1.0)))) z=vec2(-1.0);
                minDepth=min(minDepth,z);
            }
            field=vec4(minDepth,float(frameCounter),1.0);
        }
    }
#endif
    gl_FragData[0]=field;
}
