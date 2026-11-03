package com.pp.shaderlod.fabric.dev.mixin;

import com.pp.shaderlod.fabric.dev.FabricRendererFrames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dev-only observation of the real compactor result; no second transform or driver reads. */
@Mixin(targets = "com.pp.shaderlod.integration.makeupquad.AdjacentQuadCoalescer", remap = false)
public abstract class FabricCommandEvidenceMixin {
    @Inject(method = "compact(JJJIII)I", at = @At("RETURN"), require = 1, remap = false)
    private static void shaderlod$commands(long pointers, long counts, long bases, int size,
            int capacity, int stride, CallbackInfoReturnable<Integer> ci) {
        FabricRendererFrames.compacted(size, ci.getReturnValueI());
    }
}
