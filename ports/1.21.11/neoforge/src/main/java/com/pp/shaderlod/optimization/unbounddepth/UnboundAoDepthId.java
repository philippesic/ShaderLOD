package com.pp.shaderlod.optimization.unbounddepth;import net.irisshaders.iris.targets.RenderTargets;
public final class UnboundAoDepthId{private UnboundAoDepthId(){}public static int current(RenderTargets targets){return ((net.irisshaders.iris.mixinterface.GpuTextureInterface)targets.getDepthTexture()).iris$getGlId();}}
