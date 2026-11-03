package com.pp.shaderlod.optimization.framecopy.rotation;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.*;

/** Conservative live witness. Allocates scratch once on prepare, queries once per targeted draw. */
final class HistoryRotationGl {
    record Coverage(boolean complete, boolean ownedBindings, String reason, int fbo, int program, int sampler,
                    int output1, int output3, int x, int y, int width, int height) {}
    private final IntBuffer integers = BufferUtils.createIntBuffer(16);
    private final ByteBuffer mask = BufferUtils.createByteBuffer(4);
    private final PointerBuffer pointer = BufferUtils.createPointerBuffer(1);
    private final GLCapabilities context = GL.getCapabilities();
    private final int drawBuffers = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
    private final int clipDistances = GL11.glGetInteger(GL30.GL_MAX_CLIP_DISTANCES);
    // Context limits/profile are immutable. beforeQuad rejects a different capabilities identity
    // before using these values; a replacement context requires a newly prepared session.
    private final boolean compatibilityProfile = context.OpenGL32
        && (GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK) & GL32.GL_CONTEXT_COMPATIBILITY_PROFILE_BIT) != 0;
    private final int sampleMaskWords = context.OpenGL32 || context.GL_ARB_texture_multisample
        ? GL11.glGetInteger(GL32.GL_MAX_SAMPLE_MASK_WORDS) : 0;

    Coverage beforeQuad(HistoryRotationPlan p, int expectedFbo, int read, int write) {
        int fbo = 0, program = 0, sampler = 0, out1 = 0, out3 = 0, x = 0, y = 0, w = 0, h = 0;
        String reason = "COMPLETE"; boolean owned = false;
        try {
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "PREEXISTING_GL_ERROR");
            require(GL.getCapabilities() == context && context.OpenGL30, "CONTEXT_UNKNOWN");
            fbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            require(fbo == expectedFbo && program == p.program, "BOUND_OWNER_MISMATCH");
            require(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "FBO_INCOMPLETE");
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, integers);
            x = integers.get(0); y = integers.get(1); w = integers.get(2); h = integers.get(3);
            out1 = attachment(0); out3 = attachment(1);
            int oldUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            try {
                // This seam follows native Program.use and its deferred ProgramSamplers.update.
                int unit = p.historySampler.latch(
                    GL20.glGetUniformi(p.program, p.historyLocation), p.maxTextureUnits,
                    expectedFbo == p.canonicalC2.getId() && read == p.a && write == p.b);
                RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
                sampler = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                require(!(context.OpenGL33 || context.GL_ARB_sampler_objects)
                        || GL11.glGetInteger(GL33.GL_SAMPLER_BINDING) == 0, "SAMPLER_OBJECT_OVERRIDE");
            } finally { RenderSystem.activeTexture(oldUnit); }
            require(out1 == p.rt1Write && out3 == write && sampler == read && read != write, "LIVE_ALIAS_MISMATCH");
            owned = true;
            require(GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) == p.quad.vertexArray()
                    && GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING) == p.quad.elementBuffer(), "QUAD_VAO_OR_INDEX_BINDING");
            for (HistoryRotationPlan.Attribute attribute : p.quad.attributes()) {
                int i = attribute.location();
                GL20.glGetVertexAttribPointerv(i, GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER, pointer);
                require(GL20.glGetVertexAttribi(i, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED) != 0
                    && GL20.glGetVertexAttribi(i, GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING) == attribute.buffer()
                    && GL20.glGetVertexAttribi(i, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE) == attribute.size()
                    && GL20.glGetVertexAttribi(i, GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE) == attribute.type()
                    && (GL20.glGetVertexAttribi(i, GL20.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED) != 0) == attribute.normalized()
                    && (GL20.glGetVertexAttribi(i, GL30.GL_VERTEX_ATTRIB_ARRAY_INTEGER) != 0) == attribute.integer()
                    && GL20.glGetVertexAttribi(i, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE) == attribute.stride()
                    && pointer.get(0) == attribute.pointer(), "QUAD_ATTRIBUTE_BINDING");
                if (context.OpenGL33 || context.GL_ARB_instanced_arrays)
                    require(GL20.glGetVertexAttribi(i, GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR) == attribute.divisor(), "QUAD_ATTRIBUTE_DIVISOR");
            }
            require(x == 0 && y == 0 && w == p.epoch.width() && h == p.epoch.height(), "VIEWPORT_PARTIAL");
            require(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), "SCISSOR_PARTIAL");
            if (context.OpenGL41 || context.GL_ARB_viewport_array)
                require(!GL30.glIsEnabledi(GL11.GL_SCISSOR_TEST, 0), "INDEXED_SCISSOR");
            require(!GL11.glIsEnabled(GL11.GL_DEPTH_TEST) && !GL11.glIsEnabled(GL11.GL_STENCIL_TEST), "DEPTH_STENCIL");
            require(!GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD) && !GL11.glIsEnabled(GL11.GL_CULL_FACE), "RASTERIZER_CULL");
            require(!GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP), "LOGIC_OP");
            require(!GL11.glIsEnabled(GL11.GL_POLYGON_SMOOTH), "POLYGON_SMOOTH");
            // Pinned native quad has indices 0..3. Fixed restart's all-ones sentinel cannot
            // intersect them; programmable restart is safe only outside that range.
            if (context.OpenGL31 && GL11.glIsEnabled(GL31.GL_PRIMITIVE_RESTART))
                require(Integer.compareUnsigned(GL11.glGetInteger(GL31.GL_PRIMITIVE_RESTART_INDEX), 3) > 0, "PRIMITIVE_RESTART");
            if (compatibilityProfile)
                require(!GL11.glIsEnabled(0x0B42) && !GL11.glIsEnabled(0x0BC0), "COMPAT_STIPPLE_OR_ALPHA_TEST");
            if (context.GL_EXT_raster_multisample) require(!GL11.glIsEnabled(0x9327), "RASTER_MULTISAMPLE");
            GL11.glGetIntegerv(GL11.GL_POLYGON_MODE, integers);
            require(integers.get(0) == GL11.GL_FILL && integers.get(1) == GL11.GL_FILL, "POLYGON_MODE");
            require(clipDistances >= 0 && clipDistances <= 32, "UNKNOWN_CLIP_COUNT");
            for (int i = 0; i < clipDistances; i++) require(!GL11.glIsEnabled(GL30.GL_CLIP_DISTANCE0 + i), "CLIP_DISTANCE");
            require(GL11.glGetInteger(GL13.GL_SAMPLES) == 0, "MULTISAMPLE_TARGET");
            require(!GL11.glIsEnabled(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE)
                    && !GL11.glIsEnabled(GL13.GL_SAMPLE_ALPHA_TO_ONE)
                    && !GL11.glIsEnabled(GL13.GL_SAMPLE_COVERAGE), "SAMPLE_COVERAGE");
            if (context.OpenGL32 || context.GL_ARB_texture_multisample) {
                require(!GL11.glIsEnabled(GL32.GL_SAMPLE_MASK), "SAMPLE_MASK");
                int words = sampleMaskWords;
                require(words >= 1 && words <= 32, "UNKNOWN_SAMPLE_MASK_WORDS");
                for (int i = 0; i < words; i++) require(GL30.glGetIntegeri(GL32.GL_SAMPLE_MASK_VALUE, i) == -1, "SAMPLE_MASK_VALUE");
            }
            if (context.OpenGL45 || context.GL_ARB_clip_control)
                require(GL11.glGetInteger(0x935C) == 0x8CA1 && GL11.glGetInteger(0x935D) == 0x935E, "CLIP_CONTROL");
            require(drawBuffers >= 2 && drawBuffers <= 32, "UNKNOWN_DRAW_BUFFER_COUNT");
            for (int slot = 0; slot < 2; slot++) {
                GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK, slot, mask);
                require(mask.get(0) != 0 && mask.get(1) != 0 && mask.get(2) != 0, "PARTIAL_RGB_MASK");
                // glIsEnabledi(GL_BLEND) is core 3.0; require both outputs independent of global state.
                require(!GL30.glIsEnabledi(GL11.GL_BLEND, slot), "INDEXED_BLEND");
            }
            for (int i = 2; i < drawBuffers; i++) require(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + i) == GL11.GL_NONE, "EXTRA_OUTPUT");
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "LIVE_QUERY_GL_ERROR");
        } catch (RuntimeException | LinkageError unknown) {
            reason = unknown.getMessage() == null ? "UNKNOWN_LIVE_STATE" : unknown.getMessage();
        }
        return new Coverage("COMPLETE".equals(reason), owned, reason, fbo, program, sampler, out1, out3, x, y, w, h);
    }
    private static int attachment(int slot) {
        int attachment = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + slot);
        require(attachment == GL30.GL_COLOR_ATTACHMENT0 + slot, "DRAW_SLOT");
        require(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) == GL11.GL_TEXTURE, "OUTPUT_NOT_TEXTURE");
        require(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL) == 0, "OUTPUT_NOT_LEVEL0");
        // Nonlayered 2D ownership is also established by prepare/root's complete alias inventory.
        return GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
    }

    /** Native copy primitive, cache-aware binds, no attachment edits; safe even after Program.use. */
    void copy(HistoryRotationPlan.Copy copy) {
        require(GL.getCapabilities() == context, "REPAIR_CONTEXT_CHANGED");
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "REPAIR_PREEXISTING_GL_ERROR");
        int oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            // Copy from a single-attachment prebuilt FBO. Its read slot was fixed at prepare.
            copy.from().bindAsReadBuffer(); RenderSystem.bindTexture(copy.destination());
            require(nativeEndpoints(copy), "REPAIR_ENDPOINT_MISMATCH");
            GlStateManager._glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, copy.width(), copy.height());
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "REPAIR_COPY_GL_ERROR");
        } finally {
            RenderSystem.activeTexture(oldUnit); RenderSystem.bindTexture(oldTexture);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDraw);
        }
    }
    boolean nativeEndpoints(HistoryRotationPlan.Copy copy) {
        return GL.getCapabilities() == context
            && GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) == copy.framebuffer()
            && GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == copy.destination()
            && GL11.glGetInteger(GL11.GL_READ_BUFFER) == GL30.GL_COLOR_ATTACHMENT0
            && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) == copy.source();
    }
    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException(reason);
    }
}
