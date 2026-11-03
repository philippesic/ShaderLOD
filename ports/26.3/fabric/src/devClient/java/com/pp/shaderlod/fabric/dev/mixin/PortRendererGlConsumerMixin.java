package com.pp.shaderlod.fabric.dev.mixin;
import com.pp.shaderlod.fabric.dev.PortRendererFrames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Observe actual synchronous GL function entry, separately from helper/API return. */
@Mixin(targets="com.mojang.renderpearl.backend.opengl.GlCommandEncoder",remap=false)
public abstract class PortRendererGlConsumerMixin {
    @Inject(method="executeDraws(Lcom/mojang/renderpearl/backend/opengl/GlRenderPass;Lcom/mojang/renderpearl/api/pipeline/IndexType;Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)V",at=@At("HEAD"),require=1,remap=false)
    private void shaderlod$begin(CallbackInfo ci) { PortRendererFrames.beginGlSubmission(); }
    @Inject(method="executeDraws(Lcom/mojang/renderpearl/backend/opengl/GlRenderPass;Lcom/mojang/renderpearl/api/pipeline/IndexType;Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)V",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL33C;nglMultiDrawElementsBaseVertex(IJIJIJ)V"),require=1,allow=1,remap=false)
    private void shaderlod$actualGl(CallbackInfo ci) { PortRendererFrames.glFunctionEntry(); }
    @Inject(method="executeDraws(Lcom/mojang/renderpearl/backend/opengl/GlRenderPass;Lcom/mojang/renderpearl/api/pipeline/IndexType;Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)V",at=@At("RETURN"),require=1,remap=false)
    private void shaderlod$end(CallbackInfo ci) { PortRendererFrames.endGlSubmission(); }
}
