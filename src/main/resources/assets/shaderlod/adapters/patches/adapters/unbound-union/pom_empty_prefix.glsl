// Native pomEffects.glsl has one include per stage; retain its single-inclusion contract.
#if defined IS_IRIS && MC_VERSION == 12101 && (IRIS_VERSION == 10812 || IRIS_VERSION == 10808) && SHADERLOD_PROJECTED_POM == 1 && defined GBUFFERS_TERRAIN && !defined GBUFFERS_COLORWHEEL && !defined DISTANT_HORIZONS && !defined VOXY && !defined POM_ALLOW_CUTOUT
uniform int shaderlodUnboundPrefixVersion, shaderlodUnboundPrefixActive;
uniform int shaderlodOptPomEmptyHeightPrefix;
uniform int shaderlodUnboundPrefixSourceQualified, shaderlodUnboundPrefixSamplerQualified;
uniform int shaderlodUnboundPrefixEpoch, shaderlodUnboundPrefixCurrentEpoch;
uniform vec2 shaderlodUnboundPrefixImageSize;
uniform vec4 shaderlodUnboundPrefixRect0, shaderlodUnboundPrefixRect1, shaderlodUnboundPrefixRect2;
uniform vec3 shaderlodUnboundPrefixHeights;

// Native ReadNormal's coordinate transform, without any new derivative or sample.
vec2 shaderlodUnboundPrefixUV(vec2 localCoord) {
    return fract(localCoord) * vTexCoordAM.pq + vTexCoordAM.st;
}
bool shaderlodUnboundPrefixSupport(vec2 localCoord, vec4 rect) {
    vec2 uv = shaderlodUnboundPrefixUV(localCoord);
    if (!all(greaterThanEqual(uv,vec2(0.0))) || !all(lessThan(uv,vec2(1.0)))) return false;
    // Conservative ordinary isotropic MAG/base support, with a one-texel margin.
    // Actual sampler/format/filter rounding must be certified by the producer.
    vec2 address = floor(uv * shaderlodUnboundPrefixImageSize);
    return all(greaterThanEqual(address-vec2(1.0),rect.xy))
        && all(lessThanEqual(address+vec2(1.0),rect.zw));
}
bool shaderlodUnboundPrefixDescriptor(vec2 coord, vec4 rect, float upper) {
    return upper >= 0.0 && upper < 1.0
        && all(greaterThanEqual(rect.xy,vec2(0.0)))
        && all(lessThan(rect.zw,shaderlodUnboundPrefixImageSize))
        && all(greaterThanEqual(rect.zw,rect.xy+vec2(2.0)))
        && all(equal(floor(rect),rect)) && shaderlodUnboundPrefixSupport(coord,rect);
}
bool shaderlodUnboundPrefixCertificate(vec2 coord, out vec4 rect, out float upper) {
    rect=vec4(0.0); upper=1.0;
    if (!shaderlod_optimization_enabled(shaderlodOptPomEmptyHeightPrefix) || shaderlodBridgeVersion != 2 || shaderlodEnabled != 1
        || shaderlodUnboundPrefixVersion != 1 || shaderlodUnboundPrefixActive != 1
        || shaderlodUnboundPrefixSourceQualified != 1 || shaderlodUnboundPrefixSamplerQualified != 1
        || shaderlodUnboundPrefixEpoch < 1 || shaderlodUnboundPrefixEpoch > 16777216
        || shaderlodUnboundPrefixEpoch != shaderlodUnboundPrefixCurrentEpoch
        || !all(equal(shaderlodUnboundPrefixImageSize,vec2(atlasSize)))
        || !all(lessThan(abs(coord),vec2(4096.0)))
        || !all(greaterThanEqual(shaderlodUnboundPrefixImageSize,vec2(16.0)))
        || !all(lessThanEqual(shaderlodUnboundPrefixImageSize,vec2(16384.0)))
        || !all(equal(floor(shaderlodUnboundPrefixImageSize),shaderlodUnboundPrefixImageSize))) return false;
    // Same native global explicit gradients; reject each component before dot/max.
    if (!all(greaterThan(dcdx,vec2(-1.0))) || !all(lessThan(dcdx,vec2(1.0))) ||
        !all(greaterThan(dcdy,vec2(-1.0))) || !all(lessThan(dcdy,vec2(1.0)))) return false;
    // Native gradients were computed outside material/march divergence.
    // Parent's strict native magnification gate: rho < 0.125 texel.
    vec2 gx=dcdx*shaderlodUnboundPrefixImageSize;
    vec2 gy=dcdy*shaderlodUnboundPrefixImageSize;
    float rhoSquared=max(dot(gx,gx),dot(gy,gy));
    if (!(rhoSquared >= 0.0 && rhoSquared < 0.015625)) return false;
    if (shaderlodUnboundPrefixDescriptor(coord,shaderlodUnboundPrefixRect0,shaderlodUnboundPrefixHeights.x)) {
        rect=shaderlodUnboundPrefixRect0; upper=shaderlodUnboundPrefixHeights.x; return true;
    }
    if (shaderlodUnboundPrefixDescriptor(coord,shaderlodUnboundPrefixRect1,shaderlodUnboundPrefixHeights.y)) {
        rect=shaderlodUnboundPrefixRect1; upper=shaderlodUnboundPrefixHeights.y; return true;
    }
    if (shaderlodUnboundPrefixDescriptor(coord,shaderlodUnboundPrefixRect2,shaderlodUnboundPrefixHeights.z)) {
        rect=shaderlodUnboundPrefixRect2; upper=shaderlodUnboundPrefixHeights.z; return true;
    }
    return false;
}
bool shaderlod_unbound_prefix_continue(vec2 coord,float nextThreshold,
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
    return shaderlodUnboundPrefixCertificate(coord,rect,upper) && upper<=nextThreshold;
}
#endif
