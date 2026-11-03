package com.pp.shaderlod.optimization;

/** Other loader/version tuples retain the ordinary reload path until separately qualified. */
final class NativeCpuApplyProof {
    record Permission(boolean allowed,String reason) {}
    static final Permission DENIED=new Permission(false,"Native CPU Apply unqualified on this loader/version");
    static NativeCpuApplyProof capture(OptimizationSnapshot next){return null;}
    Permission permission(OptimizationSnapshot previous,OptimizationSnapshot next){return DENIED;}
    void verify(){throw new IllegalStateException("Native CPU Apply unqualified on this loader/version");}
}
