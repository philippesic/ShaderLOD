package com.pp.shaderlod.optimization.makeupmerged;

import net.caffeinemc.mods.sodium.client.gl.buffer.*;
import net.caffeinemc.mods.sodium.client.gl.device.*;
import net.caffeinemc.mods.sodium.client.gl.tessellation.*;
import net.caffeinemc.mods.sodium.client.gl.attribute.GlVertexFormat;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/** Empty retained storage objects only: all native bytes/commands/table rebuilt each pass. */
public final class MergedBuffers {
    private GlMutableBuffer geometry, offsets;
    private GlTessellation tessellation;
    private MultiDrawBatch commands;
    private int texture;
    public boolean prepare(CommandList c, MergePlan plan, List<GlMutableBuffer> sources, GlVertexFormat format, GlBuffer indices) {
        if (!GL.getCapabilities().OpenGL41 || GL11C.glGetError()!=GL11C.GL_NO_ERROR)return false;
        if (sources.size() != plan.spans.size() || plan.commands <= 0 || plan.pages() <= 0
                || plan.pages() > GL11C.glGetInteger(GL31C.GL_MAX_TEXTURE_BUFFER_SIZE)) return false;
        if (geometry == null) geometry = c.createMutableBuffer();
        if (offsets == null) offsets = c.createMutableBuffer();
        if (texture == 0) texture = GL11C.glGenTextures();
        // Orphan every pass. No synchronization assumption or cross-frame source reuse.
        c.allocateStorage(geometry, (long) plan.vertices * plan.stride, GlBufferUsage.STREAM_COPY);
        for (int i = 0; i < sources.size(); i++) {
            var span = plan.spans.get(i); var source = sources.get(i);
            if (source.getSize() < (long) span.sourceEnd() * plan.stride || source.getActiveMapping() != null) return false;
            c.copyBufferSubData(source, geometry, (long) span.sourceStart() * plan.stride,
                    (long) span.destinationStart() * plan.stride, (long) (span.sourceEnd() - span.sourceStart()) * plan.stride);
        }
        int[] words = plan.pageWords(); ByteBuffer bytes = MemoryUtil.memAlloc(words.length * 4).order(ByteOrder.nativeOrder());
        try { for (int word : words) bytes.putInt(word); bytes.flip(); c.uploadData(offsets, bytes, GlBufferUsage.STREAM_DRAW); }
        finally { MemoryUtil.memFree(bytes); }
        if (commands == null || commands.capacity() < plan.commands) {
            if (commands != null) commands.delete(); commands = new MultiDrawBatch(plan.commands);
        }
        commands.clear();
        for (var span : plan.spans) for (int i = 0; i < span.counts().length; i++) {
            long n = commands.size++;
            MemoryUtil.memPutInt(commands.pElementCount + n * 4, span.counts()[i]);
            MemoryUtil.memPutInt(commands.pBaseVertex + n * 4, span.bases()[i]);
            MemoryUtil.memPutAddress(commands.pElementPointer + (n << Pointer.POINTER_SHIFT), 0);
        }
        if (tessellation == null) tessellation = c.createTessellation(GlPrimitiveType.TRIANGLES,
                new TessellationBinding[]{TessellationBinding.forVertexBuffer(geometry, format.getShaderBindings()),
                        TessellationBinding.forElementBuffer(indices)});
        return GL11C.glGetError() == GL11C.GL_NO_ERROR;
    }
    public void bind(MergeUniforms uniforms, MergePlan plan) {
        uniforms.enable(texture, offsets.handle(), plan.pages());
    }
    public void draw(CommandList c, MergeUniforms uniforms) {
        try (DrawCommandList draw = c.beginTessellating(tessellation)) {
            draw.multiDrawElementsBaseVertex(commands, GlIndexType.UNSIGNED_INT);
        } finally { uniforms.disable(); }
    }
    public void delete(CommandList c) {
        if (tessellation != null) { c.deleteTessellation(tessellation); tessellation = null; }
        if (geometry != null) { c.deleteBuffer(geometry); geometry = null; }
        if (offsets != null) { c.deleteBuffer(offsets); offsets = null; }
        if (commands != null) { commands.delete(); commands = null; }
        if (texture != 0) { GL11C.glDeleteTextures(texture); texture = 0; }
    }
}
