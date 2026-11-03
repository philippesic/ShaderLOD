package com.pp.shaderlod.optimization.reimagineddepth.mixin;
import com.pp.shaderlod.optimization.reimagineddepth.ReimaginedAoDepthRuntime;import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Actual GL backend boundary after pass pipeline/uniform/sampler/viewport setup. */
@Mixin(targets="com.mojang.blaze3d.opengl.GlCommandEncoder",remap=false)public abstract class ReimaginedAoDepthModernDrawMixin{
 @Inject(method="drawFromBuffers",at=@At("HEAD"),require=1)private void before(CallbackInfo ci){ReimaginedAoDepthRuntime.beforeDraw();}
 }
