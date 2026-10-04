package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.CpuTimers;
import net.irisshaders.iris.shadows.ShadowRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Actual native GL submission call wrapper, distinct from Java command-buffer assembly. */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList", remap = false)
public abstract class SodiumDrawSubmissionTimingMixin {
    @Inject(method = "multiDrawElementsBaseVertex", at = @At("HEAD"), remap = false)
    private void shaderlod$begin(CallbackInfo ci) { CpuTimers.beginLayer(scope()); }

    @Inject(method = "multiDrawElementsBaseVertex", at = @At("RETURN"), remap = false)
    private void shaderlod$end(CallbackInfo ci) { CpuTimers.endLayer(scope()); }

    @Unique
    private static String scope() {
        return ShadowRenderer.ACTIVE ? "sodium-draw-submit/shadow" : "sodium-draw-submit/world";
    }
}
