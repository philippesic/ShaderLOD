package com.pp.shaderlod.optimization.bsl;

import com.pp.shaderlod.optimization.unbounddepth.UnboundAoDepthContract;
import com.pp.shaderlod.optimization.unbounddepth.UnboundAoDepthContract.Sampler;

/** BSL-only recognition of queried native depth storage; never changes GL state.
 * OpenGL 4.6 sections 8.5 and 8.11 distinguish a base format token from the
 * actual component precision/type. Only observed unsigned-normalized D24 is
 * recognized here. Every other sampler constraint stays in the shared contract.
 */
public final class BslAoDepthContract {
    private BslAoDepthContract() {}

    public static boolean sampler(Sampler observed) {
        if (UnboundAoDepthContract.sampler(observed)) return true;
        if (observed == null || observed.format() != 0x1902
                || observed.depthBits() != 24 || observed.depthType() != 0x8C17) return false;
        // A CPU description of the queried D24 storage, not a texture rewrite.
        return UnboundAoDepthContract.sampler(new Sampler(
                observed.texture(), observed.ownedTexture(), observed.width(), observed.height(),
                observed.ownerWidth(), observed.ownerHeight(), observed.level1Width(), observed.level1Height(),
                0x81A6, observed.depthBits(), observed.depthType(), observed.compare(), observed.min(), observed.mag(),
                observed.base(), observed.max(), observed.wrapS(), observed.wrapT(), observed.bias(),
                observed.minLod(), observed.maxLod(), observed.anisotropy()));
    }
}
