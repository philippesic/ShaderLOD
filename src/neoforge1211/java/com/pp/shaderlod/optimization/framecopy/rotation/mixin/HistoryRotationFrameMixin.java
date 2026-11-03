package com.pp.shaderlod.optimization.framecopy.rotation.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.pp.shaderlod.optimization.framecopy.rotation.HistoryRotationPreparation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** Includes exceptions from geometry, shadows and unrelated passes, not just c2/final. */
@Mixin(GameRenderer.class)
public abstract class HistoryRotationFrameMixin {
    @WrapMethod(method = "renderLevel", require = 1)
    private void rotation$frame(DeltaTracker tracker, Operation<Void> original) {
        try { original.call(tracker); HistoryRotationPreparation.frameReturned(); }
        catch (RuntimeException | Error failure) {
            HistoryRotationPreparation.frameFailed(failure);
            throw failure;
        }
    }
}
