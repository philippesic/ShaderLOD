#ifndef SHADERLOD_EFFECT_ADAPTER_INCLUDED
#define SHADERLOD_EFFECT_ADAPTER_INCLUDED
// Private local binding. Original authors retain all upstream attribution.
#define SHADERLOD_EFFECT_ADAPTER 0 // [0 1]
#if SHADERLOD_EFFECT_ADAPTER == 1
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodPreset = -1;
#include "/lib/shaderlod/uniform_sample_budget.glsl"
int shaderlod_effect_budget(int original, int minimum) {
    return shaderlod_uniform_sample_budget(shaderlodBridgeVersion, shaderlodEnabled,
        shaderlodPreset, original, minimum);
}
#endif
#endif
