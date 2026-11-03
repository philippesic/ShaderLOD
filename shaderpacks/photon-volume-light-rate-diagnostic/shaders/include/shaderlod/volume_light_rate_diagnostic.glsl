#ifndef SHADERLOD_VOLUME_LIGHT_RATE_DIAGNOSTIC_HELPER
#define SHADERLOD_VOLUME_LIGHT_RATE_DIAGNOSTIC_HELPER
// Private same-draw counters only. Photon v1.3b by Benjamin Stott (SixthSurge)
// supplies the consuming kernels; retain the original upstream LICENSE.
// No phase freezing, new pass, sampler, classifier, reconstruction or cache.
#ifdef SHADERLOD_VOLUME_LIGHT_RATE_DIAGNOSTIC
#if defined PROGRAM_DEFERRED1 && defined WORLD_OVERWORLD && !defined ATMOSPHERE_TRANSMITTANCE_LUT
uniform int shaderlodVolumeLightRateDiagnosticSchema;
uint shaderlod_rate_diag_primary;
uint shaderlod_rate_diag_occupied;
uint shaderlod_rate_diag_reused;
uint shaderlod_rate_diag_secondary;
uint shaderlod_rate_diag_mode;
uint shaderlod_rate_diag_path;
bool shaderlod_rate_diag_valid;
const uint SHADERLOD_RATE_DIAG_MAX = 16777215u;
const uint SHADERLOD_RATE_DIAG_MARKER = 713001u;
void shaderlod_rate_diag_increment(inout uint value) {
    if (value >= SHADERLOD_RATE_DIAG_MAX) {
        shaderlod_rate_diag_valid = false;
    } else {
        value += 1u;
    }
}
void shaderlod_rate_diag_init() {
    shaderlod_rate_diag_primary = 0u;
    shaderlod_rate_diag_occupied = 0u;
    shaderlod_rate_diag_reused = 0u;
    shaderlod_rate_diag_secondary = 0u;
    shaderlod_rate_diag_mode = 0u;
    shaderlod_rate_diag_path = 0u;
    shaderlod_rate_diag_valid = true;
}
vec4 shaderlod_rate_diag_encode(uint page) {
    if (shaderlodVolumeLightRateDiagnosticSchema != 1 || !shaderlod_rate_diag_valid || page > 2u)
        return vec4(-1.0);
    if (page == 0u) return vec4(float(shaderlod_rate_diag_primary),
        float(shaderlod_rate_diag_occupied), float(shaderlod_rate_diag_reused),
        float(shaderlod_rate_diag_secondary));
    if (page == 1u) return vec4(float(CLOUDS_CUMULUS_LIGHTING_STEPS),
        float(CLOUDS_CUMULUS_AMBIENT_STEPS), float(shaderlod_rate_diag_mode),
        float(shaderlod_rate_diag_path));
    return vec4(1.0, float(SHADERLOD_RATE_DIAG_MARKER), 1.0, 0.0);
}
#define SHADERLOD_RATE_DIAG_INIT() shaderlod_rate_diag_init();
#define SHADERLOD_RATE_DIAG_PRIMARY() shaderlod_rate_diag_increment(shaderlod_rate_diag_primary);
#define SHADERLOD_RATE_DIAG_OCCUPIED() shaderlod_rate_diag_increment(shaderlod_rate_diag_occupied);
#define SHADERLOD_RATE_DIAG_REUSED() shaderlod_rate_diag_increment(shaderlod_rate_diag_reused);
#define SHADERLOD_RATE_DIAG_SECONDARY() shaderlod_rate_diag_increment(shaderlod_rate_diag_secondary);
#define SHADERLOD_RATE_DIAG_MODE(value) shaderlod_rate_diag_mode = value;
#define SHADERLOD_RATE_DIAG_PATH(value) shaderlod_rate_diag_path = value;
#else // SHADERLOD_RATE_DIAG_STAGE
#define SHADERLOD_RATE_DIAG_INACTIVE
#endif // SHADERLOD_RATE_DIAG_STAGE
#else // SHADERLOD_VOLUME_LIGHT_RATE_DIAGNOSTIC
#define SHADERLOD_RATE_DIAG_INACTIVE
#endif // SHADERLOD_VOLUME_LIGHT_RATE_DIAGNOSTIC
#ifdef SHADERLOD_RATE_DIAG_INACTIVE
// Calls and argument evaluation erase from every ordinary/nonconsumer body.
#define SHADERLOD_RATE_DIAG_INIT()
#define SHADERLOD_RATE_DIAG_PRIMARY()
#define SHADERLOD_RATE_DIAG_OCCUPIED()
#define SHADERLOD_RATE_DIAG_REUSED()
#define SHADERLOD_RATE_DIAG_SECONDARY()
#define SHADERLOD_RATE_DIAG_MODE(value)
#define SHADERLOD_RATE_DIAG_PATH(value)
#endif // SHADERLOD_RATE_DIAG_INACTIVE
#endif // SHADERLOD_VOLUME_LIGHT_RATE_DIAGNOSTIC_HELPER
