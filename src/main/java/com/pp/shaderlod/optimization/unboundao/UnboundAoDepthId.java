package com.pp.shaderlod.optimization.unboundao;
import net.irisshaders.iris.targets.RenderTargets;
/** MC1.21.1 exact integer depth ABI. Later pinned tuples have explicit module adapters. */
public final class UnboundAoDepthId {private UnboundAoDepthId(){}public static int current(RenderTargets t){return t.getDepthTexture();}}
