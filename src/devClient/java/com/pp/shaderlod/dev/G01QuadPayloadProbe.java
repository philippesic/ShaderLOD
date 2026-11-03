package com.pp.shaderlod.dev;

import com.google.gson.*;
import java.nio.*;
import java.util.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

/** One untimed copies-retained batch. No changed payload is ever submitted. */
public final class G01QuadPayloadProbe {
    private static boolean used, armed, scope;
    private static JsonObject result = new JsonObject();
    private G01QuadPayloadProbe() {}
    public static JsonObject request(boolean arm) {
        if (!Boolean.getBoolean("shaderlod.dev.g01ExposureProbe")) throw new IllegalStateException("Untimed G01 launch required");
        if (arm) { if (used || armed) throw new IllegalStateException("One batch per process only"); armed = true; }
        JsonObject out = result.deepCopy(); out.addProperty("armed", armed); out.addProperty("used", used); return out;
    }
    public static void before(String pass) { scope = armed && "composite3".equals(pass); }
    public static void after() { scope = false; }
    public static void draw(int mode, int count, int type, long offset) {
        if (!scope || !armed) return;
        used = true; armed = false; result = new JsonObject();
        int prior = GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER), buffer = 0;
        ByteBuffer original = null;
        try (var stack = MemoryStack.stackPush()) {
            if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new IllegalStateException("Preexisting GL error");
            result.addProperty("program", GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
            result.addProperty("vao", GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));
            int ebo = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
            result.addProperty("ebo", ebo); result.addProperty("mode", mode); result.addProperty("count", count); result.addProperty("type", type); result.addProperty("offset", offset);
            if (mode != GL11.GL_TRIANGLES || count != 6 || offset != 0) throw new IllegalStateException("Noncanonical draw");
            JsonArray attrs = new JsonArray(); int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int active = GL20.glGetProgrami(program, GL20.GL_ACTIVE_ATTRIBUTES);
            if (active != 2) throw new IllegalStateException("Noncanonical active attributes");
            for (int i = 0; i < active; i++) {
                String name = GL20.glGetActiveAttrib(program, i, stack.mallocInt(1), stack.mallocInt(1));
                int loc = GL20.glGetAttribLocation(program, name), vbo = GL20.glGetVertexAttribi(loc, GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
                var ptr = stack.mallocPointer(1); GL20.glGetVertexAttribPointerv(loc, GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER, ptr);
                JsonObject a = new JsonObject(); a.addProperty("name", name); a.addProperty("location", loc); a.addProperty("buffer", vbo); a.addProperty("pointer", ptr.get(0)); attrs.add(a);
                if (buffer == 0) buffer = vbo;
                if (vbo != buffer || GL20.glGetVertexAttribi(loc, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE) != 20
                    || GL20.glGetVertexAttribi(loc, GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE) != GL11.GL_FLOAT
                    || GL20.glGetVertexAttribi(loc, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED) == 0
                    || GL20.glGetVertexAttribi(loc, GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR) != 0)
                    throw new IllegalStateException("Noncanonical POSITION_TEX layout");
            }
            result.add("attributes", attrs);
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, buffer);
            if (GL15.glGetBufferParameteri(GL31.GL_COPY_READ_BUFFER, GL15.GL_BUFFER_SIZE) != 80) throw new IllegalStateException("Noncanonical VBO size");
            original = org.lwjgl.BufferUtils.createByteBuffer(80); GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER, 0, original);
            float[] expected = {0,0,0,0,0, 1,0,0,1,0, 1,1,0,1,1, 0,1,0,0,1};
            JsonArray floats = new JsonArray(); boolean canonical = true;
            for (int i = 0; i < expected.length; i++) { float f = original.getFloat(i * 4); floats.add(f); canonical &= Float.floatToRawIntBits(f) == Float.floatToRawIntBits(expected[i]); }
            result.add("native_vbo_floats", floats); result.addProperty("canonical_before", canonical);
            if (!canonical) throw new IllegalStateException("Actual payload differs from pinned quad");
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, ebo); int bytes = type == GL11.GL_UNSIGNED_BYTE ? 1 : type == GL11.GL_UNSIGNED_SHORT ? 2 : type == GL11.GL_UNSIGNED_INT ? 4 : 0;
            if (bytes == 0) throw new IllegalStateException("Noncanonical index type");
            ByteBuffer indices = stack.malloc(6 * bytes); GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER, 0, indices); JsonArray ids = new JsonArray();
            for (int i=0;i<6;i++) ids.add(bytes==1 ? Byte.toUnsignedInt(indices.get(i)) : bytes==2 ? Short.toUnsignedInt(indices.getShort(i*2)) : indices.getInt(i*4));
            result.add("native_indices", ids);
            // Collapse X of all four vertices. Bindings/layout/direct or indexed setter state stays identical.
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, buffer); ByteBuffer collapsed = stack.malloc(80); collapsed.put(0, original, 0, 80);
            for (int i=0;i<4;i++) collapsed.putFloat(i*20, 0f);
            GL15.glBufferSubData(GL31.GL_COPY_READ_BUFFER, 0, collapsed);
            ByteBuffer read = stack.malloc(80); GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER, 0, read);
            result.addProperty("mutated_readback_exact", collapsed.equals(read));
            result.addProperty("vao_ebo_unchanged", GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)==result.get("vao").getAsInt() && GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING)==ebo);
            result.addProperty("canonical_payload_guard_rejects_mutation", !original.equals(read));
            GL15.glBufferSubData(GL31.GL_COPY_READ_BUFFER, 0, original); GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER, 0, read);
            result.addProperty("restored_exact_before_native_draw", original.equals(read));
            if (!original.equals(read) || GL11.glGetError()!=GL11.GL_NO_ERROR) throw new IllegalStateException("Payload restoration failed");
            original = null;
            result.addProperty("scope", "One copies-retained native draw batch; same handles/layout with collapsed geometry proves handle-only immutability is insufficient. Payload guard accepts canonical/rejects mutation. Mutated geometry never drawn; full alias/lifecycle proof and all query costs still required before rotation admission.");
        } catch (RuntimeException | LinkageError e) { result.addProperty("error", e.toString()); }
        finally { if (original != null && buffer != 0) { GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, buffer); GL15.glBufferSubData(GL31.GL_COPY_READ_BUFFER, 0, original); } GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, prior); scope=false; }
    }
}
