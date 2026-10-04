package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.CpuTimers;
import net.irisshaders.iris.shadows.ShadowRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.RenderBufferHandler", remap = false)
public abstract class DhRenderListTimingMixin {
    @Inject(method = "buildRenderList", at = @At("HEAD"), remap = false)
    private void shaderlod$begin(CallbackInfo ci) { CpuTimers.beginLayer(scope()); }
    @Inject(method = "buildRenderList", at = @At("RETURN"), remap = false)
    private void shaderlod$end(CallbackInfo ci) { CpuTimers.endLayer(scope()); }
    @org.spongepowered.asm.mixin.Unique
    private static String scope() { return "dh-render-list/" + (ShadowRenderer.ACTIVE ? "shadow" : "world"); }
}
