package com.pp.shaderlod.solasprefix.mixin;import com.pp.shaderlod.solasprefix.SolasPrefixRuntime;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Native cached sampler storage can be deleted/reused while Iris keeps its wrapper identity. */
@Mixin(value=com.mojang.blaze3d.opengl.GlSampler.class,remap=false)public abstract class SolasPrefixNativeSamplerMixin{@Inject(method="close()V",at=@At("HEAD"),require=1)private void close(CallbackInfo ci){SolasPrefixRuntime.allMutating();}}
