#ifndef SHADERLOD_DEFERRED_METRIC_INCLUDED
#define SHADERLOD_DEFERRED_METRIC_INCLUDED
// ShaderLOD private local experiment; original pack author credits are retained.
// Exact scheduling change: defer an output-only metric until the last sampled hit.
// No distance approximation, distance threshold, transition band, or history change.
// Native view-space length is in blocks (AstraLex); Solas returns a unitless cosine.
#define SHADERLOD_DEFERRED_METRIC 0 // [0 1]
#if SHADERLOD_DEFERRED_METRIC == 1
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodPreset;
bool shaderlod_defer_metric() {
    // QUALITY=3, unknown transport/preset, disabled mod retain native work.
    return shaderlodBridgeVersion == 2 && shaderlodEnabled == 1 &&
        shaderlodPreset >= 0 && shaderlodPreset <= 2;
}
#endif
#endif
