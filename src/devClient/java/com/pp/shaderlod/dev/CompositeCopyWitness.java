package com.pp.shaderlod.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pp.shaderlod.dev.mixin.SpatialPassAccessor;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.CompositePass;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL41;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** One untimed original c3 P/Q level-zero falsifier. No replay, copy, target or sampler edits. */
public final class CompositeCopyWitness {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final Gson CANONICAL = new GsonBuilder().disableHtmlEscaping().create();
    private static final String OUTPUT = System.getProperty("shaderlod.dev.compositeCopyWitness", "");
    private static final String PACK = "ComplementaryReimagined_r5.9.3.zip";
    private static final String ARCHIVE = "fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38";
    private static final String OPTIONS = "7744d25cca74c4fee09e7ba383c2f2348fbcfd9bde82e9508bc2e2d9e0b74bef";
    private static final String FRAGMENT = "4c97d4e289d44efd081414b69fcdedd4f405d34653b4397463cd09b244b099cf";
    private static final String VERTEX = "f1672cef8f9aa412955488ab6cf6cfc3d7af5ad77c2d11299dd6038892ee1846";
    private static final Map<String, String> ARTIFACTS = Map.of(
        "iris", "3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83",
        "sodium", "7acd7af2ef75ec883a6872c17494ead65e38025151eb08a1d60e36d34377f403");
    private static final int WIDTH = 1920, HEIGHT = 1200, PIXELS = WIDTH * HEIGHT;
    private static final int[] PACK_KEYS = {GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH,
        GL11.GL_PACK_SKIP_ROWS, GL11.GL_PACK_SKIP_PIXELS, GL11.GL_PACK_SWAP_BYTES,
        GL11.GL_PACK_LSB_FIRST, GL12.GL_PACK_IMAGE_HEIGHT, GL12.GL_PACK_SKIP_IMAGES};
    private record Compiled(Object renderer, WorldRenderingPipeline pipeline, Object pack,
                            Program program, Map<String, String> sourceHashes) {}
    private record Image(int object, int format, int width, int height, int redBits, int greenBits,
                         int blueBits, int alphaBits, int redType, int greenType, int blueType) {}
    private record Texture(Image image, List<Integer> integers, List<Integer> floatBits) {}
    private record TransferState(int active, int texture2D, int packBuffer, List<Integer> pack,
                                 int readFbo, int readBuffer, int drawFbo, int program) {}
    private record Layout(int program, int fbo, int vao, int frame, long context, long thread,
                          int mainTexture, int altTexture, boolean readAlt, int samplerUnit,
                          Texture input, Texture output, List<Integer> viewport,
                          Map<String, Boolean> raster, Map<String, List<Integer>> uniforms) {}
    private record Pending(Compiled compiled, SpatialPassAccessor pass, RenderTargets targets,
                           RenderTarget target, Layout before) {}
    private static Compiled compiled;
    private static final Map<Program, Compiled> CONSTRUCTED = new IdentityHashMap<>();
    private static final JsonArray CONSTRUCTOR_EVENTS = new JsonArray();
    private static Object boundRenderer;
    private static SpatialPassAccessor selectedPass;
    private static RenderTargets targets;
    private static Pending pending;
    private static Path rawDirectory;
    private static boolean done, attemptStarted;
    private static long quadOrdinal;
    private static final JsonObject REPORT = new JsonObject();
    private CompositeCopyWitness() {}

    /** Record bounded startup constructions; bind only the actual world pass. No reload after binding. */
    public static void compiled(Object renderer, WorldRenderingPipeline pipeline, CompositePass group,
            ProgramSource source, Program program, Map<?, String> transformed) {
        if (OUTPUT.isBlank() || done || group != CompositePass.COMPOSITE || source == null
                || !"composite3".equals(source.getName())) return;
        try {
            var event = new JsonObject();
            event.addProperty("renderer_identity", System.identityHashCode(renderer));
            event.addProperty("pipeline_identity", System.identityHashCode(pipeline));
            event.addProperty("program", program == null ? 0 : program.getProgramId());
            event.addProperty("loaded_pack", Iris.getCurrentPackName());
            event.addProperty("world_present", Minecraft.getInstance().level != null);
            event.addProperty("frame_source", SystemTimeUniforms.COUNTER.getAsInt());
            CONSTRUCTOR_EVENTS.add(event); REPORT.add("constructor_events", CONSTRUCTOR_EVENTS);
            require(PACK.equals(Iris.getCurrentPackName()) && program != null && boundRenderer == null
                    && !CONSTRUCTED.containsKey(program), "WRONG_PACK_OR_RECOMPILED_AFTER_WORLD_BIND");
            var hashes = new TreeMap<String, String>();
            hashes.put("transformed_fragment", stageHash(transformed, "FRAGMENT"));
            hashes.put("transformed_vertex", stageHash(transformed, "VERTEX"));
            hashes.put("original_preprocessed_fragment", hash(source.getFragmentSource().orElseThrow().getBytes(StandardCharsets.UTF_8)));
            hashes.put("original_preprocessed_vertex", hash(source.getVertexSource().orElseThrow().getBytes(StandardCharsets.UTF_8)));
            REPORT.add("constructor_source_hashes", JSON.toJsonTree(hashes));
            require(FRAGMENT.equals(hashes.get("transformed_fragment"))
                    && VERTEX.equals(hashes.get("transformed_vertex")), "UNQUALIFIED_C3_TRANSFORMED_SOURCE");
            require(Arrays.equals(source.getDirectives().getDrawBuffers(), new int[]{0})
                    && source.getDirectives().getMipmappedBuffers().isEmpty(), "C3_DIRECTIVES_CHANGED");
            event.add("source_hashes", JSON.toJsonTree(hashes));
            CONSTRUCTED.put(program, new Compiled(renderer, pipeline, source.getParent().getPack(), program, Map.copyOf(hashes)));
            require(CONSTRUCTED.size() <= 4, "STARTUP_C3_CONSTRUCTION_LIMIT");
        } catch (Exception failure) { fail(failure); }
    }

