package com.pp.shaderlod.optimization.makeupshadow.mixin;
import com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowSamplerPass;
import com.pp.shaderlod.optimization.complementarysampler.ComplementarySamplerPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.gl.program.ProgramSamplers",remap=false)
public abstract class SamplerPassSamplersMixin {
 @Inject(method={"update()V"},at=@At("HEAD"),require=1,allow=1)
 private void invalidate0(CallbackInfo ci){try{MakeUpShadowSamplerPass.invalidate();}finally{ComplementarySamplerPass.invalidate();}}
 @Inject(method={"clearActiveSamplers()V"},at=@At("HEAD"),require=1,allow=1)
 private static void invalidate1(CallbackInfo ci){try{MakeUpShadowSamplerPass.invalidate();}finally{ComplementarySamplerPass.invalidate();}}
}
