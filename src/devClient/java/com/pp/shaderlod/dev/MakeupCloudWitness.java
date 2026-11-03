package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pp.shaderlod.dev.mixin.SpatialPassAccessor;
import com.pp.shaderlod.dev.timing.CloudSamplerStateProbe;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.CompositePass;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Untimed fresh-JVM observation; root must join recorded stage hashes to exact exported bytes. */
public final class MakeupCloudWitness {
    private static final String OUTPUT = System.getProperty("shaderlod.dev.makeupCloudWitness", "");
    private static final Set<String> PACKS = Set.of("MakeUp-UltraFast-9.5g.zip",
        "ShaderLOD-MakeUp-cloud-saturation-native-equivalent-r3.zip",
        "ShaderLOD-MakeUp-cloud-saturation-candidate-r3.zip",
        "ShaderLOD-MakeUp-cloud-saturation-cue-r1.zip");
    private record Compiled(Object renderer, WorldRenderingPipeline pipeline, Program program,
                            String pack, JsonObject source) {}
    private static final Map<Program, Compiled> COMPILED = new IdentityHashMap<>();
    private static Compiled context;
    private static boolean done;
    private MakeupCloudWitness() {}

    public static void compiled(Object renderer, WorldRenderingPipeline pipeline, CompositePass group,
            ProgramSource source, Program program, Map<?, String> transformed) {
        if (OUTPUT.isBlank() || done || group != CompositePass.DEFERRED || source == null || program == null
                || !source.getName().equals("deferred") || !PACKS.contains(Iris.getCurrentPackName())) return;
        try {
            if (!java.util.Arrays.equals(source.getDirectives().getDrawBuffers(), new int[]{1,4}))
                throw new IllegalStateException("MAKEUP_DEFERRED_DRAW_BUFFER_MISMATCH");
            var record = new JsonObject();
            record.addProperty("original_preprocessed_fragment_sha256", hash(source.getFragmentSource().orElseThrow()));
            for (String type : List.of("FRAGMENT", "VERTEX")) {
                String value = transformed.entrySet().stream().filter(e -> e.getKey().toString().equals(type))
                    .map(Map.Entry::getValue).findFirst().orElseThrow();
                if (value.getBytes(StandardCharsets.UTF_8).length > 131072)
                    throw new IllegalStateException("MAKEUP_STAGE_SOURCE_TOO_LARGE");
                record.addProperty("transformed_" + type.toLowerCase(java.util.Locale.ROOT) + "_sha256", hash(value));
            }
            COMPILED.put(program, new Compiled(renderer, pipeline, program, Iris.getCurrentPackName(), record));
            if (COMPILED.size() > 4) throw new IllegalStateException("MAKEUP_COMPILED_IDENTITY_LIMIT");
        } catch (Exception failure) { fail(failure); }
    }

    public static void enter(Object renderer, WorldRenderingPipeline pipeline, CompositePass group, List<?> passes) {
        if (OUTPUT.isBlank() || done) return;
        context = null;
        var client = Minecraft.getInstance();
        if (group != CompositePass.DEFERRED || client.level == null || client.screen != null || client.isPaused()
                || !client.isWindowActive() || !client.level.dimension().location().toString().equals("minecraft:overworld")
                || Iris.isFallback() || !PACKS.contains(Iris.getCurrentPackName())
                || Iris.getPipelineManager().getPipelineNullable() != pipeline) return;
        try {
            for (Object object : passes) {
                if (!(object instanceof SpatialPassAccessor pass) || !pass.shaderlod$name().equals("deferred")) continue;
                var c = COMPILED.get(pass.shaderlod$program());
                if (context != null || c == null || c.renderer != renderer || c.pipeline != pipeline
                        || !c.pack.equals(Iris.getCurrentPackName())
                        || !java.util.Arrays.equals(pass.shaderlod$drawBuffers(), new int[]{1,4}))
                    throw new IllegalStateException("MAKEUP_ACTUAL_DEFERRED_PASS_MISMATCH");
                context = c;
            }
            if (context == null) throw new IllegalStateException("MAKEUP_ACTUAL_DEFERRED_PASS_ABSENT");
        } catch (Exception failure) { fail(failure); }
    }

    public static void beforeQuad() {
        if (OUTPUT.isBlank() || done || context == null) return;
        try {
            int current = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            if (current != context.program.getProgramId()) return;
            if (Iris.getPipelineManager().getPipelineNullable() != context.pipeline
                    || !context.pack.equals(Iris.getCurrentPackName()))
                throw new IllegalStateException("MAKEUP_PIPELINE_OR_PACK_CHANGED");
            var report = CloudSamplerStateProbe.makeupCloudState();
            report.addProperty("status", "actual_deferred_constructor_source_program_pass_observation");
            report.addProperty("loaded_pack", context.pack);
            report.addProperty("actual_pass_name", "deferred");
            report.addProperty("pass_group", "DEFERRED");
            report.addProperty("pipeline_identity", System.identityHashCode(context.pipeline));
            report.addProperty("iris_frame_source", SystemTimeUniforms.COUNTER.getAsInt());
            report.add("constructor_source", context.source);
            report.addProperty("scope", "Actual source-to-Program-to-pass/pre-quad observation. Exact archive/export hash join required; no completed draw, pixels, loop count, history continuity, quality or speed proof. Fresh JVM/no reload only.");
            write(report); done = true;
        } catch (Exception failure) { fail(failure); }
    }
    public static void leave() { if (!OUTPUT.isBlank()) context = null; }
    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    private static void write(JsonObject report) throws Exception {
        Path path = Path.of(OUTPUT); Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void fail(Exception failure) {
        done = true; context = null;
        var report = new JsonObject(); report.addProperty("status", "unknown_failed_makeup_deferred_observation");
        report.addProperty("failure", failure.toString());
        try { write(report); } catch (Exception ignored) { com.mojang.logging.LogUtils.getLogger().warn("Sildur witness unavailable", failure); }
    }
}
