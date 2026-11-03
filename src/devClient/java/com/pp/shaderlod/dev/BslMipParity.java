package com.pp.shaderlod.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pp.shaderlod.dev.mixin.SpatialPassAccessor;
import java.nio.ByteBuffer;
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
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.pipeline.CompositePass;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
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

/** One original-BSL c6 or c7 replay into private outputs. Never a timing or lifecycle probe. */
public final class BslMipParity {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String OUTPUT = System.getProperty("shaderlod.dev.bslMipParity", "");
    private static final String PASS = System.getProperty("shaderlod.dev.bslMipParityPass", "composite7");
    private static final boolean C6 = PASS.equals("composite6");
    private static final int[] OUTPUT_TARGETS = C6 ? new int[]{1} : new int[]{1, 2};
    private static final String C6_FRAGMENT = "e0254781a88538342415f46a9c6819656c378974f0cff8bde6a4eabc0925da9e";
    private static final String C6_VERTEX = "ed19cdc5fd2206a97218243695b3571aa35920deb59a72230f72b88e0277aac8";
    private static final String PACK = "BSL_v10.1.8.zip";
    private static final String ARCHIVE = "36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5";
    private static final String OPTIONS = "41a861dd87d34ad2d1a0dde900cdcc3cc813cf3d4338c3a8041b59c7493a265f";
    private static final String FRAGMENT = "353ed269613579cc6ef82947045e76d6b33c6e562ad6a89053196440a1dd6478";
    private static final String VERTEX = "27a778594ffaf7a902e7adfaa886908f0acfd09d3a99f3e67d95bb2d03e5e6b2";
    // Actual constructor strings: same used code; indentation differs and one uncalled
    // pure helper is absent. Exact correspondence retained in g02-parity-source-correspondence.json.
    private static final String CONSTRUCTOR_FRAGMENT = "923fe4ab8b902eb66f46a1dc8b16372cc1f4d811ecf8dc177ff235cb9a2631de";
    private static final String CONSTRUCTOR_VERTEX = "ed19cdc5fd2206a97218243695b3571aa35920deb59a72230f72b88e0277aac8";
    // Explicit finite allocation tuples; actual viewport, storage and linked
    // uniforms must still independently match before any private replay.
    private static final String EXTENT = System.getProperty("shaderlod.dev.bslMipParityExtent", "1920x1200");
    private static final int WIDTH = switch (EXTENT) { case "1920x1200" -> 1920; case "2560x1600" -> 2560; default -> -1; };
    private static final int HEIGHT = switch (EXTENT) { case "1920x1200" -> 1200; case "2560x1600" -> 1600; default -> -1; };
    private static final Set<String> SAMPLERS = C6 ? Set.of("colortex1") : Set.of("colortex1", "colortex2", "depthtex1");
    private static final int[] PIXEL_STATE = {
        GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH, GL11.GL_PACK_SKIP_ROWS,
        GL11.GL_PACK_SKIP_PIXELS, GL11.GL_PACK_SWAP_BYTES, GL11.GL_PACK_LSB_FIRST,
        GL12.GL_PACK_IMAGE_HEIGHT, GL12.GL_PACK_SKIP_IMAGES,
        GL11.GL_UNPACK_ALIGNMENT, GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_UNPACK_SKIP_ROWS,
        GL11.GL_UNPACK_SKIP_PIXELS, GL11.GL_UNPACK_SWAP_BYTES, GL11.GL_UNPACK_LSB_FIRST,
        GL12.GL_UNPACK_IMAGE_HEIGHT, GL12.GL_UNPACK_SKIP_IMAGES};
    private record Compiled(Object renderer, WorldRenderingPipeline pipeline, Object pack, Program program) {}
    private record Uniform(int type, int location, List<Integer> bits) {}
    private record Image(int object, int format, int width, int height, int depthBits, int depthType) {
        int channels() { return format == GL11.GL_RGB8 ? 3 : 4; }
        int componentBytes() { return format == GL11.GL_RGB8 ? 1 : 2; }
        int bytes() { return Math.multiplyExact(Math.multiplyExact(width, height), channels() * componentBytes()); }
        int externalFormat() { return format == GL11.GL_RGB8 ? GL11.GL_RGB : GL11.GL_RGBA; }
        int externalType() { return format == GL11.GL_RGB8 ? GL11.GL_UNSIGNED_BYTE : GL11.GL_UNSIGNED_SHORT; }
    }
    private record Input(int unit, Image image, int min, int mag) {}
    private record Layout(int program, int framebuffer, int vao, int frame,
                          Map<String, Uniform> uniforms, Map<String, Input> inputs, List<Image> outputs) {}
    private record State(int program, int drawFbo, int readFbo, int readBuffer, int vao,
                         int arrayBuffer, int elementBuffer, int active, int texture2D,
                         int packBuffer, int unpackBuffer, List<Integer> viewport,
                         List<Integer> drawBuffers, List<Integer> pixelState, List<Integer> polygonMode) {}
    private record Pending(Compiled compiled, SpatialPassAccessor pass, RenderTargets targets, Layout before) {}
    private static final Map<Program, Compiled> COMPILED = new IdentityHashMap<>();
    private static Compiled selected;
    private static SpatialPassAccessor selectedPass;
    private static RenderTargets targets;
    private static Object firstRenderer;
    private static Pending pending;
    private static boolean done, replaying;
    private BslMipParity() {}

