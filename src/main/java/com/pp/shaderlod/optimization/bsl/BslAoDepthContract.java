package com.pp.shaderlod.optimization.bsl;

import com.pp.shaderlod.optimization.unbounddepth.UnboundAoDepthContract;
import com.pp.shaderlod.optimization.unbounddepth.UnboundAoDepthContract.Sampler;

/** The shared typed storage and sampler proof, without a BSL-only format rule. */
public final class BslAoDepthContract {
    private BslAoDepthContract() {}
    public static boolean sampler(Sampler observed) {return UnboundAoDepthContract.sampler(observed);}
}
