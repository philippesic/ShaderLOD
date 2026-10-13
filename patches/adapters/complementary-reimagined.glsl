#ifndef SHADERLOD_REFINEMENT_ADAPTER_INCLUDED
#define SHADERLOD_REFINEMENT_ADAPTER_INCLUDED
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
int shaderlod_complementary_volume_budget(int original, int minimum) {
    return shaderlod_uniform_sample_budget(shaderlodBridgeVersion, shaderlodEnabled,
        shaderlodPreset, original, minimum);
}
#endif
#endif
