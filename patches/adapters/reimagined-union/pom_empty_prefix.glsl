// Native pomEffects.glsl has one include per stage; retain its single-inclusion contract.
#if SHADERLOD_PROJECTED_POM == 1 && defined GBUFFERS_TERRAIN && !defined GBUFFERS_COLORWHEEL && !defined DISTANT_HORIZONS && !defined VOXY && !defined POM_ALLOW_CUTOUT
uniform int shaderlodReimaginedPrefixVersion, shaderlodReimaginedPrefixActive;
uniform int shaderlodOptPomEmptyHeightPrefix;
uniform int shaderlodReimaginedPrefixSourceQualified, shaderlodReimaginedPrefixSamplerQualified;
uniform int shaderlodReimaginedPrefixEpoch, shaderlodReimaginedPrefixCurrentEpoch;
uniform vec2 shaderlodReimaginedPrefixImageSize;
uniform vec4 shaderlodReimaginedPrefixRect0, shaderlodReimaginedPrefixRect1, shaderlodReimaginedPrefixRect2;
uniform vec3 shaderlodReimaginedPrefixHeights;

// Native ReadNormal's coordinate transform, without any new derivative or sample.
vec2 shaderlodReimaginedPrefixUV(vec2 localCoord) {
    return fract(localCoord) * vTexCoordAM.pq + vTexCoordAM.st;
}
bool shaderlodReimaginedPrefixSupport(vec2 localCoord, vec4 rect) {
    vec2 uv = shaderlodReimaginedPrefixUV(localCoord);
    if (!all(greaterThanEqual(uv,vec2(0.0))) || !all(lessThan(uv,vec2(1.0)))) return false;
    // Conservative ordinary isotropic MAG/base support, with a one-texel margin.
    // Actual sampler/format/filter rounding must be certified by the producer.
    vec2 address = floor(uv * shaderlodReimaginedPrefixImageSize);
    return all(greaterThanEqual(address-vec2(1.0),rect.xy))
        && all(lessThanEqual(address+vec2(1.0),rect.zw));
}
bool shaderlodReimaginedPrefixDescriptor(vec2 coord, vec4 rect, float upper) {
    return upper >= 0.0 && upper < 1.0
        && all(greaterThanEqual(rect.xy,vec2(0.0)))
        && all(lessThan(rect.zw,shaderlodReimaginedPrefixImageSize))
        && all(greaterThanEqual(rect.zw,rect.xy+vec2(2.0)))
        && all(equal(floor(rect),rect)) && shaderlodReimaginedPrefixSupport(coord,rect);
}
bool shaderlodReimaginedPrefixCertificate(vec2 coord, out vec4 rect, out float upper) {
    rect=vec4(0.0); upper=1.0;
    if (!shaderlod_optimization_enabled(shaderlodOptPomEmptyHeightPrefix) || shaderlodBridgeVersion != 2 || shaderlodEnabled != 1
        || shaderlodReimaginedPrefixVersion != 1 || shaderlodReimaginedPrefixActive != 1
        || shaderlodReimaginedPrefixSourceQualified != 1 || shaderlodReimaginedPrefixSamplerQualified != 1
        || shaderlodReimaginedPrefixEpoch < 1 || shaderlodReimaginedPrefixEpoch > 16777216
        || shaderlodReimaginedPrefixEpoch != shaderlodReimaginedPrefixCurrentEpoch
        || !all(equal(shaderlodReimaginedPrefixImageSize,vec2(atlasSize)))
        || !all(lessThan(abs(coord),vec2(4096.0)))
        || !all(greaterThanEqual(shaderlodReimaginedPrefixImageSize,vec2(16.0)))
        || !all(lessThanEqual(shaderlodReimaginedPrefixImageSize,vec2(16384.0)))
        || !all(equal(floor(shaderlodReimaginedPrefixImageSize),shaderlodReimaginedPrefixImageSize))) return false;
    // Same native global explicit gradients; reject each component before dot/max.
    if (!all(lessThan(abs(dcdx),vec2(1.0))) ||
        !all(lessThan(abs(dcdy),vec2(1.0)))) return false;
    // Native gradients were computed outside material/march divergence.
    // Parent's strict native magnification gate: rho < 0.125 texel.
    vec2 gx=dcdx*shaderlodReimaginedPrefixImageSize;
    vec2 gy=dcdy*shaderlodReimaginedPrefixImageSize;
    float rhoSquared=max(dot(gx,gx),dot(gy,gy));
    if (!(rhoSquared >= 0.0 && rhoSquared < 0.015625)) return false;
    if (shaderlodReimaginedPrefixDescriptor(coord,shaderlodReimaginedPrefixRect0,shaderlodReimaginedPrefixHeights.x)) {
        rect=shaderlodReimaginedPrefixRect0; upper=shaderlodReimaginedPrefixHeights.x; return true;
    }
    if (shaderlodReimaginedPrefixDescriptor(coord,shaderlodReimaginedPrefixRect1,shaderlodReimaginedPrefixHeights.y)) {
        rect=shaderlodReimaginedPrefixRect1; upper=shaderlodReimaginedPrefixHeights.y; return true;
    }
    if (shaderlodReimaginedPrefixDescriptor(coord,shaderlodReimaginedPrefixRect2,shaderlodReimaginedPrefixHeights.z)) {
        rect=shaderlodReimaginedPrefixRect2; upper=shaderlodReimaginedPrefixHeights.z; return true;
    }
    return false;
}
bool shaderlod_reimagined_prefix_continue(vec2 coord,float nextThreshold,
    float distanceBlocks,float fade,bool opaqueMaterial,out float upper) {
    upper=1.0;
    if (!opaqueMaterial || !(fade>=0.5 && fade<1.0) ||
        !(POM_DISTANCE>=16 && POM_DISTANCE<=1024) ||
        !(distanceBlocks>=0.5*float(POM_DISTANCE) && distanceBlocks<float(POM_DISTANCE)) ||
        !all(lessThan(abs(viewVector),vec3(1e6)))) return false;
    float view2=dot(viewVector,viewVector);
    if (!(view2>1e-8 && view2<1e12 && viewVector.z<0.0 &&
        viewVector.z*viewVector.z>0.0625*view2)) return false;
    vec4 rect;
    return shaderlodReimaginedPrefixCertificate(coord,rect,upper) && upper<=nextThreshold;
}
#endif
