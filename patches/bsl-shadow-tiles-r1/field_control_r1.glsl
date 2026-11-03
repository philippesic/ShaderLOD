// ShaderLOD private B4 experiment; pinned BSL author credits remain upstream.
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodPreset;
// Root supplies 1 only for the joined source/resource/sampler/driver certificate.
// Missing supplier, unknown state, reload and dimension changes default to 0.
uniform int shaderlodBslShadowFieldQualified;

bool shaderlodBslShadowFieldEnabled() {
    return shaderlodBridgeVersion == 2 && shaderlodEnabled == 1 &&
           (shaderlodPreset == 0 || shaderlodPreset == 1 || shaderlodPreset == 2) &&
           shaderlodBslShadowFieldQualified == 1;
}
