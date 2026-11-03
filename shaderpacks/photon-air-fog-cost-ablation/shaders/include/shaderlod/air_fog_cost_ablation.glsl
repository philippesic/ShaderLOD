// Private ShaderLOD cost diagnostic; default OFF, obvious missing fog when ON.
// Photon v1.3b by Benjamin Stott (SixthSurge); retain the full upstream LICENSE.
// Included only at the ordinary Overworld composite0 consumer, never cached sky.
uniform int shaderlodBridgeVersion;
uniform int shaderlodEnabled;

bool shaderlod_air_fog_cost_ablation_active() {
    // Draw-uniform control before the original body; unknown ABI/global OFF
    // preserve original work. No distance bands, temporal history or fade.
    return shaderlodBridgeVersion == 2 && shaderlodEnabled == 1;
}

mat2x3 shaderlod_air_fog_cost_ablation_neutral() {
    // Matches raymarch_air_fog's no-volume result: column0=scattering,
    // column1=transmittance. c0 writes column1 to RT6, column0 to RT7.
    return mat2x3(vec3(0.0), vec3(1.0));
}
