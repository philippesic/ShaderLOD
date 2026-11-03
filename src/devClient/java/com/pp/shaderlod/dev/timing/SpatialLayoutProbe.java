package com.pp.shaderlod.dev.timing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pp.shaderlod.EffectUniformFrame;
import com.pp.shaderlod.ShaderLodEffectConfig;
import com.pp.shaderlod.dev.mixin.SpatialPassAccessor;
import com.pp.shaderlod.integration.AdapterRuntimeBinding;
import com.pp.shaderlod.integration.IrisAdapterRuntime;
import com.pp.shaderlod.integration.IrisRuntimeAccess;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.integration.PhotonSpatialReferenceAdapter;
import com.pp.shaderlod.integration.SourceBinding;
import com.pp.shaderlod.integration.SourceFingerprint;
import com.pp.shaderlod.lod.Effect;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.CompositePass;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.DimensionId;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/** Explicit pinned Iris/Photon adapter for the generic raw evidence core. Always untimed and default off. */
public final class SpatialLayoutProbe {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final SpatialLayoutEvidence.NativeDriver DRIVER = new SpatialLayoutEvidence.NativeDriver();
    private static final Map<Program, CompiledStage> PROGRAMS = new IdentityHashMap<>();
    private static final Map<WorldRenderingPipeline, PipelineState> PIPELINES = new IdentityHashMap<>();
    private static final Map<Object, VerifiedPack> PACKS = new IdentityHashMap<>();
    private static long nextGeneration, nextFrame, nextDraw;
    private static Capture capture;
    private static Context context;
    private static SpatialLayoutEvidence.Draw pending;
    private SpatialLayoutProbe() {}

    public static boolean enabled() { return Boolean.getBoolean(SpatialLayoutEvidence.PROPERTY); }

    /** Early gate, before Iris builds a pipeline; off performs no source/file/native work. */
    public static void initialize() { if (enabled()) requireDiagnosticProperties(System.getProperties()); }

    static void requireDiagnosticProperties(Properties properties) {
        if (!Boolean.parseBoolean(properties.getProperty(SpatialLayoutEvidence.PROPERTY, "false"))) return;
        if (!Boolean.parseBoolean(properties.getProperty("shaderlod.dev.spatialLayoutOnly", "false")))
            throw new IllegalArgumentException("Spatial layout requires spatialLayoutOnly=true");
        for (String timing : List.of("gpuTiming", "cpuTiming", "cpuLayerTiming")) {
            if (!"false".equalsIgnoreCase(properties.getProperty("shaderlod.dev." + timing, "")))
                throw new IllegalArgumentException("Spatial layout requires explicit " + timing + "=false");
        }
        if (Boolean.parseBoolean(properties.getProperty("shaderlod.dev.benchmark", "false")))
            throw new IllegalArgumentException("Spatial layout forbids benchmarking");
        if (!Boolean.parseBoolean(properties.getProperty("shaderlod.dev.measureWorkload", "false"))
                || properties.getProperty("shaderlod.dev.compare", "").isBlank()
                || Boolean.parseBoolean(properties.getProperty("shaderlod.dev.benchmarkOnly", "false"))) {
            throw new IllegalArgumentException("Spatial layout requires the fixed untimed WorkloadSession; phased/benchmark captures are unsupported");
        }
    }

    /** Exact source object used by createProgram -> exact returned Program, before numeric IDs can be reused. */
    public static void compiledProgram(Object renderer, WorldRenderingPipeline pipeline, CompositePass group,
                                       ProgramSource source, Program program, Map<?, String> transformed) {
        if (!enabled() || group != CompositePass.DEFERRED || program == null) return;
        var stage = stage(source.getName());
        if (stage == null) return;
        Object pack = source.getParent().getPack();
        var options = options(source.getParent().getPack().getShaderPackOptions());
        var compiled = new CompiledStage(renderer, pipeline, pack, stage, source.getName(), program,
                digest(source.getFragmentSource().orElseThrow()), digest(source.getVertexSource().orElseThrow()),
                options, source.getDirectives().getDrawBuffers().clone(), transformedDigests(transformed));
        PROGRAMS.put(program, compiled);
        PIPELINES.computeIfAbsent(pipeline, p -> new PipelineState(p, ++nextGeneration));
    }