    public static boolean replaying() { return replaying; }

    /** Root forwards the existing createProgram RETURN, with its exact transformed stages. */
    public static void compiled(Object renderer, WorldRenderingPipeline pipeline, CompositePass group,
            ProgramSource source, Program program, Map<?, String> transformed) {
        if (OUTPUT.isBlank() || done || group != CompositePass.COMPOSITE || source == null || program == null
                || !source.getName().equals(PASS) || !PACK.equals(Iris.getCurrentPackName())) return;
        try {
            if (!knownConstructorSource(transformed)) {
                var diagnostic = new JsonObject();
                diagnostic.addProperty("status", "unknown_failed_parity_primitive");
                diagnostic.addProperty("failure", "UNKNOWN_"+PASS+"_CONSTRUCTOR_SOURCE");
                diagnostic.addProperty("pass", PASS);
                diagnostic.addProperty("actual_fragment_sha256", stageHash(transformed, "FRAGMENT"));
                diagnostic.addProperty("actual_vertex_sha256", stageHash(transformed, "VERTEX"));
                diagnostic.addProperty("expected_fragment_sha256", C6 ? C6_FRAGMENT : FRAGMENT);
                diagnostic.addProperty("expected_vertex_sha256", C6 ? C6_VERTEX : VERTEX);
                Path directory = Path.of(OUTPUT).toAbsolutePath().resolveSibling(Path.of(OUTPUT).getFileName()+"-source-refusal");
                Files.createDirectory(directory);
                for (var entry : transformed.entrySet()) if(entry.getValue()!=null)
                    Files.writeString(directory.resolve(entry.getKey().toString()+".glsl"), entry.getValue(), StandardOpenOption.CREATE_NEW);
                diagnostic.addProperty("stages", directory.toString());
                done = true; write(diagnostic); return;
            }
            require(knownConstructorSource(transformed), "UNKNOWN_C7_CONSTRUCTOR_SOURCE");
            require(Arrays.equals(source.getDirectives().getDrawBuffers(), OUTPUT_TARGETS)
                    && source.getDirectives().getMipmappedBuffers().equals(Set.of(1)), "NOT_NATIVE_C7_DIRECTIVES");
            COMPILED.put(program, new Compiled(renderer, pipeline, source.getParent().getPack(), program));
            require(COMPILED.size() <= 4, "COMPILED_GENERATION_LIMIT_NO_RELOAD");
        } catch (Exception failure) { fail(failure); }
    }

    /** Root forwards renderAll HEAD. No renderer, pack or output is mutated here. */
    public static void enter(Object renderer, WorldRenderingPipeline pipeline, CompositePass group,
            List<?> passes, RenderTargets renderTargets) {
        if (OUTPUT.isBlank() || done || replaying) return;
        selected = null; selectedPass = null; targets = null;
        var client = Minecraft.getInstance();
        if (group != CompositePass.COMPOSITE || client.level == null || client.screen != null
                || client.isPaused() || !client.isWindowActive() || !PACK.equals(Iris.getCurrentPackName())) return;
        try {
            require(client.level.dimension().location().toString().equals("minecraft:overworld")
                    && !Iris.isFallback() && Iris.getPipelineManager().getPipelineNullable() == pipeline,
                    "UNKNOWN_WORLD_PIPELINE");
            require(firstRenderer == null || firstRenderer == renderer, "RENDERER_CHANGED_NO_RELOAD");
            firstRenderer = renderer;
            for (Object object : passes) {
                if (!(object instanceof SpatialPassAccessor pass) || !pass.shaderlod$name().equals(PASS)) continue;
                require(selected == null, "DUPLICATE_C7");
                var c = COMPILED.get(pass.shaderlod$program());
                require(c != null && c.renderer == renderer && c.pipeline == pipeline
                        && c.pack == Iris.getCurrentPack().orElse(null)
                        && Arrays.equals(pass.shaderlod$drawBuffers(), OUTPUT_TARGETS)
                        && pass.shaderlod$mipmappedBuffers().equals(Set.of(1)), "ACTUAL_C7_PASS_JOIN_FAILED");
                selected = c; selectedPass = pass;
            }
            require(selected != null, "NO_NATIVE_C7");
            targets = renderTargets;
        } catch (Exception failure) { fail(failure); }
    }

    public static void beforeQuad() {
        if (OUTPUT.isBlank() || done || replaying || selected == null
                || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != selected.program.getProgramId()) return;
        if (SystemTimeUniforms.COUNTER.getAsInt() < 600) return;
        try {
            require(pending == null, "UNPAIRED_NATIVE_QUAD");
            preflight();
            verifyOriginal();
            pending = new Pending(selected, selectedPass, targets, inspect(selected, selectedPass, targets));
        } catch (Exception failure) { fail(failure); }
    }

