package com.pp.shaderlod.optimization.framecopy.exchange.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.pp.shaderlod.optimization.framecopy.exchange.CanonicalBankExchange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets="net.irisshaders.iris.targets.ClearPass",remap=false)
public abstract class CanonicalClearMixin {
    @WrapOperation(method="execute",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"),require=1,allow=1)
    private void exchange$clear(int mask,boolean osx,Operation<Void> original){
        boolean submit=CanonicalBankExchange.beforeClearSubmission(this,mask,osx);
        if(submit){original.call(mask,osx);CanonicalBankExchange.clearSubmissionReturned(this);}
    }
}
