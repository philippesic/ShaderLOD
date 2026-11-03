#if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined GBUFFERS_TERRAIN && !defined GBUFFERS_TERRAIN_COLORWHEEL && defined IS_IRIS && ((MC_VERSION == 12101 && (IRIS_VERSION == 10808 || IRIS_VERSION == 10812)) || (MC_VERSION == 12111 && IRIS_VERSION == 11008) || (MC_VERSION == 260102 && IRIS_VERSION == 11104)) && defined PBR && defined PARALLAX && !defined DISTANT_HORIZONS && !defined VOXY
uniform int shaderlodSolasPrefixVersion, shaderlodSolasPrefixActive;
uniform vec2 shaderlodSolasPrefixImageSize;
uniform vec4 shaderlodSolasPrefixRect0, shaderlodSolasPrefixRect1, shaderlodSolasPrefixRect2;
uniform vec3 shaderlodSolasPrefixHeights;

// Native ReadNormal's coordinate transform, without any new derivative or sample.
vec2 shaderlodSolasPrefixUV(vec2 localCoord) {
    return fract(localCoord) * vTexCoordAM.pq + vTexCoordAM.st;
}
bool shaderlodSolasPrefixSupport(vec2 localCoord, vec4 rect) {
    vec2 uv = shaderlodSolasPrefixUV(localCoord);
    if (!all(greaterThanEqual(uv,vec2(0.0))) || !all(lessThan(uv,vec2(1.0)))) return false;
    // Conservative ordinary isotropic MAG/base support, with a one-texel margin.
    // Actual sampler/format/filter rounding must be certified by the producer.
    vec2 address = floor(uv * shaderlodSolasPrefixImageSize);
    return all(greaterThanEqual(address-vec2(1.0),rect.xy))
        && all(lessThanEqual(address+vec2(1.0),rect.zw));
}
bool shaderlodSolasPrefixDescriptor(vec2 coord, vec4 rect, float upper) {
    return upper >= 0.0 && upper < 1.0
        && all(greaterThanEqual(rect.xy,vec2(0.0)))
        && all(lessThan(rect.zw,shaderlodSolasPrefixImageSize))
        && all(greaterThanEqual(rect.zw,rect.xy+vec2(2.0)))
        && all(equal(floor(rect),rect)) && shaderlodSolasPrefixSupport(coord,rect);
}
bool shaderlodSolasPrefixCertificate(vec2 coord, out vec4 rect, out float upper) {
    rect=vec4(0.0); upper=1.0;
    if (!shaderlod_optimization_enabled(shaderlodOptPomEmptyHeightPrefix) || shaderlodBridgeVersion != 2 || shaderlodEnabled != 1
        || shaderlodSolasPrefixVersion != 1 || shaderlodSolasPrefixActive != 1
        || !all(greaterThanEqual(shaderlodSolasPrefixImageSize,vec2(16.0)))
        || !all(lessThanEqual(shaderlodSolasPrefixImageSize,vec2(16384.0)))
        || !all(equal(floor(shaderlodSolasPrefixImageSize),shaderlodSolasPrefixImageSize))) return false;
    // Check BOTH native gradient vectors before dot/max: max may hide a NaN operand.
    // Direct ordered component bounds are a portable finite gate; every valid rho < .125 footprint already
    // has component magnitude < .125 / imageExtent <= .0078125 (extent >= 16).
    if (!all(greaterThan(dcdx,vec2(-1.0))) || !all(lessThan(dcdx,vec2(1.0))) ||
        !all(greaterThan(dcdy,vec2(-1.0))) || !all(lessThan(dcdy,vec2(1.0)))) return false;
    // Native gradients were computed outside material/march divergence.
    // Parent's strict native magnification gate: rho < 0.125 texel.
    vec2 gx=dcdx*shaderlodSolasPrefixImageSize;
    vec2 gy=dcdy*shaderlodSolasPrefixImageSize;
    float rhoSquared=max(dot(gx,gx),dot(gy,gy));
    if (!(rhoSquared >= 0.0 && rhoSquared < 0.015625)) return false;
    if (shaderlodSolasPrefixDescriptor(coord,shaderlodSolasPrefixRect0,shaderlodSolasPrefixHeights.x)) {
        rect=shaderlodSolasPrefixRect0; upper=shaderlodSolasPrefixHeights.x; return true;
    }
    if (shaderlodSolasPrefixDescriptor(coord,shaderlodSolasPrefixRect1,shaderlodSolasPrefixHeights.y)) {
        rect=shaderlodSolasPrefixRect1; upper=shaderlodSolasPrefixHeights.y; return true;
    }
    if (shaderlodSolasPrefixDescriptor(coord,shaderlodSolasPrefixRect2,shaderlodSolasPrefixHeights.z)) {
        rect=shaderlodSolasPrefixRect2; upper=shaderlodSolasPrefixHeights.z; return true;
    }
    return false;
}
#endif
