package com.pp.shaderlod.mixin;

import com.pp.shaderlod.GeometryLodController;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve raw user/server preferences while Sodium and DH observe one consistent effective radius. */
@Mixin(Options.class)
public abstract class EffectiveRenderDistanceMixin {
    @Inject(method = "getEffectiveRenderDistance", at = @At("RETURN"), cancellable = true)
    private void shaderlod$terrainCap(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(GeometryLodController.effectiveChunks(callback.getReturnValue()));
    }
}
