package com.pp.shaderlod.optimization.framecopy.exchange.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.pp.shaderlod.optimization.framecopy.exchange.CanonicalBankExchange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pipeline.FinalPassRenderer",remap=false)
public abstract class CanonicalFinalMixin {
    @WrapOperation(method="renderFinalPass()V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/GlStateManager;_glCopyTexSubImage2D(IIIIIIII)V"),require=1,allow=1)
    private void exchange$copy(int target,int level,int xo,int yo,int x,int y,int width,int height,Operation<Void> original){
        try{
            boolean omitted=CanonicalBankExchange.beforeFinalCopy(this,target,level,xo,yo,x,y,width,height);
            if(!omitted){CanonicalBankExchange.beforeNativeCopy();original.call(target,level,xo,yo,x,y,width,height);}
            CanonicalBankExchange.finalCopyReturned(omitted);
        }catch(RuntimeException|Error failure){throw CanonicalBankExchange.copyFailed(failure);}
    }
    @Inject(method="renderFinalPass()V",at=@At("RETURN"),require=1,allow=1)
    private void exchange$done(CallbackInfo ci){CanonicalBankExchange.finalReturned(this);}
}
