// Private current-depth reduction; no upstream producer is replaced.
#include "/lib/settings.glsl"
#if SHADERLOD_BSL_SHADOW_FIELD == 1
#include "/lib/shaderlod/bsl_shadow_field_control_r1.glsl"

#ifdef VSH
void main() {
    // Iris single fullscreen quad, viewport (0,0,128,128) at fixed 2048.
    // Clip every vertex when inactive: no late fragment branch hiding 4M reads.
    gl_Position = shaderlodBslShadowFieldEnabled() ? ftransform() : vec4(2.0, 2.0, 0.0, 1.0);
}
#endif

#ifdef FSH
// SEPARATE_HARDWARE_SAMPLERS makes this actual noncomparison depth0.
uniform sampler2D shadowtex0;
uniform int frameCounter;
const int shadowcolor2Format = R32F;
// Full useful region+stamp overwritten each eligible CURRENT pass. Never read
// unused full-sized storage. Missing/inactive producer cannot pass stamp/control.
// Initial/resizing full clear still belongs to Iris; no steady per-frame clear.
const bool shadowcolor2Clear = false;
const vec4 shadowcolor2ClearColor = vec4(0.0);
const bool shadowcolor2Mipmap = false;
const bool shadowcolor2Nearest = true;

void main() {
    ivec2 cell = ivec2(gl_FragCoord.xy);
    float value = 0.0; // Unknown/invalid contents never certify all-lit.
    if (shaderlodBslShadowFieldEnabled() && shadowMapResolution == 2048 &&
        textureSize(shadowtex0, 0) == ivec2(2048) &&
        all(greaterThanEqual(cell, ivec2(0))) && all(lessThan(cell, ivec2(128))) &&
        frameCounter >= 0 && frameCounter < 16777215) {
        if (cell == ivec2(127)) {
            // Reserve one scalar cell for exact current-frame stamp.
            // This original depth tile is always a native receiver fallback.
            value = -float(frameCounter + 1);
        } else {
            value = 1.0;
            ivec2 base = cell * 16;
            for (int y = 0; y < 16; ++y) {
                for (int x = 0; x < 16; ++x) {
                    float depth = texelFetch(shadowtex0, base + ivec2(x, y), 0).r;
                    // Native normalized depth, no linearization or world scaling.
                    value = (depth >= 0.0 && depth <= 1.0) ? min(value, depth) : 0.0;
                }
            }
        }
    }
    /* DRAWBUFFERS:2 */
    gl_FragData[0] = vec4(value, 0.0, 0.0, 1.0);
}
#endif
#else
#ifdef VSH
void main() { gl_Position = vec4(2.0, 2.0, 0.0, 1.0); }
#endif
#ifdef FSH
void main() { gl_FragData[0] = vec4(0.0); }
#endif
#endif
