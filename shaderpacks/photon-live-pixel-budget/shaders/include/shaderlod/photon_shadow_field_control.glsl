#if !defined SHADERLOD_PHOTON_SHADOW_FIELD_CONTROL_INCLUDED
#define SHADERLOD_PHOTON_SHADOW_FIELD_CONTROL_INCLUDED
uniform int shaderlodPhotonShadowFieldQualified;
uniform int shaderlodPhotonRawQueryQualified;
bool shaderlod_photon_shadow_field_enabled() {
#ifdef SHADERLOD_LIVE_PIXEL_BUDGET
    return shaderlodPhotonShadowFieldQualified == 1 &&
        (shaderlod_optimization_enabled(shaderlodOptShadowMinField) ||
         shaderlod_optimization_enabled(shaderlodOptShadowRawQuery));
#else
    return false;
#endif
}
#endif
