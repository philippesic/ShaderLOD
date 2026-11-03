uniform int shaderlodBlissPrefixVersion, shaderlodBlissPrefixActive;
uniform int shaderlodOptPomEmptyHeightPrefix;
uniform vec2 shaderlodBlissPrefixImageSize;
uniform vec4 shaderlodBlissPrefixRect0, shaderlodBlissPrefixRect1, shaderlodBlissPrefixRect2;
uniform vec3 shaderlodBlissPrefixHeights;

// Native ReadNormal's coordinate transform, without any new derivative or sample.
vec2 shaderlodBlissPrefixUV(vec2 localCoord) {
    return fract(localCoord) * vtexcoordam.pq + vtexcoordam.st;
}
bool shaderlodBlissPrefixSupport(vec2 localCoord, vec4 rect) {
    vec2 uv = shaderlodBlissPrefixUV(localCoord);
    if (!all(greaterThanEqual(uv,vec2(0.0))) || !all(lessThan(uv,vec2(1.0)))) return false;
    // Conservative ordinary isotropic MAG/base support, with a one-texel margin.
    // Actual sampler/format/filter rounding must be certified by the producer.
    vec2 address = floor(uv * shaderlodBlissPrefixImageSize);
    return all(greaterThanEqual(address-vec2(1.0),rect.xy))
        && all(lessThanEqual(address+vec2(1.0),rect.zw));
}
bool shaderlodBlissPrefixDescriptor(vec2 coord, vec4 rect, float upper) {
    return upper >= 0.0 && upper < 1.0
        && all(greaterThanEqual(rect.xy,vec2(0.0)))
        && all(lessThan(rect.zw,shaderlodBlissPrefixImageSize))
        && all(greaterThanEqual(rect.zw,rect.xy+vec2(2.0)))
        && all(equal(floor(rect),rect)) && shaderlodBlissPrefixSupport(coord,rect);
}
bool shaderlodBlissPrefixCertificate(vec2 coord, out vec4 rect, out float upper) {
    rect=vec4(0.0); upper=1.0;
    if (!shaderlod_optimization_enabled(shaderlodOptPomEmptyHeightPrefix) || shaderlodBridgeVersion != 2 || shaderlodEnabled != 1
        || shaderlodBlissPrefixVersion != 1 || shaderlodBlissPrefixActive != 1
        || !all(greaterThanEqual(shaderlodBlissPrefixImageSize,vec2(16.0)))
        || !all(lessThanEqual(shaderlodBlissPrefixImageSize,vec2(16384.0)))
        || !all(equal(floor(shaderlodBlissPrefixImageSize),shaderlodBlissPrefixImageSize))) return false;
    // Check BOTH native gradient vectors before dot/max: max may hide a NaN operand.
    // Direct ordered component bounds are a portable finite gate; every valid rho < .125 footprint already
    // has component magnitude < .125 / imageExtent <= .0078125 (extent >= 16).
    if (!all(greaterThan(dcdx,vec2(-1.0))) || !all(lessThan(dcdx,vec2(1.0))) ||
        !all(greaterThan(dcdy,vec2(-1.0))) || !all(lessThan(dcdy,vec2(1.0)))) return false;
    // Native gradients were computed outside material/march divergence.
    // Parent's strict native magnification gate: rho < 0.125 texel.
    vec2 gx=dcdx*shaderlodBlissPrefixImageSize;
    vec2 gy=dcdy*shaderlodBlissPrefixImageSize;
    float rhoSquared=max(dot(gx,gx),dot(gy,gy));
    if (!(rhoSquared >= 0.0 && rhoSquared < 0.015625)) return false;
    if (shaderlodBlissPrefixDescriptor(coord,shaderlodBlissPrefixRect0,shaderlodBlissPrefixHeights.x)) {
        rect=shaderlodBlissPrefixRect0; upper=shaderlodBlissPrefixHeights.x; return true;
    }
    if (shaderlodBlissPrefixDescriptor(coord,shaderlodBlissPrefixRect1,shaderlodBlissPrefixHeights.y)) {
        rect=shaderlodBlissPrefixRect1; upper=shaderlodBlissPrefixHeights.y; return true;
    }
    if (shaderlodBlissPrefixDescriptor(coord,shaderlodBlissPrefixRect2,shaderlodBlissPrefixHeights.z)) {
        rect=shaderlodBlissPrefixRect2; upper=shaderlodBlissPrefixHeights.z; return true;
    }
    return false;
}

bool shaderlodBlissPomContinue(vec3 coord,vec3 viewDirection,float distanceBlocks,float fade,bool opaqueMaterial) {
    if(shaderlod_optimization_enabled(shaderlodOptPomEmptyHeightPrefix) && opaqueMaterial &&
        fade>=0.5 && fade<1.0 && distanceBlocks>0.0 && distanceBlocks<MAX_OCCLUSION_DISTANCE &&
        POM_DEPTH>0.0 && POM_DEPTH<=1.0 && coord.p>=0.0 && coord.p<=1.0 &&
        all(lessThan(abs(viewDirection),vec3(1e6))) && viewDirection.z< -0.25) {
        vec4 rect;float upper;
        if(shaderlodBlissPrefixCertificate(coord.st,rect,upper)) {
            // Native evaluation order and binary32 expression, monotone for POM_DEPTH>0.
            float threshold=1.0-POM_DEPTH+POM_DEPTH*upper;
            if(threshold<coord.p)return true;
        }
    }
    // Keep native query/order when unknown, Off, Quality or outside certified support.
    return (1.0-POM_DEPTH+POM_DEPTH*readNormal(coord.st).a)<coord.p && coord.p>=0.0;
}
