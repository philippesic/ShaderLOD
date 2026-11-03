package com.pp.shaderlod.dev.bindingfix.mixin;
import com.pp.shaderlod.dev.bindingfix.BindingInputContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
/** Original encoder body, including trySetup and validateDraw, always executes unchanged. */
@Mixin(targets="com.mojang.blaze3d.opengl.GlCommandEncoder",remap=false)
public abstract class BindingInputDrawMixin {
    @WrapMethod(method="executeDraws(Lcom/mojang/blaze3d/opengl/GlRenderPass;Lcom/mojang/blaze3d/IndexType;Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)V")
    private void shaderlod$inputScope(@Coerce Object pass,com.mojang.blaze3d.IndexType type,org.lwjgl.PointerBuffer pointers,
        java.nio.IntBuffer counts,java.nio.IntBuffer bases,int count,Operation<Void> original) throws Exception {
        var prior=BindingInputContext.snapshot();
        try {
            BindingInputContext.enter(pass,"executeDraws");
            original.call(pass,type,pointers,counts,bases,count);
            BindingInputContext.completedDrawBody();
        } finally {BindingInputContext.restore(prior);}
    }

    @WrapMethod(method="executeDraw(Lcom/mojang/blaze3d/opengl/GlRenderPass;IIILcom/mojang/blaze3d/IndexType;II)V")
    private void shaderlod$singleScope(@Coerce Object pass,int baseVertex,int firstIndex,int indexCount,
        com.mojang.blaze3d.IndexType type,int firstInstance,int instanceCount,Operation<Void> original) throws Exception {
        var prior=BindingInputContext.snapshot();
        try {
            BindingInputContext.enter(pass,"executeDraw");
            original.call(pass,baseVertex,firstIndex,indexCount,type,firstInstance,instanceCount);
            BindingInputContext.completedDrawBody();
        } finally {BindingInputContext.restore(prior);}
    }
    @WrapMethod(method="executeDrawIndirect(Lcom/mojang/blaze3d/opengl/GlRenderPass;Lcom/mojang/blaze3d/IndexType;Lcom/mojang/blaze3d/opengl/GlBuffer;JI)V")
    private void shaderlod$indirectScope(@Coerce Object pass,com.mojang.blaze3d.IndexType type,
        @Coerce Object indirectBuffer,long offset,int count,Operation<Void> original) throws Exception {
        var prior=BindingInputContext.snapshot();
        try {
            BindingInputContext.enter(pass,"executeDrawIndirect");
            original.call(pass,type,indirectBuffer,offset,count);
            BindingInputContext.completedDrawBody();
        } finally {BindingInputContext.restore(prior);}
    }
    @WrapMethod(method="executeDrawMultiple(Lcom/mojang/blaze3d/opengl/GlRenderPass;Ljava/util/Collection;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;Ljava/util/Collection;Ljava/lang/Object;)V")
    private void shaderlod$collectionScope(@Coerce Object pass,java.util.Collection<?> draws,
        com.mojang.blaze3d.buffers.GpuBuffer defaultIndexBuffer,com.mojang.blaze3d.IndexType defaultIndexType,
        java.util.Collection<String> dynamicUniforms,Object uniformArgument,Operation<Void> original) throws Exception {
        var prior=BindingInputContext.snapshot();
        try {
            BindingInputContext.enter(pass,"executeDrawMultiple");
            original.call(pass,draws,defaultIndexBuffer,defaultIndexType,dynamicUniforms,uniformArgument);
            BindingInputContext.completedDrawBody();
        } finally {BindingInputContext.restore(prior);}
    }
}
