package com.pp.shaderlod.benchmark.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.pp.shaderlod.benchmark.BenchmarkLiveWork;
import com.pp.shaderlod.optimization.NativeAdmissionCost;
import net.irisshaders.iris.shadows.ShadowRenderer;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Pinned Sodium native delegate, ordered inside the repository's ordinary command transformations. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList", remap = false)
public abstract class BenchmarkSodiumDrawMixin {
    @WrapOperation(method = "multiDrawElementsBaseVertex",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL32C;nglMultiDrawElementsBaseVertex(IJIJIJ)V"),
            order = 900, require = 1, allow = 1, remap = false)
    private void shaderlod$benchmarkDraw(int mode, long counts, int type, long indices, int drawCount,
                                        long bases, Operation<Void> original) {
        if (!BenchmarkLiveWork.enabled()) { shaderlod$nativeDelegate(mode, counts, type, indices, drawCount, bases, original); return; }
        long entries = 0, indexCount = 0;
        boolean shadow = ShadowRenderer.ACTIVE;
        // The native delegate owns valid count memory; no GL state readback or retained native pointer.
        if (drawCount < 0 || counts == 0 && drawCount > 0) BenchmarkLiveWork.observerFailed();
        else for (int i = 0; i < drawCount; i++) {
            int n = MemoryUtil.memGetInt(counts + (long)i * Integer.BYTES);
            if (n > 0) { entries++; indexCount += n; }
            else if (n < 0) BenchmarkLiveWork.observerFailed();
        }
        shaderlod$nativeDelegate(mode, counts, type, indices, drawCount, bases, original);
        BenchmarkLiveWork.sodiumSubmitted(entries, indexCount, shadow);
    }
    private void shaderlod$nativeDelegate(int mode,long counts,int type,long indices,int draws,long bases,Operation<Void> original) {
        long wall=NativeAdmissionCost.ENABLED&&!ShadowRenderer.ACTIVE?NativeAdmissionCost.wallStart():0;
        long cpu=NativeAdmissionCost.cpuStart(wall);boolean completed=false;
        try {original.call(mode,counts,type,indices,draws,bases);completed=true;}
        finally {NativeAdmissionCost.finish(NativeAdmissionCost.NATIVE_SUBMISSION,wall,cpu,completed);}
    }

}
