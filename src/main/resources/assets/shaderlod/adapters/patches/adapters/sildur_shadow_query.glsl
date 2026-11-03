#ifndef SHADERLOD_SILDUR_SHADOW_QUERY_INCLUDED
#define SHADERLOD_SILDUR_SHADOW_QUERY_INCLUDED
// Adapted BSL minimum-field/direct raw-query mechanism; private local source port.
// Raw/HW sampler state, arithmetic and complete epoch are supplied/qualified by parent; missing permissions retain native.
uniform sampler2D shadowtex0;
uniform sampler2D shadowtex1;
uniform sampler2D shadowcolor2;
bool shaderlod_sildur_shadow_source() {
    return shaderlodSildurShadowSamplerVersion == 1 && shaderlodSildurShadowSamplerQualified == 1 &&
        frameCounter >= 0 && frameCounter < 16777216 &&
        all(equal(textureSize(shadowtex0,0),ivec2(3072))) && all(equal(textureSize(shadowtex1,0),ivec2(3072)));
}
bool shaderlod_sildur_field_lit(vec3 p,float radius,float bias,out vec2 result) {
    result=vec2(0.0);
    if (!shaderlod_optimization_enabled(shaderlodOptShadowMinField) ||
        shaderlodSildurShadowFieldQualified != 1 || shaderlodSildurShadowFieldFrame != frameCounter ||
        !shaderlod_sildur_shadow_source() ||
        !(radius >= 0.0 && radius <= 0.01 && bias >= 0.0 && bias < 0.1 && p.z >= 0.0 && p.z <= 1.0) ||
        !all(greaterThan(p.xy,vec2(radius+2.0/3072.0))) ||
        !all(lessThan(p.xy,vec2(1.0-radius-2.0/3072.0)))) return false;
    // Include full hardware linear-PCF support and strict source quantization margin.
    ivec2 lo=ivec2(floor((p.xy-vec2(radius+2.0/3072.0))*128.0));
    ivec2 hi=ivec2(floor((p.xy+vec2(radius+2.0/3072.0))*128.0));
    if (any(lessThan(lo,ivec2(0))) || any(greaterThanEqual(hi,ivec2(128))) || any(greaterThan(hi-lo,ivec2(1)))) return false;
    vec4 a=texelFetch(shadowcolor2,lo,0),b=texelFetch(shadowcolor2,ivec2(hi.x,lo.y),0);
    vec4 c=texelFetch(shadowcolor2,ivec2(lo.x,hi.y),0),d=texelFetch(shadowcolor2,hi,0);
    if (a.z!=float(frameCounter) || b.z!=float(frameCounter) || c.z!=float(frameCounter) || d.z!=float(frameCounter) ||
        a.w!=1.0 || b.w!=1.0 || c.w!=1.0 || d.w!=1.0) return false;
    vec2 minimumDepth=min(min(a.xy,b.xy),min(c.xy,d.xy));
    // Native w=1+(i+noise)*radius/N*resolution >=1 for all admitted samples; refDepth<=p.z-bias.
    if (!all(greaterThan(minimumDepth,vec2(p.z-bias+0.0001))) || !all(lessThanEqual(minimumDepth,vec2(1.0)))) return false;
    result=vec2(1.0);return true;
}
bool shaderlod_sildur_raw_query(vec3 p,float radius,float bias,float noise,int count,
    bool temporal,int phase,float footprintWeight,out vec2 result) {
    result=vec2(0.0);
    if (!shaderlod_optimization_enabled(shaderlodOptShadowRawQuery) ||
        shaderlodSildurShadowArithmeticQualified != 1 || !shaderlod_sildur_shadow_source() ||
        !(shaderlodSildurShadowWeightSteps >=256.0 && shaderlodSildurShadowWeightSteps <=65536.0) ||
        !(radius >=0.0 && radius <=0.2/3072.0 && bias >=0.0 && bias <0.1 && noise >=0.0 && noise <1.0) ||
        count <1 || count >30 || !(p.z>=0.0 && p.z<=1.0) ||
        !all(greaterThan(p.xy,vec2(2.0/3072.0))) || !all(lessThan(p.xy,vec2(1.0-2.0/3072.0)))) return false;
    ivec2 base=ivec2(floor(p.xy*3072.0-0.5));
    // All tap PCF supports must share this exact2x2 texel footprint, before any reuse.
    vec2 frac=p.xy*3072.0-0.5-vec2(base);
    if (!all(greaterThan(frac,vec2(radius*3072.0+0.0625))) ||
        !all(lessThan(frac,vec2(1.0-radius*3072.0-0.0625)))) return false;
    vec4 z0=vec4(texelFetch(shadowtex0,base,0).x,texelFetch(shadowtex0,base+ivec2(1,0),0).x,
        texelFetch(shadowtex0,base+ivec2(0,1),0).x,texelFetch(shadowtex0,base+ivec2(1,1),0).x);
    vec4 z1=vec4(texelFetch(shadowtex1,base,0).x,texelFetch(shadowtex1,base+ivec2(1,0),0).x,
        texelFetch(shadowtex1,base+ivec2(0,1),0).x,texelFetch(shadowtex1,base+ivec2(1,1),0).x);
    if (!all(greaterThanEqual(z0,vec4(0.0))) || !all(lessThanEqual(z0,vec4(1.0))) ||
        !all(greaterThanEqual(z1,vec4(0.0))) || !all(lessThanEqual(z1,vec4(1.0)))) return false;
    int visits=temporal?4:count;
    for(int visit=0;visit<visits;++visit) {
        int i=temporal?phase+visit*3:visit;
        bool omit=footprintWeight>0.0 && i>=6 && i<9;
        if(omit && footprintWeight>=1.0) continue;
        float mass=footprintWeight>0.0?(omit?1.0-footprintWeight:(i>=3?1.0+0.5*footprintWeight:1.0)):1.0;
        float reciprocalCount=1.0/float(count);
        float alpha=(float(i)+noise)*reciprocalCount;
        float angle=(noise+alpha*4.0)*6.2831853;
        vec2 offset=vec2(cos(angle),sin(angle))*sqrt(alpha);
        float weight=1.0+(float(i)+noise)*radius*reciprocalCount*3072.0;
        vec2 f=(p.xy+radius*offset)*3072.0-0.5-vec2(base);
        f=floor(f*shaderlodSildurShadowWeightSteps+0.5)/shaderlodSildurShadowWeightSteps;
        float reference=p.z-bias*weight;
        vec4 c0=step(vec4(reference),z0),c1=step(vec4(reference),z1);
        result+=vec2(mix(mix(c0.x,c0.y,f.x),mix(c0.z,c0.w,f.x),f.y),
                     mix(mix(c1.x,c1.y,f.x),mix(c1.z,c1.w,f.x),f.y))*mass;
    }
    result*=temporal?0.25:1.0/float(count);
    return true;
}
#endif
