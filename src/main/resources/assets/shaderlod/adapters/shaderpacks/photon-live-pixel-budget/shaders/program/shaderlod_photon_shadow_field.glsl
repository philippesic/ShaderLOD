// Photon v1.3b native depth1; reduction mechanism reused from BSL fieldR1.
// Both upstream licenses/ancestry are retained by the originating recipes.
#include "/include/global.glsl"
#include "/include/shaderlod/photon_shadow_field_control.glsl"
#if defined vsh
void main() {
#if defined SHADERLOD_PHOTON_FIELD_ACTIVE
    gl_Position = shaderlod_photon_shadow_field_enabled() ? ftransform() : vec4(2.0,2.0,0.0,1.0);
#else
    gl_Position = vec4(2.0,2.0,0.0,1.0);
#endif
}
#endif
#if defined fsh
#undef shadowtex1
uniform sampler2D shadowtex1;
uniform int frameCounter;
const int shadowcolor2Format = R32F;
const bool shadowcolor2Clear = false;
const vec4 shadowcolor2ClearColor = vec4(0.0,0.0,0.0,0.0);
const bool shadowcolor2Mipmap = false;
const bool shadowcolor2Nearest = true;
layout(location=0) out vec4 shaderlodFieldOutput;
/* RENDERTARGETS: 2 */
void main() {
    ivec2 cell = ivec2(gl_FragCoord.xy);
    float value = 0.0;
#if defined SHADERLOD_PHOTON_FIELD_ACTIVE
    if (shaderlod_photon_shadow_field_enabled() && shadowMapResolution == 2048 &&
        textureSize(shadowtex1,0) == ivec2(2048) &&
        all(greaterThanEqual(cell,ivec2(0))) && all(lessThan(cell,ivec2(128))) &&
        frameCounter >= 0 && frameCounter < 16777215) {
        if (cell == ivec2(127)) value = -float(frameCounter+1);
        else {
            value = 1.0;
            ivec2 base = cell*16;
            for(int y=0;y<16;++y) for(int x=0;x<16;++x) {
                float d=texelFetch(shadowtex1,base+ivec2(x,y),0).r;
                value = d>=0.0 && d<=1.0 ? min(value,d) : 0.0;
            }
        }
    }
#endif
    shaderlodFieldOutput = vec4(value,0.0,0.0,1.0);
}
#endif
