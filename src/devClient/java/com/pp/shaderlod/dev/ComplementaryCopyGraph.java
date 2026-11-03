package com.pp.shaderlod.dev;

import com.google.gson.*;
import com.pp.shaderlod.dev.mixin.SpatialPassAccessor;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.framebuffer.ViewportData;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import java.util.zip.ZipFile;
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
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

/** One candidate-only, untimed pinned COMPOSITE-to-final graph. No pixels/replay/target allocation. */
public final class ComplementaryCopyGraph {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final Gson CANONICAL = new GsonBuilder().disableHtmlEscaping().create();
    private static final String OUTPUT = System.getProperty("shaderlod.dev.complementaryCopyGraph", "");
    private static final String EXPECTED = System.getProperty("shaderlod.dev.complementaryCopyGraphSources", "");
    private static final String PACK = "complementary-reimagined-world0-candidate.zip";
    private static final String ARCHIVE = "a0dab58df3cbe51a484dd49de4af5194a676ad2952b2e177528fb6d4f8ee80f3";
    private static final String MANIFEST_SHA = "03f67f4b9be191cee6e23a3f0c6a638606cb41d0617f73a64d87517307a59113";
    private static final String OPTIONS = "7744d25cca74c4fee09e7ba383c2f2348fbcfd9bde82e9508bc2e2d9e0b74bef";
    private static final List<String> ORDER = List.of("composite", "composite1", "composite4", "composite5", "composite6", "composite7");
    private static final Map<String,String> ARTIFACTS = Map.of(
        "iris", "3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83",
        "sodium", "7acd7af2ef75ec883a6872c17494ead65e38025151eb08a1d60e36d34377f403");
    private record Compiled(Object renderer, WorldRenderingPipeline pipeline, Object pack, Program program,
                            String name, int[] outputs, Set<Integer> mips, ViewportData viewport, JsonObject source) {}
    private record Selected(Compiled compiled, Object pass, Set<Integer> readAlt, GlFramebuffer fbo) {}
    private static final Map<Program,Compiled> COMPILED = new IdentityHashMap<>();
    private static final JsonObject REPORT = new JsonObject();
    private static final JsonArray EVENTS = new JsonArray();
    private static final List<Selected> SELECTED = new ArrayList<>();
    private static JsonObject expected;
    private static Object renderer, finalRenderer, pack;
    private static WorldRenderingPipeline pipeline;
    private static RenderTargets targets;
    private static RenderTarget rt0, rt3;
    private static int rt0Main, rt0Alt, rt3Main, rt3Alt;
    private static final Map<Integer,Integer> LAST_WRITE = new HashMap<>();
    private static Selected finalSelected, pending;
    private static JsonObject pendingState;
    private static int frame, nextDraw, nativeDraws, finalDraws, fallbackCopies, swapCopies;
    private static int c4MipTexture, c6MipTexture;
    private static long context, thread;
    private static boolean active, compositeReturned, done;
    private static Set<Integer> outstandingMips = new HashSet<>();
    private static Path outputPath;
    private ComplementaryCopyGraph() {}

