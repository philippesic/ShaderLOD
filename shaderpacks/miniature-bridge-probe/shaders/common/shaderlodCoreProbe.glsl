/* Miniature ShaderLOD diagnostic consumer, modified 2026-10-05.
 * Miniature by Mateus A. Kreuch; MIT notice restored as LICENSE.Miniature.
 * This helper changes no SSR function, budget, sample or texture binding.
 * Included only with the separate compile-time cue option enabled.
 */
#ifndef SHADERLOD_MINIATURE_CORE_PROBE_INCLUDED
#define SHADERLOD_MINIATURE_CORE_PROBE_INCLUDED

#if SHADERLOD_MINIATURE_CORE_PROBE == 1
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform float shaderlodNear;
uniform float shaderlodFar;
#endif

vec3 shaderlodMiniatureCoreCue(vec3 viewPos) {
#if SHADERLOD_MINIATURE_CORE_PROBE == 1
    // Exact protocol 2 only. Absent/old/future versions ignore their distances.
    if (shaderlodBridgeVersion != 2) return vec3(1.0, 0.0, 1.0);
    if (shaderlodEnabled == 0) return vec3(1.0, 1.0, 0.0);
    float nearBlocks = max(shaderlodNear, 0.0);
    float farBlocks = max(shaderlodFar, nearBlocks + 1.0);
    // Originating reconstructed view-space radius, in Minecraft block units.
    float radialBlocks = length(viewPos);
    float bandWeight = 1.0 - smoothstep(nearBlocks, farBlocks, radialBlocks);
    return mix(vec3(0.0, 0.0, 1.0), vec3(0.0, 1.0, 0.0), bandWeight);
#else
    return vec3(0.5);
#endif
}

#endif
