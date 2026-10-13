#ifndef SHADERLOD_BSL_FIELD_CONTROL_INCLUDED
#define SHADERLOD_BSL_FIELD_CONTROL_INCLUDED
#include "/lib/shaderlod/adapter.glsl"
uniform int shaderlodBslShadowFieldQualified;
bool shaderlodBslShadowFieldEnabled() {
    return (shaderlod_optimization_enabled(shaderlodOptShadowMinField) || shaderlod_optimization_enabled(shaderlodOptShadowRawQuery)) && shaderlodBslShadowFieldQualified == 1;
}
#endif
