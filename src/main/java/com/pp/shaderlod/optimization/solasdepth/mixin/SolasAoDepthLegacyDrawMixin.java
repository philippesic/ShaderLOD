package com.pp.shaderlod.optimization.solasdepth.mixin;
import com.pp.shaderlod.optimization.solasdepth.SolasAoDepthRuntime;import com.llamalad7.mixinextras.injector.wrapoperation.*;import net.irisshaders.iris.pipeline.CompositeRenderer;import net.irisshaders.iris.pathways.FullScreenQuadRenderer;import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=CompositeRenderer.class,remap=false)public abstract class SolasAoDepthLegacyDrawMixin{
 @WrapOperation(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V"),require=1)private void draw(FullScreenQuadRenderer r,Operation<Void> original){SolasAoDepthRuntime.currentQuad(()->original.call(r));}}
