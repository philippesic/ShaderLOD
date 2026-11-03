package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pp.shaderlod.dev.mixin.SpatialPassAccessor;
import com.pp.shaderlod.dev.timing.CloudSamplerStateProbe;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.CompositePass;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Two source-to-pass joins and native RT1 generation returns, untimed/fresh JVM/no reload only. */
public final class BslMipWitness {
    private static final String OUTPUT = System.getProperty("shaderlod.dev.bslMipWitness", "");
    private static final Set<String> PACKS = Set.of("BSL_v10.1.8.zip",
        "BSL-MipLiveness-Native-r1.zip", "BSL-MipLiveness-Candidate-r1.zip");
    private static final Set<String> NAMES = Set.of("composite6", "composite7");
    private record Compiled(Object renderer, WorldRenderingPipeline pipeline, Program program,
                            String name, String pack, JsonObject source) {}
    private record Selected(Compiled compiled, SpatialPassAccessor pass) {}
    private static final Map<Program, Compiled> COMPILED = new IdentityHashMap<>();
    private static final Map<String, JsonObject> CAPTURES = new LinkedHashMap<>();
    private static final JsonArray CALLS = new JsonArray();
    private static Map<Program, Selected> context;
    private static RenderTarget target;
    private static int frame = -1;
    private static boolean done;
    private BslMipWitness() {}

