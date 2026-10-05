/* MakeUp ShaderLOD core bridge probe, modified 2026-10-05.
 * Experimental derivative of MakeUp Ultra Fast 9.5g by Javier Garduño.
 * LGPL v3; original source/license/official URLs remain in the base archive.
 * shaderlod-bridge-probe.json and SHADERLOD_CORE_BRIDGE_PROBE.md describe this
 * isolated source experiment; no verified runtime connection or speedup claim.
 */
#ifndef SHADERLOD_MAKEUP_CORE_BRIDGE_PROBE_INCLUDED
#define SHADERLOD_MAKEUP_CORE_BRIDGE_PROBE_INCLUDED

// Consume only these existing protocol 2 core fields, and only after opt-in.
// Absent-mod uniforms start at zero; exactly version 2 is accepted here.
#if SHADERLOD_MAKEUP_CORE_BRIDGE == 1
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform float shaderlodNear;
uniform float shaderlodFar;
#endif

bool shaderlodMakeupCoreKnown() {
#if SHADERLOD_MAKEUP_CORE_BRIDGE == 1
    return shaderlodBridgeVersion == 2;
#else
    return false;
#endif
}

bool shaderlodMakeupCoreEnabled() {
#if SHADERLOD_MAKEUP_CORE_BRIDGE == 1
    if (shaderlodMakeupCoreKnown()) return shaderlodEnabled != 0;
#endif
    // Old/unknown/absent bridge falls back to the original pack switch.
#if SHADERLOD_MAKEUP_WATER_SSR == 1
    return true;
#else
    return false;
#endif
}

vec2 shaderlodMakeupCoreBands() {
    vec2 bands = vec2(SHADERLOD_MAKEUP_SSR_START, SHADERLOD_MAKEUP_SSR_END);
#if SHADERLOD_MAKEUP_CORE_BRIDGE == 1
    if (shaderlodMakeupCoreKnown()) bands = vec2(shaderlodNear, shaderlodFar);
#endif
    bands.x = max(bands.x, 0.0);
    bands.y = max(bands.y, bands.x + 1.0);
    return bands;
}

float shaderlodMakeupCoreSSRWeight(vec3 fragpos) {
    // Originating camera/view-relative radial distance in Minecraft blocks.
    vec2 bands = shaderlodMakeupCoreBands();
    float distanceSquared = dot(fragpos, fragpos);
    if (distanceSquared >= bands.y * bands.y) return 0.0;
    if (distanceSquared <= bands.x * bands.x) return 1.0;
    return 1.0 - smoothstep(bands.x, bands.y, sqrt(distanceSquared));
}

vec3 shaderlodMakeupCoreCue(vec3 fragpos) {
#if SHADERLOD_MAKEUP_CORE_BRIDGE == 1
    if (!shaderlodMakeupCoreKnown()) return vec3(1.0, 0.0, 1.0);
    if (shaderlodEnabled == 0) return vec3(1.0, 1.0, 0.0);
    float weight = shaderlodMakeupCoreSSRWeight(fragpos);
    return mix(vec3(0.0, 0.15, 1.0), vec3(0.0, 1.0, 0.0), weight);
#else
    return vec3(0.5);
#endif
}

#endif
