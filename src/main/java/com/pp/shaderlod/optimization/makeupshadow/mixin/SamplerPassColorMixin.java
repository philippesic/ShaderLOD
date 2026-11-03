package com.pp.shaderlod.optimization.makeupshadow.mixin;
import com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowSamplerPass;
import com.pp.shaderlod.optimization.complementarysampler.ComplementarySamplerPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.targets.RenderTarget",remap=false)
public abstract class SamplerPassColorMixin {
 @Inject(method={"resize(II)V","destroy()V"},at=@At("HEAD"),require=2,allow=2)
 private void invalidate0(CallbackInfo ci){try{MakeUpShadowSamplerPass.invalidate();}finally{ComplementarySamplerPass.invalidate();}}
}
