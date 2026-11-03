package com.pp.shaderlod.optimization.makeupshadow.mixin;
import com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowSamplerPass;
import com.pp.shaderlod.optimization.complementarysampler.ComplementarySamplerPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.gl.sampler.SamplerBinding",remap=false)
public abstract class SamplerPassBindingMixin {
 @Inject(method={"updateSampler()V"},at=@At("HEAD"),require=1,allow=1)
 private void invalidate0(CallbackInfo ci){try{MakeUpShadowSamplerPass.invalidate();}finally{ComplementarySamplerPass.invalidate();}}
}
