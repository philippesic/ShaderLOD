#ifndef SHADERLOD_REFINEMENT_ADAPTER_INCLUDED
#define SHADERLOD_REFINEMENT_ADAPTER_INCLUDED
// ShaderLOD private binding; upstream author credits remain in original members.
#define SHADERLOD_REFINEMENT_ADAPTER 0 // [0 1]
#if SHADERLOD_REFINEMENT_ADAPTER == 1
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodPreset = -1;
uniform float shaderlodNear;
uniform float shaderlodFar;
uniform float shaderlodTransition;
#include "/lib/shaderlod/refinement_budget.glsl"
#include "/lib/shaderlod/uniform_sample_budget.glsl"
// The sky aurora caller retains its full unit interval and native radiance weighting.
int shaderlod_bsl_volume_budget(int original) {
    return shaderlod_uniform_sample_budget(shaderlodBridgeVersion, shaderlodEnabled,
        shaderlodPreset, original, 4);
}
#endif
#endif
