package com.pp.shaderlod.optimization.makeupshadow.mixin;
import com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowSamplerPass;
import com.pp.shaderlod.optimization.complementarysampler.ComplementarySamplerPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.gl.IrisRenderSystem",remap=false)
public abstract class SamplerPassIrisSystemMixin {
 @Inject(method={"bindTextureToUnit(III)V","bindTextureForSetup(II)V","bindSamplerToUnit(II)V","unbindAllSamplers()V","samplerParameteri(III)V","samplerParameterf(IIF)V","samplerParameteriv(II[I)V","destroySampler(I)V","texParameteri(IIII)V","texParameterf(IIIF)V","texParameteriv(III[I)V","texParameterivDirect(II[I)V","texImage2D(IIIIIIIIILjava/nio/ByteBuffer;)V","copyTexImage2D(IIIIIIII)V"},at=@At("HEAD"),require=14,allow=14)
 private static void invalidate0(CallbackInfo ci){try{MakeUpShadowSamplerPass.invalidate();}finally{ComplementarySamplerPass.invalidate();}}
}
