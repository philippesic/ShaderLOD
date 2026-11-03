package com.pp.shaderlod.dev;

import com.google.gson.*;
import com.pp.shaderlod.dev.mixin.SpatialPassAccessor;
import com.pp.shaderlod.experiment.ComplementaryRasterR1;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.CompositePass;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;

/** One opt-in, untimed frame of pre-draw state. No GPU allocations, shader edits or core registration. */
public final class ComplementaryRasterActivationProbe {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final String OUTPUT = System.getProperty("shaderlod.dev.rasterActivationProbe", "");
    private static final String ARM = System.getProperty("shaderlod.dev.rasterActivationProbeArm", "");
    private static final String ARCHIVE = "d88e31c433d9e46d2dac63ee3b2f4f3ff1352c708f7891a353b7e61ff23c55eb";
    // Separate frozen Unbound source release; never replace the Reimagined pin.
    private static final String UNBOUND_ARCHIVE = "7aa174f5dbb35d995f6b74a59b73a0a87a770597a14904fe0d359cd766f1240b";
    private static final String UNBOUND_RECEIPT = "4c48b0f30457d9c8c912926199fbb6d62fe737165a5617f9ce1ec3394b8fb0ae";
    private static final String UNBOUND_MEMBERS = "34996880954709774f7401ccab9b583c04160421508ec39884c96e6498f81202";
    private static final String UNBOUND_NATIVE = "b64d94998c99bf867e80d350a4a0a8108c58507e0401eb053a70e3aca81a5036";
    private static final List<String> TEMPORAL_UNIFORMS = List.of("shaderlodTemporalVersion", "shaderlodHistoryReset",
            "shaderlodScaleHistoryVersion", "shaderlodPreviousEnabled", "shaderlodScaleTransition");
    private static final Set<String> PASSES = Set.of("deferred1", "deferred2", "composite", "composite1",
            "composite2", "composite3", "composite4", "composite5", "composite6");
    private static final List<String> UNIFORMS = List.of("shaderlodRasterR1Active", "shaderlodBridgeVersion",
            "shaderlodEnabled", "shaderlodPreset");
    private static final Set<Integer> WATCHED = Set.of(0, 1, 2, 7, 9, 10);
    private static final Set<Integer> UNBOUND_WATCHED = Set.of(0, 1, 2, 3, 5, 6, 7, 9, 10);
    private record Compiled(Object owner, WorldRenderingPipeline pipeline, Object pack, Program program,
                            String name, int[] outputs, JsonObject hashes, boolean unboundSource) {}
    private static final Map<Program, Compiled> COMPILED = new IdentityHashMap<>();
    private static final Map<Integer, SpatialPassAccessor> SELECTED = new HashMap<>();
    private static final Set<String> OBSERVED = new HashSet<>();
    private static final JsonObject REPORT = new JsonObject();
    private static final JsonArray DRAWS = new JsonArray();
    private static RenderTargets targets;
    private static Object owner, pack;
    private static WorldRenderingPipeline pipeline;
    private static CompositePass group;
    private static Path output;
    private static long context, thread;
    private static int frame;
    private static int unboundCurrentColor;
    private static boolean started, done, unbound;
    private ComplementaryRasterActivationProbe() {}

    /** Forward the existing createProgram RETURN, before its transformed map is reset. */
    public static void compiled(Object renderer, WorldRenderingPipeline p, CompositePass g,
            ProgramSource source, Program program, Map<?, String> transformed) {
        if (OUTPUT.isBlank() || done || source == null || program == null || !PASSES.contains(source.getName())) return;
        if (!source.getFragmentSource().orElse("").contains("shaderlodRasterR1Active")) return;
        try {
            require(!started, "RELOAD_AFTER_OBSERVATION_STARTED");
            var hashes = new JsonObject();
            hashes.addProperty("preprocessed_fragment_sha256", hash(source.getFragmentSource().orElseThrow()));
            hashes.addProperty("preprocessed_vertex_sha256", hash(source.getVertexSource().orElseThrow()));
            for (var entry : transformed.entrySet()) if (entry.getValue() != null)
                hashes.addProperty("transformed_" + entry.getKey().toString().toLowerCase(Locale.ROOT) + "_sha256", hash(entry.getValue()));
            require(hashes.has("transformed_fragment_sha256") && hashes.has("transformed_vertex_sha256"), "ACTUAL_TRANSFORMED_STAGES_ABSENT");
            COMPILED.put(program, new Compiled(renderer, p, Iris.getCurrentPack().orElseThrow(), program,
                    source.getName(), source.getDirectives().getDrawBuffers().clone(), hashes,
                    source.getFragmentSource().orElseThrow().contains("shaderlodUnboundTemporalR1On")));
            require(COMPILED.size() <= 64, "FRESH_JVM_CONSTRUCTION_LIMIT");
        } catch (Exception | LinkageError e) { fail(e); }
    }

