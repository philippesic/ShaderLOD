package com.pp.shaderlod.fabric.dev.mixin;

import com.pp.shaderlod.fabric.dev.FabricRendererFrames;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class FabricRendererFramesMixin {
    @Inject(method = "render", at = @At("RETURN"), require = 1)
    private void shaderlod$completed(DeltaTracker tracker, boolean renderLevel, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (renderLevel && client.level != null && !client.noRender) {
            if (Boolean.getBoolean("shaderlod.dev.fabricNativeCostControl")
                    || System.getProperties().containsKey("shaderlod.dev.fabricRendererSinglePhase"))
                com.pp.shaderlod.fabric.dev.FabricNativeCostFrames.observe(client);
            FabricRendererFrames.completeWorldRender();
        }
    }
}
