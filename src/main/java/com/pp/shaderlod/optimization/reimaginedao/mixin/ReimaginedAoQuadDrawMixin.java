package com.pp.shaderlod.optimization.reimaginedao.mixin;
import com.pp.shaderlod.optimization.reimaginedao.ReimaginedAoRuntime;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pipeline.CompositeRenderer",remap=false)
public abstract class ReimaginedAoQuadDrawMixin {
 @Inject(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V",shift=At.Shift.BEFORE,remap=false),require=1,allow=1)private void shaderlod$before(CallbackInfo ci){ReimaginedAoRuntime.beforeDraw();}
 @Inject(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V",shift=At.Shift.AFTER,remap=false),require=1,allow=1)private void shaderlod$after(CallbackInfo ci){ReimaginedAoRuntime.afterDraw();}
}
