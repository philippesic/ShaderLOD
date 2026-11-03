package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pp.shaderlod.dev.mixin.SpatialPassAccessor;
import com.pp.shaderlod.dev.timing.CloudSamplerStateProbe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.CompositePass;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Two pinned constructor-to-real-pass joins using existing untimed hooks, not a timing/coverage counter. */
public final class BlissChainWitness {
    private static final String OUTPUT = System.getProperty("shaderlod.dev.blissChainWitness", "");
    private static final String PACK = "Bliss-NativeRaster-R1c.zip";
    private static final Map<String, String> FRAGMENTS = Map.of(
        "composite5", "8d0a413d5c0df32b8b4a8dc5cc3e2b5e86faee32796e578a65f0ba08e9727477",
        "composite6", "c7bfbf42eec2a3188666f3f3c5523da8530204c10bdb59ae77d6e848979f9db7");
    private static final Map<String, String> VERTICES = Map.of(
        "composite5", "aa37aba9f8fc161dcd298fe5a2d90344f93488234464c854eaaa459260d196c9",
        "composite6", "616574db83b81f7f0799af5e6a26ec25161c188945afe8db5ab566db0822d244");
    private record Compiled(Object renderer, WorldRenderingPipeline pipeline, Program program, String name) {}
    private static final Map<Program, Compiled> COMPILED = new IdentityHashMap<>();
    private static final Map<String, JsonObject> CAPTURES = new LinkedHashMap<>();
    private static Map<Program, Compiled> context;
    private static int frame = -1;
    private static boolean done;
    private BlissChainWitness() {}

    public static void compiled(Object renderer, WorldRenderingPipeline pipeline, CompositePass group,
            ProgramSource source, Program program, Map<?, String> transformed) {
        if (OUTPUT.isBlank() || done || source == null || program == null || group != CompositePass.COMPOSITE
                || !PACK.equals(Iris.getCurrentPackName()) || !FRAGMENTS.containsKey(source.getName())) return;
        try {
            String fragment = stage(transformed, "FRAGMENT"), vertex = stage(transformed, "VERTEX");
            if (!hash(fragment).equals(FRAGMENTS.get(source.getName())) || !hash(vertex).equals(VERTICES.get(source.getName())))
                throw new IllegalStateException("BLISS_PINNED_TRANSFORMED_SOURCE_MISMATCH");
            COMPILED.put(program, new Compiled(renderer, pipeline, program, source.getName()));
            if (COMPILED.size() > 8) throw new IllegalStateException("BLISS_COMPILED_IDENTITY_LIMIT");
        } catch (Exception failure) { fail(failure); }
    }
    public static void enter(Object renderer, WorldRenderingPipeline pipeline, CompositePass group, List<?> passes) {
        if (OUTPUT.isBlank() || done) return;
        context = null;
        var client = Minecraft.getInstance();
        if (group != CompositePass.COMPOSITE || client.level == null || client.screen != null || client.isPaused()
                || !client.level.dimension().location().toString().equals("minecraft:overworld")
                || Iris.isFallback() || !PACK.equals(Iris.getCurrentPackName())
                || Iris.getPipelineManager().getPipelineNullable() != pipeline) return;
        try {
            var selected = new IdentityHashMap<Program, Compiled>();
            for (Object object : passes) {
                if (!(object instanceof SpatialPassAccessor pass) || !FRAGMENTS.containsKey(pass.shaderlod$name())) continue;
                var c = COMPILED.get(pass.shaderlod$program());
                int[] expected = pass.shaderlod$name().equals("composite5") ? new int[]{0,6} : new int[]{5};
                if (c == null || c.renderer != renderer || c.pipeline != pipeline || !c.name.equals(pass.shaderlod$name())
                        || !java.util.Arrays.equals(expected, pass.shaderlod$drawBuffers()))
                    throw new IllegalStateException("BLISS_REAL_PASS_IDENTITY_OR_DRAW_BUFFER_MISMATCH");
                selected.put(c.program, c);
            }
            if (selected.size() != 2) throw new IllegalStateException("BLISS_PREP_OR_TAA_REAL_PASS_ABSENT");
            context = selected;
        } catch (Exception failure) { fail(failure); }
    }
    public static void beforeQuad() {
        if (OUTPUT.isBlank() || done || context == null) return;
        try {
            int current = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            Compiled chosen = null;
            for (var c : context.values()) if (c.program.getProgramId() == current) {
                if (chosen != null) throw new IllegalStateException("BLISS_AMBIGUOUS_REAL_PROGRAM");
                chosen = c;
            }
            if (chosen == null) return;
            if (Iris.getPipelineManager().getPipelineNullable() != chosen.pipeline || !PACK.equals(Iris.getCurrentPackName()))
                throw new IllegalStateException("BLISS_PIPELINE_OR_PACK_CHANGED");
            int now = SystemTimeUniforms.COUNTER.getAsInt();
            if (now != frame) { CAPTURES.clear(); frame = now; }
            if (CAPTURES.containsKey(chosen.name)) return;
            var capture = CloudSamplerStateProbe.blissChainState();
            capture.addProperty("actual_pass_name", chosen.name);
            capture.addProperty("pipeline_identity", System.identityHashCode(chosen.pipeline));
            capture.addProperty("iris_frame_source", frame);
            capture.addProperty("pinned_fragment_sha256", FRAGMENTS.get(chosen.name));
            capture.addProperty("pinned_vertex_sha256", VERTICES.get(chosen.name));
            CAPTURES.put(chosen.name, capture);
            if (CAPTURES.size() == 2) {
                var report = new JsonObject(); report.addProperty("status", "same_frame_actual_prep_taa_source_pass_observation");
                report.addProperty("scope", "Pinned constructor source/Program joined to actual pass/pre-quad, sampler/FBO observations; not contents, raster coverage, quality or speed");
                CAPTURES.forEach(report::add); write(report); done = true;
            }
        } catch (Exception failure) { fail(failure); }
    }
    public static void leave() { if (!OUTPUT.isBlank()) context = null; }
    private static String stage(Map<?, String> values, String type) {
        return values.entrySet().stream().filter(e -> e.getKey().toString().equals(type)).map(Map.Entry::getValue).findFirst().orElseThrow();
    }
    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    private static void write(JsonObject report) throws Exception {
        Path path = Path.of(OUTPUT); Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void fail(Exception failure) {
        done = true; var report = new JsonObject(); report.addProperty("status", "unknown_failed_bliss_chain_observation");
        report.addProperty("failure", failure.toString());
        try { write(report); } catch (Exception ignored) { com.mojang.logging.LogUtils.getLogger().warn("Bliss witness receipt unavailable", failure); }
    }
}
