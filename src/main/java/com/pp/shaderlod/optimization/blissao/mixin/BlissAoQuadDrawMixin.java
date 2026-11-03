package com.pp.shaderlod.optimization.blissao.mixin;
import com.pp.shaderlod.optimization.blissao.BlissAoRuntime;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pipeline.CompositeRenderer",remap=false)
public abstract class BlissAoQuadDrawMixin {
 @Inject(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V",shift=At.Shift.BEFORE,remap=false),require=1,allow=1)private void shaderlod$before(CallbackInfo ci){BlissAoRuntime.beforeDraw();}
 @Inject(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V",shift=At.Shift.AFTER,remap=false),require=1,allow=1)private void shaderlod$after(CallbackInfo ci){BlissAoRuntime.afterDraw();}
}
