package com.pp.shaderlod.port.dev.mixin;
import com.pp.shaderlod.port.dev.PortRendererFrames;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public abstract class PortRendererFramesMixin {
    @Inject(method="render(Lnet/minecraft/client/DeltaTracker;Z)V", at=@At("RETURN"), require=1)
    private void shaderlod$completed(DeltaTracker tracker, boolean renderLevel, CallbackInfo ci) {
        var client=Minecraft.getInstance();
        if (renderLevel && client.level != null && client.isGameLoadFinished()) {
            if (!Boolean.getBoolean("shaderlod.dev.portRendererSpeedProbe")) return;
            try {
                var limiter = client.getFramerateLimitTracker();
                PortRendererFrames.completed(client.options.framerateLimit().get(), limiter.getFramerateLimit(),
                        limiter.getThrottleReason().name(),
                        client.options.inactivityFpsLimit().get() == net.minecraft.client.InactivityFpsLimit.MINIMIZED,
                        client.isWindowActive(), client.getWindow().isIconified());
            } catch (Exception | LinkageError unavailable) {
                PortRendererFrames.unavailableFrame(unavailable.toString());
            }
        }
    }
}
