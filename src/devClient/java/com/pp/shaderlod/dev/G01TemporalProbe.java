package com.pp.shaderlod.dev;

import com.google.gson.*;
import java.lang.reflect.Field;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.targets.*;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

/** Bounded, opt-in, untimed early-frame observation. Its mixin is absent from timing launches. */
public final class G01TemporalProbe {
    private static final Map<Integer,String> PROGRAMS = new HashMap<>();
    private static final Map<Integer,JsonObject> SOURCES = new HashMap<>();
    private static JsonArray events = new JsonArray();
    private static String label;
    private static int budget, seen, lastFrame = -1;
    private static Object lastPipeline;
    private static boolean armed;
    private static boolean matchedFrame;
    private static String onlyDimension;
    private static int onlyWidth;
    private G01TemporalProbe() {}
    public static boolean active() { return armed; }
    public static void cancel() { armed = false; }
    public static void compiled(ProgramSource source, Program program, Map<?,String> transformed) {
        if (program == null) return;
        int id = program.getProgramId(); PROGRAMS.put(id, source.getName());
        JsonObject joined = new JsonObject(); joined.addProperty("name", source.getName()); joined.addProperty("program", id);
        source.getVertexSource().ifPresent(s -> joined.addProperty("preprocessed_vertex", s));
        source.getFragmentSource().ifPresent(s -> joined.addProperty("preprocessed_fragment", s));
        for (var entry : transformed.entrySet()) if (entry.getValue() != null) {
            joined.addProperty("transformed_" + entry.getKey().toString().toLowerCase(Locale.ROOT), entry.getValue());
        }
        joined.addProperty("join", "Exact createProgram TransformPatcher return captured before native ProgramBuilder/link; same callback returns this Program. Deleted attached GL stages are not inferred.");
        SOURCES.put(id, joined);
    }
    public static JsonObject request(JsonObject request) {
        if (!Boolean.getBoolean("shaderlod.dev.g01ExposureProbe")) throw new IllegalStateException("Explicit untimed G01 launch required");
        if (request.has("frames")) {
            int frames = request.get("frames").getAsInt();
            String next = request.get("label").getAsString();
            if (armed || frames < 1 || frames > 8 || !next.matches("[a-z0-9-]{1,48}")) throw new IllegalArgumentException("Idle bounded G01 label/1..8 frames required");
            label = next; budget = frames; seen = 0; lastFrame = -1; lastPipeline = null; events = new JsonArray(); armed = true; matchedFrame = false;
            onlyDimension = request.has("only_dimension") ? request.get("only_dimension").getAsString() : null;
            onlyWidth = request.has("only_width") ? request.get("only_width").getAsInt() : 0;
        }
        JsonObject out = new JsonObject(); out.addProperty("armed", armed); out.addProperty("label", label);
        out.addProperty("frames_seen", seen); out.addProperty("frame_budget", budget); out.add("events", events.deepCopy());
        JsonArray sourceJoins = new JsonArray(); Set<Integer> included = new HashSet<>();
        for (var value : events) { var row = value.getAsJsonObject(); if (row.has("program")) { int id = row.get("program").getAsInt(); if (included.add(id) && SOURCES.containsKey(id)) sourceJoins.add(SOURCES.get(id).deepCopy()); } }
        out.add("program_source_joins", sourceJoins);
        out.addProperty("scope", "Untimed CPU-ordered GL reads at native quad/mip seams; early images and both-bank samples. No timing or whole-image numerical equivalence claim.");
        return out;
    }
    public static void quad(String phase) {
        if (phase.equals("before")) G01QuadPayloadProbe.before(PROGRAMS.get(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)));
        else G01QuadPayloadProbe.after();
        if (!armed) return;
        var client = Minecraft.getInstance();
        matchedFrame = (onlyDimension == null || client.level != null && onlyDimension.equals(client.level.dimension().location().toString()))
            && (onlyWidth == 0 || onlyWidth == client.getWindow().getWidth());
        if (!matchedFrame) return;
        int frame = SystemTimeUniforms.COUNTER.getAsInt(); Object pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (frame != lastFrame || pipeline != lastPipeline) {
            if (seen == budget) { armed = false; return; }
            seen++; lastFrame = frame; lastPipeline = pipeline;
        }
        JsonObject row = base("quad-" + phase);
        try {
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            String name = PROGRAMS.getOrDefault(program, "UNJOINED");
            row.addProperty("program", program); row.addProperty("pass", name);
            row.addProperty("draw_fbo", GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
            int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport); row.add("viewport", new Gson().toJsonTree(viewport));
            for (String uniform : List.of("viewWidth", "viewHeight")) {
                int location = GL20.glGetUniformLocation(program, uniform);
                if (location >= 0) row.addProperty(uniform, GL20.glGetUniformf(program, location));
            }
            JsonArray samplers = new JsonArray(); int unit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            try {
                for (String uniform : List.of("colortex1", "gcolor", "gaux3", "colortex8", "colortex9")) {
                    int location = GL20.glGetUniformLocation(program, uniform); if (location < 0) continue;
                    int samplerUnit = GL20.glGetUniformi(program, location); GL13.glActiveTexture(GL13.GL_TEXTURE0 + samplerUnit);
                    JsonObject sampler = new JsonObject(); sampler.addProperty("name", uniform); sampler.addProperty("unit", samplerUnit);
                    int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); sampler.addProperty("texture", texture);
                    sampler.addProperty("sampler_object", GL11.glGetInteger(GL33.GL_SAMPLER_BINDING));
                    if (texture != 0) { sampler.addProperty("width", level(0, GL11.GL_TEXTURE_WIDTH)); sampler.addProperty("height", level(0, GL11.GL_TEXTURE_HEIGHT)); }
                    samplers.add(sampler);
                }
            } finally { GL13.glActiveTexture(unit); }
            row.add("samplers", samplers);
            if (pipeline != null) {
                RenderTargets targets = (RenderTargets)field(pipeline, "renderTargets"); JsonArray banks = new JsonArray();
                for (int target : new int[]{1, 3, 6, 8, 9, 10}) {
                    if (target >= targets.getRenderTargetCount()) continue;
                    RenderTarget rt = targets.get(target); if (rt == null) continue;
                    for (int bank = 0; bank < 2; bank++) banks.add(storage(target, bank, bank == 0 ? rt.getMainTexture() : rt.getAltTexture()));
                }
                row.add("banks", banks);
            }
        } catch (Exception | LinkageError failure) { row.addProperty("error", failure.toString()); armed = false; }
        events.add(row);
    }
    public static void mip(RenderTarget target, boolean alt) {
        if (!armed) return;
        JsonObject row = base("native-mip-return"); row.addProperty("texture", alt ? target.getAltTexture() : target.getMainTexture());
        events.add(row);
    }
    public static void finalReturned() {
        if (!armed || !matchedFrame || seen == 0 || lastPipeline != Iris.getPipelineManager().getPipelineNullable() || lastFrame != SystemTimeUniforms.COUNTER.getAsInt()) return;
        var c = Minecraft.getInstance(); String name = "g01-first-" + label + "-" + seen + ".png";
        net.minecraft.client.Screenshot.grab(c.gameDirectory, name, c.getMainRenderTarget(), message -> {});
        JsonObject row = base("final-image-request"); row.addProperty("path", c.gameDirectory.toPath().resolve("screenshots").resolve(name).toString()); events.add(row);
        if (seen >= budget) armed = false;
    }
    private static JsonObject base(String kind) {
        var c = Minecraft.getInstance(); JsonObject row = new JsonObject(); row.addProperty("kind", kind);
        row.addProperty("sequence", seen); row.addProperty("frame_counter", SystemTimeUniforms.COUNTER.getAsInt());
        row.addProperty("nano_time", System.nanoTime()); row.addProperty("pipeline_identity", System.identityHashCode(Iris.getPipelineManager().getPipelineNullable()));
        row.addProperty("dimension", c.level == null ? "ABSENT" : c.level.dimension().location().toString());
        row.addProperty("framebuffer_width", c.getWindow().getWidth()); row.addProperty("framebuffer_height", c.getWindow().getHeight());
        row.addProperty("fallback", Iris.isFallback()); return row;
    }
    private static JsonObject storage(int target, int bank, int texture) {
        JsonObject row = new JsonObject(); row.addProperty("target", target); row.addProperty("bank", bank == 0 ? "main" : "alt"); row.addProperty("texture", texture);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), readBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] keys = {GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH, GL11.GL_PACK_SKIP_ROWS, GL11.GL_PACK_SKIP_PIXELS, GL11.GL_PACK_SWAP_BYTES, GL11.GL_PACK_LSB_FIRST};
        int[] prior = Arrays.stream(keys).map(GL11::glGetInteger).toArray(); int scratch = 0;
        try (var stack = MemoryStack.stackPush()) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture); int width = level(0, GL11.GL_TEXTURE_WIDTH), height = level(0, GL11.GL_TEXTURE_HEIGHT);
            row.addProperty("width", width); row.addProperty("height", height); row.addProperty("format", level(0, GL11.GL_TEXTURE_INTERNAL_FORMAT));
            JsonArray levels = new JsonArray();
            for (int l = 0; l < 16; l++) { int w = level(l, GL11.GL_TEXTURE_WIDTH), h = level(l, GL11.GL_TEXTURE_HEIGHT); if (w == 0 || h == 0) break; levels.add(w + "x" + h); if (w == 1 && h == 1) break; }
            row.add("mip_extents", levels);
            // Exposure outputs are constant over their target. RT1/RT3 are metadata only.
            if (target == 1 || target == 3) return row;
            scratch = GL30.glGenFramebuffers(); GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, scratch);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0); GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Incomplete sample FBO");
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0); for (int key : keys) GL11.glPixelStorei(key, key == GL11.GL_PACK_ALIGNMENT ? 4 : 0);
            JsonArray values = new JsonArray();
            for (int[] xy : new int[][]{{0,0},{width / 2,height / 2},{width - 1,height - 1}}) {
                var pixel = stack.mallocFloat(1); GL11.glReadPixels(xy[0], xy[1], 1, 1, GL11.GL_RED, GL11.GL_FLOAT, pixel);
                float value = pixel.get(0); JsonObject sample = new JsonObject(); sample.addProperty("x", xy[0]); sample.addProperty("y", xy[1]);
                sample.addProperty("bits", Float.floatToRawIntBits(value)); sample.addProperty("finite", Float.isFinite(value)); if (Float.isFinite(value)) sample.addProperty("value", value); values.add(sample);
            }
            row.add("red_samples", values);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo); GL11.glReadBuffer(readBuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding); GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            for (int i = 0; i < keys.length; i++) GL11.glPixelStorei(keys[i], prior[i]); if (scratch != 0) GL30.glDeleteFramebuffers(scratch);
        }
        return row;
    }
    private static int level(int level, int key) { return GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, key); }
    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(owner); } catch (NoSuchFieldException next) { }
        throw new NoSuchFieldException(name);
    }
}
