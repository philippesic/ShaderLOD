package com.pp.shaderlod.integration.bindingcompat.mixin;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Readonly tuple-specific main access to the actual allocated array; never writes pipeline, slots or policy. */
@Mixin(targets="com.mojang.blaze3d.opengl.GlRenderPass",remap=false)
public interface BindingGlPassAccessor {
    @Accessor(value="vertexBuffers",remap=false) GpuBufferSlice[] shaderlod$vertexBuffers();
}
