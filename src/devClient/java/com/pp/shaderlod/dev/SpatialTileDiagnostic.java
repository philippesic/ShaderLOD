package com.pp.shaderlod.dev;

import com.mojang.blaze3d.systems.RenderSystem;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.*;

/** Dev-only, synchronous, untimed readback. Counts NECESSARY predicates only.
 * No geometry/normal-up/distance classifier emulation; joint is an UPPER BOUND.
 * Reflects only the two fixed ShaderLOD runtime/Graph/bank implementations.
 */
public final class SpatialTileDiagnostic {
    static final long MAX_PIXELS = 4_000_000L;
    private SpatialTileDiagnostic() {}

    public static Map<String, Long> capture(String family) throws ReflectiveOperationException {
        if (!RenderSystem.isOnRenderThread()) throw new IllegalStateException("render thread required");
        if (GLFW.glfwGetCurrentContext() == 0 || !GL.getCapabilities().OpenGL33)
            throw new IllegalStateException("current GL33 context required");
        String stem = switch (family) {
            case "complementary-reimagined" -> "reimaginedspatial.Reimagined";
            case "complementary-unbound" -> "unboundspatial.Unbound";
            default -> throw new IllegalArgumentException("fixed Reimagined/Unbound families only");
        };
        Class<?> runtime = Class.forName("com.pp.shaderlod.optimization." + stem + "SpatialRuntime");
        Class<?> graphClass = Class.forName(runtime.getName() + "$Graph");
        Class<?> bankClass = Class.forName("com.pp.shaderlod.optimization." + stem + "SpatialBank");
        Object pipeline = Iris.getPipelineManager().getPipelineNullable();
        Object pack = Iris.getCurrentPack().orElse(null);
        Object graph = ((Map<?, ?>) field(runtime, null, "GRAPHS")).get(pipeline);
        if (graph == null || graph.getClass() != graphClass || pack == null)
            throw new IllegalStateException("no current owned graph");
        Object bank = field(graphClass, graph, "bank");
        if (bank == null || bank.getClass() != bankClass) throw new IllegalStateException("no owned bank");
        int width = bankClass.getField("width").getInt(bank), height = bankClass.getField("height").getInt(bank);
        int floats = floatCount(width, height);
        long frame = number(graphClass, graph, "frame"), counter = number(graphClass, graph, "frameCounter");
        long mode = number(graphClass, graph, "mode"), epoch = number(runtime, null, "epoch");
        long context = GLFW.glfwGetCurrentContext();
        Object capabilities = GL.getCapabilities();
        Program producer = (Program) field(graphClass, graph, "producer");
        Program resolver = (Program) field(graphClass, graph, "resolver");
        int[] ids = new int[5];
        for (int i = 0; i < 5; i++) ids[i] = (int) bankClass.getMethod("input", int.class).invoke(bank, i);
        verify(runtime, graphClass, bankClass, graph, pipeline, pack, bank, frame, counter, mode, epoch, producer, resolver, ids);
        requireNoError("entry (pre-existing error)");
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int pbo = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] keys = {GL11.GL_PACK_ALIGNMENT, GL12.GL_PACK_ROW_LENGTH, GL12.GL_PACK_SKIP_ROWS,
            GL12.GL_PACK_SKIP_PIXELS, GL11.GL_PACK_SWAP_BYTES, GL11.GL_PACK_LSB_FIRST,
            GL12.GL_PACK_IMAGE_HEIGHT, GL12.GL_PACK_SKIP_IMAGES};
        int[] saved = new int[keys.length];
        for (int i = 0; i < keys.length; i++) saved[i] = GL11.glGetInteger(keys[i]);
        FloatBuffer[] inputs = new FloatBuffer[5];
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            for (int i = 0; i < keys.length; i++) GL11.glPixelStorei(keys[i], i == 0 ? 4 : 0);
            requireNoError("pack normalization");
            Set<Integer> unique = new HashSet<>();
            for (int i = 0; i < 5; i++) {
                if (ids[i] <= 0 || !unique.add(ids[i]) || !GL11.glIsTexture(ids[i]))
                    throw new IllegalStateException("invalid/aliased input texture");
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, ids[i]);
                if (GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) != width
                    || GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT) != height
                    || GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT) != GL30.GL_RGBA32F
                    || GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL) != 0
                    || GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) != ids[i])
                    throw new IllegalStateException("input texture identity/shape changed");
                inputs[i] = MemoryUtil.memAllocFloat(floats);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, inputs[i]);
                requireNoError("input readback");
            }
            var result = new LinkedHashMap<>(count(inputs, width, height, (int) counter));
            verify(runtime, graphClass, bankClass, graph, pipeline, pack, bank, frame, counter, mode, epoch, producer, resolver, ids);
            if (GLFW.glfwGetCurrentContext() != context || GL.getCapabilities() != capabilities)
                throw new IllegalStateException("GL context changed");
            result.put("graphFrame", frame); result.put("frameCounter", counter); result.put("mode", mode);
            result.put("completedPrivateResolves", number(runtime, null, "completedLayers"));
            result.put("qualifiedDrawEntries", number(runtime, null, "qualifiedDrawEntries"));
            result.put("producerFrameUniformCurrent", 1L); result.put("resolverFrameUniformCurrent", 1L);
            result.put("producerModeUniformCurrent", 1L); result.put("resolverModeUniformCurrent", 1L);
            result.put("readbackBytes", (long) floats * 4 * 5);
            return Collections.unmodifiableMap(result);
        } finally {
            try {
                for (int i = 0; i < keys.length; i++) GL11.glPixelStorei(keys[i], saved[i]);
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo);
                GL13.glActiveTexture(active); GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
                requireNoError("state restoration");
                if (GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) != active
                    || GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) != bound
                    || GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING) != pbo)
                    throw new IllegalStateException("binding restoration mismatch");
                for (int i = 0; i < keys.length; i++) if (GL11.glGetInteger(keys[i]) != saved[i])
                    throw new IllegalStateException("pack restoration mismatch");
            } finally { for (FloatBuffer input : inputs) if (input != null) MemoryUtil.memFree(input); }
        }
    }

    private static void verify(Class<?> r, Class<?> g, Class<?> b, Object graph, Object pipeline, Object pack,
            Object bank, long frame, long counter, long mode, long epoch, Program producer, Program resolver, int[] ids)
            throws ReflectiveOperationException {
        if (Iris.getPipelineManager().getPipelineNullable() != pipeline || Iris.getCurrentPack().orElse(null) != pack
            || ((Map<?, ?>) field(r, null, "GRAPHS")).get(pipeline) != graph
            || field(g, graph, "pipeline") != pipeline || field(g, graph, "pack") != pack
            || field(g, graph, "bank") != bank || (boolean) field(g, graph, "activeLayer")
            || (boolean) field(g, graph, "failed") || !(boolean) field(g, graph, "preflight")
            || (boolean) field(r, null, "pending") || (boolean) field(r, null, "blocked")
            || number(g, graph, "frame") != frame || number(r, null, "frames") != frame
            || number(g, graph, "frameCounter") != counter || counter < 0 || counter >= 16777216
            || number(g, graph, "mode") != mode || mode < 1 || mode > 2
            || number(g, graph, "sourceEpoch") != epoch || number(r, null, "epoch") != epoch
            || field(g, graph, "producer") != producer || field(g, graph, "resolver") != resolver)
            throw new IllegalStateException("inactive/stale/changed graph");
        for (int i = 0; i < 5; i++) if ((int) b.getMethod("input", int.class).invoke(bank, i) != ids[i])
            throw new IllegalStateException("bank texture changed");
        for (Program p : new Program[]{producer, resolver}) {
            if (p == null || !GL20.glIsProgram(p.getProgramId())
                || GL20.glGetProgrami(p.getProgramId(), GL20.GL_LINK_STATUS) != GL11.GL_TRUE)
                throw new IllegalStateException("invalid private program");
            int loc = GL20.glGetUniformLocation(p.getProgramId(), "frameCounter");
            if (loc < 0 || GL20.glGetUniformi(p.getProgramId(), loc) != counter)
                throw new IllegalStateException("private program frame is stale");
            String modeName = "shaderlod" + r.getSimpleName().replace("Runtime", "Mode");
            int modeLoc = GL20.glGetUniformLocation(p.getProgramId(), modeName);
            if (modeLoc < 0 || !tileModeMatches(mode, GL20.glGetUniformi(p.getProgramId(), modeLoc)))
                throw new IllegalStateException("private program tile mode is stale/inapplicable");
        }
    }
    private static Object field(Class<?> owner, Object value, String name) throws ReflectiveOperationException {
        Field f = owner.getDeclaredField(name); f.setAccessible(true); return f.get(value);
    }
    private static long number(Class<?> owner, Object value, String name) throws ReflectiveOperationException {
        return ((Number) field(owner, value, name)).longValue();
    }
    private static void requireNoError(String stage) {
        int e = GL11.glGetError(); if (e != GL11.GL_NO_ERROR) throw new IllegalStateException(stage + ": GL error " + e);
    }
    static boolean tileModeMatches(long graphMode, int programMode) {
        return (graphMode == 1 || graphMode == 2) && graphMode == programMode;
    }
    static int floatCount(int width, int height) {
        long pixels = (long) width * height;
        if (width < 2 || height < 2 || pixels > MAX_PIXELS) throw new IllegalArgumentException("2D input exceeds bounded readback");
        return Math.toIntExact(pixels * 4);
    }
    // Pure counters: exact float == matches GLSL equality (including signed zero).
    // All individual predicate counts below are conditioned on all four current raw tags.
    static Map<String, Long> count(FloatBuffer[] in, int width, int height, int frame) {
        int size = floatCount(width, height);
        if (frame < 0 || frame >= 16777216 || in == null || in.length != 5) throw new IllegalArgumentException("inputs/frame");
        for (FloatBuffer b : in) if (b == null || b.position() != 0 || b.limit() != size)
            throw new IllegalArgumentException("exact zero-position RGBA buffer bounds required");
        String[] names = {"all4RawCurrentTags", "all4FiniteInputs", "all4FinitePayloadG01", "exactG", "exactNormalXYZ",
            "exactParamAW", "exactBW", "exactCW", "exactDX", "sameMaterialInt", "all4ScalarDomains", "jointNecessaryUpperBound"};
        long[] counts = new long[names.length]; long current = 0, raw = 0, old = 0, cleared = 0;
        for (int p = 0; p < size; p += 4) {
            float tag = in[3].get(p + 3);
            if (tag == frame) current++; else if (tag == -1) cleared++; else if (Float.isFinite(tag) && tag >= 0) old++;
            if (raw(in[3], p, frame)) raw++;
        }
        int[] offsets = {0, 4, width * 4, width * 4 + 4};
        for (int y = 0; y + 1 < height; y += 2) for (int x = 0; x + 1 < width; x += 2) {
            int p = (y * width + x) * 4;
            boolean tags = true, finite = true, payload = true, domains = true, material = true;
            for (int off : offsets) {
                int q = p + off;
                tags &= raw(in[3], q, frame);
                for (int t = 0; t < 4; t++) finite &= finite4(in[t], q);
                payload &= finite4(in[4], q) && unit(in[4].get(q + 3));
                domains &= unit(in[0].get(q + 3)) && unit(in[1].get(q + 3)) && unit(in[2].get(q + 3))
                    && in[3].get(q) >= 0 && in[3].get(q) < 1000;
                material &= (int) in[3].get(q + 1) == (int) in[3].get(p + 1);
            }
            if (!tags) continue;
            boolean[] pass = {true, finite, payload, equal(in[4], p, offsets, 3, 1), equal(in[0], p, offsets, 0, 3),
                equal(in[0], p, offsets, 3, 1), equal(in[1], p, offsets, 3, 1), equal(in[2], p, offsets, 3, 1),
                equal(in[3], p, offsets, 0, 1), material, domains, false};
            boolean joint = true; for (int i = 0; i < pass.length - 1; i++) joint &= pass[i];
            pass[pass.length - 1] = joint;
            for (int i = 0; i < pass.length; i++) if (pass[i]) counts[i]++;
        }
        var result = new LinkedHashMap<String, Long>();
        result.put("width", (long) width); result.put("height", (long) height);
        result.put("aligned2x2Anchors", (long) (width / 2) * (height / 2));
        result.put("currentFramePixels", current); result.put("currentTagDepthMaterialPixels", raw);
        result.put("otherNonnegativeFramePixels", old); result.put("clearedTagPixels", cleared);
        for (int i = 0; i < names.length; i++) result.put(names[i], counts[i]);
        result.put("geometryClassifierEvaluated", 0L); result.put("fullTileEligibilityEstablished", 0L);
        return Collections.unmodifiableMap(result);
    }
    private static boolean raw(FloatBuffer d, int p, int frame) {
        return finite4(d, p) && d.get(p + 3) == frame && d.get(p + 2) >= .56f && d.get(p + 2) < 1
            && d.get(p + 1) > 0 && d.get(p + 1) < 16777216;
    }
    private static boolean unit(float v) { return Float.isFinite(v) && v >= 0 && v <= 1; }
    private static boolean finite4(FloatBuffer b, int p) {
        for (int i = 0; i < 4; i++) if (!Float.isFinite(b.get(p + i)) || Math.abs(b.get(p + i)) >= 1e10f) return false;
        return true;
    }
    private static boolean equal(FloatBuffer b, int p, int[] offsets, int start, int n) {
        for (int off : offsets) for (int j = start; j < start + n; j++) if (b.get(p + j) != b.get(p + off + j)) return false;
        return true;
    }
}
