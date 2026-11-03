package com.pp.shaderlod.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import com.pp.shaderlod.optimization.OptimizationConfig;
import com.pp.shaderlod.optimization.OptimizationRuntimeContext;
import com.pp.shaderlod.optimization.g07.G07SamplingAdmission;
import com.pp.shaderlod.optimization.g07.G07SamplingRuntime;
import com.pp.shaderlod.optimization.mips.BslMipWitnessPins;
import com.pp.shaderlod.optimization.sourcecache.EffectiveOptions;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.CompositePass;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Explicit render-thread read only; never registered as a frame callback or activation supplier. */
final class G07Diagnostic {
    private static final List<String> UNIFORMS = List.of("shaderlodG07Admitted", "shaderlodBridgeVersion",
        "shaderlodEnabled", "shaderlodPreset", "shaderlodTemporalVersion", "shaderlodHistoryReset", "frameCounter");
    private G07Diagnostic() {}

    static JsonObject read(JsonObject request) throws Exception {
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        var client = Minecraft.getInstance();
        if (client.level == null || client.screen != null || client.isPaused() || Iris.isFallback())
            throw new IllegalStateException("Idle unpaused loaded shader world required");
        String name = request.get("pack").getAsString(), expected = request.get("sha256").getAsString();
        if (!name.matches("[A-Za-z0-9_.-]+\\.zip") || !((String)G07SamplingAdmission.class.getField("ARCHIVE_SHA").get(null)).equals(expected)
                || !name.equals(Iris.getCurrentPackName())
                || !name.equals(Iris.getIrisConfig().getShaderPackName().orElse(null)))
            throw new IllegalArgumentException("Actual selected exact G07 private archive required");
        var file = Iris.getShaderpacksDirectory().resolve(name);
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file) || !expected.equals(hash(Files.readAllBytes(file))))
            throw new IllegalStateException("G07 archive byte identity mismatch");
        if (!BslMipWitnessPins.exactApi()) throw new IllegalStateException("Pinned Iris pipeline/pass/program API required");
        var pack = Iris.getCurrentPack().orElseThrow();
        var pipeline = Iris.getPipelineManager().getPipelineNullable();
        var renderer = field(pipeline, "net.irisshaders.iris.pipeline.IrisRenderingPipeline", "deferredRenderer");
        if (field(renderer, "net.irisshaders.iris.pipeline.CompositeRenderer", "pipeline") != pipeline
                || field(renderer, "net.irisshaders.iris.pipeline.CompositeRenderer", "compositePass") != CompositePass.DEFERRED)
            throw new IllegalStateException("Selected deferred renderer ownership mismatch");
        var passes = (List<?>) field(renderer, "net.irisshaders.iris.pipeline.CompositeRenderer", "passes");
        if (passes.size() > 16) throw new IllegalStateException("Unexpected deferred pass count");
        int before = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        var result = new JsonObject();
        result.addProperty("archive_sha256", expected); result.addProperty("pack", name);
        result.addProperty("pipeline_identity", System.identityHashCode(pipeline));
        result.addProperty("deferred_renderer_identity", System.identityHashCode(renderer));
        result.addProperty("iris_frame_source", SystemTimeUniforms.COUNTER.getAsInt());
        var options = EffectiveOptions.current(pack.getShaderPackOptions());
        var stack = MakeUpQuadRuntime.loadedStack();
        var resources = client.getResourcePackRepository().getSelectedIds();
        result.add("effective_options", new com.google.gson.Gson().toJsonTree(options));
        result.add("loaded_stack", new com.google.gson.Gson().toJsonTree(stack));
        result.add("selected_resources", new com.google.gson.Gson().toJsonTree(resources));
        result.addProperty("exact_api", true);
        result.addProperty("options_match_on", G07SamplingAdmission.options(options));
        result.addProperty("stack_match", G07SamplingAdmission.stack(stack));
        result.addProperty("resources_match", G07SamplingAdmission.resources(resources));
        result.addProperty("queued_options_empty", Iris.getShaderPackOptionQueue().isEmpty());
        result.addProperty("source_computed_admitted", G07SamplingRuntime.admitted());
        var flags = new TreeMap<String,Boolean>(); OptimizationConfig.snapshot().flags().forEach((id,value)->flags.put(id.key(),value));
        result.add("requested_flags", new com.google.gson.Gson().toJsonTree(flags));
        result.addProperty("all_requests_off", flags.size() == 62 && flags.values().stream().noneMatch(Boolean::booleanValue));
        result.add("source_core", new com.google.gson.Gson().toJsonTree(OptimizationRuntimeContext.core()));
        var rows = new JsonArray(); int nativeDeferred = 0;
        for (Object pass : passes) {
            String passName = (String) field(pass, "net.irisshaders.iris.pipeline.CompositeRenderer$Pass", "name");
            var program = (Program) field(pass, "net.irisshaders.iris.pipeline.CompositeRenderer$Pass", "program");
            var row = new JsonObject(); row.addProperty("pass_name", passName);
            row.add("draw_buffers", new com.google.gson.Gson().toJsonTree(field(pass, "net.irisshaders.iris.pipeline.CompositeRenderer$Pass", "drawBuffers")));
            if (program == null) throw new IllegalStateException("Deferred pass has no raster program");
            int id = program.getProgramId();
            if (!GL20.glIsProgram(id) || GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) != GL11.GL_TRUE)
                throw new IllegalStateException("Selected deferred program is not live/linked");
            row.addProperty("program_id", id); var uniforms = new JsonObject();
            for (String uniform : UNIFORMS) {
                int location = GL20.glGetUniformLocation(id, uniform); var value = new JsonObject();
                value.addProperty("location", location); value.addProperty("available", location >= 0);
                if (location >= 0) value.addProperty("value", GL20.glGetUniformi(id, location));
                else value.addProperty("reason", "absent_or_optimized_out; value unknown");
                uniforms.add(uniform, value);
            }
            row.add("linked_uniforms", uniforms); var shaders = new JsonArray();
            int count = GL20.glGetProgrami(id, GL20.GL_ATTACHED_SHADERS);
            if (count > 8) throw new IllegalStateException("Unexpected attached shader count");
            if (count > 0) {
                int[] shaderIds = new int[count]; GL20.glGetAttachedShaders(id, (int[])null, shaderIds);
                for (int shader : shaderIds) {
                    var source = new JsonObject(); source.addProperty("shader_id", shader);
                    source.addProperty("type", GL20.glGetShaderi(shader, GL20.GL_SHADER_TYPE));
                    int length = GL20.glGetShaderi(shader, GL20.GL_SHADER_SOURCE_LENGTH);
                    if (length > 524288) throw new IllegalStateException("Attached source exceeds bounded diagnostic size");
                    String text = GL20.glGetShaderSource(shader);
                    source.addProperty("source_sha256", hash(text.getBytes(StandardCharsets.UTF_8)));
                    source.addProperty("source_bytes", text.getBytes(StandardCharsets.UTF_8).length);
                    source.addProperty("contains_g07_temporal_function", text.contains("g07TemporalAo")); int g07=text.indexOf("g07TemporalAo"); if(g07>=0)source.addProperty("g07_temporal_source_excerpt",text.substring(Math.max(0,g07-16),Math.min(text.length(),g07+4096))); shaders.add(source);
                }
            }
            row.add("attached_shaders", shaders); rows.add(row);
            if ("deferred".equals(passName)) nativeDeferred++;
        }
        if (nativeDeferred != 1) throw new IllegalStateException("Unique selected native deferred pass absent");
        if (Iris.getCurrentPack().orElse(null) != pack || Iris.getPipelineManager().getPipelineNullable() != pipeline
                || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != before)
            throw new IllegalStateException("Pipeline/pack/program binding changed during read-only diagnostic");
        result.addProperty("gl_current_program_before", before); result.addProperty("gl_current_program_after", before);
        result.add("selected_deferred_passes", rows);
        result.addProperty("scope", "Pinned live pipeline-selected deferred pass/program join and actual last-uploaded linked uniforms. On-demand snapshot, no draw-event/physical sample counter, no performance or quality claim. Missing/optimized uniforms remain unknown.");
        return result;
    }

    private static Object field(Object owner, String type, String name) throws Exception {
        if (owner == null || !owner.getClass().getName().equals(type)) throw new IllegalStateException("Unexpected pinned owner: " + type);
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
