package com.pp.shaderlod.sildurprefix.mixin;
import com.pp.shaderlod.sildurprefix.SildurPrefixRuntime;import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=IrisRenderingPipeline.class,remap=false)public abstract class SildurPrefixPipelineR8Mixin{
@Inject(method="onSetAlbedoTex(Lcom/mojang/blaze3d/textures/GpuTextureView;)V",at=@At("HEAD"),require=1)private void changing(CallbackInfo ci){SildurPrefixRuntime.bindingChanging();}
@Inject(method="onSetAlbedoTex(Lcom/mojang/blaze3d/textures/GpuTextureView;)V",at=@At("RETURN"),require=1)private void changed(CallbackInfo ci){SildurPrefixRuntime.bindingChanged();}
@Inject(method="destroy()V",at=@At("HEAD"),require=1)private void destroy(CallbackInfo ci){SildurPrefixRuntime.pipelineDestroying(this);}
}