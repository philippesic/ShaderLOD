#ifndef SHADERLOD_LIVE_PIXEL_HISTORY_INCLUDED
#define SHADERLOD_LIVE_PIXEL_HISTORY_INCLUDED
// ShaderLOD private temporal adapter for Photon v1.3b by SixthSurge.
// Dimensionless raster side scale; no distance units or distance bands.
// Private ON + TAA/TAAU + !VOXY only. No new pass or history encoding.
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodTemporalVersion;
uniform int shaderlodHistoryReset;

bool shaderlod_live_pixel_history_known() {
    return shaderlodBridgeVersion == 2 &&
        (shaderlodEnabled == 0 || shaderlodEnabled == 1) &&
        shaderlodTemporalVersion == 1 &&
        (shaderlodHistoryReset == 0 || shaderlodHistoryReset == 1);
}

float shaderlod_live_pixel_scale() {
    // Absent temporal uniform defaults to zero, which is NOT known history.
    // Unknown transport keeps configured stock static scale; history is rejected.
    if (!shaderlod_live_pixel_history_known()) {
        return TAAU_RENDER_SCALE;
    }
    // Binary dimensionless policy: global OFF 1.0, global ON 0.75.
    // Producer must reset on startup, reload/world and effective scale changes.
    return shaderlodEnabled == 1 ? 0.75 : 1.0;
}

bool shaderlod_live_pixel_history_reset() {
    // Unknown transport cannot certify the previous effective raster rectangle.
    // Reject every unavailable frame, including all-zero missing uniforms.
    // This is conservative safety, with unaccepted cloud/noise quality costs.
    return !shaderlod_live_pixel_history_known() || shaderlodHistoryReset != 0;
}
#define taau_render_scale shaderlod_live_pixel_scale()
#endif
