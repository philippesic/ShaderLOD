package com.pp.shaderlod.optimization.sildurao.mixin;
import com.pp.shaderlod.optimization.sildurao.SildurAoRuntime;
import com.pp.shaderlod.optimization.sildurspatial.SildurSpatialRuntime;
import com.llamalad7.mixinextras.injector.wrapoperation.*;import net.irisshaders.iris.pathways.FullScreenQuadRenderer;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets="net.irisshaders.iris.pipeline.CompositeRenderer",remap=false)
public abstract class SildurAoQuadDrawMixin {
 @WrapOperation(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V"),require=1,allow=1)
 private void shaderlod$draw(FullScreenQuadRenderer renderer,Operation<Void> original){com.pp.shaderlod.optimization.sildurreceiver.SildurReceiverRuntime.currentQuad(()->com.pp.shaderlod.optimization.blissspatial.BlissSpatialDrawScope.draw(()->{SildurAoRuntime.beforeDraw();SildurSpatialRuntime.beforeDraw();com.pp.shaderlod.optimization.sildurworld.SildurWorldRuntime.beforeDraw();},()->original.call(renderer),()->{SildurAoRuntime.afterDraw();SildurSpatialRuntime.afterDraw();com.pp.shaderlod.optimization.sildurworld.SildurWorldRuntime.afterDraw();},()->{SildurSpatialRuntime.abortDraw();com.pp.shaderlod.optimization.sildurworld.SildurWorldRuntime.abort();},()->{try{SildurSpatialRuntime.restoreDraw();}finally{com.pp.shaderlod.optimization.sildurworld.SildurWorldRuntime.restore();}}));}
}
