package com.pp.shaderlod.optimization.reimagineddepth;import net.irisshaders.iris.targets.RenderTargets;
public final class ReimaginedAoDepthId{private ReimaginedAoDepthId(){}public static int current(RenderTargets targets){return targets.getDepthTexture();}}
