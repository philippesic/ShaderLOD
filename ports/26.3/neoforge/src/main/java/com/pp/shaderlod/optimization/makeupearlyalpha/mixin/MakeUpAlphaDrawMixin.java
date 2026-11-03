package com.pp.shaderlod.optimization.makeupearlyalpha.mixin;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
import com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime;
@Mixin(targets="com.mojang.renderpearl.backend.opengl.GlCommandEncoder",remap=false)
public abstract class MakeUpAlphaDrawMixin {
 @Shadow private GlRenderPipeline lastPipeline;
 @WrapOperation(method="executeDraws(Lcom/mojang/renderpearl/backend/opengl/GlRenderPass;Lcom/mojang/renderpearl/api/pipeline/IndexType;Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)V",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL33C;nglMultiDrawElementsBaseVertex(IJIJIJ)V"),require=1,allow=1)
 private void shaderlod$makeupDraw(int mode,long count,int type,long indices,int draws,long base,Operation<Void> nativeCall){Throwable primary=null;try{MakeUpEarlyAlphaRuntime.draw(lastPipeline==null?null:lastPipeline.program());nativeCall.call(mode,count,type,indices,draws,base);}catch(RuntimeException|Error failure){primary=failure;throw failure;}finally{try{MakeUpEarlyAlphaRuntime.revoke();}catch(RuntimeException|Error cleanup){if(primary==null)throw cleanup;if(primary!=cleanup)primary.addSuppressed(cleanup);}}}
}
