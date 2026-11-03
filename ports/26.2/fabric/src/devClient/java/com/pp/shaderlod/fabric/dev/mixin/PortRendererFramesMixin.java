package com.pp.shaderlod.fabric.dev.mixin;
import com.pp.shaderlod.fabric.dev.PortRendererFrames;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Exact pinned normal world-render return, after the real LevelRenderer render call. */
@Mixin(value=GameRenderer.class,remap=false)
public abstract class PortRendererFramesMixin {
    @Inject(method="renderLevel(Lnet/minecraft/client/DeltaTracker;)V",at=@At("RETURN"),require=1,allow=1,remap=false)
    private void shaderlod$worldComplete(net.minecraft.client.DeltaTracker tracker, CallbackInfo ci) {
        if(!PortRendererFrames.requested())return;
        var client=Minecraft.getInstance();
        if(client.level!=null && client.player!=null) {
            // Read facts before incrementing so unavailable APIs never silently remove a completed render.
            try {
                var frameLimiter=client.getFramerateLimitTracker();var window=client.getWindow();
                PortRendererFrames.completed(client.options.inactivityFpsLimit().get().name(),client.options.framerateLimit().get(),
                    frameLimiter.getFramerateLimit(),frameLimiter.getThrottleReason().name(),client.isWindowActive() && window.isFocused(),window.isIconified());
            } catch(RuntimeException | LinkageError unavailable) {
                PortRendererFrames.unavailableCompleted(unavailable.toString());
            }
        }
    }
}
