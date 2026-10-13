#ifndef SHADERLOD_RENDER_BUDGET_INCLUDED
#define SHADERLOD_RENDER_BUDGET_INCLUDED

// Shared ShaderLOD policy; pack adapters supply their own verified bridge and
// raster/reconstruction contract. No uniforms, pack sampling, or extra passes.
// Scales are dimensionless side-length fractions, NOT distances in blocks.
// Preset ordinals 0/1/2 retain the version-2 contract; QUALITY appends id 3.
const int SHADERLOD_RENDER_BALANCED = 0;
const int SHADERLOD_RENDER_PERFORMANCE = 1;
const int SHADERLOD_RENDER_AGGRESSIVE = 2;
const int SHADERLOD_RENDER_QUALITY = 3;

float shaderlod_render_budget_scale(int bridge_version, int enabled,
    int preset, float stock_scale) {
    // Invalid pack input (including NaN) retains a full raster budget.
    if (!(stock_scale > 0.0 && stock_scale <= 1.0)) return 1.0;
    float fallback = stock_scale;
    // An absent custom-uniform bridge has version 0; retain the chosen pack scale.
    if (bridge_version != 2 || (enabled != 0 && enabled != 1)) return fallback;
    // Off and Quality preserve the user's chosen pack quality/render scale.
    if (enabled == 0) return fallback;
    if (preset == SHADERLOD_RENDER_BALANCED) return min(fallback, 0.875);
    if (preset == SHADERLOD_RENDER_PERFORMANCE) return min(fallback, 0.75);
    // Private experimental screen only: uninspected, no measured speed claim.
    // Default/UI remain Performance/Balanced/Quality until runtime quality QA.
    if (preset == SHADERLOD_RENDER_AGGRESSIVE) return min(fallback, 0.667);
    if (preset == SHADERLOD_RENDER_QUALITY) return fallback;
    return fallback;
}

// No gradual/live transition is implied. Consumers must reset/reload temporal
// resources when the effective budget changes until history transport is proven.
#endif // SHADERLOD_RENDER_BUDGET_INCLUDED
