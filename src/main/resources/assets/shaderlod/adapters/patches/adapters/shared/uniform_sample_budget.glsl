#ifndef SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED
#define SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED
// Uniform quality budget: no distance units or per-fragment transition.
// Retain the complete integration interval and normalize with the returned count.
// Call before all samples; no derivatives, texture reads, jitter or late contribution fade.
// Global Off, QUALITY and missing/unknown core bridge retain selected upstream work.
int shaderlod_uniform_sample_budget(int bridge, int enabled, int preset, int original, int minimum) {
    if (bridge != 2 || enabled != 1 || preset < 0 || preset > 2 ||
        original < minimum || minimum < 1 || original > 65536) return original;
    float retained = preset == 0 ? 0.875 : 0.75;
    return max(minimum, min(original, int(ceil(float(original) * retained))));
}
#endif
