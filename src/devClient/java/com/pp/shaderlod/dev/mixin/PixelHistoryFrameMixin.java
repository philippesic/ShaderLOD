package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.PixelHistorySession;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pinned post-render/post-blit, pre-swap seam; independent of HUD visibility. */
@Mixin(Minecraft.class)
public abstract class PixelHistoryFrameMixin {
    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay()V"), require = 1)
    private void shaderlod$historyFrame(boolean renderLevel, CallbackInfo callback) {
        PixelHistorySession.afterFrame(Minecraft.getInstance());
    }
}
