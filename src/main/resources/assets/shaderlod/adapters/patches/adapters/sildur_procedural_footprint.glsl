#if !defined SHADERLOD_PROCEDURAL_FOOTPRINT_INCLUDED
#define SHADERLOD_PROCEDURAL_FOOTPRINT_INCLUDED
// General carrier-plus-modulator footprint policy. Derivatives supplied by caller
// in procedural coordinate units per raster pixel, captured before divergence.
// modulation_gradient_bound is a Lipschitz bound in cycles/coordinate unit.
float shaderlod_procedural_cycles(
    vec2 dx, vec2 dy, vec2 carrier, float modulation_gradient_bound
) {
    return max(abs(dot(carrier, dx)) + modulation_gradient_bound * length(dx),
               abs(dot(carrier, dy)) + modulation_gradient_bound * length(dy));
}

float shaderlod_procedural_weight(float cycles_per_pixel) {
    // Squared sine has a second harmonic. Fade starts at eight pixels/cycle;
    // fully omit at four, where the second harmonic reaches Nyquist.
    return 1.0 - smoothstep(0.125, 0.25, cycles_per_pixel);
}
#endif
