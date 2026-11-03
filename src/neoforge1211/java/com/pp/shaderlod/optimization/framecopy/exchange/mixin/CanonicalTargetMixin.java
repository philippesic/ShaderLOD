package com.pp.shaderlod.optimization.framecopy.exchange.mixin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.pp.shaderlod.optimization.framecopy.exchange.CanonicalBankExchange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets="net.irisshaders.iris.targets.RenderTarget",remap=false)
public abstract class CanonicalTargetMixin {
    @ModifyReturnValue(method="getMainTexture()I",at=@At("RETURN"),require=1,allow=1)
    private int exchange$main(int original){return CanonicalBankExchange.mainTexture(this,original);}
    @ModifyReturnValue(method="getAltTexture()I",at=@At("RETURN"),require=1,allow=1)
    private int exchange$alt(int original){return CanonicalBankExchange.altTexture(this,original);}
}
