package com.pp.shaderlod.optimization.bsl.mixin;
import com.pp.shaderlod.optimization.bsl.BslReceiverRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.gl.program.Program",remap=false)
public abstract class BslReceiverProgramMixin {
    @Inject(method="use()V",at=@At("RETURN"),require=1)
    private void shaderlod$receiver(CallbackInfo ci) {BslReceiverRuntime.uploadCurrent();}
}
