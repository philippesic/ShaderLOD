package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.CpuTimers;
import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.GpuSamplingPolicy.Category;
import net.irisshaders.iris.shadows.ShadowRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.renderer.LodRenderer", remap = false)
public abstract class DhTotalTimingMixin {
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void shaderlod$begin(CallbackInfo ci) { begin("main"); }
    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void shaderlod$end(CallbackInfo ci) { end("main"); }
    @Inject(method = "renderDeferred", at = @At("HEAD"), remap = false)
    private void shaderlod$beginDeferred(CallbackInfo ci) { begin("deferred"); }
    @Inject(method = "renderDeferred", at = @At("RETURN"), remap = false)
    private void shaderlod$endDeferred(CallbackInfo ci) { end("deferred"); }

    @org.spongepowered.asm.mixin.Unique
    private static String scope(String stage) {
        return "dh-total/" + (ShadowRenderer.ACTIVE ? "shadow/" : "world/") + stage;
    }
    @org.spongepowered.asm.mixin.Unique
    private static void begin(String stage) {
        CpuTimers.beginLayer(scope(stage));
        GpuTimers.beginCategory(scope(stage), Category.DH_TOTAL);
    }
    @org.spongepowered.asm.mixin.Unique
    private static void end(String stage) {
        GpuTimers.endCategory();
        CpuTimers.endLayer(scope(stage));
    }
}
