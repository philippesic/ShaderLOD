package com.pp.shaderlod.optimization.reimaginedearlyalpha.mixin;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.opengl.GlProgram;
import com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaRuntime;
@Mixin(targets="com.mojang.blaze3d.opengl.GlCommandEncoder",remap=false)
public abstract class ReimaginedAlphaDrawMixin {
 @Shadow private GlProgram lastProgram;
 @WrapOperation(method="executeDraws(Lcom/mojang/blaze3d/opengl/GlRenderPass;Lcom/mojang/blaze3d/IndexType;Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)V",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL33C;nglMultiDrawElementsBaseVertex(IJIJIJ)V"),require=1,allow=1)
 private void shaderlod$reimaginedDraw(int mode,long count,int type,long indices,int draws,long base,Operation<Void> nativeCall){Throwable primary=null;try{ReimaginedEarlyAlphaRuntime.draw(lastProgram);nativeCall.call(mode,count,type,indices,draws,base);}catch(RuntimeException|Error failure){primary=failure;throw failure;}finally{try{ReimaginedEarlyAlphaRuntime.revoke();}catch(RuntimeException|Error cleanup){if(primary==null)throw cleanup;if(primary!=cleanup)primary.addSuppressed(cleanup);}}}
}
