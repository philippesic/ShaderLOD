// MakeUp9.5g native candle intensity curve; upstream LGPL-3.0-or-later retained.
// This stage-local bridge reuses the existing option/include names, adding no options.
#ifndef SHADERLOD_REFINEMENT_ADAPTER_INCLUDED
#define SHADERLOD_REFINEMENT_ADAPTER_INCLUDED
#define SHADERLOD_REFINEMENT_ADAPTER 0 // [0 1]
#if SHADERLOD_REFINEMENT_ADAPTER == 1
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodPreset = -1;
#include "/include/shaderlod/optimization_flags.glsl"
#endif
#endif
#if SHADERLOD_REFINEMENT_ADAPTER == 1
uniform int shaderlodOptZeroEmissionArithmetic;
bool shaderlod_makeup_zero_candle(float value) {
    return shaderlod_optimization_enabled(shaderlodOptZeroEmissionArithmetic) && value == 0.0;
}
#endif
