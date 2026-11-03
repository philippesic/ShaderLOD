package com.pp.shaderlod.optimization.framecopy.rotation.mixin;

import com.pp.shaderlod.optimization.framecopy.rotation.HistoryRotationRuntime;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.function.Supplier;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.irisshaders.iris.samplers.IrisSamplers", remap = false)
public abstract class HistoryRotationSamplerMixin {
    // Two IRETURNs in this exact synthetic supplier, both mandatory.
    @ModifyReturnValue(method = "lambda$addRenderTargetSamplers$0(Ljava/util/function/Supplier;Lnet/irisshaders/iris/targets/RenderTargets;I)I",
        at = @At("RETURN"), require = 2, allow = 2)
    private static int rotation$supplier(int original, Supplier<?> stage, RenderTargets targets, int index) {
        return HistoryRotationRuntime.selectSampler(targets, index, original);
    }
}
