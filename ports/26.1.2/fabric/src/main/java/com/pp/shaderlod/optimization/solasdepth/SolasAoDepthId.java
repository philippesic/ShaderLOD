package com.pp.shaderlod.optimization.solasdepth;
import net.irisshaders.iris.targets.RenderTargets;import net.irisshaders.iris.mixinterface.GpuTextureInterface;
/** Explicit pinned GPU-texture Iris depth ABI, backed by actual saved class bytes. */
public final class SolasAoDepthId {private SolasAoDepthId(){}public static int current(RenderTargets t){return ((GpuTextureInterface)t.getDepthTexture()).iris$getGlId();}}
