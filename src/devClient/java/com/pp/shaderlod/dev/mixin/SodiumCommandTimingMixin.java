package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.CpuTimers;
import net.irisshaders.iris.shadows.ShadowRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pinned per-region CPU assembly and submission wrapper; contains no GPU queries. */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class SodiumCommandTimingMixin {
    @Inject(method = "fillCommandBuffer", at = @At("HEAD"), remap = false)
    private static void shaderlod$beginFill(CallbackInfo ci) { CpuTimers.beginLayer(fillScope()); }

    @Inject(method = "fillCommandBuffer", at = @At("RETURN"), remap = false)
    private static void shaderlod$endFill(CallbackInfo ci) { CpuTimers.endLayer(fillScope()); }

    @Inject(method = "executeDrawBatch", at = @At("HEAD"), remap = false)
    private static void shaderlod$beginExecute(CallbackInfo ci) { CpuTimers.beginLayer(executeScope()); }

    @Inject(method = "executeDrawBatch", at = @At("RETURN"), remap = false)
    private static void shaderlod$endExecute(CallbackInfo ci) { CpuTimers.endLayer(executeScope()); }

    @Unique
    private static String fillScope() {
        return ShadowRenderer.ACTIVE ? "sodium-command-fill/shadow" : "sodium-command-fill/world";
    }

    @Unique
    private static String executeScope() {
        return ShadowRenderer.ACTIVE ? "sodium-execute-batch/shadow" : "sodium-execute-batch/world";
    }
}
