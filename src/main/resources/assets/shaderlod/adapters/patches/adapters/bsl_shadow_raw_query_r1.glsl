// ShaderLOD private explicit raw-PCF R1. BSL source/credits retained by builder.
// NEW primary query: no hardware-filter equivalence or precision bound claimed.
#include "/lib/shaderlod/bsl_shadow_field_control_r1.glsl"
uniform int shaderlodBslRawQueryQualified;
uniform sampler2D shadowcolor2;
// Native calls retain HW aliases. This sampler is the separate RAW depth0 name.
#undef shadowtex0
uniform sampler2D shadowtex0;
// Keep raw use in these helper bodies BEFORE restoring native shadowtex0 below.

struct ShaderlodBslRawFootprint {
    ivec2 base[9];
    vec2 fraction[9];
    ivec2 firstCell;
    ivec2 lastCell;
};

bool shaderlodBslRawFootprint(vec3 shadowPos, float offset, float subsurface,
                             out ShaderlodBslRawFootprint footprint) {
    if (!shaderlod_optimization_enabled(shaderlodOptShadowRawQuery) || shaderlodBridgeVersion != 2 || shaderlodEnabled != 1 ||
        !(shaderlodPreset == 0 || shaderlodPreset == 1 || shaderlodPreset == 2) ||
        shaderlodBslRawQueryQualified != 1 || subsurface != 0.0 ||
        shadowMapResolution != 2048 || offset != 1.0 / 2048.0 ||
        frameCounter < 0 || frameCounter >= 16777215 ||
        textureSize(shadowtex0, 0) != ivec2(2048) ||
        textureSize(shadowcolor2, 0) != ivec2(2048) ||
        !(shadowPos.z >= 0.0 && shadowPos.z <= 1.0)) return false;

    ivec2 lower = ivec2(2047);
    ivec2 upper = ivec2(0);
    for (int i = 0; i < 9; i++) {
        // Exactly native multiplication, addition, radius and biased reference.
        vec2 shadowOffset = shadowOffsets[i] * offset;
        vec2 p = (shadowPos.st + shadowOffset) * 2048.0 - 0.5;
        // Test finite/interior BEFORE floor/int conversion. No clamp/wrap guess.
        if (!all(greaterThanEqual(p, vec2(0.0))) ||
            !all(lessThan(p, vec2(2047.0)))) return false;
        vec2 whole = floor(p);
        footprint.base[i] = ivec2(whole);
        footprint.fraction[i] = p - whole;
        if (!all(greaterThanEqual(footprint.fraction[i], vec2(0.0))) ||
            !all(lessThanEqual(footprint.fraction[i], vec2(1.0)))) return false;
        lower = min(lower, footprint.base[i]);
        upper = max(upper, footprint.base[i] + ivec2(1));
    }
    // The ACTUAL stored integer endpoints determine every queried cell.
    footprint.firstCell = lower / 16;
    footprint.lastCell = upper / 16;
    if (any(greaterThan(footprint.lastCell - footprint.firstCell, ivec2(1))) ||
        footprint.lastCell == ivec2(127)) return false;
    // Stamp is checked by BOTH arms. Invalid field/stale epoch takes native9.
    return texelFetch(shadowcolor2, ivec2(127), 0).r == -float(frameCounter + 1);
}

float shaderlodBslRawPcf(ivec2 base, vec2 fraction, float reference) {
    vec4 d = vec4(texelFetch(shadowtex0, base, 0).r,
                  texelFetch(shadowtex0, base + ivec2(1, 0), 0).r,
                  texelFetch(shadowtex0, base + ivec2(0, 1), 0).r,
                  texelFetch(shadowtex0, base + ivec2(1, 1), 0).r);
    vec4 c = vec4(reference <= d.x ? 1.0 : 0.0,
                  reference <= d.y ? 1.0 : 0.0,
                  reference <= d.z ? 1.0 : 0.0,
                  reference <= d.w ? 1.0 : 0.0);
    // Defined all-pass endpoint; no precision claim about mix(1,1,f).
    if (c == vec4(1.0)) return 1.0;
    if (c == vec4(0.0)) return 0.0;
    return mix(mix(c.x, c.y, fraction.x),
               mix(c.z, c.w, fraction.x), fraction.y);
}

float shaderlodBslRawPrimary9(ShaderlodBslRawFootprint footprint, float reference) {
    float sum = 0.0;
    for (int i = 0; i < 9; i++) {
        sum += shaderlodBslRawPcf(footprint.base[i], footprint.fraction[i], reference);
    }
    return sum;
}

bool shaderlodBslRawFieldAllLit(ShaderlodBslRawFootprint footprint, float reference) {
    // Validate EACH operand before min: min may mask an invalid field value.
    vec4 m = vec4(texelFetch(shadowcolor2, footprint.firstCell, 0).r,
                  texelFetch(shadowcolor2, ivec2(footprint.lastCell.x, footprint.firstCell.y), 0).r,
                  texelFetch(shadowcolor2, ivec2(footprint.firstCell.x, footprint.lastCell.y), 0).r,
                  texelFetch(shadowcolor2, footprint.lastCell, 0).r);
    // Strict positive rejects producer's invalid-depth sentinel, zero clears,
    // valid zero minima and negative stamp. >= retains positive exact ties.
    return all(greaterThan(m, vec4(0.0))) &&
           all(lessThanEqual(m, vec4(1.0))) &&
           all(greaterThanEqual(m, vec4(reference)));
}
#define shadowtex0 shadowtex0HW
