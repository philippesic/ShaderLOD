package com.pp.shaderlod.optimization.makeupao;
import net.irisshaders.iris.targets.RenderTargets;import net.irisshaders.iris.mixinterface.GpuTextureInterface;
/** Explicit pinned GPU-texture Iris depth ABI, backed by actual saved class bytes. */
public final class MakeUpAoDepthId {private MakeUpAoDepthId(){}public static int current(RenderTargets t){return ((GpuTextureInterface)t.getDepthTexture()).iris$getGlId();}}
