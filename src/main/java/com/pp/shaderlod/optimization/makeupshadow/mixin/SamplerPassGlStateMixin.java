package com.pp.shaderlod.optimization.makeupshadow.mixin;
import com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowSamplerPass;
import com.pp.shaderlod.optimization.complementarysampler.ComplementarySamplerPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="com.mojang.blaze3d.platform.GlStateManager",remap=true)
public abstract class SamplerPassGlStateMixin {
 @Inject(method={"_bindTexture(I)V","_texParameter(III)V","_texParameter(IIF)V","_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V","_deleteTexture(I)V","_deleteTextures([I)V"},at=@At("HEAD"),require=6,allow=6)
 private static void invalidate0(CallbackInfo ci){try{MakeUpShadowSamplerPass.invalidate();}finally{ComplementarySamplerPass.invalidate();}}
}
