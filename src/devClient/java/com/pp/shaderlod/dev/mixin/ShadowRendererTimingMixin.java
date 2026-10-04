package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.GpuTimers;
import net.irisshaders.iris.shadows.ShadowRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShadowRenderer.class, remap = false)
public abstract class ShadowRendererTimingMixin {
    @Inject(method = "renderShadows", at = @At("HEAD"), remap = false)
    private void shaderlod$begin(CallbackInfo ci) { GpuTimers.beginStandalonePass("shadow-map"); }

    @Inject(method = "renderShadows", at = @At("RETURN"), remap = false)
    private void shaderlod$end(CallbackInfo ci) { GpuTimers.endStandalonePass(); }
}
