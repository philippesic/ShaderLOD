package com.pp.shaderlod.dev.mixin;
import com.pp.shaderlod.dev.PortRendererFrames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Observe actual synchronous GL function entry, separately from helper/API return. */
@Mixin(targets="com.mojang.blaze3d.opengl.GlCommandEncoder",remap=false)
public abstract class PortRendererGlConsumerMixin {
    @Inject(method="executeDraws(Lcom/mojang/blaze3d/opengl/GlRenderPass;Lcom/mojang/blaze3d/IndexType;Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/opengl/GlCommandEncoder;validateDraw(Lcom/mojang/blaze3d/opengl/GlRenderPass;Lcom/mojang/blaze3d/IndexType;)V"),require=1,allow=1,remap=false)
    private void shaderlod$layout(@Coerce Object pass,com.mojang.blaze3d.IndexType type,org.lwjgl.PointerBuffer pointers,
            java.nio.IntBuffer counts,java.nio.IntBuffer bases,int count,CallbackInfo ci) {
        com.pp.shaderlod.dev.PortVertexLayoutObservation.observe(pass);
    }
    @Inject(method="executeDraws(Lcom/mojang/blaze3d/opengl/GlRenderPass;Lcom/mojang/blaze3d/IndexType;Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)V",at=@At("HEAD"),require=1,remap=false)
    private void shaderlod$begin(CallbackInfo ci) { PortRendererFrames.beginGlSubmission(); }
    @Inject(method="executeDraws(Lcom/mojang/blaze3d/opengl/GlRenderPass;Lcom/mojang/blaze3d/IndexType;Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)V",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL33C;nglMultiDrawElementsBaseVertex(IJIJIJ)V"),require=1,allow=1,remap=false)
    private void shaderlod$actualGl(CallbackInfo ci) {
        PortRendererFrames.glFunctionEntry();
        com.pp.shaderlod.dev.bindingfix.BindingInputContext.glFunctionEntry();
    }
    @Inject(method="executeDraws(Lcom/mojang/blaze3d/opengl/GlRenderPass;Lcom/mojang/blaze3d/IndexType;Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)V",at=@At("RETURN"),require=1,remap=false)
    private void shaderlod$end(CallbackInfo ci) { PortRendererFrames.endGlSubmission(); }
}
