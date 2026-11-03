package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.ShaderAnimationClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dev plugin admits this only for the launch-fixed benchmark diagnostic. */
@Mixin(targets = "net.irisshaders.iris.uniforms.SystemTimeUniforms$Timer", remap = false)
public abstract class ShaderAnimationClockMixin {
    @Inject(method = "getFrameTimeCounter()F", at = @At("RETURN"), cancellable = true, require = 1)
    private void shaderlod$fixedAnimationTime(CallbackInfoReturnable<Float> callback) {
        if (ShaderAnimationClock.fixed()) callback.setReturnValue(ShaderAnimationClock.fixedValue());
    }
}
