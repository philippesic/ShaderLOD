package com.pp.shaderlod.optimization.photonhistory.mixin;
import com.pp.shaderlod.optimization.photonhistory.PhotonAoHistoryRuntime;import com.llamalad7.mixinextras.injector.wrapoperation.*;import net.irisshaders.iris.pipeline.CompositeRenderer;import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=CompositeRenderer.class,remap=false)public abstract class PhotonAoHistoryDirectDrawMixin{
 @WrapOperation(method="renderAll()V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/opengl/GlStateManager;_drawElements(IIIJ)V"),require=1)private void draw(int mode,int count,int type,long offset,Operation<Void> original){PhotonAoHistoryRuntime.currentQuad(()->original.call(mode,count,type,offset));}}
