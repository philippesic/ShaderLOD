package com.pp.shaderlod.integration.bindingcompat.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="com.mojang.blaze3d.opengl.GlCommandEncoder",remap=false)
public abstract class BindingGlEntryMixin {
 @Inject(method="executeDraws(Lcom/mojang/blaze3d/opengl/GlRenderPass;Lcom/mojang/blaze3d/IndexType;Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)V",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL33C;nglMultiDrawElementsBaseVertex(IJIJIJ)V"),require=1,allow=1,remap=false)
 private void shaderlod$mainGlEntry(CallbackInfo ci){com.pp.shaderlod.integration.bindingcompat.MakeUpBindingCompatibility.glFunctionEntry();}
}
