// AstraLex93 normal-only port of the existing Solas two-PNG frequency cutoff.
// Native PNG is128x128, native high wave is1/12 UV per block; keep native linear mix weights.
#if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined SHADERLOD_ASTRA_NORMAL_CONTEXT && defined FRAGMENT && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && WATER_MODE == 0 && !defined DISTANT_HORIZONS && !defined VOXY
vec3 shaderlodAstraNormalWaterPos = vec3(0.0);
float shaderlodAstraWaterHighWeight = 1.0;
bool shaderlodAstraNormalWaterReady = false;
// Scalar policy kept separate for exact native fallback/model checks; no texture queries here.
float shaderlod_astra_water_high_weight(vec2 dx, vec2 dy, vec2 baseDx, vec2 baseDy, float blocks, vec3 tangentView) {
    // Check every component before length/max: max can otherwise hide a NaN operand.
    if (!all(lessThan(abs(dx),vec2(1e6))) || !all(lessThan(abs(dy),vec2(1e6))) ||
        !all(lessThan(abs(baseDx),vec2(1e6))) || !all(lessThan(abs(baseDy),vec2(1e6))) ||
        !all(lessThan(abs(tangentView),vec3(1e6))) || !(blocks >= 0.0 && blocks < 1e6) ||
        !(shaderlodNear > 0.0 && shaderlodNear < shaderlodFar && shaderlodFar <= 4096.0)) return 1.0;
    float view2 = dot(tangentView,tangentView);
    if (!(view2 > 1e-8 && view2 < 1e12 && tangentView.z < 0.0 && tangentView.z*tangentView.z > 0.0625*view2)) return 1.0;
    float footprint = max(length(dx),length(dy));
    float expansion = footprint / max(max(length(baseDx),length(baseDy)),1e-6);
    if (!(expansion >= 0.0 && expansion < 4.0)) return 1.0;
    float frequency = footprint * (128.0/12.0);
    float start = max(32.0,shaderlodNear);
    float finish = max(48.0,start+16.0);
    return 1.0 - smoothstep(start,finish,blocks) *
        (1.0-smoothstep(2.0,4.0,expansion)) * smoothstep(0.125,0.25,frequency);
}
#endif
