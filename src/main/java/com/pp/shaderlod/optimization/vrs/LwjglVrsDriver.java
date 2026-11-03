package com.pp.shaderlod.optimization.vrs;

import java.nio.ByteBuffer;
import java.util.Map;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

/** No NV entry point is called by capability discovery or on unsupported contexts. */
public final class LwjglVrsDriver implements NvBloomVrsBackend.Driver {
    private GLCapabilities observed;
    private VrsCapabilities capabilities;
    private boolean supported;
    private long nvCalls, allocations;
    @Override public long context() { return GLFW.glfwGetCurrentContext(); }
    @Override public VrsCapabilities capabilities() {
        var c = GL.getCapabilities();
        if (observed != c) {
            observed = c;
            capabilities = new VrsCapabilities(c.OpenGL45,c.GL_NV_shading_rate_image,c.GL_NV_framebuffer_mixed_samples,
                    Map.of("glBindShadingRateImageNV",c.glBindShadingRateImageNV != 0,
                            "glShadingRateImagePaletteNV",c.glShadingRateImagePaletteNV != 0,
                            "glGetShadingRateImagePaletteNV",c.glGetShadingRateImagePaletteNV != 0,
                            "glShadingRateImageBarrierNV",c.glShadingRateImageBarrierNV != 0,
                            "glCreateTextures",c.glCreateTextures != 0,
                            "glTextureStorage2D",c.glTextureStorage2D != 0,
                            "glClearTexImage",c.glClearTexImage != 0,
                            "glGetTextureLevelParameteriv",c.glGetTextureLevelParameteriv != 0));
            supported = capabilities.supported();
        }
        return capabilities;
    }
    private boolean supported() {
        // Refresh on capabilities identity change before using the immutable verdict.
        capabilities();
        return supported;
    }
    private void require() {
        if (!supported()) throw new IllegalStateException("Unsupported NV VRS context");
        nvCalls++;
    }
    @Override public int tileWidth() { require(); return GL11.glGetInteger(NVShadingRateImage.GL_SHADING_RATE_IMAGE_TEXEL_WIDTH_NV); }
    @Override public int tileHeight() { require(); return GL11.glGetInteger(NVShadingRateImage.GL_SHADING_RATE_IMAGE_TEXEL_HEIGHT_NV); }
    @Override public int maxCoarseSamples() { require(); return GL11.glGetInteger(NVShadingRateImage.GL_MAX_COARSE_FRAGMENT_SAMPLES_NV); }
    @Override public int createZeroRateImage(int width,int height) {
        if (!supported()) throw new IllegalStateException("Unsupported NV VRS context");
        int id = GL45.glCreateTextures(GL11.GL_TEXTURE_2D);
        try {
            GL45.glTextureStorage2D(id,1,GL30.GL_R8UI,width,height);
            // NULL means zero clear values; no client pixel buffer or pixel-store mutation.
            GL44.glClearTexImage(id,0,GL30.GL_RED_INTEGER,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
            if (GL45.glGetTextureLevelParameteri(id,0,GL11.GL_TEXTURE_WIDTH) != width
                    || GL45.glGetTextureLevelParameteri(id,0,GL11.GL_TEXTURE_HEIGHT) != height
                    || GL45.glGetTextureLevelParameteri(id,0,GL11.GL_TEXTURE_INTERNAL_FORMAT) != GL30.GL_R8UI)
                throw new IllegalStateException("Immutable R8UI rate image storage readback failed");
            allocations++; return id;
        } catch (RuntimeException | LinkageError failed) { GL11.glDeleteTextures(id); throw failed; }
    }
    @Override public void deleteImage(int texture) { GL11.glDeleteTextures(texture); }
    @Override public void imageBarrier() { require(); NVShadingRateImage.glShadingRateImageBarrierNV(true); }
    @Override public int boundImage() { require(); return GL11.glGetInteger(NVShadingRateImage.GL_SHADING_RATE_IMAGE_BINDING_NV); }
    @Override public int paletteZero() {
        require();
        try (var stack = MemoryStack.stackPush()) {
            var out = stack.mallocInt(1);
            NVShadingRateImage.glGetShadingRateImagePaletteNV(0,0,out); return out.get(0);
        }
    }
    @Override public boolean enabledZero() { require(); return GL30.glIsEnabledi(NVShadingRateImage.GL_SHADING_RATE_IMAGE_NV,0); }
    @Override public void bindImage(int texture) { require(); NVShadingRateImage.glBindShadingRateImageNV(texture); }
    @Override public void paletteZero(int rate) {
        require();
        try (var stack = MemoryStack.stackPush()) { NVShadingRateImage.glShadingRateImagePaletteNV(0,0,stack.ints(rate)); }
    }
    @Override public void enabledZero(boolean enabled) {
        require();
        if (enabled) GL30.glEnablei(NVShadingRateImage.GL_SHADING_RATE_IMAGE_NV,0);
        else GL30.glDisablei(NVShadingRateImage.GL_SHADING_RATE_IMAGE_NV,0);
    }
    public Map<String,Object> counters() { return Map.of("nv_calls",nvCalls,"image_allocations",allocations); }
}
