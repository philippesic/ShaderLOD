package com.pp.shaderlod.port.dev.mixin;

import com.pp.shaderlod.port.dev.PortRendererFrames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dev-only observation of the real compactor result; no second transform or driver reads. */
@Mixin(targets = "com.pp.shaderlod.integration.makeupquad.AdjacentQuadCoalescer", remap = false)
public abstract class PortCommandEvidenceMixin {
    @Inject(method = "compact(JJJIII)I", at = @At("RETURN"), require = 1, remap = false)
    private static void shaderlod$commands(long pointers, long counts, long bases, int size,
            int capacity, int stride, CallbackInfoReturnable<Integer> ci) {
        PortRendererFrames.compacted(size, ci.getReturnValueI());
    }
}
