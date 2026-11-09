package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;

/** Pinned Iris DH depth handoff, including both allocation and fast-copy branches. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.compat.dh.DHCompatInternal",remap=false)
public abstract class DhTransferTimingMixin {
    @Shadow public abstract int getStoredDepthTex();
    @Shadow public abstract int getDepthTexNoTranslucent();
    @WrapMethod(method="copyTranslucents")
    private void shaderlod$copy(int width,int height,Operation<Void> original){
        String scope="operation/dh-depth-handoff";
        CpuTimers.beginLayer(scope);
        CostMapSession.transfer(getStoredDepthTex(),getDepthTexNoTranslucent(),width,height);
        GpuTimers.beginCategory(scope,GpuSamplingPolicy.Category.IRIS);
        try{original.call(width,height);}finally{GpuTimers.endCategory();CpuTimers.endLayer(scope);}
    }
}