    /** Root forwards quad RETURN, before Iris restores blend state or starts final. */
    public static void afterQuad() {
        if (OUTPUT.isBlank() || done || replaying || pending == null) return;
        Pending draw = pending; pending = null;
        State saved = null;
        int scratchFbo = 0;
        List<Integer> scratchTextures = new ArrayList<>();
        List<ByteBuffer> nativePixels = new ArrayList<>();
        List<String> restoreFailures = new ArrayList<>();
        JsonObject result = new JsonObject();
        boolean comparedEqual = false, originalUnchanged = false;
        int replayCalls = 0, replayReturns = 0;
        Exception failure = null;
        try {
            Layout after = inspect(draw.compiled, draw.pass, draw.targets);
            require(after.equals(draw.before), "NATIVE_QUAD_CHANGED_INPUTS_UNIFORMS_OR_LAYOUT");
            saved = state();
            result.add("native_layout", JSON.toJsonTree(after));
            result.add("entry_state", JSON.toJsonTree(saved));
            Path rawDirectory = Path.of(OUTPUT).toAbsolutePath().resolveSibling(
                Path.of(OUTPUT).getFileName().toString() + "-pixels");
            Files.createDirectory(rawDirectory);
            var rawFiles = new com.google.gson.JsonArray(); result.add("raw_images", rawFiles);
            if (!C6) {
            ByteBuffer historyInput = readVerified(after.inputs.get("colortex2").image);
            try {
                long populated = 0;
                for (int offset = 0; offset < historyInput.capacity(); offset += 8)
                    if (historyInput.get(offset + 6) != 0 || historyInput.get(offset + 7) != 0) populated++;
                result.addProperty("history_alpha_nonzero_texels", populated);
                rawFiles.add(retain(rawDirectory.resolve("input-history-rgba16.bin"), historyInput));
                require(populated > 0, "HISTORY_ALPHA_STILL_CLEAR");
            } finally { MemoryUtil.memFree(historyInput); }
            }
            for (int index = 0; index < after.outputs.size(); index++) {
                ByteBuffer pixels = readVerified(after.outputs.get(index)); nativePixels.add(pixels);
                rawFiles.add(retain(rawDirectory.resolve("native-output-" + index + ".bin"), pixels));
            }
            List<Image> scratch = new ArrayList<>();
            for (int index = 0; index < after.outputs.size(); index++)
                scratch.add(createScratch(after.outputs.get(index), nativePixels.get(index), scratchTextures));
            int allocatedFbo = GL30.glGenFramebuffers();
            require(allocatedFbo > 0 && allocatedFbo != saved.drawFbo && allocatedFbo != saved.readFbo
                    && !GL30.glIsFramebuffer(allocatedFbo), "SCRATCH_FBO_ID_INVALID");
            scratchFbo = allocatedFbo;
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scratchFbo);
            require(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == scratchFbo, "SCRATCH_FBO_BIND_FAILED");
            for (int index = 0; index < scratch.size(); index++)
                GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + index,
                        GL11.GL_TEXTURE_2D, scratch.get(index).object, 0);
            GL20.glDrawBuffers(C6 ? new int[]{GL30.GL_COLOR_ATTACHMENT0} : new int[]{GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1});
            require(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE,
                    "SCRATCH_FRAMEBUFFER_INCOMPLETE");
            // Restore the original active unit's binding before replay. Sampler units stay untouched.
            GL13.glActiveTexture(saved.active); GL11.glBindTexture(GL11.GL_TEXTURE_2D, saved.texture2D);
            setInputMin(after.inputs.get("colortex1").image.object, GL11.GL_LINEAR);
            verifyReplayInputs(after);
            require(uniforms(after.program).equals(after.uniforms), "UNIFORMS_CHANGED_BEFORE_REPLAY");
            require(GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) == saved.vao
                    && GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == saved.program
                    && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == scratchFbo, "NATIVE_QUAD_BINDING_CHANGED");
            replaying = true;
            try {
                // renderQuad alone: begin/end would overwrite Iris's saved VAO/depth lifecycle.
                replayCalls++;
                FullScreenQuadRenderer.INSTANCE.renderQuad();
                replayReturns++;
            } finally { replaying = false; }
            require(SystemTimeUniforms.COUNTER.getAsInt() == after.frame
                    && uniforms(after.program).equals(after.uniforms), "FRAME_OR_UNIFORMS_CHANGED_DURING_REPLAY");
            var comparisons = new com.google.gson.JsonArray();
            result.add("comparisons", comparisons);
            comparedEqual = true;
            for (int index = 0; index < scratch.size(); index++) {
                ByteBuffer actual = readVerified(scratch.get(index));
                try {
                    rawFiles.add(retain(rawDirectory.resolve("linear-output-" + index + ".bin"), actual));
                    JsonObject comparison = compare(nativePixels.get(index), actual, after.outputs.get(index));
                    comparisons.add(comparison);
                    comparedEqual &= comparison.get("mismatch_bytes").getAsLong() == 0;
                } finally { MemoryUtil.memFree(actual); }
            }
            originalUnchanged = true;
            for (int index = 0; index < after.outputs.size(); index++) {
                ByteBuffer unchanged = readVerified(after.outputs.get(index));
                try { originalUnchanged &= equal(nativePixels.get(index), unchanged); }
                finally { MemoryUtil.memFree(unchanged); }
            }
            require(originalUnchanged, "ORIGINAL_OUTPUT_CHANGED_DURING_PRIVATE_REPLAY");
        } catch (Exception problem) { failure = problem; }
        finally {
            replaying = false;
            if (saved != null) {
                State state = saved;
                Input input = draw.before.inputs.get("colortex1");
                restore(() -> setInputMin(input.image.object, input.min), restoreFailures);
                restore(() -> GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, state.drawFbo), restoreFailures);
                restore(() -> GL20.glDrawBuffers(ints(state.drawBuffers)), restoreFailures);
                restore(() -> GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, state.readFbo), restoreFailures);
                restore(() -> GL11.glReadBuffer(state.readBuffer), restoreFailures);
                restore(() -> GL11.glViewport(state.viewport.get(0), state.viewport.get(1), state.viewport.get(2), state.viewport.get(3)), restoreFailures);
                restore(() -> GL20.glUseProgram(state.program), restoreFailures);
                restore(() -> GL30.glBindVertexArray(state.vao), restoreFailures);
                restore(() -> GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, state.arrayBuffer), restoreFailures);
                restore(() -> GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, state.elementBuffer), restoreFailures);
                restore(() -> GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, state.packBuffer), restoreFailures);
                restore(() -> GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, state.unpackBuffer), restoreFailures);
                for (int i = 0; i < PIXEL_STATE.length; i++) {
                    int parameter = PIXEL_STATE[i], value = state.pixelState.get(i);
                    restore(() -> GL11.glPixelStorei(parameter, value), restoreFailures);
                }
                restore(() -> GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, state.polygonMode.get(0)), restoreFailures);
                restore(() -> { GL13.glActiveTexture(state.active); GL11.glBindTexture(GL11.GL_TEXTURE_2D, state.texture2D); }, restoreFailures);
            }
            int fbo = scratchFbo;
            if (fbo > 0) restore(() -> GL30.glDeleteFramebuffers(fbo), restoreFailures);
            for (int texture : scratchTextures) restore(() -> GL11.glDeleteTextures(texture), restoreFailures);
            for (ByteBuffer bytes : nativePixels) MemoryUtil.memFree(bytes);
            if (saved != null) {
                try {
                    require(saved.equals(state()), "GL_STATE_NOT_RESTORED");
                    require(inspect(draw.compiled, draw.pass, draw.targets).equals(draw.before), "NATIVE_LAYOUT_NOT_RESTORED");
                    verifyOriginal();
                } catch (Exception problem) { restoreFailures.add(problem.toString()); }
            }
        }
        done = true; selected = null; selectedPass = null; targets = null;
        result.addProperty("status", failure != null || !restoreFailures.isEmpty() ? "unknown_failed_parity_primitive"
                : comparedEqual ? "same_frame_"+(C6 ? "c6" : "c7")+"_filter_parity_observed" : "same_frame_"+(C6 ? "c6" : "c7")+"_filter_mismatch");
        if (failure != null) result.addProperty("failure", failure.toString());
        result.add("restore_failures", JSON.toJsonTree(restoreFailures));
        result.addProperty("original_outputs_rechecked_unchanged", originalUnchanged);
        result.addProperty("replay_calls", replayCalls); result.addProperty("replay_returns", replayReturns);
        result.addProperty("original_archive_sha256", ARCHIVE); result.addProperty("native_options_sha256", OPTIONS);
        result.addProperty("pass", PASS);
        result.addProperty("width", WIDTH); result.addProperty("height", HEIGHT);
        result.addProperty("scope", "One original native "+PASS+" draw vs same-frame same-program/sampler-input replay with RT1 MIN_FILTER changed from 9987 to 9729. Private matching RGB8/RGBA16 MRTs, complement initialization and verified full readback. No candidate/final/lifecycle/motion/performance acceptance; GL errors are not consumed.");
        write(result);
    }

    public static void leave() {
        if (OUTPUT.isBlank() || done || replaying) return;
        if (pending != null) { pending = null; fail(new IllegalStateException("NATIVE_QUAD_RETURN_MISSING")); }
        selected = null; selectedPass = null; targets = null;
    }

    private static void preflight() {
        require(WIDTH > 0 && HEIGHT > 0, "UNSUPPORTED_PARITY_EXTENT");
        require(PASS.equals("composite6") || PASS.equals("composite7"), "UNSUPPORTED_PARITY_PASS");
        require(GL.getCapabilities().OpenGL41, "REQUIRE_PINNED_GL41_CONTEXT");
        require(!Files.exists(Path.of(OUTPUT), LinkOption.NOFOLLOW_LINKS), "OUTPUT_MUST_BE_FRESH");
        for (String flag : List.of("gpuTiming", "cpuTiming", "cpuLayerTiming"))
            require("false".equals(System.getProperty("shaderlod.dev." + flag)),
                    "TIMING_MUST_BE_EXPLICITLY_DISABLED:" + flag);
        for (String flag : List.of("gpuTiming", "cpuTiming", "cpuLayerTiming", "benchmark", "cloudSamplerProbe",
                "spatialLayoutProbe", "pixelCoverage", "pixelHistoryCapture", "workTrace"))
            require(!Boolean.getBoolean("shaderlod.dev." + flag), "OTHER_DIAGNOSTIC_OR_TIMING_ENABLED:" + flag);
        for (String flag : List.of("bslMipWitness", "blissChainWitness", "sildurReceiverWitness", "makeupCloudWitness", "settledConsumerUniforms"))
            require(System.getProperty("shaderlod.dev." + flag, "").isBlank(), "OTHER_WITNESS_ENABLED:" + flag);
    }

    private static void verifyOriginal() throws Exception {
        require(PACK.equals(Iris.getCurrentPackName()) && !Iris.isFallback(), "ORIGINAL_PACK_NOT_LOADED");
        var values = WorkloadInputs.effectiveOptions();
        require(values.size() == 384 && OPTIONS.equals(hash(JSON.toJson(values).getBytes(StandardCharsets.UTF_8))), "NATIVE_384_OPTIONS_CHANGED");
        Path archive = Iris.getShaderpacksDirectory().resolve(PACK);
        require(Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(archive)
                && ARCHIVE.equals(hash(Files.readAllBytes(archive))), "ORIGINAL_ARCHIVE_CHANGED");
    }

    private static Layout inspect(Compiled c, SpatialPassAccessor pass, RenderTargets renderTargets) {
        require(c.pack == Iris.getCurrentPack().orElse(null) && c.pipeline == Iris.getPipelineManager().getPipelineNullable()
                && GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == c.program.getProgramId(), "PROGRAM_OR_PIPELINE_CHANGED");
        State state = state();
        require(state.drawFbo > 0 && state.vao > 0 && state.elementBuffer > 0
                && state.viewport.equals(List.of(0, 0, WIDTH, HEIGHT)), "NATIVE_FBO_VAO_OR_FULL_VIEWPORT_UNKNOWN");
        require(Minecraft.getInstance().getMainRenderTarget().width == WIDTH
                && Minecraft.getInstance().getMainRenderTarget().height == HEIGHT, "OUTPUT_EXTENT_CHANGED");
        require(state.polygonMode.equals(List.of(GL11.GL_FILL, GL11.GL_FILL)), "NONFILL_QUAD_UNSUPPORTED");
        require(GL11.glGetInteger(GL41.GL_PROGRAM_PIPELINE_BINDING) == 0
                && GL11.glGetInteger(GL40.GL_TRANSFORM_FEEDBACK_BUFFER_ACTIVE) == 0, "OTHER_PROGRAM_OR_TRANSFORM_FEEDBACK_ACTIVE");
        for (int flag : new int[]{GL11.GL_DEPTH_TEST, GL11.GL_STENCIL_TEST, GL11.GL_SCISSOR_TEST,
                GL30.GL_RASTERIZER_DISCARD, GL11.GL_COLOR_LOGIC_OP})
            require(!GL11.glIsEnabled(flag), "PARTIAL_OR_DESTINATION_DEPENDENT_DRAW:" + flag);
        require(GL11.glGetInteger(GL13.GL_SAMPLE_BUFFERS) == 0 && GL11.glGetInteger(GL13.GL_SAMPLES) == 0,
                "MULTISAMPLE_TARGET_UNSUPPORTED");
        for (int target : new int[]{GL15.GL_SAMPLES_PASSED, GL33.GL_ANY_SAMPLES_PASSED, GL33.GL_TIME_ELAPSED,
                GL30.GL_PRIMITIVES_GENERATED, GL30.GL_TRANSFORM_FEEDBACK_PRIMITIVES_WRITTEN})
            require(GL15.glGetQueryi(target, GL15.GL_CURRENT_QUERY) == 0, "ACTIVE_QUERY_WOULD_COUNT_REPLAY");
        require(state.packBuffer == 0 && state.unpackBuffer == 0, "PIXEL_BUFFER_BINDING_UNSUPPORTED");
        for (int i = 0; i < PIXEL_STATE.length; i++) require(state.pixelState.get(i) == (i == 0 || i == 8 ? 4 : 0), "NONCANONICAL_PIXEL_STORE");
        require(state.drawBuffers.size() >= OUTPUT_TARGETS.length, "DRAW_BUFFER_COUNT_CHANGED");
        for (int i = 0; i < OUTPUT_TARGETS.length; i++) require(state.drawBuffers.get(i) == GL30.GL_COLOR_ATTACHMENT0+i, "DRAW_BUFFER_MAPPING_CHANGED");
        for (int i = OUTPUT_TARGETS.length; i < state.drawBuffers.size(); i++) require(state.drawBuffers.get(i) == GL11.GL_NONE, "EXTRA_DRAW_BUFFER");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (int i = 0; i < OUTPUT_TARGETS.length; i++) {
                require(!GL30.glIsEnabledi(GL11.GL_BLEND, i), "BLENDING_UNSUPPORTED");
                var mask = stack.malloc(4); GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK, i, mask);
                for (int j = 0; j < 4; j++) require(mask.get(j) != 0, "PARTIAL_COLOR_MASK");
            }
        }
        require(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "NATIVE_FBO_INCOMPLETE");
        Map<String, Uniform> uniforms = uniforms(state.program);
        Map<String, Input> inputs = new TreeMap<>();
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            for (var entry : uniforms.entrySet()) {
                if (entry.getValue().type != GL20.GL_SAMPLER_2D) continue;
                String name = entry.getKey(); require(SAMPLERS.contains(name), "UNEXPECTED_SAMPLER");
                int unit = entry.getValue().bits.getFirst();
                require(unit >= 0 && unit < GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS), "SAMPLER_UNIT_UNKNOWN");
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                require(GL11.glGetInteger(GL33.GL_SAMPLER_BINDING) == 0, "SAMPLER_OBJECT_OVERRIDE");
                Image image = image(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
                int expectedFormat = name.equals("colortex1") ? GL11.GL_RGB8 : name.equals("colortex2") ? GL11.GL_RGBA16 : GL30.GL_DEPTH_COMPONENT32F;
                // The depth sampler is replayed from the identical native object; it is
                // never allocated, converted or read back by this probe. Asahi reports
                // Iris's unsized depth allocation as GL_DEPTH_COMPONENT. Qualify its
                // actual depth precision/type instead of treating that token as 32F.
                boolean depth = name.equals("depthtex1");
                boolean knownDepth = image.format == GL30.GL_DEPTH_COMPONENT32F && image.depthBits == 32 && image.depthType == GL11.GL_FLOAT
                    || image.format == GL11.GL_DEPTH_COMPONENT
                        && ((image.depthBits == 24 || image.depthBits == 32) && image.depthType == GL30.GL_UNSIGNED_NORMALIZED
                            || image.depthBits == 32 && image.depthType == GL11.GL_FLOAT);
                require((depth ? knownDepth : image.format == expectedFormat) && image.width == WIDTH && image.height == HEIGHT, "INPUT_ALLOCATION_CHANGED name="+name+" actual="+image+" expectedFormat="+expectedFormat+" expectedExtent="+WIDTH+"x"+HEIGHT);
                int min = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
                int mag = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
                require(min == (name.equals("colortex1") ? GL11.GL_LINEAR_MIPMAP_LINEAR : name.equals("colortex2") ? GL11.GL_LINEAR : GL11.GL_NEAREST)
                        && mag == (name.equals("depthtex1") ? GL11.GL_NEAREST : GL11.GL_LINEAR), "INPUT_FILTER_CHANGED");
                require(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL) == 0
                        && GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL) == 1000
                        && GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD) == -1000.0f
                        && GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD) == 1000.0f
                        && GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS) == 0.0f
                        && GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE) == GL11.GL_NONE
                        && GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S) == GL12.GL_CLAMP_TO_EDGE
                        && GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T) == GL12.GL_CLAMP_TO_EDGE,
                        "INPUT_LEVEL_LOD_OR_WRAP_CHANGED");
                require(GL.getCapabilities().GL_EXT_texture_filter_anisotropic
                        && GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT) == 1.0f,
                        "ANISOTROPY_UNQUALIFIED");
                inputs.put(name, new Input(unit, image, min, mag));
            }
        } finally { GL13.glActiveTexture(active); }
        require(inputs.keySet().equals(SAMPLERS), "ACTIVE_INPUT_SET_CHANGED");
        List<Image> outputs = C6 ? List.of(attachment(0)) : List.of(attachment(0), attachment(1));
        require(outputs.get(0).format == GL11.GL_RGB8 && (C6 || outputs.get(1).format == GL11.GL_RGBA16), "OUTPUT_FORMAT_CHANGED");
        require(C6 || outputs.get(0).object != outputs.get(1).object, "OUTPUTS_ALIAS");
        for (Image output : outputs) {
            require(output.width == WIDTH && output.height == HEIGHT, "OUTPUT_ALLOCATION_CHANGED");
            for (Input input : inputs.values()) require(input.image.object != output.object, "NATIVE_DRAW_CHANGES_REPLAY_INPUT");
        }
        for (int rt : OUTPUT_TARGETS) {
            var target = renderTargets.get(rt); require(target != null, "TARGET_ABSENT");
            boolean alt = pass.shaderlod$stageReadsFromAlt().contains(rt);
            int read = alt ? target.getAltTexture() : target.getMainTexture();
            int write = alt ? target.getMainTexture() : target.getAltTexture();
            require(read != write && inputs.get("colortex" + rt).image.object == read
                    && outputs.get(rt - 1).object == write, "PHYSICAL_RT_READ_WRITE_JOIN_FAILED");
        }
        return new Layout(state.program, state.drawFbo, state.vao, SystemTimeUniforms.COUNTER.getAsInt(), uniforms, inputs, outputs);
    }

    private static Map<String, Uniform> uniforms(int program) {
        require(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_TRUE, "PROGRAM_NOT_LINKED");
        int count = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
        require(count > 0 && count <= 128 && GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORM_MAX_LENGTH) <= 1024, "UNIFORM_INVENTORY_UNBOUNDED");
        Map<String, Uniform> result = new TreeMap<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var size = stack.mallocInt(1); var type = stack.mallocInt(1);
            for (int index = 0; index < count; index++) {
                String name = GL20.glGetActiveUniform(program, index, 1024, size, type);
                int location = GL20.glGetUniformLocation(program, name), kind = type.get(0);
                require(size.get(0) == 1 && location >= 0, "UNIFORM_ARRAY_OR_LOCATION_UNKNOWN");
                List<Integer> bits = new ArrayList<>();
                if (kind == GL20.GL_SAMPLER_2D || kind == GL11.GL_INT || kind == GL20.GL_BOOL) bits.add(GL20.glGetUniformi(program, location));
                else {
                    int components = switch (kind) {
                        case GL11.GL_FLOAT -> 1;
                        case GL20.GL_FLOAT_VEC2 -> 2;
                        case GL20.GL_FLOAT_VEC3 -> 3;
                        case GL20.GL_FLOAT_VEC4, GL20.GL_FLOAT_MAT2 -> 4;
                        case GL20.GL_FLOAT_MAT3 -> 9;
                        case GL20.GL_FLOAT_MAT4 -> 16;
                        default -> throw new IllegalStateException("UNSUPPORTED_UNIFORM_TYPE:" + kind);
                    };
                    var values = stack.mallocFloat(components);
                    for (int i = 0; i < components; i++) values.put(i, Float.NaN);
                    GL20.glGetUniformfv(program, location, values);
                    for (int i = 0; i < components; i++) { require(Float.isFinite(values.get(i)), "NONFINITE_OR_UNWRITTEN_UNIFORM"); bits.add(Float.floatToRawIntBits(values.get(i))); }
                }
                result.put(name, new Uniform(kind, location, List.copyOf(bits)));
            }
        }
        require(result.size() == count && result.containsKey("viewWidth") && result.containsKey("viewHeight"), "INCOMPLETE_UNIFORM_MAP");
        require(result.get("viewWidth").bits.equals(List.of(Float.floatToRawIntBits((float) WIDTH)))
                && result.get("viewHeight").bits.equals(List.of(Float.floatToRawIntBits((float) HEIGHT))), "LINKED_VIEW_EXTENT_CHANGED");
        return result;
    }

    private static State state() {
        int count = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS); require(count >= 2 && count <= 32, "DRAW_BUFFER_LIMIT_UNKNOWN");
        List<Integer> draws = new ArrayList<>(), pixels = new ArrayList<>();
        for (int i = 0; i < count; i++) draws.add(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + i));
        for (int parameter : PIXEL_STATE) pixels.add(GL11.glGetInteger(parameter));
        int[] viewport = new int[4], polygon = new int[2]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport); GL11.glGetIntegerv(GL11.GL_POLYGON_MODE, polygon);
        return new State(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), GL11.glGetInteger(GL11.GL_READ_BUFFER),
                GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
                GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING), GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),
                GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING),
                GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING), list(viewport), draws, pixels, list(polygon));
    }

    private static Image attachment(int index) {
        int point = GL30.GL_COLOR_ATTACHMENT0 + index;
        require(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, point, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) == GL11.GL_TEXTURE
                && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, point, GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL) == 0
                && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, point, GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE) == 0
                && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, point, GL32.GL_FRAMEBUFFER_ATTACHMENT_LAYERED) == 0,
                "OUTPUT_NOT_ORDINARY_LEVEL0_TEXTURE");
        return image(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, point, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME));
    }

    private static Image image(int texture) {
        require(texture > 0 && GL11.glIsTexture(texture), "UNKNOWN_TEXTURE");
        int old = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == texture, "NOT_2D_TEXTURE");
            return new Image(texture, GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT),
                    GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH), GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT),
                    GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL14.GL_TEXTURE_DEPTH_SIZE),
                    GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL30.GL_TEXTURE_DEPTH_TYPE));
        } finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D, old); }
    }

    private static ByteBuffer readVerified(Image image) {
        require(image.format == GL11.GL_RGB8 || image.format == GL11.GL_RGBA16, "READBACK_FORMAT_UNSUPPORTED");
        require(image.width == WIDTH && image.height == HEIGHT, "READBACK_BOUND_EXCEEDED");
        ByteBuffer first = MemoryUtil.memAlloc(image.bytes()), second = null;
        boolean returned = false;
        try {
            second = MemoryUtil.memAlloc(image.bytes());
            for (int i = 0; i < image.bytes(); i++) { first.put(i, (byte) 0x35); second.put(i, (byte) 0xca); }
            read(image, first); read(image, second);
            require(equal(first, second), "READBACK_NOT_FULLY_WRITTEN");
            returned = true; return first;
        } finally { if (second != null) MemoryUtil.memFree(second); if (!returned) MemoryUtil.memFree(first); }
    }

    private static void read(Image image, ByteBuffer bytes) {
        int old = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try { GL11.glBindTexture(GL11.GL_TEXTURE_2D, image.object); GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, image.externalFormat(), image.externalType(), bytes); }
        finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D, old); }
    }

    private static Image createScratch(Image original, ByteBuffer nativeBytes, List<Integer> cleanup) {
        int texture = GL11.glGenTextures();
        require(texture > 0 && !GL11.glIsTexture(texture), "SCRATCH_TEXTURE_ALLOCATION_FAILED"); cleanup.add(texture);
        ByteBuffer inverse = MemoryUtil.memAlloc(original.bytes());
        try {
            for (int i = 0; i < original.bytes(); i++) inverse.put(i, (byte) ~nativeBytes.get(i));
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == texture, "SCRATCH_TEXTURE_BIND_FAILED");
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, original.format, original.width, original.height, 0,
                    original.externalFormat(), original.externalType(), inverse);
            Image scratch = image(texture);
            require(scratch.format == original.format && scratch.width == original.width && scratch.height == original.height,
                    "SCRATCH_STORAGE_MISMATCH");
            ByteBuffer initialized = readVerified(scratch);
            try { require(equal(inverse, initialized), "SCRATCH_COMPLEMENT_NOT_INITIALIZED"); }
            finally { MemoryUtil.memFree(initialized); }
            return scratch;
        } finally { MemoryUtil.memFree(inverse); }
    }

    private static void setInputMin(int texture, int filter) {
        int old = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == texture, "INPUT_FILTER_TEXTURE_BIND_FAILED");
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
            require(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER) == filter, "INPUT_FILTER_SETTER_NOT_OBSERVED");
        } finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D, old); }
    }

    private static void verifyReplayInputs(Layout original) {
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            for (var entry : original.inputs.entrySet()) {
                Input input = entry.getValue();
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + input.unit);
                require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == input.image.object
                        && GL11.glGetInteger(GL33.GL_SAMPLER_BINDING) == 0,
                        "REPLAY_INPUT_BINDING_CHANGED");
                require(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER)
                        == (entry.getKey().equals("colortex1") ? GL11.GL_LINEAR : input.min), "REPLAY_INPUT_FILTER_CHANGED");
            }
        } finally { GL13.glActiveTexture(active); }
    }

    private static JsonObject compare(ByteBuffer expected, ByteBuffer actual, Image image) throws Exception {
        long bytes = 0, texels = 0; int texelBytes = image.channels() * image.componentBytes();
        for (int i = 0; i < expected.capacity(); i += texelBytes) {
            boolean changed = false;
            for (int j = 0; j < texelBytes; j++) if (expected.get(i + j) != actual.get(i + j)) { bytes++; changed = true; }
            if (changed) texels++;
        }
        var result = new JsonObject(); result.addProperty("format", image.format);
        result.addProperty("bytes", expected.capacity()); result.addProperty("texels", WIDTH * HEIGHT);
        result.addProperty("native_sha256", hash(expected)); result.addProperty("linear_replay_sha256", hash(actual));
        result.addProperty("mismatch_bytes", bytes); result.addProperty("mismatch_texels", texels);
        return result;
    }
    private static boolean equal(ByteBuffer a, ByteBuffer b) { return a.asReadOnlyBuffer().equals(b.asReadOnlyBuffer()); }
    private static boolean knownConstructorSource(Map<?, String> transformed) throws Exception {
        String fragment=stageHash(transformed,"FRAGMENT"),vertex=stageHash(transformed,"VERTEX");
        if (C6) return C6_FRAGMENT.equals(fragment)&&C6_VERTEX.equals(vertex);
        return FRAGMENT.equals(fragment)&&VERTEX.equals(vertex)
                || CONSTRUCTOR_FRAGMENT.equals(fragment)&&CONSTRUCTOR_VERTEX.equals(vertex);
    }
    private static String stageHash(Map<?, String> transformed, String type) throws Exception {
        String value = transformed.entrySet().stream().filter(e -> e.getKey().toString().equals(type)).map(Map.Entry::getValue).findFirst().orElseThrow();
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); require(bytes.length <= 131072, "SOURCE_BOUND_EXCEEDED"); return hash(bytes);
    }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static String hash(ByteBuffer bytes) throws Exception { var d = MessageDigest.getInstance("SHA-256"); d.update(bytes.asReadOnlyBuffer()); return HexFormat.of().formatHex(d.digest()); }
    private static JsonObject retain(Path path, ByteBuffer pixels) throws Exception {
        try (var channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var bytes = pixels.asReadOnlyBuffer();
            while (bytes.hasRemaining()) channel.write(bytes);
        }
        var result = new JsonObject(); result.addProperty("path", path.toString());
        result.addProperty("bytes", pixels.capacity()); result.addProperty("sha256", hash(pixels));
        return result;
    }
    private static List<Integer> list(int[] values) { return Arrays.stream(values).boxed().toList(); }
    private static int[] ints(List<Integer> values) { return values.stream().mapToInt(Integer::intValue).toArray(); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void restore(Runnable operation, List<String> failures) { try { operation.run(); } catch (RuntimeException failure) { failures.add(failure.toString()); } }
    private static void fail(Exception failure) {
        done = true; pending = null; selected = null; selectedPass = null; targets = null;
        var result = new JsonObject(); result.addProperty("status", "unknown_failed_parity_primitive"); result.addProperty("failure", failure.toString()); write(result);
    }
    private static void write(JsonObject result) {
        try {
            Path path = Path.of(OUTPUT); Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(result), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (Exception failure) { com.mojang.logging.LogUtils.getLogger().warn("BSL mip parity receipt unavailable", failure); }
    }
}
