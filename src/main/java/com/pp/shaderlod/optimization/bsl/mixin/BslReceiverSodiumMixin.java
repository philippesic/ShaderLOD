package com.pp.shaderlod.optimization.bsl.mixin;
import com.pp.shaderlod.optimization.bsl.BslReceiverRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pipeline.programs.SodiumShader",remap=false)
public abstract class BslReceiverSodiumMixin {
    @Inject(method="setupState()V",at=@At("RETURN"),require=1)
    private void shaderlod$receiver(CallbackInfo ci) {BslReceiverRuntime.uploadCurrent();}
}
