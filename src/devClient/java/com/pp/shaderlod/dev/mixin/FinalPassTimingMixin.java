package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.CpuTimers;
import net.irisshaders.iris.pipeline.FinalPassRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FinalPassRenderer.class, remap = false)
public abstract class FinalPassTimingMixin {
    @Inject(method = "renderFinalPass", at = @At("HEAD"), remap = false)
    private void shaderlod$begin(CallbackInfo ci) {
        CpuTimers.beginLayer("iris/final");
        GpuTimers.beginStandalonePass("final");
    }

    @Inject(method = "renderFinalPass", at = @At("RETURN"), remap = false)
    private void shaderlod$end(CallbackInfo ci) {
        GpuTimers.endStandalonePass();
        CpuTimers.endLayer("iris/final");
    }
}
