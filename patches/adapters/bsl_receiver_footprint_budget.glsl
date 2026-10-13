#ifndef SHADERLOD_RECEIVER_FOOTPRINT_BUDGET_R1_INCLUDED
#define SHADERLOD_RECEIVER_FOOTPRINT_BUDGET_R1_INCLUDED

// Private experiment policy, not a production activation or visual-error bound.
// The adapter supplies shadow UV derivatives per raster pixel, evaluated before
// divergent control flow, and the FULL native filter's positive UV half extent.
// That support includes hardware filtering, jitter and every retained layer.
// The local affine inverse maps its UV rectangle to an output-pixel bound.
// It cannot bound discontinuous visibility, receiver curvature, caster motion,
// thin foliage, color/transmission, or the error of a changed quadrature.
//
// eligible certifies the adapter's exact source/runtime, encoding/filter state,
// receiver/material/AA/depth domain and locally usable derivatives. Unknowns
// must pass false. There are no texture reads, derivatives, uniforms or history
// in this helper. Its caller pays for hoisted derivatives and classification.
//
// Result is a quadrature BLEND weight, not a tap count: 0 = native; 1 = the
// adapter's fixed reduced tier. Between them, evaluate enough native samples
// to blend continuously. A hard tap switch at a nonzero blend creates popping.
// Only the plateau permits eliminating the omitted native samples. Exact
// endpoint/native-equivalent behavior and added ALU/divergence/specialization
// costs still require source, driver, motion and total-frame qualification.
float shaderlod_receiver_reduction_weight_r1(bool eligible,
    vec2 shadowUvDx, vec2 shadowUvDy, vec2 nativeUvHalfExtent,
    float outputPixelsPerRasterPixel) {
    if (!eligible) return 0.0;
    // Ordered comparisons also reject NaN/infinity on legacy GLSL, without
    // depending on a loader's version upgrade for isnan/isinf built-ins.
    if (!all(lessThanEqual(abs(shadowUvDx), vec2(100.0))) ||
        !all(lessThanEqual(abs(shadowUvDy), vec2(100.0))) ||
        !all(greaterThan(nativeUvHalfExtent, vec2(0.0))) ||
        !all(lessThanEqual(nativeUvHalfExtent, vec2(1.0))) ||
        !(outputPixelsPerRasterPixel >= 1.0 && outputPixelsPerRasterPixel <= 16.0))
        return 0.0;

    float determinant = shadowUvDx.x * shadowUvDy.y - shadowUvDy.x * shadowUvDx.y;
    float largestColumn2 = max(dot(shadowUvDx, shadowUvDx), dot(shadowUvDy, shadowUvDy));
    // Relative conditioning refusal; do not turn a grazing/singular receiver
    // into a large inverse or assume it has negligible projected support.
    if (!(largestColumn2 > 1e-20 && largestColumn2 < 1e4 &&
        abs(determinant) > 0.01 * largestColumn2)) return 0.0;

    vec2 outputHalfExtent = vec2(
        dot(abs(vec2(shadowUvDy.y, shadowUvDy.x)), nativeUvHalfExtent),
        dot(abs(vec2(shadowUvDx.y, shadowUvDx.x)), nativeUvHalfExtent)
    ) * (outputPixelsPerRasterPixel / abs(determinant));
    float supportRadius2 = dot(outputHalfExtent, outputHalfExtent);
    if (!(supportRadius2 >= 0.0 && supportRadius2 < 0.25)) return 0.0;
    // Fixed, unsearched hypothesis: full reduction <= 0.25 output pixel;
    // smoothly restore the native quadrature by 0.5 output pixel. Interpolation
    // is in squared radius to avoid a square root. These are not quality claims.
    return 1.0 - smoothstep(0.0625, 0.25, supportRadius2);
}

#endif
