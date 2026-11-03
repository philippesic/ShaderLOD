#ifndef SHADERLOD_AO_HISTORY_GATHER_R1_INCLUDED
#define SHADERLOD_AO_HISTORY_GATHER_R1_INCLUDED
// Photon v1.3b by Benjamin Stott (SixthSurge); original LICENSE retained.
// Five-tap Catmull-Rom math copied from Photon include/utility/bicubic.glsl.
// Same native sharpness (0.65 at caller), weights/order and sampler.
// Only reads base-only c6/c14; explicit LOD avoids divergent derivatives.
// No AO ray reuse, distance bands, new targets or history encoding.

uniform int shaderlodPhotonAOHistoryQualified; // default0; only actual owned normal draw may qualify
bool shaderlod_ao_history_gather_active() {
    // Missing bridge, global Off, Quality and unknown presets use native gathers.
    return shaderlodPhotonAOHistoryQualified == 1 &&
        shaderlod_optimization_enabled(shaderlodOptAoHistoryReuse);
}

vec4 shaderlod_ao_history_filter_lod0(
    sampler2D sampler,
    vec2 coord,
    const float sharpness
) {
    vec2 res = vec2(textureSize(sampler, 0));
    vec2 view_pixel_size = 1.0 / res;

    vec2 position = res * coord;
    vec2 center_position = floor(position - 0.5) + 0.5;
    vec2 f = position - center_position;
    vec2 f2 = f * f;
    vec2 f3 = f * f2;

    vec2 w0 = -sharpness * f3 + 2.0 * sharpness * f2 - sharpness * f;
    vec2 w1 = (2.0 - sharpness) * f3 - (3.0 - sharpness) * f2 + 1.0;
    vec2 w2 =
        -(2.0 - sharpness) * f3 + (3.0 - 2.0 * sharpness) * f2 + sharpness * f;
    vec2 w3 = sharpness * f3 - sharpness * f2;

    vec2 w12 = w1 + w2;
    vec2 tc12 = view_pixel_size * (center_position + w2 / w12);
    vec4 center_color = textureLod(sampler, vec2(tc12.x, tc12.y), 0.0);

    vec2 tc0 = view_pixel_size * (center_position - 1.0);
    vec2 tc3 = view_pixel_size * (center_position + 2.0);

    float l0 = w12.x * w0.y;
    float l1 = w0.x * w12.y;
    float l2 = w12.x * w12.y;
    float l3 = w3.x * w12.y;
    float l4 = w12.x * w3.y;

    vec4 color = textureLod(sampler, vec2(tc12.x, tc0.y), 0.0) * l0 +
        textureLod(sampler, vec2(tc0.x, tc12.y), 0.0) * l1 + center_color * l2 +
        textureLod(sampler, vec2(tc3.x, tc12.y), 0.0) * l3 +
        textureLod(sampler, vec2(tc12.x, tc3.y), 0.0) * l4;

    return color / (l0 + l1 + l2 + l3 + l4);
}
#endif
