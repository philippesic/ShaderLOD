package com.pp.shaderlod.optimization.unboundao.mixin;
import com.pp.shaderlod.optimization.unboundao.UnboundAoRuntime;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pipeline.CompositeRenderer",remap=false)
public abstract class UnboundAoQuadDrawMixin {
 @Inject(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V",shift=At.Shift.BEFORE,remap=false),require=1,allow=1)private void shaderlod$before(CallbackInfo ci){UnboundAoRuntime.beforeDraw();}
 @Inject(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V",shift=At.Shift.AFTER,remap=false),require=1,allow=1)private void shaderlod$after(CallbackInfo ci){UnboundAoRuntime.afterDraw();}
}
