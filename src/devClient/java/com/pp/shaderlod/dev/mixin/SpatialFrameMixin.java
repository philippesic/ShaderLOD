package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.SpatialLayoutProbe;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** RETURN follows production frame listeners and never re-evaluates smoothers/uniform suppliers. */
@Mixin(value = FrameUpdateNotifier.class, remap = false)
public abstract class SpatialFrameMixin {
    @Inject(method = "onNewFrame()V", at = @At("RETURN"), require = 1, remap = false)
    private void shaderlod$frame(CallbackInfo callback) {
        SpatialLayoutProbe.frameUpdated((FrameUpdateNotifier)(Object)this);
    }
}
