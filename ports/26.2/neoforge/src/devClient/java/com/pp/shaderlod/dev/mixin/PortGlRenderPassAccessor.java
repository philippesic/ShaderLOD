package com.pp.shaderlod.dev.mixin;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Readonly dev access to the actual allocated array; never writes pipeline, slots or policy. */
@Mixin(targets="com.mojang.blaze3d.opengl.GlRenderPass",remap=false)
public interface PortGlRenderPassAccessor {
    @Accessor(value="vertexBuffers",remap=false) GpuBufferSlice[] shaderlod$vertexBuffers();
}
