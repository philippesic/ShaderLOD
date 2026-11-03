package com.pp.shaderlod.optimization.makeupao.mixin;
import com.pp.shaderlod.optimization.makeupao.MakeUpAoRuntime;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pipeline.CompositeRenderer",remap=false)
public abstract class MakeUpAoGlDrawMixin {
 @Inject(method="renderAll()V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/opengl/GlStateManager;_drawElements(IIIJ)V",shift=At.Shift.BEFORE,remap=false),require=1,allow=1)private void shaderlod$before(CallbackInfo ci){MakeUpAoRuntime.beforeDraw();}
 @Inject(method="renderAll()V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/opengl/GlStateManager;_drawElements(IIIJ)V",shift=At.Shift.AFTER,remap=false),require=1,allow=1)private void shaderlod$after(CallbackInfo ci){MakeUpAoRuntime.afterDraw();}
}