    /** Notifier RETURN observes the same immutable values already saved by production uniform suppliers. */
    public static void frameUpdated(FrameUpdateNotifier notifier) {
        if (!enabled()) return;
        for (var pipeline : PIPELINES.values()) {
            if (pipeline.pipeline.getFrameUpdateNotifier() != notifier) continue;
            var state = EffectUniformFrame.forNotifier(notifier, notifier::addListener);
            var active = Iris.getCurrentPack().orElse(null);
            pipeline.frame = new Frame(++nextFrame, active, active == null ? Map.of() : options(active.getShaderPackOptions()),
                    state.binding(), state.control(Effect.OPAQUE_LIGHTING));
        }
    }

    private static Map<String, String> transformedDigests(Map<?, String> transformed) {
        var result = new TreeMap<String, String>();
        transformed.forEach((key, value) -> { if (value != null) result.put(key.toString(), digest(value)); });
        return Map.copyOf(result);
    }

    /** Scopes an actual renderer's Pass list. No sampler-list or numeric-ID-only stage discovery. */
    public static void enterRenderer(Object renderer, WorldRenderingPipeline pipeline, CompositePass group, List<?> passes) {
        if (!enabled()) return;
        context = null;
        if (capture == null || !capture.armed || group != CompositePass.DEFERRED) return;
        var selected = new LinkedHashMap<Integer, CompiledStage>();
        for (Object object : passes) {
            var pass = (SpatialPassAccessor) object;
            Program program = pass.shaderlod$program();
            var compiled = PROGRAMS.get(program);
            if (compiled == null) continue;
            if (compiled.renderer != renderer || compiled.pipeline != pipeline
                    || !compiled.name.equals(pass.shaderlod$name())
                    || !java.util.Arrays.equals(compiled.drawBuffers, pass.shaderlod$drawBuffers())
                    || selected.put(program.getProgramId(), compiled) != null) {
                capture.fail("ACTUAL_PASS_SOURCE_PROGRAM_ASSOCIATION_MISMATCH"); return;
            }
        }
        context = new Context(pipeline, Map.copyOf(selected));
    }

    public static void leaveRenderer() {
        if (!enabled()) return;
        if (pending != null && capture != null) capture.fail("DRAW_DID_NOT_COMPLETE_BEFORE_RENDERER_RETURN");
        pending = null; context = null;
    }

    public static void beginCapture(Path directory) {
        if (!enabled()) return;
        requireDiagnosticProperties(System.getProperties());
        if (capture != null) throw new IllegalStateException("Spatial layout capture already exists");
        capture = new Capture(directory.resolve("spatial-layout-evidence.json"));
    }

