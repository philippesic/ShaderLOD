package com.pp.shaderlod.solasprefix.mixin;
import com.pp.shaderlod.solasprefix.SolasPrefixRuntime;import net.irisshaders.iris.pipeline.IrisRenderingPipeline;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Actual26.1.2 binding path has no TextureFormat.setupTextureParameters invocation. Owner valid() compares current format/immutable native sampler; reload retires first. */
@Mixin(value=IrisRenderingPipeline.class,remap=false) public abstract class SolasPrefixPipelineR8Mixin {
 @Inject(method="onSetAlbedoTex",at=@At("RETURN"),require=1)private void binding(CallbackInfo ci){SolasPrefixRuntime.bindingChanged();}
 @Inject(method="destroy()V",at=@At("HEAD"),require=1)private void destroy(CallbackInfo ci){SolasPrefixRuntime.pipelineDestroying(this);}
}