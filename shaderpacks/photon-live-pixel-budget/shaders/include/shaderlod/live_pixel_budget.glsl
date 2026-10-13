#ifndef SHADERLOD_LIVE_PIXEL_BUDGET_INCLUDED
#define SHADERLOD_LIVE_PIXEL_BUDGET_INCLUDED
// ShaderLOD explicit render-budget adapter for Photon v1.3b (SixthSurge).
// Upstream TAAU reconstruction, lighting and sample counts remain unchanged.
// Dimensionless raster side-length scale; NOT distance LOD (no block units).
// Only consumed with private ON, TAA+TAAU and without Voxy's static JSON scale.
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodPreset;

#include "/include/shaderlod/render_budget.glsl"

float shaderlod_live_pixel_scale() {
    // Core/preset policy does not certify adapter capability/source registration.
    // Cloud history uses the prior scale's rectangle. Reload the pipeline for
    // enabled/preset changes; smooth interpolation would not repair that history.
    return shaderlod_render_budget_scale(shaderlodBridgeVersion,
        shaderlodEnabled, shaderlodPreset, TAAU_RENDER_SCALE);
}
#define taau_render_scale shaderlod_live_pixel_scale()
#endif
