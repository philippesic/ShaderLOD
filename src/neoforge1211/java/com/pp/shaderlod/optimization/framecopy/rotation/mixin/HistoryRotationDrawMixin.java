package com.pp.shaderlod.optimization.framecopy.rotation.mixin;

import com.pp.shaderlod.optimization.framecopy.rotation.HistoryRotationPreparation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pinned actual native submission seam. Observes arguments only inside canonical c2 scope. */
@Mixin(targets = "com.mojang.blaze3d.platform.GlStateManager", remap = false)
public abstract class HistoryRotationDrawMixin {
    @Inject(method = "_drawElements(IIIJ)V", at = @At(value = "INVOKE", target =
        "Lorg/lwjgl/opengl/GL11;glDrawElements(IIIJ)V"), require = 1, allow = 1)
    private static void rotation$draw(int mode, int count, int type, long offset, CallbackInfo ci) {
        HistoryRotationPreparation.nativeDraw(mode, count, type, offset);
    }
}
