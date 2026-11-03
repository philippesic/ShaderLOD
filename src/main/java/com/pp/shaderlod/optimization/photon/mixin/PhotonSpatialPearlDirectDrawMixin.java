package com.pp.shaderlod.optimization.photon.mixin;
import com.pp.shaderlod.optimization.photon.PhotonSpatialNormal;import com.llamalad7.mixinextras.injector.wrapoperation.*;import net.irisshaders.iris.pipeline.CompositeRenderer;import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=CompositeRenderer.class,remap=false)public abstract class PhotonSpatialPearlDirectDrawMixin{
 @WrapOperation(method="renderAll()V",at=@At(value="INVOKE",target="Lcom/mojang/renderpearl/backend/opengl/GlStateManager;_drawElements(IIIJ)V"),require=1)private void draw(int mode,int count,int type,long offset,Operation<Void> original){PhotonSpatialNormal.currentQuad(()->original.call(mode,count,type,offset));}}
