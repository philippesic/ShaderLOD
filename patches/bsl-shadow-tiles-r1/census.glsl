// ShaderLOD B4 eligibility diagnostic. BSL by Capt Tatsu remains upstream.
// NEVER TIME: writes receiver coordinates, not lighting, and allocates RGBA32F.
// No shadow lookup, derivative, distance fade, history or field is added here.
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;
uniform int shaderlodPreset;
vec4 shaderlodBslTilesRecord = vec4(-1.0);
int shaderlodBslTilesVisits = 0;

bool shaderlodBslTilesCensusEnabled() {
    return shaderlodBridgeVersion == 2 && shaderlodEnabled == 1 &&
           (shaderlodPreset == 0 || shaderlodPreset == 1 || shaderlodPreset == 2);
}

void shaderlodBslTilesObserve(vec3 shadowPos, float offset, float subsurface) {
    // Called at the reached native filtered receiver, after native bias/fade.
    // xyz are normalized shadow UV/reference depth; w is normalized radius.
    // -1 unvisited, -2 subsurface, -3 unexpected radius, -4 repeated receiver.
    shaderlodBslTilesVisits += 1;
    if (shaderlodBslTilesVisits != 1) {
        shaderlodBslTilesRecord = vec4(-4.0);
    } else if (subsurface != 0.0) {
        shaderlodBslTilesRecord = vec4(-2.0);
    } else if (offset != 1.0 / shadowMapResolution) {
        shaderlodBslTilesRecord = vec4(-3.0);
    } else {
        shaderlodBslTilesRecord = vec4(shadowPos, offset);
    }
}
