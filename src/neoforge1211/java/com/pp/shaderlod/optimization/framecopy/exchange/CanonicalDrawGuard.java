package com.pp.shaderlod.optimization.framecopy.exchange;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.*;

/** Canonical exchange witness, derived from the retained conservative raster guard.
 * Exact payload is read immediately before terminal GL submission; all query/readback costs stay enabled. */
final class CanonicalDrawGuard {
    record Expected(int program, int historyLocation, int rt1Write, int width, int height) {}
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

    Coverage beforeQuad(Expected p, int expectedFbo, int read, int write, int mode, int count, int type, long offset) {
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
                int unit = GL20.glGetUniformi(p.program, p.historyLocation);
                require(unit >= 0 && unit < GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS), "SAMPLER_UNIT");
                RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
                sampler = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                require(!(context.OpenGL33 || context.GL_ARB_sampler_objects)
                        || GL11.glGetInteger(GL33.GL_SAMPLER_BINDING) == 0, "SAMPLER_OBJECT_OVERRIDE");
            } finally { RenderSystem.activeTexture(oldUnit); }
            require(out1 == p.rt1Write && out3 == write && sampler == read && read != write, "LIVE_ALIAS_MISMATCH");
            owned = true;
            payload(p.program, mode, count, type, offset);
            sampling(read, p.width, p.height);
            // FBO completeness allows unequal attachment sizes; effective raster coverage
            // is their intersection. Query BOTH actual output allocations, not cached sizes.
            outputStorage(out3,p.width,p.height,true);
            outputStorage(out1,p.width,p.height,false);
            require(x == 0 && y == 0 && w == p.width && h == p.height, "VIEWPORT_PARTIAL");
            require(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), "SCISSOR_PARTIAL");
            if (context.OpenGL41 || context.GL_ARB_viewport_array)
                require(!GL30.glIsEnabledi(GL11.GL_SCISSOR_TEST, 0), "INDEXED_SCISSOR");
            require(!GL11.glIsEnabled(GL11.GL_DEPTH_TEST) && !GL11.glIsEnabled(GL11.GL_STENCIL_TEST), "DEPTH_STENCIL");
            require(!GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD), "RASTERIZER_DISCARD");
            // Exact payload plus pinned positive-scale vertex shader yields two CCW triangles.
            // Native Iris keeps BACK culling enabled; this removes neither triangle.
            require(!GL11.glIsEnabled(GL11.GL_CULL_FACE)
                || GL11.glGetInteger(GL11.GL_CULL_FACE_MODE)==GL11.GL_BACK
                    &&GL11.glGetInteger(GL11.GL_FRONT_FACE)==GL11.GL_CCW,"CULL_COVERS_NO_FRONT_TRIANGLE");
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

    /** Exact live content checks, not a handle/layout immutability attestation. */
    private void payload(int program, int mode, int count, int type, long offset) {
        require(mode == GL11.GL_TRIANGLES && count == 6 && offset == 0, "DRAW_CALL");
        require(GL20.glGetProgrami(program, GL20.GL_ACTIVE_ATTRIBUTES) == 2, "ACTIVE_ATTRIBUTES");
        int vbo = 0;
        for (int i=0;i<2;i++) {
            int location=GL20.glGetAttribLocation(program,i==0?"Position":"UV0");
            require(location>=0,"ATTRIBUTE_NAME");
            GL20.glGetVertexAttribPointerv(location,GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER,pointer);
            int buffer=GL20.glGetVertexAttribi(location,GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
            // Separate vertex binding/DSA calls need not update legacy stride/pointer state.
            // Witness the effective fetch too, immediately before the terminal draw.
            if(context.OpenGL43 || context.GL_ARB_vertex_attrib_binding) {
                int binding=GL20.glGetVertexAttribi(location,GL43.GL_VERTEX_ATTRIB_BINDING);
                require(binding>=0 && binding<GL11.glGetInteger(GL43.GL_MAX_VERTEX_ATTRIB_BINDINGS),"ATTRIBUTE_BINDING_INDEX");
                int relative=GL20.glGetVertexAttribi(location,GL43.GL_VERTEX_ATTRIB_RELATIVE_OFFSET);
                long base=GL32.glGetInteger64i(GL43.GL_VERTEX_BINDING_OFFSET,binding);
                require(GL30.glGetIntegeri(GL43.GL_VERTEX_BINDING_BUFFER,binding)==buffer
                    &&GL30.glGetIntegeri(GL43.GL_VERTEX_BINDING_STRIDE,binding)==20
                    &&GL30.glGetIntegeri(GL43.GL_VERTEX_BINDING_DIVISOR,binding)==0
                    &&relative>=0&&base>=0&&base<=80&&base+relative==(i==0?0:12),"EFFECTIVE_ATTRIBUTE_BINDING");
            }
            require(buffer>0 && (vbo==0 || buffer==vbo),"ATTRIBUTE_BUFFER"); vbo=buffer;
            require(GL20.glGetVertexAttribi(location,GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED)!=0
                &&GL20.glGetVertexAttribi(location,GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE)==(i==0?3:2)
                &&GL20.glGetVertexAttribi(location,GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE)==GL11.GL_FLOAT
                &&GL20.glGetVertexAttribi(location,GL20.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED)==0
                &&GL20.glGetVertexAttribi(location,GL30.GL_VERTEX_ATTRIB_ARRAY_INTEGER)==0
                &&GL20.glGetVertexAttribi(location,GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE)==20
                &&GL20.glGetVertexAttribi(location,GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR)==0
                &&pointer.get(0)==(i==0?0:12),"ATTRIBUTE_LAYOUT_OR_INDEXED_SETTER");
        }
        int ebo=GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        require(ebo>0,"ELEMENT_BUFFER");
        int old=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);
        try {
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,vbo);
            require(GL15.glGetBufferParameteri(GL31.GL_COPY_READ_BUFFER,GL15.GL_BUFFER_SIZE)==80
                &&GL15.glGetBufferParameteri(GL31.GL_COPY_READ_BUFFER,GL15.GL_BUFFER_MAPPED)==0,"VBO_ALLOCATION_OR_MAPPING");
            vertexBytes.clear();GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,0,vertexBytes);
            for(int i=0;i<VERTICES.length;i++)require(vertexBytes.getInt(i*4)==Float.floatToRawIntBits(VERTICES[i]),"VBO_CONTENTS");
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,ebo);
            int bytes=type==GL11.GL_UNSIGNED_BYTE?1:type==GL11.GL_UNSIGNED_SHORT?2:type==GL11.GL_UNSIGNED_INT?4:0;
            require(bytes>0 && GL15.glGetBufferParameteri(GL31.GL_COPY_READ_BUFFER,GL15.GL_BUFFER_SIZE)>=6*bytes
                &&GL15.glGetBufferParameteri(GL31.GL_COPY_READ_BUFFER,GL15.GL_BUFFER_MAPPED)==0,"EBO_ALLOCATION_OR_MAPPING");
            indexBytes.clear();indexBytes.limit(6*bytes);GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,0,indexBytes);
            for(int i=0;i<6;i++) {
                int value=bytes==1?Byte.toUnsignedInt(indexBytes.get(i)):bytes==2?Short.toUnsignedInt(indexBytes.getShort(i*2)):indexBytes.getInt(i*4);
                require(value==INDICES[i],"EBO_CONTENTS");
            }
        } finally { GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,old); }
    }
    private static void outputStorage(int texture,int width,int height,boolean history) {
        int prior=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            // Raw bind/restore leaves native and Iris binding caches exactly as found.
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            require(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)==width
                &&GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)==height,"OUTPUT_STORAGE_EXTENT");
            if(history)require(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT)==GL30.GL_R11F_G11F_B10F,"OUTPUT_STORAGE_FORMAT");
        } finally {GL11.glBindTexture(GL11.GL_TEXTURE_2D,prior);}
    }
    private void sampling(int expected,int width,int height) {
        int oldUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            // Program.use has initialized this one real history sampler. Inspect it without mutation.
            int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),location=GL20.glGetUniformLocation(program,"colortex3");
            require(location>=0,"HISTORY_LOCATION");RenderSystem.activeTexture(GL13.GL_TEXTURE0+GL20.glGetUniformi(program,location));
            require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==expected,"HISTORY_BINDING");
            require(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)==width
                &&GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)==height
                &&GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT)==GL30.GL_R11F_G11F_B10F,"HISTORY_STORAGE");
            require(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL)==0
                &&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER)==GL11.GL_LINEAR
                &&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER)==GL11.GL_LINEAR
                &&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S)==GL12.GL_CLAMP_TO_EDGE
                &&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T)==GL12.GL_CLAMP_TO_EDGE
                &&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL14.GL_TEXTURE_COMPARE_MODE)==GL11.GL_NONE
                &&GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MIN_LOD)<=0
                &&GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LOD)>=0,"HISTORY_SAMPLING");
            int[] swizzle={GL11.GL_RED,GL11.GL_GREEN,GL11.GL_BLUE,GL11.GL_ALPHA};
            for(int i=0;i<4;i++)require(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_R+i)==swizzle[i],"HISTORY_SWIZZLE");
        } finally { RenderSystem.activeTexture(oldUnit); }
    }
    private static final float[] VERTICES={0,0,0,0,0, 1,0,0,1,0, 1,1,0,1,1, 0,1,0,0,1};
    private static final int[] INDICES={0,1,2,2,3,0};
    private final ByteBuffer vertexBytes=BufferUtils.createByteBuffer(80),indexBytes=BufferUtils.createByteBuffer(24);
    static void require(boolean condition,String reason) {if(!condition)throw new IllegalStateException(reason);}
}
