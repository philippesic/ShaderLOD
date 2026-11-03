#if !defined SHADERLOD_PHOTON_SHADOW_QUERY_INCLUDED
#define SHADERLOD_PHOTON_SHADOW_QUERY_INCLUDED
#include "/include/shaderlod/photon_shadow_field_control.glsl"
uniform sampler2D shadowcolor2;
#undef shadowtex1
uniform sampler2D shadowtex1;
struct ShaderlodPhotonShadowSupport {
    ivec2 base[12]; vec2 fraction[12]; ivec2 first; ivec2 last;
};
bool shaderlod_photon_shadow_support(vec3 clip, float filter_radius, int count,
    float dither, float sss, out ShaderlodPhotonShadowSupport support) {
    if (!shaderlod_photon_shadow_field_enabled() || sss != 0.0 ||
        count < 4 || count > 12 || shadowMapResolution != 2048 ||
        textureSize(shadowtex1,0) != ivec2(2048) || textureSize(shadowcolor2,0) != ivec2(2048) ||
        frameCounter < 0 || frameCounter >= 16777215 ||
        !(clip.z>=0.0 && clip.z<=1.0) || !(filter_radius>0.0 && filter_radius<1.0) ||
        !(dither>=0.0 && dither<=1.0)) return false;
    ivec2 lower=ivec2(2047), upper=ivec2(0);
    for(int i=0;i<count;++i) {
        vec2 offset=vogel_disc_sample(i,count,dither*tau)*filter_radius;
        vec2 uv=clip.xy+offset;
        uv/=get_distortion_factor(uv); uv=uv*0.5+0.5;
        vec2 p=uv*2048.0-0.5;
        if (!all(greaterThanEqual(p,vec2(1.0))) || !all(lessThan(p,vec2(2046.0)))) return false;
        vec2 whole=floor(p); support.base[i]=ivec2(whole);support.fraction[i]=p-whole;
        lower=min(lower,ivec2(floor(p-1.0)));upper=max(upper,ivec2(floor(p+1.0))+ivec2(1));
    }
    support.first=lower/16;support.last=upper/16;
    if(any(greaterThan(support.last-support.first,ivec2(1))) || support.last==ivec2(127))return false;
    return texelFetch(shadowcolor2,ivec2(127),0).r == -float(frameCounter+1);
}
bool shaderlod_photon_shadow_all_lit(ShaderlodPhotonShadowSupport s,float reference) {
    vec4 m=vec4(texelFetch(shadowcolor2,s.first,0).r,
        texelFetch(shadowcolor2,ivec2(s.last.x,s.first.y),0).r,
        texelFetch(shadowcolor2,ivec2(s.first.x,s.last.y),0).r,
        texelFetch(shadowcolor2,s.last,0).r);
    return all(greaterThan(m,vec4(0.0))) && all(lessThanEqual(m,vec4(1.0))) &&
        all(greaterThan(m,vec4(reference+1.0/65536.0)));
}
float shaderlod_photon_raw_compare(ivec2 b,vec2 f,float reference) {
    vec4 d=vec4(texelFetch(shadowtex1,b,0).r,texelFetch(shadowtex1,b+ivec2(1,0),0).r,
        texelFetch(shadowtex1,b+ivec2(0,1),0).r,texelFetch(shadowtex1,b+ivec2(1,1),0).r);
    vec4 c=vec4(reference<=d.x?1.0:0.0,reference<=d.y?1.0:0.0,
        reference<=d.z?1.0:0.0,reference<=d.w?1.0:0.0);
    if(c==vec4(1.0))return 1.0;if(c==vec4(0.0))return 0.0;
    return mix(mix(c.x,c.y,f.x),mix(c.z,c.w,f.x),f.y);
}
#define shadowtex1 shadowtex1HW
#endif