    /** Quad HEAD runs after the real Program.use uniforms/samplers/images and CustomUniforms.push. */
    public static void beforeQuadDraw() {
        if (!enabled() || capture == null || !capture.armed || context == null || capture.draws >= 8) return;
        var compiled = context.programs.get(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
        if (compiled == null) return;
        try {
            if (pending != null) throw new IllegalStateException("UNPAIRED_QUAD_HEAD");
            capture.draws++;
            var pipeline = PIPELINES.get(context.pipeline);
            if (pipeline == null || pipeline.frame == null) throw new IllegalStateException("NO_ACTUAL_NOTIFIER_FRAME");
            var frame = pipeline.frame;
            var pack = currentPack();
            var verified = PACKS.get(pack.identity());
            if (verified == null) {
                verified = verifyPack(pack);
                PACKS.put(pack.identity(), verified);
            }
            boolean sourceMatches = compiled.pack == pack.identity() && frame.pack == pack.identity()
                    && compiled.options.equals(verified.options) && frame.options.equals(verified.options)
                    && pack.effectiveOptions().equals(verified.options)
                    && verified.stamp.equals(stamp(pack.source()))
                    && compiled.transformedDigests.containsKey("VERTEX") && compiled.transformedDigests.containsKey("FRAGMENT")
                    && java.util.Arrays.equals(compiled.drawBuffers, compiled.stage == SpatialLayoutEvidence.Stage.PRODUCER ? new int[]{0,2} : new int[]{0,3});
            var target = Minecraft.getInstance().getMainRenderTarget();
            if (capture.session == null) {
                capture.pack = verified;
                capture.pipeline = pipeline;
                capture.contract = contract(verified.binding, verified.optionsDigest, target.width, target.height);
                capture.session = SpatialLayoutEvidence.open(System.getProperties(), capture.contract, DRIVER);
                capture.session.beginCapture();
            }
            boolean identity = capture.pack == verified && capture.pipeline == pipeline
                    && target.width == capture.contract.width() && target.height == capture.contract.height()
                    && Iris.getCurrentDimension() == DimensionId.OVERWORLD
                    && Iris.getPipelineManager().getPipelineNullable() == context.pipeline;
            var typed = typedPolicy(programId(compiled));
            boolean available = sourceMatches && identity && available(frame.binding)
                    && available(IrisAdapterRuntime.snapshot()) && !Iris.isFallback()
                    && typedMatches(typed, frame.control)
                    && typed.epoch != null && typed.epoch == SystemTimeUniforms.COUNTER.getAsInt();
            var stageContract = compiled.stage == SpatialLayoutEvidence.Stage.PRODUCER
                    ? capture.contract.producer() : capture.contract.consumer();
            pending = new SpatialLayoutEvidence.Draw(compiled.stage, stageContract.identity(),
                    verified.fingerprint.memberSha256().get(member(compiled.stage)), verified.fingerprint.sourceSha256(),
                    digestOptions(pack.effectiveOptions()), available, programId(compiled), pipeline.generation,
                    frame.ordinal, ++nextDraw, typed.epoch == null ? -1 : typed.epoch);
            var provenance = new JsonObject();
            provenance.add("draw", JSON.toJsonTree(pending));
            provenance.addProperty("actualPassName", compiled.name);
            provenance.addProperty("actualCompositeGroup", "DEFERRED");
            provenance.addProperty("preTransformFragmentSha256", compiled.fragmentDigest);
            provenance.addProperty("preTransformVertexSha256", compiled.vertexDigest);
            provenance.add("actualTransformPatcherStageSha256", JSON.toJsonTree(compiled.transformedDigests));
            provenance.add("actualDrawBuffers", JSON.toJsonTree(compiled.drawBuffers));
            provenance.add("frameBinding", JSON.toJsonTree(frame.binding));
            provenance.add("frameOpaquePolicy", JSON.toJsonTree(frame.control));
            provenance.add("typedLinkedPolicy", JSON.toJsonTree(typed));
            provenance.addProperty("sourceOptionsIdentityMatches", sourceMatches);
            provenance.addProperty("pipelineDimensionExtentMatches", identity);
            capture.provenance.add(provenance);
            capture.session.beforeDraw(pending);
        } catch (RuntimeException | IOException failure) {
            pending = null; capture.fail(failure.toString());
            if (capture.session != null) capture.session.endCapture();
            capture.armed = false;
        }
    }

    /** Same immutable Draw at the actual quad RETURN; the core rechecks current program/typed epoch/layout. */
    public static void afterQuadDraw() {
        if (!enabled() || pending == null || capture == null) return;
        var draw = pending; pending = null;
        if (capture.session == null) return;
        try {
            var frame = capture.pipeline.frame;
            var pack = currentPack();
            var target = Minecraft.getInstance().getMainRenderTarget();
            boolean coherent = frame.ordinal == draw.renderFrame() && frame.pack == pack.identity()
                    && PACKS.get(pack.identity()) == capture.pack
                    && pack.effectiveOptions().equals(capture.pack.options)
                    && capture.pack.stamp.equals(stamp(pack.source()))
                    && target.width == capture.contract.width() && target.height == capture.contract.height()
                    && Iris.getPipelineManager().getPipelineNullable() == capture.pipeline.pipeline
                    && Iris.getCurrentDimension() == DimensionId.OVERWORLD && !Iris.isFallback()
                    && available(frame.binding) && available(IrisAdapterRuntime.snapshot())
                    && typedMatches(typedPolicy(draw.expectedProgram()), frame.control)
                    && SystemTimeUniforms.COUNTER.getAsInt() == draw.epoch();
            if (!coherent) throw new IllegalStateException("SOURCE_OPTIONS_FRAME_PIPELINE_OR_POLICY_CHANGED_DURING_ACTUAL_QUAD");
            capture.session.afterDraw(draw);
        } catch (RuntimeException | IOException failure) {
            capture.fail(failure.toString()); capture.session.endCapture(); capture.armed = false;
        }
    }

    public static void endCapture() {
        if (!enabled() || capture == null) return;
        capture.armed = false;
        if (pending != null) capture.fail("CAPTURE_ENDED_WITHOUT_QUAD_RETURN");
        pending = null; context = null;
        if (capture.session != null) capture.session.endCapture();
        try {
            if (capture.pack != null && !capture.pack.fingerprint.equals(PackSourceReader.read(capture.pack.source))) {
                capture.fail("TERMINAL_SOURCE_CHANGED_SINCE_PACK_VERIFICATION");
            }
            if (capture.pack != null) {
                var pack = currentPack();
                var target = Minecraft.getInstance().getMainRenderTarget();
                if (PACKS.get(pack.identity()) != capture.pack || !pack.effectiveOptions().equals(capture.pack.options)
                        || target.width != capture.contract.width() || target.height != capture.contract.height()
                        || Iris.getPipelineManager().getPipelineNullable() != capture.pipeline.pipeline
                        || Iris.getCurrentDimension() != DimensionId.OVERWORLD || Iris.isFallback()
                        || !available(IrisAdapterRuntime.snapshot())) capture.fail("TERMINAL_RUNTIME_CONTRACT_CHANGED");
            }
        } catch (RuntimeException | IOException failure) {
            capture.fail("TERMINAL_RUNTIME_QUERY_FAILED: " + failure);
        }
        try { Files.writeString(capture.output, JSON.toJson(report())); }
        catch (IOException failure) { throw new IllegalStateException("Cannot save spatial layout evidence", failure); }
    }

    public static JsonObject report() {
        var report = new JsonObject();
        report.addProperty("kind", "pinned_iris_photon_spatial_layout_integration");
        report.addProperty("performanceEvidence", false); report.addProperty("diagnosticOnly", true);
        report.addProperty("defaultOff", true);
        report.addProperty("stageAssociation", "actual createProgram ProgramSource->Program + actual DEFERRED Pass name/program/drawBuffers + quad HEAD/RETURN");
        if (capture == null) { report.addProperty("status", "UNKNOWN_CAPTURE_NOT_STARTED"); return report; }
        report.addProperty("captureWindowFinished", !capture.armed);
        var raw = capture.session == null ? null : capture.session.report();
        boolean observed = raw != null && !raw.getAsJsonArray("records").isEmpty()
                && raw.getAsJsonArray("records").asList().stream().noneMatch(r -> r.getAsJsonObject().get("status").getAsString().equals("UNKNOWN"));
        report.addProperty("status", capture.failures.isEmpty() && observed ? "OBSERVATIONS_ONLY" : "UNKNOWN");
        report.add("integrationFailures", capture.failures.deepCopy());
        report.add("drawProvenance", capture.provenance.deepCopy());
        if (capture.pack != null) {
            report.addProperty("sourcePath", capture.pack.source.toString());
            report.add("sourceFingerprint", JSON.toJsonTree(capture.pack.fingerprint));
            report.add("verifiedSourceBinding", JSON.toJsonTree(capture.pack.binding));
            report.add("effectiveOptions", JSON.toJsonTree(capture.pack.options));
            report.addProperty("effectiveOptionsSha256", capture.pack.optionsDigest);
        }
        if (raw != null) report.add("rawLayout", raw);
        report.addProperty("samplingScope", "Fixed screen tiles selected across forest terrain/horizon and near/sky controls; no inferred receiver eligibility or consumer cache acceptance");
        return report;
    }

    static SpatialLayoutEvidence.Stage stage(String name) {
        return switch (name) {
            case "deferred4" -> SpatialLayoutEvidence.Stage.PRODUCER;
            case "deferred5" -> SpatialLayoutEvidence.Stage.CONSUMER;
            default -> null;
        };
    }
    private static int programId(CompiledStage stage) { return stage.program.getProgramId(); }
    private static String member(SpatialLayoutEvidence.Stage stage) {
        return "shaders/world0/" + (stage == SpatialLayoutEvidence.Stage.PRODUCER ? "deferred4.fsh" : "deferred5.fsh");
    }
    static boolean available(AdapterRuntimeBinding.Snapshot snapshot) {
        return snapshot != null && snapshot.descriptor() != null
                && snapshot.descriptor().identity().equals(PhotonSpatialReferenceAdapter.IDENTITY)
                && snapshot.available(PhotonSpatialReferenceAdapter.OPAQUE_SPATIAL_IRRADIANCE);
    }
    private static AdapterRuntimeBinding.Pack currentPack() {
        var pack = new IrisRuntimeAccess(Set.of()).currentPack().orElseThrow(() -> new IllegalStateException("NO_ACTIVE_PACK"));
        return new AdapterRuntimeBinding.Pack(pack.identity(), pack.source(), options(Iris.getCurrentPack().orElseThrow().getShaderPackOptions()));
    }
    private static Map<String, String> options(net.irisshaders.iris.shaderpack.option.ShaderPackOptions options) {
        var keys = new java.util.HashSet<>(options.getOptionSet().getBooleanOptions().keySet());
        keys.addAll(options.getOptionSet().getStringOptions().keySet());
        return IrisRuntimeAccess.effectiveOptions(options, keys);
    }
    private static VerifiedPack verifyPack(AdapterRuntimeBinding.Pack pack) throws IOException {
        // Files are operator-frozen; mutation during/after verification is rejected by stamp and terminal rehash.
        var before = stamp(pack.source());
        var fingerprint = PackSourceReader.read(pack.source());
        var after = stamp(pack.source());
        if (!before.equals(after)) throw new IllegalStateException("PACK_CHANGED_DURING_VERIFICATION");
        var binding = selectBinding(fingerprint, pack.effectiveOptions());
        return new VerifiedPack(pack.source(), fingerprint, binding, pack.effectiveOptions(), digestOptions(pack.effectiveOptions()), after);
    }
    static SourceBinding selectBinding(SourceFingerprint fingerprint, Map<String, String> options) {
        var matches = PhotonSpatialReferenceAdapter.sourceBindings().stream().filter(b -> b.matches(fingerprint) && b.acceptsOptions(options)).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("UNKNOWN_SOURCE_MEMBERS_OR_OPTIONS");
        return matches.getFirst();
    }
    private static String stamp(Path source) throws IOException {
        if (!Files.isRegularFile(source) || Files.isSymbolicLink(source)) throw new IOException("Require frozen regular source archive");
        var a = Files.readAttributes(source, BasicFileAttributes.class);
        return source.toAbsolutePath().normalize() + ":" + a.fileKey() + ":" + a.size() + ":" + a.lastModifiedTime();
    }
    static String digestOptions(Map<String, String> options) {
        return digest(new GsonBuilder().disableHtmlEscaping().create().toJson(new TreeMap<>(options)));
    }
    private static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    static SpatialLayoutEvidence.Contract contract(SourceBinding binding, String optionsDigest, int width, int height) {
        var producer = new SpatialLayoutEvidence.StageContract("world0/deferred4", binding.memberSha256().get(member(SpatialLayoutEvidence.Stage.PRODUCER)),
                List.of(new SpatialLayoutEvidence.Output("shaderlodSkyColor", 0, 0, 0, GL30.GL_R11F_G11F_B10F, SpatialLayoutEvidence.Role.SCENE),
                        new SpatialLayoutEvidence.Output("shaderlodSpatialOutput", 1, 1, 2, GL30.GL_RGBA32F, SpatialLayoutEvidence.Role.CACHE)),
                List.of(new SpatialLayoutEvidence.Input("colortex0", 0, GL30.GL_R11F_G11F_B10F, SpatialLayoutEvidence.Role.SCENE)));
        var consumer = new SpatialLayoutEvidence.StageContract("world0/deferred5", binding.memberSha256().get(member(SpatialLayoutEvidence.Stage.CONSUMER)),
                List.of(new SpatialLayoutEvidence.Output("fragment_color", 0, 0, 0, GL30.GL_R11F_G11F_B10F, SpatialLayoutEvidence.Role.SCENE),
                        new SpatialLayoutEvidence.Output("colortex3_clear", 1, 1, 3, GL11.GL_RGBA8, SpatialLayoutEvidence.Role.OTHER)),
                List.of(new SpatialLayoutEvidence.Input("colortex0", 0, GL30.GL_R11F_G11F_B10F, SpatialLayoutEvidence.Role.SCENE),
                        new SpatialLayoutEvidence.Input("colortex2", 2, GL30.GL_RGBA32F, SpatialLayoutEvidence.Role.CACHE),
                        new SpatialLayoutEvidence.Input("colortex3", 3, GL11.GL_RGBA8, SpatialLayoutEvidence.Role.OTHER)));
        return new SpatialLayoutEvidence.Contract("shaderlod:photon-shared-spatial-native-overworld-v1", binding.sourceSha256(), optionsDigest,
                producer, consumer, "frameCounter", width, height, new SpatialLayoutEvidence.Codec(2, 258, 16777216, -1, 16777216), tiles(width, height), 16);
    }
    static List<SpatialLayoutEvidence.Tile> tiles(int width, int height) {
        var selected = new java.util.LinkedHashSet<SpatialLayoutEvidence.Tile>();
        // OpenGL origin is lower left: terrain/horizon rows plus bottom-near and upper-sky controls.
        for (double y : new double[]{0.30, 0.42, 0.50, 0.56}) for (double x : new double[]{0.25, 0.40, 0.50, 0.60, 0.75})
            selected.add(new SpatialLayoutEvidence.Tile(Math.min((width - 2) / 2, (int)(width * x) / 2), Math.min((height - 2) / 2, (int)(height * y) / 2)));
        for (double y : new double[]{0.08, 0.85}) selected.add(new SpatialLayoutEvidence.Tile((width / 2) / 2, Math.min((height - 2) / 2, (int)(height * y) / 2)));
        return List.copyOf(selected);
    }

    record TypedPolicy(Integer core, Integer enabled, Integer effects, Integer capabilities, Integer availability,
                       Integer mode, Float near, Float far, Float transition, Float medium, Float low, Integer epoch) {}
    private static TypedPolicy typedPolicy(int program) {
        return new TypedPolicy(DRIVER.scalarInt(program, "shaderlodBridgeVersion"), DRIVER.scalarInt(program, "shaderlodEnabled"),
                DRIVER.scalarInt(program, "shaderlodEffectsVersion"), DRIVER.scalarInt(program, "shaderlodCapabilitiesVersion"),
                DRIVER.scalarInt(program, "shaderlodOpaqueSpatialAvailable"), DRIVER.scalarInt(program, "shaderlodOpaqueLightingMode"),
                scalarFloat(program, "shaderlodOpaqueLightingNear"), scalarFloat(program, "shaderlodOpaqueLightingFar"),
                scalarFloat(program, "shaderlodOpaqueLightingTransition"), scalarFloat(program, "shaderlodOpaqueLightingMediumWorkRatio"),
                scalarFloat(program, "shaderlodOpaqueLightingFarWorkRatio"), DRIVER.scalarInt(program, "frameCounter"));
    }
    static boolean typedMatches(TypedPolicy typed, ShaderLodEffectConfig.Control control) {
        return Integer.valueOf(2).equals(typed.core) && Integer.valueOf(1).equals(typed.enabled)
                && Integer.valueOf(2).equals(typed.effects) && Integer.valueOf(1).equals(typed.capabilities)
                && Integer.valueOf(1).equals(typed.availability) && control != null
                && Integer.valueOf(control.mode().protocolValue()).equals(typed.mode)
                && Float.valueOf((float)control.policy().bands().nearBlocks()).equals(typed.near)
                && Float.valueOf((float)control.policy().bands().farBlocks()).equals(typed.far)
                && Float.valueOf((float)control.policy().bands().transitionBlocks()).equals(typed.transition)
                && Float.valueOf((float)control.policy().mediumWorkRatio()).equals(typed.medium)
                && Float.valueOf((float)control.policy().farWorkRatio()).equals(typed.low);
    }
    private static Float scalarFloat(int program, String name) {
        int count = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
        if (count < 0 || count > 4096) return null;
        try (var stack = MemoryStack.stackPush()) {
            var size = stack.mallocInt(1); var type = stack.mallocInt(1);
            for (int i = 0; i < count; i++) {
                if (!name.equals(GL20.glGetActiveUniform(program, i, 1024, size, type))) continue;
                int location = GL20.glGetUniformLocation(program, name);
                if (size.get(0) != 1 || type.get(0) != GL20.GL_FLOAT || location < 0) return null;
                float value = GL20.glGetUniformf(program, location);
                return Float.isFinite(value) ? value : null;
            }
        }
        return null;
    }

    private record CompiledStage(Object renderer, WorldRenderingPipeline pipeline, Object pack, SpatialLayoutEvidence.Stage stage,
                                 String name, Program program, String fragmentDigest, String vertexDigest,
                                 Map<String, String> options, int[] drawBuffers, Map<String, String> transformedDigests) {}
    private record VerifiedPack(Path source, SourceFingerprint fingerprint, SourceBinding binding, Map<String, String> options,
                                String optionsDigest, String stamp) {}
    private record Context(WorldRenderingPipeline pipeline, Map<Integer, CompiledStage> programs) {}
    private record Frame(long ordinal, Object pack, Map<String, String> options, AdapterRuntimeBinding.Snapshot binding, ShaderLodEffectConfig.Control control) {}
    private static final class PipelineState {
        final WorldRenderingPipeline pipeline; final long generation; Frame frame;
        PipelineState(WorldRenderingPipeline pipeline, long generation) { this.pipeline = pipeline; this.generation = generation; }
    }
    private static final class Capture {
        final Path output; final JsonArray provenance = new JsonArray(), failures = new JsonArray();
        boolean armed = true; int draws; VerifiedPack pack; PipelineState pipeline;
        SpatialLayoutEvidence.Contract contract; SpatialLayoutEvidence.Session session;
        Capture(Path output) { this.output = output; }
        void fail(String reason) { if (failures.size() < 32) failures.add(reason); }
    }
}
