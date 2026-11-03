package com.pp.shaderlod.optimization.blissspatial.mixin;
import com.pp.shaderlod.optimization.blissspatial.BlissSpatialRuntime;import com.llamalad7.mixinextras.injector.wrapoperation.*;import net.irisshaders.iris.pathways.FullScreenQuadRenderer;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets="net.irisshaders.iris.pipeline.CompositeRenderer",remap=false)
public abstract class BlissSpatialQuadDrawMixin{
 @WrapOperation(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V"),require=1,allow=1)
 private void shaderlod$draw(FullScreenQuadRenderer renderer,Operation<Void> original){com.pp.shaderlod.optimization.blissspatial.BlissSpatialDrawScope.draw(BlissSpatialRuntime::beforeDraw,()->original.call(renderer),BlissSpatialRuntime::afterDraw,BlissSpatialRuntime::abortDraw,BlissSpatialRuntime::restoreDraw);}
}
