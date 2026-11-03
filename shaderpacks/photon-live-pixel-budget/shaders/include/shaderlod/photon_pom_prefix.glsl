#if !defined SHADERLOD_PHOTON_POM_PREFIX_INCLUDED
#define SHADERLOD_PHOTON_POM_PREFIX_INCLUDED
uniform int shaderlodPhotonPrefixVersion, shaderlodPhotonPrefixActive;
uniform vec2 shaderlodPhotonPrefixImageSize;
uniform vec4 shaderlodPhotonPrefixRect0,shaderlodPhotonPrefixRect1,shaderlodPhotonPrefixRect2;
uniform vec3 shaderlodPhotonPrefixHeights;
bool shaderlod_photon_prefix_support(vec2 local,vec4 rect) {
    vec2 uv=get_uv_from_local_coord(local);
    if(!all(greaterThanEqual(uv,vec2(0.0))) || !all(lessThan(uv,vec2(1.0))))return false;
    vec2 address=floor(uv*shaderlodPhotonPrefixImageSize);
    return all(greaterThanEqual(address-1.0,rect.xy)) && all(lessThanEqual(address+1.0,rect.zw));
}
bool shaderlod_photon_prefix_descriptor(vec2 local,vec4 rect,float height) {
    return height>=0.0 && height<1.0 && all(greaterThanEqual(rect.xy,vec2(0.0))) &&
        all(lessThan(rect.zw,shaderlodPhotonPrefixImageSize)) &&
        all(greaterThanEqual(rect.zw,rect.xy+2.0)) && all(equal(floor(rect),rect)) &&
        shaderlod_photon_prefix_support(local,rect);
}
bool shaderlod_photon_prefix_certificate(vec2 local,mat2 gradient,out vec4 rect,out float lowerDepth) {
    rect=vec4(0.0);lowerDepth=0.0;
    if(!shaderlod_optimization_enabled(shaderlodOptPomEmptyHeightPrefix) || shaderlodPreset!=1 ||
        shaderlodPhotonPrefixVersion!=1 || shaderlodPhotonPrefixActive!=1 ||
        !all(greaterThanEqual(shaderlodPhotonPrefixImageSize,vec2(16.0))) ||
        !all(lessThanEqual(shaderlodPhotonPrefixImageSize,vec2(16384.0))) ||
        !all(equal(floor(shaderlodPhotonPrefixImageSize),shaderlodPhotonPrefixImageSize)) ||
        vec2(textureSize(normals,0))!=shaderlodPhotonPrefixImageSize)return false;
    vec2 gx=gradient[0]*shaderlodPhotonPrefixImageSize,gy=gradient[1]*shaderlodPhotonPrefixImageSize;
    float rho=max(dot(gx,gx),dot(gy,gy));if(!(rho>=0.0 && rho<0.015625))return false;
    float upper=1.0;
    if(shaderlod_photon_prefix_descriptor(local,shaderlodPhotonPrefixRect0,shaderlodPhotonPrefixHeights.x)) {rect=shaderlodPhotonPrefixRect0;upper=shaderlodPhotonPrefixHeights.x;}
    else if(shaderlod_photon_prefix_descriptor(local,shaderlodPhotonPrefixRect1,shaderlodPhotonPrefixHeights.y)) {rect=shaderlodPhotonPrefixRect1;upper=shaderlodPhotonPrefixHeights.y;}
    else if(shaderlod_photon_prefix_descriptor(local,shaderlodPhotonPrefixRect2,shaderlodPhotonPrefixHeights.z)) {rect=shaderlodPhotonPrefixRect2;upper=shaderlodPhotonPrefixHeights.z;}
    else return false;
    // Native sampled depth=1-alpha. Additional inward margin retains a
    // conservative lower depth after binary32 subtraction; supplier owns image/filter proof.
    lowerDepth=max(0.0,1.0-upper-1.0/65536.0);return lowerDepth>0.0;
}
#endif