    public static void compiled(Object renderer, WorldRenderingPipeline pipeline, CompositePass group,
            ProgramSource source, Program program, Map<?, String> transformed) {
        if (OUTPUT.isBlank() || done || group != CompositePass.COMPOSITE || source == null || program == null
                || !NAMES.contains(source.getName()) || !PACKS.contains(Iris.getCurrentPackName())) return;
        try {
            var record = new JsonObject();
            for (String type : List.of("FRAGMENT", "VERTEX")) {
                String value = transformed.entrySet().stream().filter(e -> e.getKey().toString().equals(type))
                    .map(Map.Entry::getValue).findFirst().orElseThrow();
                if (value.getBytes(StandardCharsets.UTF_8).length > 131072)
                    throw new IllegalStateException("BSL_MIP_SOURCE_LIMIT");
                record.addProperty("transformed_" + type.toLowerCase(java.util.Locale.ROOT) + "_sha256",
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))));
            }
            COMPILED.put(program, new Compiled(renderer, pipeline, program, source.getName(), Iris.getCurrentPackName(), record));
            if (COMPILED.size() > 8) throw new IllegalStateException("BSL_MIP_COMPILED_IDENTITY_LIMIT");
        } catch (Exception failure) { fail(failure); }
    }
    public static void enter(Object renderer, WorldRenderingPipeline pipeline, CompositePass group,
            List<?> passes, RenderTargets targets) {
        if (OUTPUT.isBlank() || done) return;
        context = null; target = null;
        var client = Minecraft.getInstance();
        if (group != CompositePass.COMPOSITE || client.level == null || client.screen != null || client.isPaused()
                || !client.isWindowActive() || !client.level.dimension().location().toString().equals("minecraft:overworld")
                || Iris.isFallback() || !PACKS.contains(Iris.getCurrentPackName())
                || Iris.getPipelineManager().getPipelineNullable() != pipeline) return;
        try {
            var selected = new IdentityHashMap<Program, Selected>();
            for (Object object : passes) {
                if (!(object instanceof SpatialPassAccessor pass) || !NAMES.contains(pass.shaderlod$name())) continue;
                var c = COMPILED.get(pass.shaderlod$program());
                int[] buffers = pass.shaderlod$name().equals("composite6") ? new int[]{1} : new int[]{1,2};
                if (c == null || c.renderer != renderer || c.pipeline != pipeline || !c.name.equals(pass.shaderlod$name())
                        || !c.pack.equals(Iris.getCurrentPackName()) || !java.util.Arrays.equals(pass.shaderlod$drawBuffers(), buffers))
                    throw new IllegalStateException("BSL_MIP_ACTUAL_PASS_IDENTITY_MISMATCH");
                selected.put(c.program, new Selected(c, pass));
            }
            if (selected.size() != 2) throw new IllegalStateException("BSL_MIP_C6_C7_ABSENT");
            target = targets.get(1);
            if (target == null || target.getMainTexture() == target.getAltTexture())
                throw new IllegalStateException("BSL_MIP_RT1_STORAGE_ABSENT");
            context = selected; advanceFrame();
        } catch (Exception failure) { fail(failure); }
    }
    /** RETURN of exact native setupMipmapping: generation and filter setter have returned. */
    public static void generationReturned(RenderTarget generated, boolean readAlt) {
        if (OUTPUT.isBlank() || done || context == null || generated != target) return;
        try {
            advanceFrame();
            var call = new JsonObject(); call.addProperty("frame_source", frame);
            call.addProperty("selected_texture", readAlt ? generated.getAltTexture() : generated.getMainTexture());
            call.addProperty("read_alt", readAlt); call.addProperty("ordinal", CALLS.size() + 1);
            call.addProperty("scope", "Native setupMipmapping returned for actual RT1; no GL error consumed or timing measured");
            CALLS.add(call);
            if (CALLS.size() > 8) throw new IllegalStateException("BSL_MIP_CALL_LIMIT");
        } catch (Exception failure) { fail(failure); }
    }
    public static void beforeQuad() {
        if (OUTPUT.isBlank() || done || context == null) return;
        try {
            int current = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM); Selected selected = null;
            for (var s : context.values()) if (s.compiled.program.getProgramId() == current) {
                if (selected != null) throw new IllegalStateException("BSL_MIP_PROGRAM_AMBIGUOUS"); selected = s;
            }
            if (selected == null) return;
            var c = selected.compiled;
            if (Iris.getPipelineManager().getPipelineNullable() != c.pipeline || !c.pack.equals(Iris.getCurrentPackName()))
                throw new IllegalStateException("BSL_MIP_PIPELINE_CHANGED");
            advanceFrame(); if (CAPTURES.containsKey(c.name)) return;
            var capture = CloudSamplerStateProbe.bslMipState();
            capture.addProperty("actual_pass_name", c.name); capture.addProperty("pipeline_identity", System.identityHashCode(c.pipeline));
            capture.addProperty("iris_frame_source", frame); capture.add("constructor_source", c.source);
            capture.addProperty("rt1_main", target.getMainTexture()); capture.addProperty("rt1_alt", target.getAltTexture());
            capture.addProperty("rt1_read_alt", selected.pass.shaderlod$stageReadsFromAlt().contains(1));
            var mips = new JsonArray(); selected.pass.shaderlod$mipmappedBuffers().stream().sorted().forEach(mips::add);
            capture.add("actual_mipmapped_buffers", mips); capture.addProperty("rt1_generation_returns_before_quad", CALLS.size());
            CAPTURES.put(c.name, capture);
            if (CAPTURES.size() == 2) {
                var report = new JsonObject(); report.addProperty("status", "same_frame_actual_c6_c7_source_pass_generation_observation");
                report.addProperty("loaded_pack", c.pack); report.add("rt1_generation_returns", CALLS.deepCopy());
                CAPTURES.forEach(report::add);
                report.addProperty("scope", "Fresh JVM/no reload, actual source/pass/stage input and native RT1 generation returns. External archive/export/option/state joins mandatory. No pixel/history/lifecycle parity, GPU completion or performance proof.");
                write(report); done = true;
            }
        } catch (Exception failure) { fail(failure); }
    }
    private static void advanceFrame() {
        int now = SystemTimeUniforms.COUNTER.getAsInt();
        if (now != frame) { frame = now; CAPTURES.clear(); while (!CALLS.isEmpty()) CALLS.remove(CALLS.size()-1); }
    }
    public static void leave() { if (!OUTPUT.isBlank()) { context = null; target = null; } }
    private static void write(JsonObject report) throws Exception {
        Path path = Path.of(OUTPUT); Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void fail(Exception failure) {
        done = true; context = null; target = null;
        var report = new JsonObject(); report.addProperty("status", "unknown_failed_bsl_mip_observation"); report.addProperty("failure", failure.toString());
        try { write(report); } catch (Exception ignored) { com.mojang.logging.LogUtils.getLogger().warn("BSL mip witness unavailable", failure); }
    }
}
