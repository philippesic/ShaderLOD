package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.CpuTimers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererTimingMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void shaderlod$begin(DeltaTracker tracker, boolean renderLevel, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        CpuTimers.begin("game-render");
        GpuTimers.beginFrame(renderLevel && client.level != null && !client.noRender);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void shaderlod$end(DeltaTracker tracker, boolean renderLevel, CallbackInfo ci) {
        GpuTimers.endFrame();
        CpuTimers.end("game-render");
    }
}