    /** Existing COMPOSITE renderAll HEAD, using existing targets.get(0), never getOrCreate. */
    public static void enter(Object renderer, WorldRenderingPipeline pipeline, CompositePass group,
            List<?> passes, RenderTargets renderTargets) {
        if (OUTPUT.isBlank() || done || group != CompositePass.COMPOSITE) return;
        try {
            require(pending == null, "REENTERED_RENDERER_DURING_PENDING_DRAW");
            selectedPass = null; targets = null;
            var client = Minecraft.getInstance();
            if (client.level == null || client.screen != null || client.isPaused() || !client.isWindowActive()) return;
            require(boundRenderer == null || boundRenderer == renderer, "WORLD_RENDERER_CHANGED_NO_RELOAD");
            for (Object object : passes) {
                if (!(object instanceof SpatialPassAccessor pass) || !"composite3".equals(pass.shaderlod$name())) continue;
                var actual = CONSTRUCTED.get(pass.shaderlod$program());
                require(selectedPass == null && actual != null && actual.renderer == renderer && actual.pipeline == pipeline
                        && actual.pack == Iris.getCurrentPack().orElse(null),
                        "ACTUAL_C3_PROGRAM_ABSENT_OR_DUPLICATED");
                compiled = actual; validateWorld(compiled);
                require(Arrays.equals(pass.shaderlod$drawBuffers(), new int[]{0})
                        && pass.shaderlod$mipmappedBuffers().isEmpty(), "ACTUAL_C3_DIRECTIVES_CHANGED");
                Object computes = passField(pass, "computes");
                require(computes != null && computes.getClass().isArray(), "COMPUTE_INVENTORY_UNKNOWN");
                for (int i = 0; i < Array.getLength(computes); i++)
                    require(Array.get(computes, i) == null, "COMPUTE_COMPANION_UNSUPPORTED");
                selectedPass = pass;
            }
            require(selectedPass != null && renderTargets.get(0) != null, "ACTUAL_C3_OR_EXISTING_RT0_ABSENT");
            boundRenderer = renderer;
            targets = renderTargets;
        } catch (Exception failure) { fail(failure); }
    }

