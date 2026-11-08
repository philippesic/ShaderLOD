package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.*;
import net.irisshaders.iris.gl.IrisRenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pinned Iris API boundaries; named API cost includes dependencies, never bandwidth. */
@Mixin(value=IrisRenderSystem.class,remap=false)
public abstract class CostMapOperationsMixin {
    @Inject(method="copyTexImage2D(IIIIIIII)V",at=@At("HEAD"),require=1,remap=false)
    private static void shaderlod$0HEAD(CallbackInfo ci) { CpuTimers.beginLayer("operation/copy-image");GpuTimers.beginCategory("operation/copy-image",GpuSamplingPolicy.Category.IRIS); }
    @Inject(method="copyTexImage2D(IIIIIIII)V",at=@At("RETURN"),require=1,remap=false)
    private static void shaderlod$0RETURN(CallbackInfo ci) { GpuTimers.endCategory();CpuTimers.endLayer("operation/copy-image"); }
    @Inject(method="copyTexSubImage2D(IIIIIIIII)V",at=@At("HEAD"),require=1,remap=false)
    private static void shaderlod$1HEAD(CallbackInfo ci) { CpuTimers.beginLayer("operation/copy-sub-image");GpuTimers.beginCategory("operation/copy-sub-image",GpuSamplingPolicy.Category.IRIS); }
    @Inject(method="copyTexSubImage2D(IIIIIIIII)V",at=@At("RETURN"),require=1,remap=false)
    private static void shaderlod$1RETURN(CallbackInfo ci) { GpuTimers.endCategory();CpuTimers.endLayer("operation/copy-sub-image"); }
    @Inject(method="blitFramebuffer(IIIIIIIIIIII)V",at=@At("HEAD"),require=1,remap=false)
    private static void shaderlod$2HEAD(CallbackInfo ci) { CpuTimers.beginLayer("operation/blit");GpuTimers.beginCategory("operation/blit",GpuSamplingPolicy.Category.IRIS); }
    @Inject(method="blitFramebuffer(IIIIIIIIIIII)V",at=@At("RETURN"),require=1,remap=false)
    private static void shaderlod$2RETURN(CallbackInfo ci) { GpuTimers.endCategory();CpuTimers.endLayer("operation/blit"); }
    @Inject(method="clearBufferSubData(IIJJII[I)V",at=@At("HEAD"),require=1,remap=false)
    private static void shaderlod$3HEAD(CallbackInfo ci) { CpuTimers.beginLayer("operation/clear-buffer");GpuTimers.beginCategory("operation/clear-buffer",GpuSamplingPolicy.Category.IRIS); }
    @Inject(method="clearBufferSubData(IIJJII[I)V",at=@At("RETURN"),require=1,remap=false)
    private static void shaderlod$3RETURN(CallbackInfo ci) { GpuTimers.endCategory();CpuTimers.endLayer("operation/clear-buffer"); }
    @Inject(method="dispatchCompute(III)V",at=@At("HEAD"),require=1,remap=false)
    private static void shaderlod$4HEAD(CallbackInfo ci) { CpuTimers.beginLayer("operation/dispatch");GpuTimers.beginCategory("operation/dispatch",GpuSamplingPolicy.Category.IRIS); }
    @Inject(method="dispatchCompute(III)V",at=@At("RETURN"),require=1,remap=false)
    private static void shaderlod$4RETURN(CallbackInfo ci) { GpuTimers.endCategory();CpuTimers.endLayer("operation/dispatch"); }
    @Inject(method="generateMipmaps(II)V",at=@At("HEAD"),require=1,remap=false)
    private static void shaderlod$mipBegin(CallbackInfo ci){CpuTimers.beginLayer("operation/mip");GpuTimers.beginCategory("operation/mip",GpuSamplingPolicy.Category.IRIS);}
    @Inject(method="generateMipmaps(II)V",at=@At("RETURN"),require=1,remap=false)
    private static void shaderlod$mipEnd(CallbackInfo ci){GpuTimers.endCategory();CpuTimers.endLayer("operation/mip");}
}

