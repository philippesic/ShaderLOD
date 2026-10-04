package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.CpuTimers;
import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.GpuSamplingPolicy.Category;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftFrameTimingMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void shaderlod$beginTick(boolean renderLevel, CallbackInfo ci) {
        boolean world = Minecraft.getInstance().level != null;
        GpuTimers.beginTick(world);
        CpuTimers.beginFrame(GpuTimers.frameId(), world);
        CpuTimers.begin("run-tick");
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void shaderlod$endTick(boolean renderLevel, CallbackInfo ci) {
        CpuTimers.end("run-tick");
        GpuTimers.endTick();
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
    private void shaderlod$beginGpuRegion(boolean renderLevel, CallbackInfo ci) {
        GpuTimers.beginCategory("render-and-blit", Category.RENDER_AND_BLIT);
        CpuTimers.begin("render-and-blit");
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay()V"))
    private void shaderlod$beginSwap(boolean renderLevel, CallbackInfo ci) {
        CpuTimers.end("render-and-blit");
        GpuTimers.endCategory();
        CpuTimers.begin("swap-and-events");
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay()V", shift = At.Shift.AFTER))
    private void shaderlod$endSwap(boolean renderLevel, CallbackInfo ci) { CpuTimers.end("swap-and-events"); }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"))
    private void shaderlod$beginLimiter(boolean renderLevel, CallbackInfo ci) { CpuTimers.begin("frame-limiter"); }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V", shift = At.Shift.AFTER))
    private void shaderlod$endLimiter(boolean renderLevel, CallbackInfo ci) { CpuTimers.end("frame-limiter"); }
}