    public static void compiled(Object owner, WorldRenderingPipeline p, CompositePass group,
            ProgramSource source, Program program, Map<?,String> transformed) {
        if (OUTPUT.isBlank() || done || group != CompositePass.COMPOSITE) return;
        recordCompiled(owner, p, source, program, transformed);
    }
    public static void finalCompiled(Object owner, WorldRenderingPipeline p, ProgramSource source,
            Program program, Map<?,String> transformed) {
        if (OUTPUT.isBlank() || done) return;
        recordCompiled(owner, p, source, program, transformed);
    }
    private static void recordCompiled(Object owner, WorldRenderingPipeline p, ProgramSource s,
            Program program, Map<?,String> transformed) {
        try {
            require(!active && renderer == null, "RELOAD_AFTER_BIND_FORBIDDEN");
            require(s != null && program != null && (ORDER.contains(s.getName()) || "final".equals(s.getName())),
                "UNEXPECTED_OR_DISABLED_COMPOSITE_PROGRAM");
            require(PACK.equals(Iris.getCurrentPackName()), "WRONG_PACK");
            loadExpected();
            require(s.getGeometrySource().isEmpty() && transformed.entrySet().stream().noneMatch(e ->
                !Set.of("VERTEX", "FRAGMENT").contains(e.getKey().toString()) && e.getValue() != null), "EXTRA_SHADER_STAGE");
            var hashes = new JsonObject();
            for (String type : List.of("FRAGMENT", "VERTEX")) {
                String text = transformed.entrySet().stream().filter(e -> type.equals(e.getKey().toString()))
                    .map(Map.Entry::getValue).findFirst().orElseThrow();
                hashes.addProperty("transformed_" + type.toLowerCase(Locale.ROOT), hash(text));
            }
            hashes.addProperty("preprocessed_fragment", hash(s.getFragmentSource().orElseThrow()));
            hashes.addProperty("preprocessed_vertex", hash(s.getVertexSource().orElseThrow()));
            var pin = expected.getAsJsonObject("programs").getAsJsonObject(s.getName());
            require(pin.keySet().equals(Set.of("transformed_fragment", "transformed_vertex"))
                && hashes.get("transformed_fragment").equals(pin.get("transformed_fragment"))
                && hashes.get("transformed_vertex").equals(pin.get("transformed_vertex")),
                "EXACT_SOURCE_PIN_MISMATCH:" + s.getName());
            require(!COMPILED.containsKey(program), "PROGRAM_OBJECT_REUSED");
            COMPILED.put(program, new Compiled(owner, p, s.getParent().getPack(), program, s.getName(),
                s.getDirectives().getDrawBuffers().clone(), Set.copyOf(s.getDirectives().getMipmappedBuffers()), s.getDirectives().getViewportScale(), hashes));
            require(COMPILED.size() <= 24, "STARTUP_CONSTRUCTION_LIMIT");
        } catch (Exception e) { fail(e); }
    }
    private static void loadExpected() throws Exception {
        if (expected != null) return;
        Path path = Path.of(EXPECTED);
        require(!EXPECTED.isBlank() && path.isAbsolute() && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
            && !Files.isSymbolicLink(path) && Files.size(path) < 32768, "SOURCE_MANIFEST_REQUIRED");
        String raw = Files.readString(path);
        require(MANIFEST_SHA.equals(hash(raw)), "SOURCE_MANIFEST_BYTES_CHANGED");
        expected = JsonParser.parseString(raw).getAsJsonObject();
        require(ARCHIVE.equals(expected.get("archive_sha256").getAsString())
            && OPTIONS.equals(expected.get("options_sha256").getAsString()), "MANIFEST_PACK_OR_OPTIONS_CHANGED");
        var names = new HashSet<>(ORDER); names.add("final");
        require(expected.getAsJsonObject("programs").keySet().equals(names), "MANIFEST_PROGRAM_INVENTORY_CHANGED");
        REPORT.addProperty("source_manifest_sha256", hash(raw)); REPORT.add("source_manifest", expected.deepCopy());
    }
    public static void enter(Object owner, WorldRenderingPipeline p, CompositePass group,
            List<?> passes, RenderTargets all) {
        if (OUTPUT.isBlank() || done || group != CompositePass.COMPOSITE) return;
        var client = Minecraft.getInstance();
        if (client.level == null || client.screen != null || client.isPaused() || !client.isWindowActive()) return;
        try {
            require(!active && renderer == null, "ONE_ATTEMPT_NO_RELOAD");
            reserve(); preflight(); verifyPins();
            renderer = owner; pipeline = p; pack = Iris.getCurrentPack().orElseThrow(); targets = all;
            rt0 = all.get(0); rt3 = all.get(3); require(rt0 != null && rt3 != null, "EXISTING_TARGET_REQUIRED");
            rt0Main = rt0.getMainTexture(); rt0Alt = rt0.getAltTexture();
            rt3Main = rt3.getMainTexture(); rt3Alt = rt3.getAltTexture();
            verifyNativeTargetSizes();
            context = GLFW.glfwGetCurrentContext(); thread = Thread.currentThread().threadId();
            frame = SystemTimeUniforms.COUNTER.getAsInt(); active = true; validate();
            require(passes.size() == ORDER.size(), "ENABLED_PASS_COUNT_CHANGED");
            var inventory = new JsonArray();
            for (int i = 0; i < passes.size(); i++) {
                require(passes.get(i) instanceof SpatialPassAccessor, "UNKNOWN_PASS_TYPE");
                var pass = (SpatialPassAccessor)passes.get(i);
                var c = COMPILED.get(pass.shaderlod$program());
                require(c != null && c.renderer == owner && c.pipeline == p && c.pack == pack
                    && ORDER.get(i).equals(c.name) && c.name.equals(pass.shaderlod$name()), "ACTUAL_PASS_SOURCE_JOIN_FAILED");
                require(Arrays.equals(c.outputs, pass.shaderlod$drawBuffers()) && c.mips.equals(pass.shaderlod$mipmappedBuffers()),
                    "ACTUAL_PASS_DIRECTIVES_CHANGED");
                noComputes(pass); Object fbo = field(pass, "framebuffer"); require(fbo instanceof GlFramebuffer, "UNKNOWN_FBO");
                var selected = new Selected(c, pass, Set.copyOf(pass.shaderlod$stageReadsFromAlt()), (GlFramebuffer)fbo);
                SELECTED.add(selected); inventory.add(passRecord(selected));
            }
            require(SELECTED.get(2).compiled.mips.equals(Set.of(0)) && SELECTED.get(4).compiled.mips.equals(Set.of(3)),
                "C4_RT0_OR_C6_RT3_MIP_REQUEST_CHANGED");
            REPORT.add("enabled_composite_passes", inventory);
            Object directives = field(all, "packDirectives");
            Object rtDirectives = directives.getClass().getMethod("getRenderTargetDirectives").invoke(directives);
            Object clears = rtDirectives.getClass().getMethod("getBuffersToBeCleared").invoke(rtDirectives);
            require(clears instanceof Collection<?> && ((Collection<?>)clears).contains(0), "RT0_NOT_ACTUALLY_CLEAR_LISTED");
            REPORT.add("actual_clear_membership", JSON.toJsonTree(clears));
            outstandingMips = new HashSet<>(SELECTED.get(0).compiled.mips);
            REPORT.addProperty("jvm_pid", ProcessHandle.current().pid()); REPORT.addProperty("frame_source", frame);
            REPORT.addProperty("gl_context", context); REPORT.addProperty("render_thread", thread);
            REPORT.addProperty("renderer_identity", System.identityHashCode(renderer));
            REPORT.addProperty("pipeline_identity", System.identityHashCode(pipeline));
            REPORT.addProperty("pack_identity", System.identityHashCode(pack));
            REPORT.addProperty("render_targets_identity", System.identityHashCode(targets));
        } catch (Exception e) { fail(e); }
    }
    public static void generationReturned(String stage, RenderTarget generated, boolean readAlt) {
        if (!active || done) return;
        try {
            validate(); Selected s = currentExpected(stage);
            int index = -1;
            for (int n : outstandingMips) if (targets.get(n) == generated) { require(index < 0, "MIP_TARGET_AMBIGUOUS"); index = n; }
            require(index >= 0 && s.readAlt.contains(index) == readAlt, "UNEXPECTED_MIP_TARGET_OR_STORAGE");
            int texture = readAlt ? generated.getAltTexture() : generated.getMainTexture();
            if (LAST_WRITE.containsKey(index)) require(LAST_WRITE.get(index) == texture, "MIP_NOT_LATEST_PHYSICAL_OUTPUT");
            var event = event("native_setup_mipmapping_return"); event.addProperty("pass", s.compiled.name);
            event.addProperty("logical_target", index); event.addProperty("physical_texture", texture);
            event.addProperty("read_alt", readAlt); event.add("image", textureState(texture, index));
            int nativeFilter = generated.getInternalFormat().getPixelFormat().isInteger()
                ? GL11.GL_NEAREST_MIPMAP_NEAREST : GL11.GL_LINEAR_MIPMAP_LINEAR;
            require(event.getAsJsonObject("image").get("min_filter").getAsInt() == nativeFilter,
                "NATIVE_MIP_FILTER_NOT_RETURNED");
            if (s.compiled.name.equals("composite4")) c4MipTexture = texture;
            if (s.compiled.name.equals("composite6")) c6MipTexture = texture;
            outstandingMips.remove(index);
        } catch (Exception e) { fail(e); }
    }
    public static void beforeQuad() {
        if (!active || done) return;
        try {
            validate(); require(pending == null && outstandingMips.isEmpty(), "QUAD_WITHOUT_EXPECTED_MIP_RETURNS");
            pending = currentExpected(compositeReturned ? "final" : "composite");
            pendingState = drawState(pending); nativeDraws = 0;
            var event = event("quad_head"); event.add("pass", passRecord(pending)); event.add("state", pendingState);
            if (pending.compiled.name.equals("composite4"))
                require(samplerTexture(pendingState, "colortex0") == c4MipTexture, "C4_MIP_INPUT_NOT_ACTUAL_SAMPLER");
            if (pending.compiled.name.equals("final")) {
                require(samplerTexture(pendingState, "colortex3") == c6MipTexture, "FINAL_NOT_C6_MIP_STORAGE");
                require(!pending.compiled.mips.contains(3), "UNEXPECTED_FINAL_RT3_REGENERATION");
            }
        } catch (Exception e) { fail(e); }
    }
    /** Invoke AFTER the exact native VertexBuffer.draw() in FullScreenQuadRenderer.renderQuad. */
    public static void nativeDrawReturned() {
        if (!active || done) return;
        try { validate(); require(pending != null && ++nativeDraws == 1, "EXTRA_OR_UNJOINED_NATIVE_DRAW");
            event("native_vertex_buffer_draw_return").addProperty("pass", pending.compiled.name);
        } catch (Exception e) { fail(e); }
    }
    public static void afterQuad() {
        if (!active || done) return;
        try {
            validate(); require(pending != null && nativeDraws == 1, "NATIVE_DRAW_RETURN_MISSING");
            require(pendingState.equals(drawState(pending)), "QUAD_HEAD_RETURN_STATE_CHANGED");
            event("quad_return").addProperty("pass", pending.compiled.name);
            for (var item : pendingState.getAsJsonArray("outputs")) {
                var output = item.getAsJsonObject(); int logical = output.get("logical_target").getAsInt();
                if (logical >= 0) LAST_WRITE.put(logical, output.get("physical_texture").getAsInt());
            }
            if (compositeReturned) finalDraws++;
            else { nextDraw++; if (nextDraw < SELECTED.size()) outstandingMips = new HashSet<>(SELECTED.get(nextDraw).compiled.mips); }
            pending = null; pendingState = null;
        } catch (Exception e) { fail(e); }
    }
    public static void leave() {
        if (!active || done) return;
        try { validate(); require(!compositeReturned && pending == null && nextDraw == ORDER.size(), "COMPOSITE_DRAW_STREAM_INCOMPLETE");
            compositeReturned = true; event("composite_return");
        } catch (Exception e) { fail(e); }
    }
    public static void finalEnterReflected(Object owner, WorldRenderingPipeline p, RenderTargets all,
            List<?> swaps, GlFramebuffer colorHolder) {
        if (!active || done) return;
        try { finalEnter(owner, p, all, field(owner, "finalPass"), swaps, colorHolder); }
        catch (Exception e) { fail(e); }
    }
    public static void finalEnter(Object owner, WorldRenderingPipeline p, RenderTargets all, Object finalPass,
            List<?> swaps, GlFramebuffer colorHolder) {
        if (!active || done) return;
        try {
            validate(); require(compositeReturned && finalRenderer == null && p == pipeline && all == targets && finalPass != null,
                "FINAL_PIPELINE_OR_PASS_MISMATCH");
            finalRenderer = owner; Program program = (Program)field(finalPass, "program"); Compiled c = COMPILED.get(program);
            require(c != null && "final".equals(c.name) && c.renderer == owner && c.pipeline == pipeline && c.pack == pack,
                "FINAL_SOURCE_PROGRAM_PASS_JOIN_FAILED");
            noComputes(finalPass);
            @SuppressWarnings("unchecked") Set<Integer> alt = (Set<Integer>)field(finalPass, "stageReadsFromAlt");
            require(c.mips.equals(field(finalPass, "mipmappedBuffers")), "FINAL_MIP_REQUEST_CHANGED");
            finalSelected = new Selected(c, finalPass, Set.copyOf(alt), colorHolder);
            outstandingMips = new HashSet<>(c.mips);
            var list = new JsonArray();
            for (Object swap : swaps) {
                int n = (Integer)field(swap, "target"); require(n != 0, "COMPENSATING_RT0_SWAP_COPY");
                var record = new JsonObject(); record.addProperty("target", n);
                record.addProperty("target_texture", (Integer)field(swap, "targetTexture"));
                record.addProperty("from_fbo", ((GlFramebuffer)field(swap, "from")).getId()); list.add(record);
            }
            REPORT.add("actual_final_swap_list", list); REPORT.add("actual_final_pass", passRecord(finalSelected));
            event("final_enter");
        } catch (Exception e) { fail(e); }
    }
    /** Before native final fallback copy; any fallback is outside this certificate. */
    public static void fallbackCopy() {
        if (!active || done) return;
        fallbackCopies++; event("final_fallback_copy_call"); fail(new IllegalStateException("FINAL_FALLBACK_COPY_UNSUPPORTED"));
    }
    /** Before each actual native final swap-copy call, after native FBO/texture binding. */
    public static void swapCopy() {
        if (!active || done) return;
        try {
            validate(); require(finalSelected != null && finalDraws == 1, "SWAP_OUTSIDE_JOINED_FINAL");
            int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            require(texture != rt0.getMainTexture() && texture != rt0.getAltTexture(), "ACTUAL_RT0_SWAP_COPY");
            JsonArray swaps = REPORT.getAsJsonArray("actual_final_swap_list"); require(swapCopies < swaps.size(), "EXTRA_SWAP_CALL");
            JsonObject expectedSwap = swaps.get(swapCopies).getAsJsonObject();
            int fbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            require(texture == expectedSwap.get("target_texture").getAsInt() && fbo == expectedSwap.get("from_fbo").getAsInt(),
                "ACTUAL_SWAP_LIST_CALL_JOIN_FAILED");
            var event = event("final_swap_copy_call"); event.addProperty("target_texture", texture); event.addProperty("read_fbo", fbo); swapCopies++;
        } catch (Exception e) { fail(e); }
    }
    public static void finalLeave() {
        if (!active || done) return;
        try {
            validate(); require(finalDraws == 1 && pending == null && fallbackCopies == 0
                && swapCopies == REPORT.getAsJsonArray("actual_final_swap_list").size(), "FINAL_STREAM_INCOMPLETE");
            REPORT.addProperty("status", "candidate_same_frame_pinned_graph_observed");
            REPORT.addProperty("scope", "One fresh-JVM COMPOSITE HEAD through final RETURN. Pinned native source/program/pass/quad/physical sampler/FBO/mip/swap joins. No arbitrary third-party GL interception, GPU completion, pixels/history/lifecycle/quality or cost proof.");
            event("final_return"); done = true; active = false; finish();
        } catch (Exception e) { fail(e); }
    }
    private static Selected currentExpected(String stage) {
        if ("final".equals(stage)) { require(compositeReturned && finalSelected != null && finalDraws == 0, "UNEXPECTED_FINAL_DRAW"); return finalSelected; }
        require(!compositeReturned && nextDraw < SELECTED.size(), "UNEXPECTED_COMPOSITE_DRAW"); return SELECTED.get(nextDraw);
    }
    private static JsonObject drawState(Selected s) throws Exception {
        noComputes(s.pass);
        require(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == s.compiled.program.getProgramId()
            && GL20.glGetProgrami(s.compiled.program.getProgramId(), GL20.GL_LINK_STATUS) == GL11.GL_TRUE,
            "ACTUAL_CURRENT_PROGRAM_MISMATCH");
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        require(draw == s.fbo.getId() && draw > 0 && GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)
            == GL30.GL_FRAMEBUFFER_COMPLETE, "ACTUAL_DRAW_FBO_MISMATCH");
        var state = new JsonObject(); state.addProperty("program", s.compiled.program.getProgramId());
        state.addProperty("draw_fbo", draw); state.addProperty("read_fbo", GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING));
        state.addProperty("vao", GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));
        int[] vp = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        state.add("viewport", JSON.toJsonTree(vp));
        state.add("native_viewport_contract", nativeViewport(s, vp));
        var outputs = new JsonArray();
        int count = s.compiled.name.equals("final") ? 1 : s.compiled.outputs.length;
        for (int i = 0; i < count; i++) {
            int attachment = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + i);
            require(attachment >= GL30.GL_COLOR_ATTACHMENT0 && attachment < GL30.GL_COLOR_ATTACHMENT0 + 32, "UNKNOWN_DRAW_ATTACHMENT");
            require(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) == GL11.GL_TEXTURE
                && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL) == 0
                && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment,
                    GL32.GL_FRAMEBUFFER_ATTACHMENT_LAYERED) == 0, "NON_LEVEL0_ORDINARY_OUTPUT");
            int texture = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            int logical = s.compiled.name.equals("final") ? -1 : s.compiled.outputs[i];
            if (logical >= 0) {
                RenderTarget target = targets.get(logical); require(target != null, "OUTPUT_TARGET_ABSENT");
                int expectedTexture = s.readAlt.contains(logical) ? target.getMainTexture() : target.getAltTexture();
                require(texture == expectedTexture, "PHYSICAL_OUTPUT_FLIP_JOIN_FAILED");
            } else require(texture == Minecraft.getInstance().getMainRenderTarget().getColorTextureId(), "FINAL_SURFACE_OUTPUT_CHANGED");
            var output = new JsonObject(); output.addProperty("logical_target", logical); output.addProperty("attachment", attachment);
            output.addProperty("physical_texture", texture); output.add("image", textureState(texture, logical)); outputs.add(output);
        }
        int max = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS); require(max > 0 && max <= 32, "DRAW_BUFFER_LIMIT");
        for (int i = count; i < max; i++) require(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + i) == GL11.GL_NONE, "EXTRA_DRAW_OUTPUT");
        state.add("outputs", outputs);
        var samplers = new JsonObject(); int program = s.compiled.program.getProgramId();
        int uniforms = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS); require(uniforms <= 256, "UNIFORM_LIMIT");
        for (int i = 0; i < uniforms; i++) {
            String name; int uniformSize, uniformType;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var size = stack.mallocInt(1); var type = stack.mallocInt(1);
                name = GL20.glGetActiveUniform(program, i, 1024, size, type);
                uniformSize = size.get(0); uniformType = type.get(0);
            }
            if (!name.matches("colortex[0-9]+")) continue;
            require(uniformSize == 1 && uniformType == GL20.GL_SAMPLER_2D, "NON_NATIVE_COLORTEX_SAMPLER");
            int logical = Integer.parseInt(name.substring(8)); RenderTarget target = targets.get(logical);
            require(target != null, "SAMPLER_TARGET_ABSENT");
            int location = GL20.glGetUniformLocation(program, name); require(location >= 0, "ACTIVE_SAMPLER_LOCATION_UNKNOWN");
            int unit = GL20.glGetUniformi(program, location);
            require(unit >= 0 && unit < GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS), "SAMPLER_UNIT_OR_OVERRIDE_UNKNOWN");
            int saved = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE); int texture;
            try {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                require(GL11.glGetInteger(GL33.GL_SAMPLER_BINDING) == 0, "SAMPLER_UNIT_OR_OVERRIDE_UNKNOWN");
                texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            } finally { GL13.glActiveTexture(saved); }
            require(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == saved, "SAMPLER_ACTIVE_UNIT_RESTORE_FAILED");
            int expectedTexture = s.readAlt.contains(logical) ? target.getAltTexture() : target.getMainTexture();
            require(texture == expectedTexture, "ACTUAL_PHYSICAL_SAMPLER_FLIP_JOIN_FAILED:" + name);
            if (LAST_WRITE.containsKey(logical)) require(LAST_WRITE.get(logical) == texture, "SAMPLER_NOT_LATEST_PHYSICAL_WRITE:" + name);
            var sampler = new JsonObject(); sampler.addProperty("unit", unit); sampler.addProperty("physical_texture", texture);
            sampler.addProperty("read_alt", s.readAlt.contains(logical)); sampler.add("image", textureState(texture, logical)); samplers.add(name, sampler);
        }
        state.add("samplers", samplers); return state;
    }
    private static int samplerTexture(JsonObject state, String name) {
        require(state.getAsJsonObject("samplers").has(name), "REQUIRED_ACTIVE_SAMPLER_ABSENT:" + name);
        return state.getAsJsonObject("samplers").getAsJsonObject(name).get("physical_texture").getAsInt();
    }
    // Exact native properties + native398 + usable feature pin permit only these target extents.
    private static int[] expectedNativeExtent(int logical) {
        return logical == 1 || logical == 7 ? new int[]{960,600} : new int[]{1920,1200};
    }
    private static void verifyNativeTargetSizes() throws Exception {
        PackDirectives directives = (PackDirectives)field(targets, "packDirectives");
        var observations = new JsonArray(); REPORT.add("actual_native_target_sizes", observations);
        for (int logical = 0; logical < targets.getRenderTargetCount(); logical++) {
            RenderTarget target = targets.get(logical); if (target == null) continue;
            var size = directives.getTextureScaleOverride(logical, 1920, 1200);
            int[] expectedSize = expectedNativeExtent(logical);
            var item = new JsonObject(); item.addProperty("logical_target", logical);
            item.add("parsed_property_extent", JSON.toJsonTree(new int[]{size.x, size.y}));
            item.add("render_target_extent", JSON.toJsonTree(new int[]{target.getWidth(), target.getHeight()}));
            item.add("pinned_expected_extent", JSON.toJsonTree(expectedSize)); observations.add(item);
            require(size.x == expectedSize[0] && size.y == expectedSize[1]
                && target.getWidth() == size.x && target.getHeight() == size.y, "PARSED_NATIVE_TARGET_SIZE_MISMATCH");
        }
    }
    private static JsonObject nativeViewport(Selected s, int[] actual) throws Exception {
        var detail = new JsonObject(); detail.addProperty("pass", s.compiled.name);
        detail.add("actual_gl_viewport", JSON.toJsonTree(actual));
        // Add the mutable object before any field lookup/gate, retaining partial failure detail.
        REPORT.add("last_viewport_observation", detail);
        boolean last = s.compiled.name.equals("final");
        int width = last ? Minecraft.getInstance().getMainRenderTarget().width : (Integer)field(s.pass, "viewWidth");
        int height = last ? Minecraft.getInstance().getMainRenderTarget().height : (Integer)field(s.pass, "viewHeight");
        ViewportData viewport = last ? ViewportData.defaultValue() : (ViewportData)field(s.pass, "viewportScale");
        detail.addProperty("view_width", width); detail.addProperty("view_height", height);
        detail.addProperty("scale", viewport.scale()); detail.addProperty("viewport_x", viewport.viewportX()); detail.addProperty("viewport_y", viewport.viewportY());
        detail.addProperty("viewport_origin", last ? "final inherits native full-size composite7 viewport" : "actual CompositeRenderer.Pass fields");
        int[] derived = {(int)(width * viewport.viewportX()), (int)(height * viewport.viewportY()),
            (int)(width * viewport.scale()), (int)(height * viewport.scale())};
        detail.add("native_derived_gl_viewport", JSON.toJsonTree(derived));
        detail.add("compiled_source_viewport", JSON.toJsonTree(s.compiled.viewport));
        int[] expectedSize = expectedNativeExtent(s.compiled.name.equals("composite") ? 7 : -1);
        detail.add("pinned_expected_extent", JSON.toJsonTree(expectedSize));
        require(viewport.equals(s.compiled.viewport) && viewport.equals(ViewportData.defaultValue()), "PINNED_NATIVE_VIEWPORT_DIRECTIVE_CHANGED");
        require(width == expectedSize[0] && height == expectedSize[1], "PINNED_NATIVE_PASS_VIEW_EXTENT_CHANGED");
        if (!last) for (int logical : s.compiled.outputs) {
            RenderTarget target = targets.get(logical); int[] outputSize = expectedNativeExtent(logical);
            require(target != null && target.getWidth() == width && target.getHeight() == height
                && outputSize[0] == width && outputSize[1] == height, "PASS_OUTPUT_VIEW_EXTENT_JOIN_FAILED");
        }
        require(Arrays.equals(actual, derived), "ACTUAL_NATIVE_PASS_VIEWPORT_MISMATCH");
        return detail.deepCopy();
    }

    /** Temporary query binding only. Always restore and verify, even on a descriptor failure. */
    private static JsonObject textureState(int texture, int logical) {
        require(texture > 0 && GL11.glIsTexture(texture), "NOT_EXISTING_TEXTURE");
        int activeUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        var state = new JsonObject();
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == texture, "TEXTURE_QUERY_NOT_2D");
            for (var pair : Map.of("width", GL11.GL_TEXTURE_WIDTH, "height", GL11.GL_TEXTURE_HEIGHT,
                    "internal_format", GL11.GL_TEXTURE_INTERNAL_FORMAT).entrySet())
                state.addProperty(pair.getKey(), GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, pair.getValue()));
            state.addProperty("logical_target", logical); state.addProperty("physical_texture", texture);
            REPORT.add("last_texture_observation", state.deepCopy());
            int[] extent = expectedNativeExtent(logical);
            require(state.get("width").getAsInt() == extent[0] && state.get("height").getAsInt() == extent[1], "PINNED_NATIVE_TEXTURE_EXTENT_CHANGED");
            if (texture == rt0.getMainTexture() || texture == rt0.getAltTexture())
                require(state.get("internal_format").getAsInt() == GL30.GL_R11F_G11F_B10F, "ACTUAL_RT0_FORMAT_CHANGED");
            state.addProperty("min_filter", GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER));
            state.addProperty("mag_filter", GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER));
            state.addProperty("base_level", GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL));
            state.addProperty("max_level", GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL));
            require(state.get("base_level").getAsInt() == 0 && state.get("max_level").getAsInt() == 1000,
                "NATIVE_TEXTURE_LEVEL_RANGE_CHANGED");
        } finally {
            GL13.glActiveTexture(activeUnit); GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding);
            require(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == activeUnit
                && GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == binding, "TEXTURE_QUERY_RESTORE_FAILED");
        }
        return state;
    }
    private static JsonObject passRecord(Selected s) {
        var record = new JsonObject(); record.addProperty("name", s.compiled.name);
        record.addProperty("actual_pass_identity", System.identityHashCode(s.pass));
        record.addProperty("program_object_identity", System.identityHashCode(s.compiled.program));
        record.addProperty("program", s.compiled.program.getProgramId()); record.addProperty("fbo", s.fbo.getId());
        record.add("source", s.compiled.source.deepCopy()); record.add("outputs", JSON.toJsonTree(s.compiled.outputs));
        record.add("mips", JSON.toJsonTree(new TreeSet<>(s.compiled.mips))); record.add("read_alt", JSON.toJsonTree(new TreeSet<>(s.readAlt)));
        return record;
    }
    private static void noComputes(Object pass) throws Exception {
        Object values = field(pass, "computes"); require(values != null && values.getClass().isArray(), "COMPUTE_INVENTORY_UNKNOWN");
        for (int i = 0; i < Array.getLength(values); i++) require(Array.get(values,i) == null, "COMPUTE_COMPANION_OR_REPLACEMENT");
        require(!pass.getClass().getName().contains("ComputeOnly"), "COMPUTE_ONLY_PASS");
    }
    private static Object field(Object object, String name) throws Exception {
        Class<?> type = object.getClass();
        while (type != null) {
            try { var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(object); }
            catch (NoSuchFieldException e) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private static void validate() {
        var c = Minecraft.getInstance();
        require(c.level != null && "minecraft:overworld".equals(c.level.dimension().location().toString())
            && c.screen == null && !c.isPaused() && c.isWindowActive() && !Iris.isFallback()
            && PACK.equals(Iris.getCurrentPackName()) && Iris.getCurrentPack().orElse(null) == pack
            && Iris.getPipelineManager().getPipelineNullable() == pipeline, "WORLD_PACK_PIPELINE_CHANGED");
        require(SystemTimeUniforms.COUNTER.getAsInt() == frame && GLFW.glfwGetCurrentContext() == context
            && context != 0 && Thread.currentThread().threadId() == thread, "FRAME_CONTEXT_THREAD_CHANGED");
        require(targets.get(0) == rt0 && targets.get(3) == rt3 && rt0.getMainTexture() != rt0.getAltTexture()
            && rt3.getMainTexture() != rt3.getAltTexture()
            && rt0.getMainTexture() == rt0Main && rt0.getAltTexture() == rt0Alt
            && rt3.getMainTexture() == rt3Main && rt3.getAltTexture() == rt3Alt, "TARGET_OBJECT_OR_ALIAS_CHANGED");
        require(c.getMainRenderTarget().width == 1920 && c.getMainRenderTarget().height == 1200
            && c.getWindow().getWidth() == 1920 && c.getWindow().getHeight() == 1200, "SURFACE_EXTENT_CHANGED");
    }
    private static void preflight() {
        require(GL.getCapabilities().OpenGL41 && GLFW.glfwGetCurrentContext() != 0, "GL41_CONTEXT_REQUIRED");
        for (String flag : List.of("gpuTiming", "cpuTiming", "cpuLayerTiming"))
            require("false".equals(System.getProperty("shaderlod.dev." + flag)), "EXPLICIT_FALSE_REQUIRED:" + flag);
        for (String flag : List.of("benchmark", "cloudSamplerProbe", "spatialLayoutProbe", "pixelCoverage", "pixelHistoryCapture",
                "workTrace", "workTraceOnly", "measureWorkload", "measureOnly", "physicalResolution", "dhDrawEvidence"))
            require(!Boolean.parseBoolean(System.getProperty("shaderlod.dev."+flag, "false")), "OTHER_DIAGNOSTIC:"+flag);
        for (String flag : List.of("bslMipWitness", "bslMipParity", "blissChainWitness", "sildurReceiverWitness", "makeupCloudWitness",
                "settledConsumerUniforms", "compositeCopyWitness"))
            require(System.getProperty("shaderlod.dev."+flag, "").isBlank(), "OTHER_WITNESS:"+flag);
        REPORT.addProperty("gl_vendor", GL11.glGetString(GL11.GL_VENDOR)); REPORT.addProperty("gl_renderer", GL11.glGetString(GL11.GL_RENDERER));
        REPORT.addProperty("gl_version", GL11.glGetString(GL11.GL_VERSION));
    }
    private static void verifyPins() throws Exception {
        var values = new TreeMap<String,String>(); WorkloadInputs.effectiveOptions().entrySet().forEach(e -> values.put(e.getKey(),e.getValue().getAsString()));
        require(values.size() == 398 && OPTIONS.equals(hash(CANONICAL.toJson(values)))
            && "0".equals(values.get("WORLD_BLUR")) && "-1".equals(values.get("MOTION_BLUR_EFFECT"))
            && "0.5".equals(values.get("REFLECTION_RES")), "NATIVE398_OPTIONS_CHANGED");
        REPORT.add("effective_options", JSON.toJsonTree(values)); REPORT.addProperty("options_sha256", OPTIONS);
        Path archive = Iris.getShaderpacksDirectory().resolve(PACK);
        require(Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(archive)
            && ARCHIVE.equals(LoadedArtifactEvidence.fileSha256(archive)), "CANDIDATE_ARCHIVE_CHANGED");
        REPORT.addProperty("archive_sha256", ARCHIVE);
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var member = zip.getEntry("shaders/shaders.properties"); require(member != null, "PINNED_NATIVE_PROPERTIES_ABSENT");
            byte[] bytes; try (var input = zip.getInputStream(member)) { bytes = input.readAllBytes(); }
            String properties = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            REPORT.addProperty("actual_shader_properties_sha256", properties);
            require("18be65521769d8849ee8dbbace76134486a613181c1cf654855022e48218520d".equals(properties), "PINNED_NATIVE_PROPERTIES_CHANGED");
        }
        boolean supported = FeatureFlags.BLOCK_EMISSION_ATTRIBUTE.isUsable();
        REPORT.addProperty("IRIS_FEATURE_BLOCK_EMISSION_ATTRIBUTE_macro_supported", supported);
        REPORT.addProperty("pack_block_emission_feature", Iris.getCurrentPack().orElseThrow().hasFeature(FeatureFlags.BLOCK_EMISSION_ATTRIBUTE));
        require(supported, "PINNED_NATIVE_REFLECTION_SIZE_FEATURE_UNAVAILABLE");
        var versions = new TreeMap<String,String>(); for (var mod : ModList.get().getMods()) versions.put(mod.getModId(), mod.getVersion().toString());
        REPORT.add("loaded_mod_versions", JSON.toJsonTree(versions));
        require("1.21.1".equals(versions.get("minecraft")) && "21.1.253".equals(versions.get("neoforge")), "LOADER_OR_MC_CHANGED");
        require(!versions.containsKey("distanthorizons") && !versions.containsKey("voxy") && !versions.containsKey("shaderlod"), "MINIMAL_OFF_RUNTIME_REQUIRED");
        var loaded = new JsonArray();
        for (var pin : ARTIFACTS.entrySet()) {
            int matches = 0;
            for (var file : ModList.get().getModFiles()) {
                if (file.getMods().stream().noneMatch(m -> m.getModId().equals(pin.getKey()))) continue;
                matches++; Path path = file.getFile().getFilePath(); require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "LOADED_ARTIFACT_NOT_REGULAR");
                String actual = LoadedArtifactEvidence.fileSha256(path); require(pin.getValue().equals(actual), "LOADED_ARTIFACT_CHANGED:"+pin.getKey());
                var record = new JsonObject(); record.addProperty("mod", pin.getKey()); record.addProperty("path", path.toString()); record.addProperty("sha256", actual); loaded.add(record);
            }
            require(matches == 1, "LOADED_ARTIFACT_JOIN_AMBIGUOUS");
        }
        REPORT.add("loaded_pinned_artifacts", loaded);
    }
    private static void reserve() throws Exception {
        outputPath = Path.of(OUTPUT); require(outputPath.isAbsolute() && !Files.exists(outputPath, LinkOption.NOFOLLOW_LINKS), "FRESH_ABSOLUTE_OUTPUT_REQUIRED");
        Files.createDirectories(outputPath.getParent());
    }
    private static JsonObject event(String kind) {
        var event = new JsonObject(); event.addProperty("ordinal", EVENTS.size()+1); event.addProperty("kind",kind); event.addProperty("frame_source",frame);
        EVENTS.add(event); REPORT.add("events", EVENTS); require(EVENTS.size() <= 96, "EVENT_LIMIT"); return event;
    }
    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    private static void require(boolean value, String reason) { if (!value) throw new IllegalStateException(reason); }
    private static void finish() {
        try {
            if (outputPath == null) reserve();
            Files.writeString(outputPath, JSON.toJson(REPORT), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (Exception e) { com.mojang.logging.LogUtils.getLogger().warn("Complementary copy graph evidence unavailable",e); }
    }
    private static void fail(Exception e) {
        done = true; active = false; REPORT.addProperty("status","unknown_failed_candidate_graph_observation"); REPORT.addProperty("failure", e.toString()); finish();
    }
}
