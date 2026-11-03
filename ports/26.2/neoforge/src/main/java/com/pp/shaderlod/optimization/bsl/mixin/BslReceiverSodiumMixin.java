package com.pp.shaderlod.optimization.bsl.mixin;
import com.pp.shaderlod.optimization.bsl.BslReceiverRuntime;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Explicit primary release caller; qualifier upload AFTER native samplers/uniforms/FBO state. */
@Mixin(targets="net.irisshaders.iris.pipeline.programs.ExtendedShader",remap=false)
public abstract class BslReceiverSodiumMixin {
 @Inject(method="iris$setupState(Ljava/util/HashMap;Lcom/mojang/blaze3d/textures/GpuTextureView;)V",at=@At("RETURN"),require=1,allow=1,remap=false)
 private void shaderlod$receiver(CallbackInfo ci){BslReceiverRuntime.uploadCurrent();}
}
