package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.BenchmarkControlProbe;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Canonical clocks only: no detailed profiler, source observer or main-mod API. */
@Mixin(GameRenderer.class)
public abstract class NeutralBenchmarkFrameMixin {
    @Inject(method="renderLevel",at=@At("HEAD"))
    private void shaderlod$world(DeltaTracker tracker,CallbackInfo ci) { BenchmarkControlProbe.beginWorld(); }
    @Inject(method="render",at=@At("HEAD"))
    private void shaderlod$begin(DeltaTracker tracker,boolean level,CallbackInfo ci) { BenchmarkControlProbe.beginRender(); }
    @Inject(method="render",at=@At("RETURN"))
    private void shaderlod$end(DeltaTracker tracker,boolean level,CallbackInfo ci) { BenchmarkControlProbe.endRender(); }
}