    /** Existing quad HEAD. Other draws do no observation; only one matching draw is attempted. */
    public static void beforeQuad() {
        if (OUTPUT.isBlank() || done || selectedPass == null || compiled == null) return;
        try {
            require(pending == null, "NESTED_QUAD_WHILE_C3_PENDING");
            if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != compiled.program.getProgramId()) return;
            require(!attemptStarted, "SECOND_ATTEMPT_FORBIDDEN"); attemptStarted = true;
            reserveOutput(); preflight(); verifyOriginal();
            RenderTarget target = targets.get(0);
            Layout before = inspect(compiled, selectedPass, targets, target);
            pending = new Pending(compiled, selectedPass, targets, target, before);
            REPORT.add("quad_head", JSON.toJsonTree(before));
            REPORT.addProperty("quad_observation_ordinal", ++quadOrdinal);
            REPORT.addProperty("renderer_identity", System.identityHashCode(compiled.renderer));
            REPORT.addProperty("pipeline_identity", System.identityHashCode(compiled.pipeline));
            REPORT.addProperty("pack_identity", System.identityHashCode(compiled.pack));
            REPORT.addProperty("render_targets_identity", System.identityHashCode(targets));
            REPORT.addProperty("render_target_identity", System.identityHashCode(target));
            REPORT.addProperty("pass_identity", System.identityHashCode(selectedPass));
        } catch (Exception failure) { fail(failure); }
    }

    /** Exact matching quad RETURN, before CompositeRenderer can start composite4 setupMipmapping. */
    public static void afterQuad() {
        if (OUTPUT.isBlank() || done || pending == null) return;
        Pending draw = pending; pending = null; done = true;
        TransferState saved = null;
        List<String> restorationFailures = new ArrayList<>();
        ByteBuffer p = null, q = null, pf = null, qf = null;
        Exception failure = null;
        boolean packedEqual = false, finite = false, numericQualified = false;
        try {
            Layout after = inspect(draw.compiled, draw.pass, draw.targets, draw.target);
            REPORT.add("quad_return", JSON.toJsonTree(after));
            require(after.equals(draw.before), "C3_HEAD_RETURN_IDENTITY_OR_STATE_CHANGED");
            saved = transferState();
            REPORT.add("transfer_entry_state", JSON.toJsonTree(saved));
            canonicalPackState();
            p = readVerified(after.input.image, false, "P-level0-packed");
            q = readVerified(after.output.image, false, "Q-level0-packed");
            JsonObject comparison = comparePacked(p, q);
            REPORT.add("packed_comparison", comparison);
            packedEqual = comparison.get("differing_texels").getAsLong() == 0;
            finite = comparison.get("P_nonfinite_channels").getAsLong() == 0
                    && comparison.get("Q_nonfinite_channels").getAsLong() == 0;
            pf = readVerified(after.input.image, true, "P-level0-rgba32f");
            qf = readVerified(after.output.image, true, "Q-level0-rgba32f");
            JsonObject pn = qualifyNumeric(p, pf), qn = qualifyNumeric(q, qf);
            REPORT.add("P_numeric_qualification", pn); REPORT.add("Q_numeric_qualification", qn);
            numericQualified = pn.get("qualified").getAsBoolean() && qn.get("qualified").getAsBoolean();
        } catch (Exception problem) { failure = problem; }
        finally {
            if (saved != null) {
                TransferState state = saved;
                for (int i = 0; i < PACK_KEYS.length; i++) {
                    final int j = i;
                    restore(() -> GL11.glPixelStorei(PACK_KEYS[j], state.pack.get(j)), restorationFailures);
                }
                restore(() -> GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, state.packBuffer), restorationFailures);
                restore(() -> GL13.glActiveTexture(state.active), restorationFailures);
                restore(() -> GL11.glBindTexture(GL11.GL_TEXTURE_2D, state.texture2D), restorationFailures);
                restore(() -> require(transferState().equals(state), "TRANSFER_STATE_NOT_RESTORED"), restorationFailures);
                restore(() -> require(inspect(draw.compiled, draw.pass, draw.targets, draw.target).equals(draw.before),
                        "POST_READBACK_LAYOUT_OR_STORAGE_CHANGED"), restorationFailures);
            }
            for (ByteBuffer bytes : new ByteBuffer[]{p, q, pf, qf}) if (bytes != null) MemoryUtil.memFree(bytes);
        }
        REPORT.add("restoration_failures", JSON.toJsonTree(restorationFailures));
        if (failure != null) REPORT.addProperty("failure", failure.toString());
        REPORT.addProperty("status", failure != null || !restorationFailures.isEmpty() || !numericQualified
                ? "unknown_failed_copy_observation" : !packedEqual ? "original_same_frame_packed_copy_mismatch"
                : !finite ? "packed_equal_nonfinite_qualified_only" : "original_same_frame_finite_packed_copy_equal");
        finish();
    }

    public static void leave() {
        if (OUTPUT.isBlank() || done) return;
        if (pending != null) { pending = null; fail(new IllegalStateException("MATCHED_QUAD_RETURN_MISSING")); }
        selectedPass = null; targets = null;
    }

    private static void preflight() {
        require(GL.getCapabilities().OpenGL41 && GLFW.glfwGetCurrentContext() != 0, "GL41_CURRENT_CONTEXT_REQUIRED");
        // compare=off is a nonempty launcher tag, so missing=false via Boolean.getBoolean is insufficient.
        for (String flag : List.of("gpuTiming", "cpuTiming", "cpuLayerTiming"))
            require("false".equals(System.getProperty("shaderlod.dev." + flag)), "EXPLICIT_FALSE_REQUIRED:" + flag);
        for (String flag : List.of("benchmark", "cloudSamplerProbe", "spatialLayoutProbe", "pixelCoverage",
                "pixelHistoryCapture", "workTrace", "workTraceOnly", "measureWorkload", "measureOnly"))
            require(!Boolean.parseBoolean(System.getProperty("shaderlod.dev." + flag, "false")), "DIAGNOSTIC_ENABLED:" + flag);
        for (String flag : List.of("bslMipWitness", "bslMipParity", "blissChainWitness", "sildurReceiverWitness",
                "makeupCloudWitness", "settledConsumerUniforms"))
            require(System.getProperty("shaderlod.dev." + flag, "").isBlank(), "OTHER_WITNESS_ENABLED:" + flag);
        REPORT.addProperty("compare_launcher_tag", System.getProperty("shaderlod.dev.compare", ""));
        REPORT.addProperty("gl_vendor", GL11.glGetString(GL11.GL_VENDOR));
        REPORT.addProperty("gl_renderer", GL11.glGetString(GL11.GL_RENDERER));
        REPORT.addProperty("gl_version", GL11.glGetString(GL11.GL_VERSION));
    }

    private static void verifyOriginal() throws Exception {
        validateWorld(compiled);
        var values = new TreeMap<String, String>();
        WorkloadInputs.effectiveOptions().entrySet().forEach(entry -> values.put(entry.getKey(), entry.getValue().getAsString()));
        REPORT.add("effective_options", JSON.toJsonTree(values));
        String digest = hash(CANONICAL.toJson(values).getBytes(StandardCharsets.UTF_8));
        REPORT.addProperty("effective_options_sha256", digest);
        require(values.size() == 398 && OPTIONS.equals(digest) && "0".equals(values.get("WORLD_BLUR"))
                && "-1".equals(values.get("MOTION_BLUR_EFFECT")), "NATIVE398_OPTIONS_CHANGED");
        Path archive = Iris.getShaderpacksDirectory().resolve(PACK);
        require(Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(archive), "ORIGINAL_ARCHIVE_NOT_REGULAR");
        String actualArchive = LoadedArtifactEvidence.fileSha256(archive);
        REPORT.addProperty("original_archive_sha256", actualArchive); require(ARCHIVE.equals(actualArchive), "ORIGINAL_ARCHIVE_CHANGED");
        var versions = new TreeMap<String, String>();
        for (var mod : ModList.get().getMods()) versions.put(mod.getModId(), mod.getVersion().toString());
        REPORT.add("loaded_mod_versions", JSON.toJsonTree(versions));
        require("1.21.1".equals(versions.get("minecraft")) && "21.1.253".equals(versions.get("neoforge")), "UNPINNED_LOADER_OR_MC");
        require(!versions.containsKey("distanthorizons") && !versions.containsKey("voxy") && !versions.containsKey("shaderlod"),
                "ORIGINAL_MINIMAL_OFF_RUNTIME_REQUIRED");
        var files = new JsonArray();
        for (var pin : ARTIFACTS.entrySet()) {
            int matches = 0;
            for (var file : ModList.get().getModFiles()) {
                if (file.getMods().stream().noneMatch(mod -> mod.getModId().equals(pin.getKey()))) continue;
                matches++;
                Path path = file.getFile().getFilePath();
                var receipt = new JsonObject(); receipt.addProperty("mod", pin.getKey()); receipt.addProperty("path", path.toString());
                files.add(receipt); REPORT.add("loaded_pinned_artifacts", files);
                require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "LOADED_ARTIFACT_NOT_REGULAR:" + pin.getKey());
                String sha = LoadedArtifactEvidence.fileSha256(path); receipt.addProperty("sha256", sha);
                require(pin.getValue().equals(sha), "LOADED_ARTIFACT_PIN_MISMATCH:" + pin.getKey());
            }
            require(matches == 1, "LOADED_ARTIFACT_JOIN_AMBIGUOUS:" + pin.getKey());
        }
    }

    private static void validateWorld(Compiled c) {
        var client = Minecraft.getInstance();
        require(client.level != null && client.level.dimension().location().toString().equals("minecraft:overworld")
                && PACK.equals(Iris.getCurrentPackName()) && !Iris.isFallback()
                && Iris.getCurrentPack().orElse(null) == c.pack
                && Iris.getPipelineManager().getPipelineNullable() == c.pipeline, "WORLD_PACK_PIPELINE_CHANGED");
        require(client.getMainRenderTarget().width == WIDTH && client.getMainRenderTarget().height == HEIGHT
                && client.getWindow().getWidth() == WIDTH && client.getWindow().getHeight() == HEIGHT
                && client.isWindowActive() && !client.isPaused() && client.screen == null, "NATIVE_FULL_SURFACE_OR_FOCUS_CHANGED");
    }

    private static Layout inspect(Compiled c, SpatialPassAccessor pass, RenderTargets all, RenderTarget target) throws Exception {
        validateWorld(c);
        require(all == targets && all.get(0) == target && pass == selectedPass && pass.shaderlod$program() == c.program,
                "TARGET_OR_PASS_OBJECT_CHANGED");
        require(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == c.program.getProgramId()
                && GL20.glGetProgrami(c.program.getProgramId(), GL20.GL_LINK_STATUS) == GL11.GL_TRUE, "CURRENT_PROGRAM_JOIN_FAILED");
        Object fbo = passField(pass, "framebuffer");
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        require(fbo instanceof GlFramebuffer && ((GlFramebuffer) fbo).getId() == draw && draw > 0, "ACTUAL_PASS_FBO_JOIN_FAILED");
        require(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "INCOMPLETE_FBO");
        int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        require(Arrays.equals(viewport, new int[]{0, 0, WIDTH, HEIGHT}), "PARTIAL_VIEWPORT");
        int n = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS); require(n > 0 && n <= 32, "DRAW_BUFFER_LIMIT");
        for (int i = 0; i < n; i++) require(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + i)
                == (i == 0 ? GL30.GL_COLOR_ATTACHMENT0 : GL11.GL_NONE), "DRAW_BUFFER_MAPPING_CHANGED");
        require(GL11.glGetInteger(GL41.GL_PROGRAM_PIPELINE_BINDING) == 0
                && GL11.glGetInteger(GL40.GL_TRANSFORM_FEEDBACK_BUFFER_ACTIVE) == 0, "OTHER_PIPELINE_OR_TRANSFORM_FEEDBACK");
        require(GL11.glGetInteger(GL13.GL_SAMPLE_BUFFERS) == 0 && GL11.glGetInteger(GL13.GL_SAMPLES) == 0, "MULTISAMPLE_UNSUPPORTED");
        var raster = new TreeMap<String, Boolean>();
        for (int flag : new int[]{GL11.GL_BLEND, GL11.GL_DEPTH_TEST, GL11.GL_STENCIL_TEST, GL11.GL_SCISSOR_TEST,
                GL30.GL_RASTERIZER_DISCARD, GL11.GL_COLOR_LOGIC_OP, GL30.GL_FRAMEBUFFER_SRGB}) {
            boolean value = flag == GL11.GL_BLEND ? GL30.glIsEnabledi(flag, 0) : GL11.glIsEnabled(flag);
            raster.put(Integer.toString(flag), value); require(!value, "PARTIAL_OR_NONIDENTITY_RASTER_STATE:" + flag);
        }
        // Exact pinned Iris quad vertices and MC quad indices are CCW. The pinned
        // c3 vertex transform scales x/y positively; BACK+CCW culling retains both triangles.
        boolean culling = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int cullMode = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
        int frontFace = GL11.glGetInteger(GL11.GL_FRONT_FACE);
        REPORT.addProperty("cull_enabled", culling); REPORT.addProperty("cull_mode", cullMode);
        REPORT.addProperty("front_face", frontFace);
        require(!culling || (cullMode == GL11.GL_BACK && frontFace == GL11.GL_CCW), "PINNED_QUAD_WOULD_BE_CULLED");
        raster.put("cull_enabled", culling); raster.put("cull_back", cullMode == GL11.GL_BACK);
        raster.put("front_ccw", frontFace == GL11.GL_CCW);
        raster.put("dither", GL11.glIsEnabled(GL11.GL_DITHER)); // observed; not changed or silently normalized
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var mask = stack.malloc(4); GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK, 0, mask);
            for (int i = 0; i < 4; i++) require(mask.get(i) != 0, "PARTIAL_COLOR_MASK");
        }
        int[] polygon = new int[2]; GL11.glGetIntegerv(GL11.GL_POLYGON_MODE, polygon);
        require(Arrays.equals(polygon, new int[]{GL11.GL_FILL, GL11.GL_FILL}), "NONFILL_QUAD");
        for (int flag : new int[]{GL15.GL_SAMPLES_PASSED, GL33.GL_ANY_SAMPLES_PASSED, GL33.GL_TIME_ELAPSED,
                GL30.GL_PRIMITIVES_GENERATED, GL30.GL_TRANSFORM_FEEDBACK_PRIMITIVES_WRITTEN})
            require(GL15.glGetQueryi(flag, GL15.GL_CURRENT_QUERY) == 0, "ACTIVE_QUERY_UNTIMED_ONLY");
        Map<String, List<Integer>> uniforms = uniforms(c.program.getProgramId());
        int unit = uniforms.get("colortex0").get(1);
        require(unit >= 0 && unit < GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS), "SAMPLER_UNIT_INVALID");
        int savedSamplerUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            require(GL11.glGetInteger(GL33.GL_SAMPLER_BINDING) == 0, "SAMPLER_OVERRIDE");
        } finally { GL13.glActiveTexture(savedSamplerUnit); }
        int main = target.getMainTexture(), alt = target.getAltTexture();
        require(main > 0 && alt > 0 && main != alt, "RT0_STORAGE_ALIAS");
        boolean readAlt = pass.shaderlod$stageReadsFromAlt().contains(0);
        int input = readAlt ? alt : main, output = readAlt ? main : alt;
        int attachment = GL30.GL_COLOR_ATTACHMENT0;
        require(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) == GL11.GL_TEXTURE
                && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) == output
                && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL) == 0
                && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE) == 0
                && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment, GL32.GL_FRAMEBUFFER_ATTACHMENT_LAYERED) == 0,
                "OUTPUT_NOT_EXPECTED_ORDINARY_LEVEL0_RT0");
        TransferState state = transferState();
        Texture p = null, q = null;
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == input, "COLORTEX0_PHYSICAL_INPUT_MISMATCH");
            p = texture(input);
            // Bind output only on the original active unit. The sampler-unit binding is never overwritten.
            GL13.glActiveTexture(state.active); q = texture(output);
        } finally {
            GL13.glActiveTexture(state.active); GL11.glBindTexture(GL11.GL_TEXTURE_2D, state.texture2D);
            require(transferState().equals(state), "INSPECTION_TRANSFER_STATE_CHANGED");
        }
        return new Layout(c.program.getProgramId(), draw, GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                SystemTimeUniforms.COUNTER.getAsInt(), GLFW.glfwGetCurrentContext(), Thread.currentThread().threadId(),
                main, alt, readAlt, unit, p, q, list(viewport), Map.copyOf(raster), uniforms);
    }

    private static Map<String, List<Integer>> uniforms(int program) {
        int count = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
        require(count > 0 && count <= 128 && GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORM_MAX_LENGTH) <= 1024,
                "C3_UNIFORM_INVENTORY_UNBOUNDED");
        var result = new TreeMap<String, List<Integer>>();
        var inventory = new JsonObject(); REPORT.add("linked_uniform_inventory", inventory);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var size = stack.mallocInt(1); var type = stack.mallocInt(1);
            for (int index = 0; index < count; index++) {
                String name = GL20.glGetActiveUniform(program, index, 1024, size, type);
                int location = GL20.glGetUniformLocation(program, name), kind = type.get(0);
                var identity = new JsonObject(); identity.addProperty("type", kind);
                identity.addProperty("size", size.get(0)); identity.addProperty("location", location);
                inventory.add(name, identity);
                require(size.get(0) == 1 && location >= 0, "C3_UNIFORM_ARRAY_OR_LOCATION_UNKNOWN");
                if (kind == GL20.GL_SAMPLER_2D) {
                    require(name.equals("colortex0"), "C3_ADDITIONAL_SAMPLED_INPUT");
                    result.put(name, List.of(location, GL20.glGetUniformi(program, location)));
                } else if (kind == GL11.GL_INT || kind == GL20.GL_BOOL || kind == GL20.GL_INT_VEC2
                        || kind == GL20.GL_INT_VEC3 || kind == GL20.GL_INT_VEC4 || kind == GL20.GL_BOOL_VEC2
                        || kind == GL20.GL_BOOL_VEC3 || kind == GL20.GL_BOOL_VEC4) {
                    int components = switch (kind) {
                        case GL20.GL_INT_VEC2, GL20.GL_BOOL_VEC2 -> 2;
                        case GL20.GL_INT_VEC3, GL20.GL_BOOL_VEC3 -> 3;
                        case GL20.GL_INT_VEC4, GL20.GL_BOOL_VEC4 -> 4;
                        default -> 1;
                    };
                    var values = stack.mallocInt(components); GL20.glGetUniformiv(program, location, values);
                    var bits = new ArrayList<Integer>(); bits.add(kind); bits.add(location);
                    for (int component = 0; component < components; component++) bits.add(values.get(component));
                    result.put(name, List.copyOf(bits));
                } else {
                    int components = switch (kind) {
                        case GL11.GL_FLOAT -> 1;
                        case GL20.GL_FLOAT_VEC2 -> 2;
                        case GL20.GL_FLOAT_VEC3 -> 3;
                        case GL20.GL_FLOAT_VEC4, GL20.GL_FLOAT_MAT2 -> 4;
                        case GL20.GL_FLOAT_MAT3 -> 9;
                        case GL20.GL_FLOAT_MAT4 -> 16;
                        default -> throw new IllegalStateException("C3_UNSUPPORTED_UNIFORM_TYPE:" + name + ":" + kind);
                    };
                    var values = stack.mallocFloat(components); GL20.glGetUniformfv(program, location, values);
                    var bits = new ArrayList<Integer>(); bits.add(kind); bits.add(location);
                    for (int component = 0; component < components; component++) bits.add(Float.floatToRawIntBits(values.get(component)));
                    result.put(name, List.copyOf(bits));
                }
            }
        }
        require(result.containsKey("colortex0"), "C3_LINKED_COPY_SAMPLER_UNKNOWN");
        return Map.copyOf(result);
    }

    /** Every local texture bind restores the binding on the selected unit, even on rejected storage. */
    private static Texture texture(int id) {
        require(id > 0 && GL11.glIsTexture(id), "TEXTURE_NOT_LIVE");
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == id, "TEXTURE_NOT_2D");
            Image image = new Image(id, level(GL11.GL_TEXTURE_INTERNAL_FORMAT), level(GL11.GL_TEXTURE_WIDTH), level(GL11.GL_TEXTURE_HEIGHT),
                    level(GL11.GL_TEXTURE_RED_SIZE), level(GL11.GL_TEXTURE_GREEN_SIZE), level(GL11.GL_TEXTURE_BLUE_SIZE), level(GL11.GL_TEXTURE_ALPHA_SIZE),
                    level(GL30.GL_TEXTURE_RED_TYPE), level(GL30.GL_TEXTURE_GREEN_TYPE), level(GL30.GL_TEXTURE_BLUE_TYPE));
            require(image.format == GL30.GL_R11F_G11F_B10F && image.width == WIDTH && image.height == HEIGHT
                    && image.redBits == 11 && image.greenBits == 11 && image.blueBits == 10 && image.alphaBits == 0
                    && image.redType == GL11.GL_FLOAT && image.greenType == GL11.GL_FLOAT && image.blueType == GL11.GL_FLOAT,
                    "NATIVE_R11F_G11F_B10F_STORAGE_REQUIRED");
            List<Integer> integers = new ArrayList<>();
            for (int key : new int[]{GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_TEXTURE_MAG_FILTER, GL12.GL_TEXTURE_BASE_LEVEL,
                    GL12.GL_TEXTURE_MAX_LEVEL, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_TEXTURE_WRAP_S, GL11.GL_TEXTURE_WRAP_T,
                    GL33.GL_TEXTURE_SWIZZLE_R, GL33.GL_TEXTURE_SWIZZLE_G, GL33.GL_TEXTURE_SWIZZLE_B, GL33.GL_TEXTURE_SWIZZLE_A})
                integers.add(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, key));
            require(Set.of(GL11.GL_LINEAR, GL11.GL_LINEAR_MIPMAP_LINEAR).contains(integers.get(0))
                    && integers.get(1) == GL11.GL_LINEAR && integers.get(2) == 0 && integers.get(3) == 1000
                    && integers.get(4) == GL11.GL_NONE && integers.get(5) == GL12.GL_CLAMP_TO_EDGE && integers.get(6) == GL12.GL_CLAMP_TO_EDGE
                    && integers.subList(7, 11).equals(List.of(GL11.GL_RED, GL11.GL_GREEN, GL11.GL_BLUE, GL11.GL_ALPHA)),
                    "UNQUALIFIED_TEXTURE_LEVEL_SAMPLING_OR_SWIZZLE");
            List<Integer> floats = new ArrayList<>();
            for (int key : new int[]{GL12.GL_TEXTURE_MIN_LOD, GL12.GL_TEXTURE_MAX_LOD, GL14.GL_TEXTURE_LOD_BIAS})
                floats.add(Float.floatToRawIntBits(GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, key)));
            require(floats.equals(List.of(Float.floatToRawIntBits(-1000f), Float.floatToRawIntBits(1000f), 0)), "LOD_CLAMPS_CHANGED");
            if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                float aniso = GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT);
                require(aniso == 1f, "ANISOTROPY_OVERRIDE"); floats.add(Float.floatToRawIntBits(aniso));
            }
            return new Texture(image, List.copyOf(integers), List.copyOf(floats));
        } finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding); }
    }

    private static int level(int key) { return GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, key); }
    private static TransferState transferState() {
        var store = new ArrayList<Integer>(); for (int key : PACK_KEYS) store.add(GL11.glGetInteger(key));
        return new TransferState(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),
                GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING), List.copyOf(store),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), GL11.glGetInteger(GL11.GL_READ_BUFFER),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
    }
    private static void canonicalPackState() {
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        for (int i = 0; i < PACK_KEYS.length; i++) GL11.glPixelStorei(PACK_KEYS[i], i == 0 ? 1 : 0);
        require(GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING) == 0, "PACK_PBO_NOT_UNBOUND");
        for (int i = 0; i < PACK_KEYS.length; i++) require(GL11.glGetInteger(PACK_KEYS[i]) == (i == 0 ? 1 : 0), "PACK_STATE_SET_FAILED");
    }

    /** Poison both destinations differently; retain first bytes always and second bytes on incomplete read. */
    private static ByteBuffer readVerified(Image image, boolean rgbaFloat, String label) throws Exception {
        int bytes = Math.multiplyExact(PIXELS, rgbaFloat ? 16 : 4);
        ByteBuffer first = MemoryUtil.memAlloc(bytes).order(ByteOrder.nativeOrder()), second = null;
        boolean returned = false;
        try {
            second = MemoryUtil.memAlloc(bytes).order(ByteOrder.nativeOrder());
            for (int i = 0; i < bytes; i++) { first.put(i, (byte) 0x35); second.put(i, (byte) 0xca); }
            Exception transferFailure = null;
            try { read(image, rgbaFloat, first); read(image, rgbaFloat, second); }
            catch (Exception problem) { transferFailure = problem; }
            var receipt = new JsonObject(); receipt.addProperty("texture", image.object);
            receipt.addProperty("bytes", bytes); receipt.addProperty("byte_order", ByteOrder.nativeOrder().toString());
            receipt.addProperty("first_sha256", hash(first)); receipt.addProperty("second_sha256", hash(second));
            receipt.addProperty("full_destinations_match", equal(first, second));
            if (transferFailure != null) receipt.addProperty("transfer_failure", transferFailure.toString());
            REPORT.add(label, receipt);
            saveBytes(label + ".bin", first);
            if (transferFailure != null || !equal(first, second)) saveBytes(label + "-second.bin", second);
            if (transferFailure != null) throw transferFailure;
            require(equal(first, second), "READBACK_NOT_FULLY_WRITTEN_OR_UNSTABLE:" + label);
            returned = true; return first;
        } finally { if (second != null) MemoryUtil.memFree(second); if (!returned) MemoryUtil.memFree(first); }
    }
    private static void read(Image image, boolean rgbaFloat, ByteBuffer bytes) {
        int old = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, image.object);
            require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == image.object, "READBACK_BIND_FAILED");
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, rgbaFloat ? GL11.GL_RGBA : GL11.GL_RGB,
                    rgbaFloat ? GL11.GL_FLOAT : GL30.GL_UNSIGNED_INT_10F_11F_11F_REV, bytes);
        } finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D, old); }
    }

    /** Packed GL readback bits, not an assertion of raw GPU-memory or NaN-payload preservation. */
    static JsonObject comparePacked(ByteBuffer p, ByteBuffer q) {
        require(p.remaining() == PIXELS * 4 && q.remaining() == PIXELS * 4, "PACKED_COMPARE_SIZE");
        long differingTexels = 0, differingChannels = 0, differingBits = 0, differingBorderTexels = 0;
        long finitePairs = 0, numericDifferences = 0, pNonfinite = 0, qNonfinite = 0, nonfiniteCategoryDifferences = 0;
        long[] pn = new long[5], qn = new long[5]; double maximum = 0;
        JsonArray examples = new JsonArray();
        for (int i = 0; i < PIXELS; i++) {
            int a = p.getInt(i * 4), b = q.getInt(i * 4);
            if (a != b) {
                differingTexels++; differingBits += Integer.bitCount(a ^ b);
                int x = i % WIDTH, y = i / WIDTH;
                if (x == 0 || x == WIDTH - 1 || y == 0 || y == HEIGHT - 1) differingBorderTexels++;
                if (examples.size() < 16) { var e = new JsonObject(); e.addProperty("x", x); e.addProperty("y", y);
                    e.addProperty("P_u32_hex", String.format("%08x", a)); e.addProperty("Q_u32_hex", String.format("%08x", b)); examples.add(e); }
            }
            for (int c = 0; c < 3; c++) {
                int av = component(a, c), bv = component(b, c), mantissa = c == 2 ? 5 : 6;
                if (av != bv) differingChannels++;
                int ac = category(av, mantissa), bc = category(bv, mantissa); pn[ac]++; qn[bc]++;
                if (ac >= 3) pNonfinite++; if (bc >= 3) qNonfinite++;
                if ((ac >= 3 || bc >= 3) && ac != bc) nonfiniteCategoryDifferences++;
                if (ac < 3 && bc < 3) {
                    finitePairs++; double delta = Math.abs(decode(av, mantissa) - decode(bv, mantissa));
                    if (delta != 0) numericDifferences++; maximum = Math.max(maximum, delta);
                }
            }
        }
        var result = new JsonObject(); result.addProperty("texels", PIXELS); result.addProperty("channels", (long) PIXELS * 3);
        result.addProperty("differing_texels", differingTexels); result.addProperty("differing_channels", differingChannels);
        result.addProperty("differing_raw_readback_bits", differingBits); result.addProperty("differing_border_texels", differingBorderTexels);
        result.addProperty("finite_channel_pairs", finitePairs); result.addProperty("finite_numeric_differences", numericDifferences);
        result.addProperty("maximum_absolute_finite_error", maximum);
        result.addProperty("P_nonfinite_channels", pNonfinite); result.addProperty("Q_nonfinite_channels", qNonfinite);
        result.addProperty("nonfinite_category_differences", nonfiniteCategoryDifferences);
        result.add("P_categories_zero_subnormal_normal_inf_nan", JSON.toJsonTree(pn));
        result.add("Q_categories_zero_subnormal_normal_inf_nan", JSON.toJsonTree(qn)); result.add("first_differences", examples);
        return result;
    }
    private static JsonObject qualifyNumeric(ByteBuffer packed, ByteBuffer rgba) {
        long finiteMismatch = 0, nonfiniteCategoryMismatch = 0, nanChannels = 0, alphaNotOne = 0;
        for (int i = 0; i < PIXELS; i++) {
            int word = packed.getInt(i * 4);
            for (int c = 0; c < 3; c++) {
                int bits = component(word, c), mantissa = c == 2 ? 5 : 6, kind = category(bits, mantissa);
                float f = rgba.getFloat(i * 16 + c * 4);
                if (kind < 3) { if (!Float.isFinite(f) || (double) f != decode(bits, mantissa)) finiteMismatch++; }
                else if (kind == 3) { if (f != Float.POSITIVE_INFINITY) nonfiniteCategoryMismatch++; }
                else { nanChannels++; if (!Float.isNaN(f)) nonfiniteCategoryMismatch++; }
            }
            if (rgba.getInt(i * 16 + 12) != Float.floatToRawIntBits(1f)) alphaNotOne++;
        }
        var result = new JsonObject(); result.addProperty("finite_decode_mismatch_channels", finiteMismatch);
        result.addProperty("nonfinite_category_mismatch_channels", nonfiniteCategoryMismatch);
        result.addProperty("nan_payloads_not_certified_channels", nanChannels); result.addProperty("readback_alpha_not_one_texels", alphaNotOne);
        result.addProperty("qualified", finiteMismatch == 0 && nonfiniteCategoryMismatch == 0 && alphaNotOne == 0);
        result.addProperty("alpha_scope", "RGB storage RGBA-float readback synthesizes alpha1; not arbitrary RGBA or separate sampled-alpha proof");
        return result;
    }
    static int component(int word, int channel) { return (word >>> (channel == 0 ? 0 : channel == 1 ? 11 : 22)) & (channel == 2 ? 1023 : 2047); }
    static int category(int bits, int mantissa) {
        int exponent = bits >>> mantissa, fraction = bits & ((1 << mantissa) - 1);
        return exponent == 0 ? (fraction == 0 ? 0 : 1) : exponent == 31 ? (fraction == 0 ? 3 : 4) : 2;
    }
    static double decode(int bits, int mantissa) {
        int exponent = bits >>> mantissa, fraction = bits & ((1 << mantissa) - 1);
        if (exponent == 31) return fraction == 0 ? Double.POSITIVE_INFINITY : Double.NaN;
        return exponent == 0 ? Math.scalb((double) fraction, -14 - mantissa)
                : Math.scalb(1.0 + Math.scalb((double) fraction, -mantissa), exponent - 15);
    }

    private static Object passField(Object object, String name) throws Exception {
        // Exact pinned Pass fields; reflection is read-only and unsupported access fails closed.
        Class<?> type = object.getClass();
        while (type != null) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        throw new IllegalStateException("PINNED_PASS_FIELD_ABSENT:" + name);
    }
    private static String stageHash(Map<?, String> stages, String stage) throws Exception {
        String value = stages.entrySet().stream().filter(e -> e.getKey().toString().equals(stage))
                .map(Map.Entry::getValue).findFirst().orElseThrow();
        require(value.length() <= 131072, "SOURCE_BOUND"); return hash(value.getBytes(StandardCharsets.UTF_8));
    }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static String hash(ByteBuffer bytes) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256"); digest.update(bytes.duplicate()); return HexFormat.of().formatHex(digest.digest());
    }
    private static boolean equal(ByteBuffer a, ByteBuffer b) { return a.remaining() == b.remaining() && a.mismatch(b) == -1; }
    private static List<Integer> list(int[] values) { return Arrays.stream(values).boxed().toList(); }
    private static void require(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
    @FunctionalInterface private interface RestoreOperation { void run() throws Exception; }
    private static void restore(RestoreOperation action, List<String> failures) { try { action.run(); } catch (Exception e) { failures.add(e.toString()); } }
    private static void reserveOutput() throws Exception {
        if (rawDirectory != null) return;
        Path output = Path.of(OUTPUT).toAbsolutePath().normalize();
        require(!Files.exists(output, LinkOption.NOFOLLOW_LINKS), "OUTPUT_MUST_BE_FRESH");
        Files.createDirectories(output.getParent());
        Path directory = output.resolveSibling(output.getFileName() + ".raw");
        Files.createDirectory(directory); rawDirectory = directory;
        REPORT.addProperty("raw_directory", directory.toString());
    }
    private static void saveBytes(String name, ByteBuffer bytes) throws Exception {
        try (FileChannel channel = FileChannel.open(rawDirectory.resolve(name), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer source = bytes.duplicate(); while (source.hasRemaining()) channel.write(source);
        }
    }
    private static void fail(Exception failure) {
        done = true; pending = null; selectedPass = null; targets = null;
        REPORT.addProperty("status", "unknown_failed_copy_observation"); REPORT.addProperty("failure", failure.toString()); finish();
    }
    private static void finish() {
        REPORT.addProperty("scope", "One original native398 world0 c3 quad HEAD/RETURN observation before c4 setup. Full packed level0 equality falsifier only; no replay, candidate graph, higher-mip, history, final, lifecycle, motion, DH/PBR or cost acceptance. GL errors are not consumed.");
        REPORT.addProperty("storage_lifetime_scope", "Same RenderTargets/RenderTarget objects, physical IDs/descriptors, GL context/render thread/frame across one synchronous quad and readback callback; no event pumping. Observation ordinal is not a native allocation/resize generation counter. External same-ID storage replacement is not certified.");
        REPORT.addProperty("native_alpha_bits", 0);
        try { reserveOutput(); Files.writeString(Path.of(OUTPUT), JSON.toJson(REPORT), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); }
        catch (Exception failure) { com.mojang.logging.LogUtils.getLogger().warn("Composite copy witness report unavailable; preserve existing output and raw files", failure); }
    }
}
