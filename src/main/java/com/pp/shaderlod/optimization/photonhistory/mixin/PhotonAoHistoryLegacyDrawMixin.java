package com.pp.shaderlod.optimization.photonhistory.mixin;
import com.pp.shaderlod.optimization.photonhistory.PhotonAoHistoryRuntime;import com.llamalad7.mixinextras.injector.wrapoperation.*;import net.irisshaders.iris.pipeline.CompositeRenderer;import net.irisshaders.iris.pathways.FullScreenQuadRenderer;import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=CompositeRenderer.class,remap=false)public abstract class PhotonAoHistoryLegacyDrawMixin{
 @WrapOperation(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V"),require=1)private void draw(FullScreenQuadRenderer r,Operation<Void> original){PhotonAoHistoryRuntime.currentQuad(()->original.call(r));}}
