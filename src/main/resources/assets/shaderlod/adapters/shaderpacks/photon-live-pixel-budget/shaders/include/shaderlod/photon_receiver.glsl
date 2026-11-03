#if !defined SHADERLOD_PHOTON_RECEIVER_INCLUDED
#define SHADERLOD_PHOTON_RECEIVER_INCLUDED
#include "/include/shaderlod/receiver_footprint_budget_r1.glsl"
uniform int shaderlodPhotonReceiverQualified;
vec2 shaderlod_receiver_dx=vec2(0.0),shaderlod_receiver_dy=vec2(0.0);
vec3 shaderlod_receiver_clip=vec3(0.0);
bool shaderlod_receiver_eligible=false;
float shaderlod_photon_receiver_weight(vec3 clip,float radius,int count,float dither,float sss) {
    if(!shaderlod_optimization_enabled(shaderlodOptShadowReceiverFootprint) ||
        shaderlodPhotonReceiverQualified!=1 || !shaderlod_receiver_eligible || sss!=0.0 ||
        !(radius>0.0 && radius<1.0) || count<6 || count>12 ||
        !all(lessThanEqual(abs(clip-shaderlod_receiver_clip),vec3(1e-6))))return 0.0;
    vec2 center=clip.xy/get_distortion_factor(clip.xy)*0.5+0.5;
    vec2 extent=vec2(0.0);
    for(int i=0;i<count;++i) {
        vec2 uv=clip.xy+vogel_disc_sample(i,count,dither*tau)*radius;
        uv/=get_distortion_factor(uv);uv=uv*0.5+0.5;
        extent=max(extent,abs(uv-center));
    }
    extent+=vec2(shadow_map_pixel_size);
    return shaderlod_receiver_reduction_weight_r1(true,shaderlod_receiver_dx,
        shaderlod_receiver_dy,extent,1.0/taau_render_scale);
}
#endif