    private static boolean eligible() {
        var c = Minecraft.getInstance();
        return !OUTPUT.isBlank() && !done && c.level != null && c.screen == null && !c.isPaused()
                && c.isWindowActive() && ComplementaryRasterR1.ownsPrivateGraph()
                && (ARM.isBlank() || Files.isRegularFile(Path.of(ARM), LinkOption.NOFOLLOW_LINKS));
    }
    private static void start(WorldRenderingPipeline p, RenderTargets all) throws Exception {
        reserve(); started = true; pipeline = p; targets = all; pack = Iris.getCurrentPack().orElseThrow();
        unbound = !System.getProperty("shaderlod.unboundTemporalR1.archiveSha256", "").isBlank();
        context = GLFW.glfwGetCurrentContext(); thread = Thread.currentThread().threadId();
        frame = SystemTimeUniforms.COUNTER.getAsInt();
        REPORT.addProperty("scope", "one frame; pre-draw state only; no timing, pixels or avoided-work proof");
        REPORT.addProperty("jvm_pid", ProcessHandle.current().pid()); REPORT.addProperty("frame", frame);
        REPORT.addProperty("thread", thread); REPORT.addProperty("gl_context", context);
        REPORT.addProperty("gl_vendor", GL11.glGetString(GL11.GL_VENDOR));
        REPORT.addProperty("gl_renderer", GL11.glGetString(GL11.GL_RENDERER));
        REPORT.addProperty("gl_version", GL11.glGetString(GL11.GL_VERSION));
        REPORT.addProperty("pack_name", Iris.getCurrentPackName());
        REPORT.addProperty("pipeline_identity", System.identityHashCode(p));
        REPORT.addProperty("admission_branch", unbound ? "unbound_temporal_r1" : "reimagined_r11");
        REPORT.addProperty("archive_property", System.getProperty(property("archiveSha256"), ""));
        REPORT.addProperty("options_pin_property", System.getProperty(property("optionsPin"), ""));
        var client = Minecraft.getInstance();
        REPORT.add("main_surface_extent", JSON.toJsonTree(new int[]{client.getMainRenderTarget().width, client.getMainRenderTarget().height}));
        REPORT.addProperty("dimension", client.level.dimension().location().toString());
        REPORT.addProperty("world_time", client.level.getGameTime());
        REPORT.add("camera_position", JSON.toJsonTree(new double[]{client.gameRenderer.getMainCamera().getPosition().x,
                client.gameRenderer.getMainCamera().getPosition().y, client.gameRenderer.getMainCamera().getPosition().z}));
        REPORT.add("effective_options", JSON.toJsonTree(WorkloadInputs.effectiveOptions()));
        REPORT.add("draws", DRAWS);
        require(context != 0 && GL.getCapabilities().OpenGL41 && !Iris.isFallback(), "PINNED_GL41_WORLD_REQUIRED");
        for (String flag : List.of("gpuTiming", "cpuTiming", "cpuLayerTiming"))
            require("false".equals(System.getProperty("shaderlod.dev." + flag)), "EXPLICIT_FALSE_REQUIRED:" + flag);
        Path archive = Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName());
        String actual = hashBytes(Files.readAllBytes(archive)); REPORT.addProperty("archive_sha256", actual);
        if (unbound) unboundPins(archive, actual);
        else require(ARCHIVE.equals(actual) && ARCHIVE.equals(System.getProperty("shaderlod.rasterR1.archiveSha256")), "EXACT_PRIVATE_CANDIDATE_REQUIRED");
        var versions = new TreeMap<String, String>();
        for (var mod : ModList.get().getMods()) versions.put(mod.getModId(), mod.getVersion().toString());
        REPORT.add("loaded_versions", JSON.toJsonTree(versions));
        var artifacts = new JsonArray(); REPORT.add("loaded_artifacts", artifacts);
        boolean irisPin = false, sodiumPin = false;
        for (var file : ModList.get().getModFiles()) {
            if (file.getMods().stream().noneMatch(m -> Set.of("iris", "sodium", "shaderlod").contains(m.getModId()))) continue;
            var item = new JsonObject(); artifacts.add(item); Path path = file.getFile().getFilePath();
            item.addProperty("path", path.toString());
            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                String digest = hashBytes(Files.readAllBytes(path)); item.addProperty("sha256", digest);
                irisPin |= file.getMods().stream().anyMatch(m -> m.getModId().equals("iris"))
                        && digest.equals("3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83");
                sodiumPin |= file.getMods().stream().anyMatch(m -> m.getModId().equals("sodium"))
                        && digest.equals("7acd7af2ef75ec883a6872c17494ead65e38025151eb08a1d60e36d34377f403");
            }
            else item.addProperty("artifact_scope", "development directory; no jar hash or transformed-bytecode proof");
        }
        require("1.21.1".equals(versions.get("minecraft")) && "21.1.253".equals(versions.get("neoforge")), "MC_LOADER_PIN_CHANGED");
        if (unbound) require(irisPin && sodiumPin
                && !versions.containsKey("distanthorizons") && !versions.containsKey("voxy")
                && !versions.containsKey("create") && !versions.containsKey("flywheel"), "UNBOUND_SUPPORTED_ARTIFACTS_REQUIRED");
        REPORT.add("controller_and_registered_core", coreState(p));
        var allocations = new JsonArray(); REPORT.add("watched_allocations", allocations);
        for (int logical : new TreeSet<>(watched())) {
            RenderTarget target = all.get(logical); require(target != null, "WATCHED_TARGET_ABSENT:" + logical);
            var item = new JsonObject(); allocations.add(item);
            item.addProperty("logical_target", logical);
            item.addProperty("target_identity", System.identityHashCode(target));
            int w = client.getMainRenderTarget().width, h = client.getMainRenderTarget().height;
            double scale = logical == 1 || logical == 7 ? .5 : logical == 9 || logical == 10 ? .75 : 1.;
            item.add("expected_allocation_extent", JSON.toJsonTree(new int[]{(int)(w * scale), (int)(h * scale)}));
            item.addProperty("target_allocation_matches", target.getWidth() == (int)(w * scale) && target.getHeight() == (int)(h * scale));
            item.add("target_extent", JSON.toJsonTree(new int[]{target.getWidth(), target.getHeight()}));
            item.add("main", texture(target.getMainTexture(), logical));
            item.add("alt", texture(target.getAltTexture(), logical));
            if (unbound) require(item.get("target_allocation_matches").getAsBoolean()
                    && target.getMainTexture() != target.getAltTexture(), "UNBOUND_ALLOCATION_SIDES_REQUIRED:" + logical);
        }
    }
    private static String property(String suffix) { return (unbound ? "shaderlod.unboundTemporalR1." : "shaderlod.rasterR1.") + suffix; }
    private static Set<Integer> watched() { return unbound ? UNBOUND_WATCHED : WATCHED; }
    private static void unboundPins(Path archive, String actual) throws Exception {
        require(UNBOUND_ARCHIVE.matches("[0-9a-f]{64}") && UNBOUND_RECEIPT.matches("[0-9a-f]{64}")
                && UNBOUND_MEMBERS.matches("[0-9a-f]{64}"), "UNBOUND_FINAL_PINS_REQUIRED");
        require(UNBOUND_ARCHIVE.equals(actual) && UNBOUND_ARCHIVE.equals(System.getProperty(property("archiveSha256"))), "EXACT_UNBOUND_CANDIDATE_REQUIRED");
        Path receiptPath = Path.of(System.getProperty(property("receiptPin"), ""));
        byte[] bytes = Files.readAllBytes(receiptPath); require(UNBOUND_RECEIPT.equals(hashBytes(bytes)), "EXACT_UNBOUND_RECEIPT_REQUIRED");
        var receipt = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        for (var pin : Map.of("schema", "shaderlod-unbound-temporal-controller-r1",
                "original_archive_sha256", "98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a",
                "native_map_sha256", UNBOUND_NATIVE, "candidate_archive_sha256", UNBOUND_ARCHIVE,
                "candidate_member_pins_sha256", UNBOUND_MEMBERS, "c2_format", "RGBA16F",
                "output_extent", "1920x1200", "compact_graph", "deferred2:10,composite2:9",
                "internal_composite5", "true").entrySet())
            require(receipt.has(pin.getKey()) && pin.getValue().equals(receipt.get(pin.getKey()).getAsString()), "UNBOUND_RECEIPT_FIELD:" + pin.getKey());
        require(UNBOUND_NATIVE.equals(hashBytes(Files.readAllBytes(Path.of(System.getProperty(property("optionsPin"), ""))))), "EXACT_UNBOUND_NATIVE398_REQUIRED");
        byte[] memberBytes = Files.readAllBytes(receiptPath.resolveSibling("candidate-member-pins.json"));
        require(UNBOUND_MEMBERS.equals(hashBytes(memberBytes)), "EXACT_UNBOUND_SOURCE_PINS_REQUIRED");
        var members = JsonParser.parseString(new String(memberBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        var sources = new JsonObject(); REPORT.add("admitted_source_member_sha256", sources);
        try (var zip = new ZipFile(archive.toFile())) {
            for (String name : List.of("program/composite5.glsl", "program/composite6.glsl", "program/deferred2.glsl",
                    "program/composite2.glsl", "lib/pipelineSettings.glsl", "lib/shaderlod/unbound_temporal_r1.glsl",
                    "lib/shaderlod/unbound_metadata_r1.glsl", "lib/shaderlod/unbound_resolve_r1.glsl")) {
                String member = "shaders/" + name; var entry = zip.getEntry(member);
                require(entry != null && members.has(member), "UNBOUND_SOURCE_MEMBER_ABSENT:" + member);
                try (var stream = zip.getInputStream(entry)) {
                    String digest = hashBytes(stream.readAllBytes()); sources.addProperty(member, digest);
                    require(digest.equals(members.get(member).getAsString()), "UNBOUND_SOURCE_MEMBER_CHANGED:" + member);
                }
            }
        }
        require(Minecraft.getInstance().getMainRenderTarget().width == 1920 && Minecraft.getInstance().getMainRenderTarget().height == 1200,
                "UNBOUND_EXACT_OUTPUT_REQUIRED");
        require(Boolean.TRUE.equals(field(ComplementaryRasterR1.class, null, "unboundGraph")) && ComplementaryRasterR1.isActive(), "UNBOUND_ACTIVE_BRANCH_REQUIRED");
        REPORT.addProperty("receipt_sha256", UNBOUND_RECEIPT); REPORT.add("source_receipt", receipt);
    }
    /** Forward existing SpatialCompositeMixin HEAD. Each group selects existing native pass objects. */
    public static void enter(Object renderer, WorldRenderingPipeline p, CompositePass g, List<?> passes, RenderTargets all) {
        if (g != CompositePass.DEFERRED && g != CompositePass.COMPOSITE || !eligible()) return;
        try {
            if (!started) start(p, all);
            validate(); require(p == pipeline && all == targets, "PIPELINE_TARGET_CHANGED");
            owner = renderer; group = g; SELECTED.clear();
            for (Object value : passes) if (value instanceof SpatialPassAccessor pass && PASSES.contains(pass.shaderlod$name())) {
                var c = COMPILED.get(pass.shaderlod$program());
                require(c != null && c.owner == renderer && c.pipeline == p && c.pack == pack
                        && c.name.equals(pass.shaderlod$name()) && Arrays.equals(c.outputs, pass.shaderlod$drawBuffers()), "ACTUAL_PASS_SOURCE_JOIN_FAILED:" + pass.shaderlod$name());
                if (unbound) require(c.unboundSource, "ACTUAL_UNBOUND_SOURCE_BRANCH_REQUIRED:" + c.name);
                require(SELECTED.put(c.program.getProgramId(), pass) == null, "PROGRAM_ID_ALIAS");
            }
        } catch (Exception | LinkageError e) { fail(e); }
    }
    /** Existing SpatialQuadMixin HEAD: exact native CompositeRenderer draw after use/bind/viewport. */
    public static void beforeQuad() {
        if (!started || done || owner == null) return;
        try {
            validate(); int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            SpatialPassAccessor pass = SELECTED.get(program);
            if (pass == null) {
                if (PASSES.contains(ComplementaryRasterR1.passName())) {
                    REPORT.addProperty("unjoined_named_pass", ComplementaryRasterR1.passName());
                    REPORT.addProperty("unjoined_current_program", program);
                    throw new IllegalStateException("NAMED_DRAW_WITHOUT_SELECTED_PROGRAM_JOIN");
                }
                return;
            }
            var c = COMPILED.get(pass.shaderlod$program());
            var event = new JsonObject(); DRAWS.add(event);
            event.addProperty("pass", c.name); event.addProperty("group", group.toString());
            event.addProperty("current_program", program); event.addProperty("renderer_identity", System.identityHashCode(owner));
            event.addProperty("actual_pass_identity", System.identityHashCode(pass)); event.add("source", c.hashes.deepCopy());
            require(c.name.equals(ComplementaryRasterR1.passName()) && GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_TRUE, "NAMED_LINKED_PROGRAM_JOIN_FAILED");
            require(OBSERVED.add(c.name), "DUPLICATE_WATCHED_DRAW");
            int[] observedViewport = int4(GL11.GL_VIEWPORT);
            event.add("viewport", JSON.toJsonTree(observedViewport));
            int[] observedScissor = int4(GL11.GL_SCISSOR_BOX); boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            event.add("scissor_box", JSON.toJsonTree(observedScissor)); event.addProperty("scissor_enabled", scissorEnabled);
            event.addProperty("controller_active", ComplementaryRasterR1.isActive());
            if (unbound) {
                require(ComplementaryRasterR1.isActive(), "UNBOUND_ACTIVE_DRAW_REQUIRED");
                if (Set.of("composite5", "composite6", "deferred2", "composite2").contains(c.name)) {
                    int[] expected = c.name.equals("composite6") ? new int[]{0, 0, 1920, 1200} : new int[]{0, 0, 1440, 900};
                    event.add("expected_viewport", JSON.toJsonTree(expected));
                    require(Arrays.equals(observedViewport, expected), "UNBOUND_PASS_VIEWPORT_REQUIRED:" + c.name);
                    if (c.name.equals("composite5")) require(scissorEnabled && Arrays.equals(observedScissor, expected), "UNBOUND_C5_INTERNAL_SCISSOR_REQUIRED");
                    if (c.name.equals("composite6")) require(!scissorEnabled || observedScissor[0] <= 0 && observedScissor[1] <= 0
                            && observedScissor[0] + observedScissor[2] >= 1920 && observedScissor[1] + observedScissor[3] >= 1200,
                            "UNBOUND_C6_FULL_OUTPUT_SCISSOR_REQUIRED");
                }
                if (c.name.equals("composite") || c.name.equals("composite4")) {
                    int logical = c.name.equals("composite") ? 5 : 0;
                    boolean skip = ComplementaryRasterR1.skipMip(c.name, targets.get(logical));
                    event.addProperty("controller_skip_mip_target", logical); event.addProperty("controller_skip_mip", skip);
                    event.addProperty("skip_mip_scope", "existing controller decision; no executed mip interception or avoided-work proof");
                    require(skip, "UNBOUND_COMPACT_MIP_OWNER_REQUIRED");
                }
            }
            var uniforms = new JsonObject(); event.add("linked_uniforms", uniforms);
            boolean nativeCopy = unboundNativeCopy(c);
            if (nativeCopy) require(observedViewport[0] == 0 && observedViewport[1] == 0
                    && observedViewport[2] == 1440 && observedViewport[3] == 900
                    && scissorEnabled && Arrays.equals(observedScissor, observedViewport), "UNBOUND_C3_COPY_INTERNAL_VIEWPORT_SCISSOR_REQUIRED");
            boolean linkedPredicate = true;
            for (String name : UNIFORMS) {
                var item = new JsonObject(); uniforms.add(name, item);
                int location = GL20.glGetUniformLocation(program, name); item.addProperty("location", location);
                if (location < 0) { item.addProperty("availability", "absent_or_optimized_out"); linkedPredicate = false; continue; }
                int value = GL20.glGetUniformi(program, location); item.addProperty("value", value);
                linkedPredicate &= switch (name) { case "shaderlodBridgeVersion" -> value == 2;
                    case "shaderlodPreset" -> value >= 0 && value <= 2; default -> value == 1; };
            }
            event.addProperty("all_linked_activation_predicates_true", linkedPredicate);
            if (nativeCopy) {
                event.addProperty("activation_predicate_applicability", "NOT_APPLICABLE_CPU_VIEWPORT_COPY");
                event.addProperty("controller_matches_linked_predicate", "not_applicable; pinned native copy consumes no activation uniform");
                event.addProperty("linked_uniform_scope", "actual original locations/values retained; absent uniforms are unknown_or_optimized_out, never true");
            } else event.addProperty("controller_matches_linked_predicate", ComplementaryRasterR1.isActive() == linkedPredicate);
            if (unbound && !nativeCopy) require(linkedPredicate, "UNBOUND_LINKED_ACTIVATION_REQUIRED:" + c.name);
            var core = REPORT.getAsJsonObject("controller_and_registered_core");
            var comparisons = new JsonObject(); event.add("linked_vs_registered_core", comparisons);
            for (String[] pair : List.of(new String[]{"shaderlodBridgeVersion", "bridge_version"},
                    new String[]{"shaderlodEnabled", "registered_enabled"}, new String[]{"shaderlodPreset", "registered_preset"})) {
                var item = uniforms.getAsJsonObject(pair[0]);
                if (!item.has("value") || !core.has(pair[1])) comparisons.addProperty(pair[0], "unavailable");
                else { int expected = pair[1].equals("registered_enabled") ? (core.get(pair[1]).getAsBoolean() ? 1 : 0) : core.get(pair[1]).getAsInt();
                    comparisons.addProperty(pair[0], item.get("value").getAsInt() == expected); }
                if (unbound && !nativeCopy) require(comparisons.get(pair[0]).isJsonPrimitive()
                        && comparisons.get(pair[0]).getAsJsonPrimitive().isBoolean() && comparisons.get(pair[0]).getAsBoolean(),
                        "UNBOUND_LINKED_CORE_REQUIRED:" + pair[0]);
            }
            if (unbound && c.name.equals("composite6")) {
                var temporal = new JsonObject(); event.add("linked_temporal_uniforms", temporal);
                for (String name : TEMPORAL_UNIFORMS) {
                    var item = new JsonObject(); temporal.add(name, item);
                    int location = GL20.glGetUniformLocation(program, name); item.addProperty("location", location);
                    require(location >= 0 && core.has(name), "UNBOUND_C6_TEMPORAL_UNIFORM_REQUIRED:" + name);
                    int value = GL20.glGetUniformi(program, location); item.addProperty("value", value);
                    int expected = core.get(name).getAsInt(); item.addProperty("registered_value", expected);
                    item.addProperty("matches_registered_core", value == expected);
                    require(value == expected, "UNBOUND_C6_TEMPORAL_CORE_MISMATCH:" + name);
                    require(name.endsWith("Version") ? value == 1 : value == 0 || value == 1, "UNBOUND_C6_TEMPORAL_DOMAIN:" + name);
                }
                event.addProperty("temporal_uniforms_allow_history_reuse",
                        temporal.getAsJsonObject("shaderlodHistoryReset").get("value").getAsInt() == 0
                        && temporal.getAsJsonObject("shaderlodScaleTransition").get("value").getAsInt() == 0
                        && temporal.getAsJsonObject("shaderlodPreviousEnabled").get("value").getAsInt() == 1);
            }
            outputs(event, pass); samplers(event, pass, program);
            if (unbound) unboundBindings(event, c.name);
        } catch (Exception | LinkageError e) { fail(e); }
    }
    public static void leave() {
        if (!started || done || owner == null) return;
        try {
            validate(); owner = null; SELECTED.clear();
            if (group == CompositePass.COMPOSITE) {
                REPORT.add("observed_passes", JSON.toJsonTree(new TreeSet<>(OBSERVED)));
                require(OBSERVED.equals(PASSES), "WATCHED_PRE_DRAW_INVENTORY_INCOMPLETE");
                REPORT.addProperty("status", "one_frame_pre_draw_activation_state_observed"); finish();
            }
        } catch (Exception | LinkageError e) { fail(e); }
    }
    /** Optional forward at existing Sodium native opaque draw seam; no sampler or shader-source claims. */
    public static void beforeOpaqueDraw() {
        if (!eligible() || REPORT.has("opaque_submission")) return;
        try {
            if (!Boolean.TRUE.equals(field(ComplementaryRasterR1.class, null, "geometry"))
                    || Boolean.TRUE.equals(field(ComplementaryRasterR1.class, null, "shadow"))
                    || Boolean.TRUE.equals(field(ComplementaryRasterR1.class, null, "handAuxiliary"))) return;
            WorldRenderingPipeline p = Iris.getPipelineManager().getPipelineNullable();
            Object all = field(ComplementaryRasterR1.class, null, "targets");
            if (!started) start(p, (RenderTargets)all);
            validate();
            var state = new JsonObject(); REPORT.add("opaque_submission", state);
            state.addProperty("source_seam", "Sodium ImmediateDrawCommandList before nglMultiDrawElementsBaseVertex; not every geometry draw");
            state.addProperty("current_program", GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
            int[] observedViewport = int4(GL11.GL_VIEWPORT);
            state.add("viewport", JSON.toJsonTree(observedViewport));
            int[] observedScissor = int4(GL11.GL_SCISSOR_BOX); boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            state.add("scissor_box", JSON.toJsonTree(observedScissor)); state.addProperty("scissor_enabled", scissorEnabled);
            state.addProperty("controller_geometry", (Boolean)field(ComplementaryRasterR1.class, null, "geometry"));
            state.addProperty("controller_shadow", (Boolean)field(ComplementaryRasterR1.class, null, "shadow"));
            if (unbound) require(Arrays.equals(observedViewport, new int[]{0, 0, 1440, 900}), "UNBOUND_OPAQUE_INTERNAL_VIEWPORT_REQUIRED");
            if (unbound) require(scissorEnabled && Arrays.equals(observedScissor, observedViewport), "UNBOUND_OPAQUE_INTERNAL_SCISSOR_REQUIRED");
        } catch (Exception | LinkageError e) { fail(e); }
    }
    private static void outputs(JsonObject event, SpatialPassAccessor pass) {
        var outputs = new JsonArray(); event.add("physical_outputs", outputs);
        event.addProperty("draw_fbo", GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
        int[] logicals = pass.shaderlod$drawBuffers();
        for (int i = 0; i < logicals.length; i++) {
            var item = new JsonObject(); outputs.add(item); int logical = logicals[i];
            int attachment = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + i);
            item.addProperty("logical_target", logical); item.addProperty("attachment", attachment);
            int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            item.addProperty("object_type", type); require(type == GL11.GL_TEXTURE, "OUTPUT_NOT_TEXTURE");
            int physical = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            item.addProperty("physical_texture", physical);
            RenderTarget target = targets.get(logical); require(target != null, "OUTPUT_TARGET_ABSENT");
            int expected = pass.shaderlod$stageReadsFromAlt().contains(logical) ? target.getMainTexture() : target.getAltTexture();
            item.addProperty("expected_physical_texture", expected); item.addProperty("physical_join_matches", physical == expected);
            require(physical == expected, "OUTPUT_PHYSICAL_JOIN_FAILED"); item.add("texture", texture(physical, logical));
        }
    }
    private static void samplers(JsonObject event, SpatialPassAccessor pass, int program) {
        var samplers = new JsonObject(); event.add("watched_samplers", samplers);
        for (int logical : new TreeSet<>(watched())) {
            String name = "colortex" + logical; var item = new JsonObject(); samplers.add(name, item);
            int location = GL20.glGetUniformLocation(program, name); item.addProperty("location", location);
            if (location < 0) { item.addProperty("availability", "absent_or_optimized_out"); continue; }
            int unit = GL20.glGetUniformi(program, location); item.addProperty("unit", unit);
            require(unit >= 0 && unit < GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS), "SAMPLER_UNIT_OUT_OF_RANGE");
            int saved = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), physical, sampler;
            try {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                physical = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                sampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            } finally { GL13.glActiveTexture(saved); item.addProperty("active_unit_restored", GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == saved); }
            item.addProperty("physical_texture", physical);
            item.addProperty("sampler_object", sampler);
            RenderTarget target = targets.get(logical); require(target != null, "SAMPLER_TARGET_ABSENT");
            int expected = pass.shaderlod$stageReadsFromAlt().contains(logical) ? target.getAltTexture() : target.getMainTexture();
            item.addProperty("expected_physical_texture", expected); item.addProperty("physical_join_matches", physical == expected);
            require(physical == expected, "SAMPLER_PHYSICAL_JOIN_FAILED:" + name);
            item.add("texture", texture(physical, logical));
            if (sampler != 0) {
                var override = new JsonObject(); item.add("sampler_override", override);
                override.addProperty("min_filter", GL33.glGetSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER));
                override.addProperty("mag_filter", GL33.glGetSamplerParameteri(sampler, GL11.GL_TEXTURE_MAG_FILTER));
            }
        }
    }
    private static int outputTexture(JsonObject event, int logical) {
        for (var value : event.getAsJsonArray("physical_outputs")) {
            var item = value.getAsJsonObject();
            if (item.get("logical_target").getAsInt() == logical) return item.get("physical_texture").getAsInt();
        }
        throw new IllegalStateException("UNBOUND_REQUIRED_OUTPUT_ABSENT:" + logical);
    }
    private static int samplerTexture(JsonObject event, int logical) {
        var item = event.getAsJsonObject("watched_samplers").getAsJsonObject("colortex" + logical);
        require(item.has("physical_texture"), "UNBOUND_REQUIRED_SAMPLER_ABSENT:" + logical);
        return item.get("physical_texture").getAsInt();
    }
    private static void unboundBindings(JsonObject event, String name) {
        if (name.equals("composite3")) {
            require(event.getAsJsonObject("watched_samplers").getAsJsonObject("colortex0").get("unit").getAsInt() == 0,
                    "UNBOUND_C3_COPY_COLORTEX0_UNIT_REQUIRED");
            require(outputTexture(event, 0) != samplerTexture(event, 0), "UNBOUND_C3_COPY_PHYSICAL_ALIAS");
            event.addProperty("native_copy_scope", "exact WORLD_BLUR0 source; controller active, internal viewport/scissor and existing physical FBO/sampler joins; no shader activation predicate");
        }
        if (name.equals("deferred2") || name.equals("composite2")) {
            int source = name.equals("deferred2") ? 5 : 0, destination = name.equals("deferred2") ? 10 : 9;
            require(outputTexture(event, destination) != samplerTexture(event, source), "UNBOUND_COMPACT_COPY_ALIAS");
            require(!ComplementaryRasterR1.skipMip(name, targets.get(destination)), "UNBOUND_COMPACT_MIP_SUPPRESSED");
            event.addProperty("compact_copy_source", source); event.addProperty("compact_copy_destination", destination);
        }
        if (name.equals("composite5")) unboundCurrentColor = outputTexture(event, 3);
        if (!name.equals("composite6")) return;
        int current = samplerTexture(event, 3), history = samplerTexture(event, 2);
        int visible = outputTexture(event, 3), nextHistory = outputTexture(event, 2);
        require(unboundCurrentColor > 0 && current == unboundCurrentColor, "UNBOUND_C5_C6_CURRENT_BINDING_REQUIRED");
        require(current != history && current != visible && current != nextHistory
                && history != visible && history != nextHistory && visible != nextHistory, "UNBOUND_CURRENT_HISTORY_PHYSICAL_ALIAS");
        var join = new JsonObject(); event.add("current_output_history_join", join);
        join.addProperty("c5_internal_current_texture", current); join.addProperty("c6_full_visible_output_texture", visible);
        join.addProperty("c6_persistent_previous_history_texture", history); join.addProperty("c6_full_next_history_texture", nextHistory);
        join.addProperty("all_four_physical_textures_distinct", true);
        join.addProperty("scope", "one-frame binding and pinned c2 Clear=false source; no prior pixels, clear execution, temporal reuse or quality proof");
    }
    private static boolean unboundNativeCopy(Compiled c) {
        if (!unbound || !c.name.equals("composite3")) return false;
        var options = REPORT.getAsJsonObject("effective_options");
        require(options != null && options.has("WORLD_BLUR") && options.get("WORLD_BLUR").getAsString().equals("0")
                && c.outputs.length == 1 && c.outputs[0] == 0 && c.unboundSource
                && "a454594faf2eecdcb073578e2dcb1d13d1ae53781c15f0de0b69a90bcc03736d".equals(c.hashes.get("preprocessed_fragment_sha256").getAsString())
                && "91f4d22a07e68659680bfc0fefdc022ac45dfc913afee6c88ebf1fc634568e2c".equals(c.hashes.get("preprocessed_vertex_sha256").getAsString())
                && "24dfc313f22535128757c44570714074c6094fbdcebce7b06dae24834dc4b3fc".equals(c.hashes.get("transformed_fragment_sha256").getAsString())
                && "1785a895990427589cc1ec9b6241113de6f10c294b652d1f245167ebe26e791d".equals(c.hashes.get("transformed_vertex_sha256").getAsString()),
                "UNBOUND_C3_EXACT_NATIVE_COPY_SOURCE_REQUIRED");
        // Existing archive/options/actual pass-program join already required.
        // WORLD_BLUR0 native main only fetches current c0 and writes c0.
        return true;
    }
    private static JsonObject texture(int id, int logical) {
        var item = new JsonObject(); REPORT.add("last_texture_observation", item);
        item.addProperty("physical_texture", id); item.addProperty("logical_target", logical);
        int savedUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), savedBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            require(id > 0 && GL11.glIsTexture(id), "EXISTING_TEXTURE_REQUIRED"); GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            for (var pair : Map.of("width", GL11.GL_TEXTURE_WIDTH, "height", GL11.GL_TEXTURE_HEIGHT,
                    "internal_format", GL11.GL_TEXTURE_INTERNAL_FORMAT).entrySet())
                item.addProperty(pair.getKey(), GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, pair.getValue()));
            for (var pair : Map.of("min_filter", GL11.GL_TEXTURE_MIN_FILTER, "mag_filter", GL11.GL_TEXTURE_MAG_FILTER,
                    "base_level", GL12.GL_TEXTURE_BASE_LEVEL, "max_level", GL12.GL_TEXTURE_MAX_LEVEL).entrySet())
                item.addProperty(pair.getKey(), GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, pair.getValue()));
            if (unbound) {
                double scale = logical == 1 || logical == 7 ? .5 : logical == 9 || logical == 10 ? .75 : 1.;
                require(item.get("width").getAsInt() == (int)(1920 * scale) && item.get("height").getAsInt() == (int)(1200 * scale),
                        "UNBOUND_PHYSICAL_ALLOCATION_EXTENT_REQUIRED:" + logical);
            }
            if (unbound && Set.of(0, 2, 3, 5, 9, 10).contains(logical)) {
                int expected = logical == 2 ? GL30.GL_RGBA16F : logical == 0 || logical == 9 ? GL30.GL_R11F_G11F_B10F : GL11.GL_RGBA8;
                item.addProperty("expected_internal_format", expected);
                require(item.get("internal_format").getAsInt() == expected, "UNBOUND_TARGET_FORMAT_REQUIRED:" + logical);
            }
            int maxMip = 31 - Integer.numberOfLeadingZeros(Math.max(item.get("width").getAsInt(), item.get("height").getAsInt()));
            var levels = new JsonArray(); item.add("allocated_mip_extents", levels);
            for (int level = 1; level <= maxMip; level++) levels.add(JSON.toJsonTree(new int[]{level,
                    GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH),
                    GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT)}));
        } finally {
            GL13.glActiveTexture(savedUnit); GL11.glBindTexture(GL11.GL_TEXTURE_2D, savedBinding);
            boolean restored = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == savedUnit && GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == savedBinding;
            item.addProperty("query_state_restored", restored); require(restored, "TEXTURE_QUERY_RESTORE_FAILED");
        }
        return item;
    }
    private static JsonObject coreState(Object p) {
        var result = new JsonObject();
        for (String name : List.of("privateGraph", "certified", "active", "sampledActive", "sampledCounter", "sampledWidth", "sampledHeight", "frameOpen"))
            try { result.add(name, JSON.toJsonTree(field(ComplementaryRasterR1.class, null, name))); }
            catch (Exception | LinkageError e) { result.addProperty(name + "_unavailable", e.toString()); }
        if (unbound) for (String name : List.of("unboundGraph", "unboundCoreReset", "reset", "previousActive", "havePrevious"))
            try { result.add(name, JSON.toJsonTree(field(ComplementaryRasterR1.class, null, name))); }
            catch (Exception | LinkageError e) { result.addProperty(name + "_unavailable", e.toString()); }
        // Preserve real selector evidence; do not invoke the controller's mutating eligibility methods.
        var methods = new JsonArray(); result.add("actual_common_uniforms_shaderlod_methods", methods);
        boolean selectorMatches = false;
        for (var method : CommonUniforms.class.getDeclaredMethods()) if (method.getName().contains("shaderlod")) {
            methods.add(method.getName()); selectorMatches |= method.getName().endsWith("$shaderlod$register");
        }
        result.addProperty("controller_registration_selector_matches", selectorMatches);
        try {
            Object notifier = field(p.getClass(), p, "updateNotifier");
            result.addProperty("notifier_identity", System.identityHashCode(notifier));
            Class<?> type = Class.forName("com.pp.shaderlod.CoreUniformFrame", false, CommonUniforms.class.getClassLoader());
            result.addProperty("bridge_version", type.getField("BRIDGE_VERSION").getInt(null));
            Object state; synchronized (type) { state = ((Map<?, ?>)field(type, null, "FRAMES")).get(notifier); }
            result.addProperty("existing_registered_state", state != null);
            if (state != null) {
                Object current = type.getMethod("current").invoke(state);
                if (unbound) for (String[] pair : List.of(new String[]{"shaderlodTemporalVersion", "temporalVersion"},
                        new String[]{"shaderlodHistoryReset", "historyReset"}, new String[]{"shaderlodScaleHistoryVersion", "scaleHistoryVersion"},
                        new String[]{"shaderlodPreviousEnabled", "previousEnabled"}, new String[]{"shaderlodScaleTransition", "scaleTransition"})) {
                    Object value = current.getClass().getMethod(pair[1]).invoke(current);
                    result.addProperty(pair[0], value instanceof Boolean ? ((Boolean)value ? 1 : 0) : ((Number)value).intValue());
                }
                Object core = current.getClass().getMethod("core").invoke(current);
                Object settings = core.getClass().getMethod("settings").invoke(core);
                Object preset = core.getClass().getMethod("preset").invoke(core);
                result.addProperty("registered_enabled", Boolean.TRUE.equals(settings.getClass().getMethod("enabled").invoke(settings)));
                result.addProperty("registered_preset", ((Enum<?>)preset).ordinal());
                result.addProperty("registered_preset_name", ((Enum<?>)preset).name());
            }
        } catch (Exception | LinkageError e) { result.addProperty("registered_core_unavailable", e.toString()); }
        result.addProperty("inactive_reason_scope", "raw controller flags, actual merged method names and existing notifier map; no fabricated controller reason");
        return result;
    }
    private static Object field(Class<?> type, Object object, String name) throws Exception {
        while (type != null) {
            try { var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(object); }
            catch (NoSuchFieldException missing) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private static int[] int4(int token) { int[] value = new int[4]; GL11.glGetIntegerv(token, value); return value; }
    private static void validate() {
        var c = Minecraft.getInstance();
        require(c.level != null && c.screen == null && !c.isPaused() && c.isWindowActive() && !Iris.isFallback()
                && Iris.getCurrentPack().orElse(null) == pack && Iris.getPipelineManager().getPipelineNullable() == pipeline
                && SystemTimeUniforms.COUNTER.getAsInt() == frame && Thread.currentThread().threadId() == thread
                && GLFW.glfwGetCurrentContext() == context, "FRAME_WORLD_PIPELINE_CONTEXT_CHANGED");
    }
    private static String hash(String s) throws Exception { return hashBytes(s.getBytes(StandardCharsets.UTF_8)); }
    private static String hashBytes(byte[] b) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
    private static void require(boolean ok, String reason) { if (!ok) throw new IllegalStateException(reason); }
    private static void reserve() throws Exception {
        if (output != null) return; Path requested = Path.of(OUTPUT);
        require(requested.isAbsolute() && !Files.exists(requested, LinkOption.NOFOLLOW_LINKS), "FRESH_ABSOLUTE_OUTPUT_REQUIRED");
        Files.createDirectories(requested.getParent()); output = requested;
    }
    private static void fail(Throwable e) {
        REPORT.addProperty("status", "unknown_failed_pre_draw_activation_observation");
        REPORT.addProperty("failure", e.toString()); finish();
    }
    private static void finish() {
        done = true;
        try { reserve(); Files.writeString(output, JSON.toJson(REPORT), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); }
        catch (Exception e) { com.mojang.logging.LogUtils.getLogger().warn("Raster activation evidence unavailable; no existing file replaced", e); }
        COMPILED.clear(); SELECTED.clear(); owner = null;
    }
}
