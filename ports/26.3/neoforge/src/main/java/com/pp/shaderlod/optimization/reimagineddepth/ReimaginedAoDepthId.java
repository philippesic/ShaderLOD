package com.pp.shaderlod.optimization.reimagineddepth;import net.irisshaders.iris.targets.RenderTargets;
public final class ReimaginedAoDepthId{private ReimaginedAoDepthId(){}public static int current(RenderTargets targets){return ((net.irisshaders.iris.mixinterface.GpuTextureInterface)targets.getDepthTexture()).iris$getGlId();}}
