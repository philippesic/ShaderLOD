#if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined SHADERLOD_ASTRA_RECEIVER_CONTEXT && defined FRAGMENT && defined OVERWORLD && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined ADVANCED_MATERIALS && defined PARALLAX && MATERIAL_FORMAT == 1 && !defined DISTANT_HORIZONS && !defined VOXY
uniform int shaderlodAstraPrefixVersion, shaderlodAstraPrefixActive;
uniform int shaderlodOptPomEmptyHeightPrefix;
uniform vec2 shaderlodAstraPrefixImageSize;
uniform vec4 shaderlodAstraPrefixRect0, shaderlodAstraPrefixRect1, shaderlodAstraPrefixRect2;
uniform vec3 shaderlodAstraPrefixHeights;

// Native ReadNormal's coordinate transform, without any new derivative or sample.
vec2 shaderlodAstraPrefixUV(vec2 localCoord) {
    return fract(localCoord) * vTexCoordAM.zw + vTexCoordAM.xy;
}
bool shaderlodAstraPrefixSupport(vec2 localCoord, vec4 rect) {
    vec2 uv = shaderlodAstraPrefixUV(localCoord);
    if (!all(greaterThanEqual(uv,vec2(0.0))) || !all(lessThan(uv,vec2(1.0)))) return false;
    // Conservative ordinary isotropic MAG/base support, with a one-texel margin.
    // Actual sampler/format/filter rounding must be certified by the producer.
    vec2 address = floor(uv * shaderlodAstraPrefixImageSize);
    return all(greaterThanEqual(address-vec2(1.0),rect.xy))
        && all(lessThanEqual(address+vec2(1.0),rect.zw));
}
bool shaderlodAstraPrefixDescriptor(vec2 coord, vec4 rect, float upper) {
    return upper >= 0.0 && upper < 1.0
        && all(greaterThanEqual(rect.xy,vec2(0.0)))
        && all(lessThan(rect.zw,shaderlodAstraPrefixImageSize))
        && all(greaterThanEqual(rect.zw,rect.xy+vec2(2.0)))
        && all(equal(floor(rect),rect)) && shaderlodAstraPrefixSupport(coord,rect);
}
bool shaderlodAstraPrefixCertificate(vec2 coord, out vec4 rect, out float upper) {
    rect=vec4(0.0); upper=1.0;
    if (!shaderlod_optimization_enabled(shaderlodOptPomEmptyHeightPrefix) || shaderlodBridgeVersion != 2 || shaderlodEnabled != 1
        || shaderlodAstraPrefixVersion != 1 || shaderlodAstraPrefixActive != 1
        || !all(greaterThanEqual(shaderlodAstraPrefixImageSize,vec2(16.0)))
        || !all(lessThanEqual(shaderlodAstraPrefixImageSize,vec2(16384.0)))
        || !all(equal(floor(shaderlodAstraPrefixImageSize),shaderlodAstraPrefixImageSize))) return false;
    // Check BOTH native gradient vectors before dot/max: max may hide a NaN operand.
    // Direct ordered component bounds are a portable finite gate; every valid rho < .125 footprint already
    // has component magnitude < .125 / imageExtent <= .0078125 (extent >= 16).
    if (!all(greaterThan(dcdx,vec2(-1.0))) || !all(lessThan(dcdx,vec2(1.0))) ||
        !all(greaterThan(dcdy,vec2(-1.0))) || !all(lessThan(dcdy,vec2(1.0)))) return false;
    // Native gradients were computed outside material/march divergence.
    // Parent's strict native magnification gate: rho < 0.125 texel.
    vec2 gx=dcdx*shaderlodAstraPrefixImageSize;
    vec2 gy=dcdy*shaderlodAstraPrefixImageSize;
    float rhoSquared=max(dot(gx,gx),dot(gy,gy));
    if (!(rhoSquared >= 0.0 && rhoSquared < 0.015625)) return false;
    if (shaderlodAstraPrefixDescriptor(coord,shaderlodAstraPrefixRect0,shaderlodAstraPrefixHeights.x)) {
        rect=shaderlodAstraPrefixRect0; upper=shaderlodAstraPrefixHeights.x; return true;
    }
    if (shaderlodAstraPrefixDescriptor(coord,shaderlodAstraPrefixRect1,shaderlodAstraPrefixHeights.y)) {
        rect=shaderlodAstraPrefixRect1; upper=shaderlodAstraPrefixHeights.y; return true;
    }
    if (shaderlodAstraPrefixDescriptor(coord,shaderlodAstraPrefixRect2,shaderlodAstraPrefixHeights.z)) {
        rect=shaderlodAstraPrefixRect2; upper=shaderlodAstraPrefixHeights.z; return true;
    }
    return false;
}
#endif
