package com.pp.shaderlod.optimization.bsl.mixin;
import com.pp.shaderlod.optimization.bsl.BslReceiverRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.Iris",remap=false)
public abstract class BslReceiverReloadMixin {
    @Inject(method="reload()V",at=@At("HEAD"),require=1)
    private static void shaderlod$receiverSource(CallbackInfo ci) {BslReceiverRuntime.invalidate();}
}
