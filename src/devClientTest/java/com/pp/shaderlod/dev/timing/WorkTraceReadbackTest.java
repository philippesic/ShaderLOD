package com.pp.shaderlod.dev.timing;

import com.google.gson.JsonParser;
import com.pp.shaderlod.integration.AdapterRuntimeBinding;
import com.pp.shaderlod.integration.CapabilityExport;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.integration.SourceBinding;
import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.lod.adapter.AdapterDescriptor;
import com.pp.shaderlod.lod.adapter.AdapterIdentity;
import com.pp.shaderlod.lod.adapter.ProtocolRequirement;
import com.pp.shaderlod.lod.adapter.WorkCapability;
import com.pp.shaderlod.lod.adapter.WorkKey;
import com.pp.shaderlod.lod.adapter.WorkKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

/** Bounded raw readback/state/failure fixtures, with no native GL context or production output writes. */
public final class WorkTraceReadbackTest {
    private static final WorkKey TRACE = new WorkKey(Effect.VOLUMETRICS, "fixture:work_trace", WorkKind.COUNT);
    private static final AdapterDescriptor DESCRIPTOR = new AdapterDescriptor(new AdapterIdentity("fixture:trace", "1"),
            List.of(new ProtocolRequirement("shaderlod:capabilities", Set.of(1))),
            List.of(new WorkCapability(TRACE, new WorkCapability.CountFloor(1))));
    private static final List<CapabilityExport> EXPORTS = List.of(new CapabilityExport(TRACE, "shaderlodFixtureTraceSchema"));
    private static final int[] PACK = {GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH, GL11.GL_PACK_SKIP_ROWS,
            GL11.GL_PACK_SKIP_PIXELS, GL11.GL_PACK_SWAP_BYTES, GL11.GL_PACK_LSB_FIRST,
            GL12.GL_PACK_IMAGE_HEIGHT, GL12.GL_PACK_SKIP_IMAGES};

    private static WorkTraceEvidence.Metadata metadata() {
        return new WorkTraceEvidence.Metadata(DESCRIPTOR,
                List.of(new SourceBinding(DESCRIPTOR.identity(), "a".repeat(64), Map.of("shaders/fixture.fsh", "b".repeat(64)), Map.of())),
                EXPORTS, Path.of("/fixture/source.zip"), List.of(0, 1, 2), "shaderlodFixtureTraceSchema", 1, 4, 8, 60);
    }

    private static final class Driver implements WorkTraceEvidence.Driver {
        final Map<Integer, Integer> values = new HashMap<>();
        final Map<Integer, Integer> readBuffers = new HashMap<>();
        final Map<Integer, WorkTraceEvidence.Image> images = new HashMap<>();
        final int[] viewport = {3, 5, 40, 28};
        int readFbo = 12, reads, mutations; boolean blend, failPixel, failImage, complete = true;
        boolean failRestore, scissor, masked; int restoreAttempts;
        Driver() {
            values.put(GL20.GL_CURRENT_PROGRAM, 17); values.put(GL30.GL_DRAW_FRAMEBUFFER_BINDING, 23);
            values.put(GL21.GL_PIXEL_PACK_BUFFER_BINDING, 47);
            readBuffers.put(12, GL30.GL_COLOR_ATTACHMENT0 + 4); readBuffers.put(23, GL30.GL_COLOR_ATTACHMENT0 + 6);
            for (int page = 0; page < 3; page++) {
                values.put(GL20.GL_DRAW_BUFFER0 + page, GL30.GL_COLOR_ATTACHMENT0 + page);
                images.put(GL30.GL_COLOR_ATTACHMENT0 + page, new WorkTraceEvidence.Image(100 + page, 0, GL30.GL_RGBA32F, 80, 70));
            }
            for (int i = 0; i < PACK.length; i++) values.put(PACK[i], 8 + i);
        }
        public int integer(int name) {
            if (name == GL30.GL_READ_FRAMEBUFFER_BINDING) return readFbo;
            if (name == GL11.GL_READ_BUFFER) return readBuffers.get(readFbo);
            return values.getOrDefault(name, 0);
        }
        public int[] viewport() { return viewport.clone(); }
        public boolean blendEnabled(int location) { return blend; }
        public boolean enabled(int name) { return scissor && name == GL11.GL_SCISSOR_TEST; }
        public boolean colorWritable(int location) { return !masked; }
        public void bindReadFramebuffer(int framebuffer) { mutations++; readFbo = framebuffer; }
        public void readBuffer(int attachment) {
            mutations++;
            if (attachment == GL30.GL_COLOR_ATTACHMENT0 + 6 && failRestore) {
                restoreAttempts++; throw new IllegalStateException("restore target failed");
            }
            readBuffers.put(readFbo, attachment);
        }
        public void bindPackBuffer(int buffer) { mutations++; values.put(GL21.GL_PIXEL_PACK_BUFFER_BINDING, buffer); }
        public void pack(int name, int value) { mutations++; values.put(name, value); }
        public boolean complete() { return complete; }
        public WorkTraceEvidence.Image image(int attachment) {
            if (failImage) throw new IllegalStateException("attachment getter failed");
            return images.get(attachment);
        }
        public float[] pixel(int x, int y) {
            reads++;
            check(readFbo == 23 && integer(GL21.GL_PIXEL_PACK_BUFFER_BINDING) == 0, "Read correct FBO with pack PBO detached");
            for (int i = 0; i < PACK.length; i++) check(integer(PACK[i]) == (i == 0 ? 1 : 0), "Canonical tight pack state");
            check(x >= 3 && x < 43 && y >= 5 && y < 33, "Read within offset viewport");
            if (failPixel) throw new IllegalStateException("read failed");
            return switch (integer(GL11.GL_READ_BUFFER) - GL30.GL_COLOR_ATTACHMENT0) {
                case 0 -> new float[]{7, 13, 3, 26};
                case 1 -> new float[]{12, 9.5f, 9.5f, 2};
                case 2 -> new float[]{1, 4, 1, 0};
                default -> throw new AssertionError("Unknown selected attachment");
            };
        }
        void restored(Map<Integer, Integer> previous) {
            check(values.equals(previous), "All pack state and PBO restored");
            check(readFbo == 12, "Original read FBO restored");
            check(readBuffers.get(12) == GL30.GL_COLOR_ATTACHMENT0 + 4, "Original FBO read buffer restored");
            if (!failRestore) check(readBuffers.get(23) == GL30.GL_COLOR_ATTACHMENT0 + 6, "Target FBO read buffer independently restored");
            check(values.get(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == 23 && values.get(GL20.GL_CURRENT_PROGRAM) == 17, "Draw FBO and program untouched");
        }
    }

    public static void main(String[] args) throws Exception {
        defaultsAndTiming();
        successfulCapture();
        failuresRestore();
        outputAndBounds();
        codec();
        contracts();
        if (args.length > 0) actualMetadata(Path.of(args[0]));
        if (args.length > 1) pinnedBytecode(Path.of(args[1]));
        System.out.println("PASS WorkTraceReadbackTest: 6 mock/no-GL fixture groups" + (args.length > 0 ? " + exact diagnostic metadata/source acceptance" : ""));
    }

    private static void defaultsAndTiming() {
        String old = System.getProperty(WorkTraceEvidence.PROPERTY);
        try {
            System.clearProperty(WorkTraceEvidence.PROPERTY);
            WorkTraceEvidence.initialize(); WorkTraceEvidence.afterQuadDraw(); WorkTraceEvidence.beginCapture(); WorkTraceEvidence.endCapture();
        } finally { if (old != null) System.setProperty(WorkTraceEvidence.PROPERTY, old); }
        var properties = new Properties(); properties.setProperty("shaderlod.dev.workTraceMetadata", "missing intentionally");
        WorkTraceEvidence.requireDiagnosticProperties(properties);
        properties.setProperty(WorkTraceEvidence.PROPERTY, "true");
        rejected(() -> WorkTraceEvidence.requireDiagnosticProperties(properties));
        properties.setProperty("shaderlod.dev.workTraceOnly", "true"); properties.setProperty("shaderlod.dev.compare", "on");
        properties.setProperty("shaderlod.dev.gpuTiming", "false"); properties.setProperty("shaderlod.dev.cpuTiming", "false");
        properties.setProperty("shaderlod.dev.cpuLayerTiming", "false"); properties.setProperty("shaderlod.dev.measureWorkload", "true");
        WorkTraceEvidence.requireDiagnosticProperties(properties);
        properties.setProperty("shaderlod.dev.cpuTiming", "true"); rejected(() -> WorkTraceEvidence.requireDiagnosticProperties(properties));
        properties.setProperty("shaderlod.dev.cpuTiming", "false"); properties.setProperty("shaderlod.dev.gpuTiming", "true");
        rejected(() -> WorkTraceEvidence.requireDiagnosticProperties(properties));
    }

    private static void successfulCapture() {
        var driver = new Driver(); var previous = Map.copyOf(driver.values);
        var result = WorkTraceEvidence.capture(metadata(), driver);
        check(result.get("status").getAsString().equals("RAW_SAME_DRAW_CAPTURE"), "Complete same-draw raw capture");
        check(!result.get("performanceEvidence").getAsBoolean(), "Explicit diagnostic-only row");
        check(driver.reads == 48 && result.getAsJsonArray("samples").size() == 16, "4x4 grid bounded to 48 RGBA reads");
        var decoded = result.getAsJsonArray("samples").get(0).getAsJsonObject().getAsJsonObject("decoded");
        check(decoded.get("primaryIterations").getAsInt() == 7 && decoded.get("selectedCeilTarget").getAsInt() == 10,
                "Actual counters separate from requested/selected ceil budget");
        driver.restored(previous);
    }

    private static void failuresRestore() {
        for (int failure = 0; failure < 6; failure++) {
            var driver = new Driver(); var previous = Map.copyOf(driver.values);
            if (failure == 0) driver.failPixel = true;
            if (failure == 1) driver.failImage = true;
            if (failure == 2) driver.complete = false;
            if (failure == 3) driver.failRestore = true;
            if (failure == 4) driver.scissor = true;
            if (failure == 5) driver.masked = true;
            var result = WorkTraceEvidence.capture(metadata(), driver);
            check(result.get("status").getAsString().equals("UNKNOWN"), "Failure never yields valid counts");
            if (failure == 3) check(result.has("restoreErrors") && driver.restoreAttempts == 1, "Restoration failures explicit; other restores still attempted");
            driver.restored(previous);
        }
    }

    private static void outputAndBounds() {
        for (int failure = 0; failure < 4; failure++) {
            var driver = new Driver(); var previous = Map.copyOf(driver.values);
            if (failure == 0) driver.blend = true;
            if (failure == 1) driver.values.put(GL20.GL_DRAW_BUFFER0 + 2, GL30.GL_COLOR_ATTACHMENT0 + 5);
            if (failure == 2) driver.images.put(GL30.GL_COLOR_ATTACHMENT0 + 1, new WorkTraceEvidence.Image(2, 0, GL30.GL_RGBA16F, 80, 70));
            if (failure == 3) driver.images.put(GL30.GL_COLOR_ATTACHMENT0 + 2, new WorkTraceEvidence.Image(3, 0, GL30.GL_RGBA32F, 40, 28));
            previous = Map.copyOf(driver.values);
            var result = WorkTraceEvidence.capture(metadata(), driver);
            check(result.get("status").getAsString().equals("UNKNOWN") && driver.reads == 0, "Reject unsafe output/format/extent before reads");
            driver.restored(previous);
        }
        check(WorkTraceEvidence.grid(new int[]{0, 0, 1, 1}, 8).size() == 1, "No repeated grid pixels at tiny viewport");
        check(WorkTraceEvidence.grid(new int[]{0, 0, 90000, 90000}, 8).size() == 64, "Maximum 64 pixels");
        rejected(() -> WorkTraceEvidence.grid(new int[]{0, 0, 40, 28}, 9));
        rejected(() -> WorkTraceEvidence.grid(new int[]{Integer.MAX_VALUE, 0, 2, 1}, 1));
    }

    private static void codec() {
        float[][] valid = {{0, 16777215, 2, 3}, {20, 10.25f, 10.5f, 2}, {1, 0, 1, 0}};
        check(WorkTraceEvidence.decode(valid).get("status").getAsString().equals("VALID_SCHEMA_1"), "Exact upper count boundary and fractional budgets");
        rejectedDecode(new float[][]{{0, Float.NaN, 2, 3}, valid[1], valid[2]});
        rejectedDecode(new float[][]{{0, 16777216, 2, 3}, valid[1], valid[2]});
        rejectedDecode(new float[][]{{0, 2.5f, 2, 3}, valid[1], valid[2]});
        rejectedDecode(new float[][]{valid[0], valid[1], {2, 0, 1, 0}});
        rejectedDecode(new float[][]{valid[0], {20, 10, 10, 4}, valid[2]});
        rejectedDecode(new float[][]{valid[0], valid[1], {1, 0, 1, 1}});
        rejectedDecode(new float[][]{valid[0], valid[1], {1, 0, 0, 0}});
        rejectedDecode(new float[][]{valid[0], valid[1]});
        rejectedDecode(new float[][]{{-1, -1, -1, -1}, valid[1], valid[2]});
        rejectedDecode(new float[][]{{-1, -1, -1, -1}, {-1, -1, -1, -1}, {-1, -1, -1, -1}});
    }
    private static void rejectedDecode(float[][] raw) {
        var decoded = WorkTraceEvidence.decode(raw);
        check(decoded.get("status").getAsString().equals("UNKNOWN") && !decoded.has("primaryIterations"), "Malformed/invalid never becomes zero counters");
    }

    private static void contracts() throws Exception {
        var metadata = metadata();
        var available = new AdapterRuntimeBinding.Snapshot(AdapterRuntimeBinding.Reason.AVAILABLE, DESCRIPTOR, Set.of(TRACE));
        check(WorkTraceEvidence.eligible(metadata, available), "Registered exact descriptor and operation required");
        check(!WorkTraceEvidence.eligible(metadata, AdapterRuntimeBinding.Snapshot.unavailable(AdapterRuntimeBinding.Reason.UNKNOWN_SOURCE)), "Unknown source fails closed without GL");
        check(!WorkTraceEvidence.eligible(metadata, new AdapterRuntimeBinding.Snapshot(AdapterRuntimeBinding.Reason.AVAILABLE, DESCRIPTOR, Set.of())), "Missing operation fails closed");
        Path source = Files.createTempDirectory("work-trace-source-fixture-");
        try {
            Files.createDirectories(source.resolve("shaders")); Files.writeString(source.resolve("shaders/fixture.fsh"), "exact reviewed source");
            var fingerprint = PackSourceReader.read(source);
            var binding = new SourceBinding(DESCRIPTOR.identity(), fingerprint.sourceSha256(), fingerprint.memberSha256(), Map.of());
            var pinned = new WorkTraceEvidence.Metadata(DESCRIPTOR, List.of(binding), EXPORTS, source.toAbsolutePath(), List.of(0, 1, 2), "shaderlodFixtureTraceSchema", 1, 1, 1, 1);
            WorkTraceEvidence.validateSource(pinned);
            Files.writeString(source.resolve("shaders/fixture.fsh"), "edited source");
            rejected(() -> { try { WorkTraceEvidence.validateSource(pinned); } catch (java.io.IOException e) { throw new RuntimeException(e); } });
        } finally {
            Files.deleteIfExists(source.resolve("shaders/fixture.fsh")); Files.deleteIfExists(source.resolve("shaders")); Files.deleteIfExists(source);
        }
        rejected(() -> new SourceBinding(DESCRIPTOR.identity(), "*", Map.of("shaders/*", "b".repeat(64)), Map.of()));
        rejected(() -> new WorkTraceEvidence.Metadata(DESCRIPTOR, metadata.sources(), EXPORTS, metadata.source(), List.of(0, 1, 1), metadata.markerUniform(), 1, 1, 1, 1));
        rejected(() -> new WorkTraceEvidence.Metadata(DESCRIPTOR, metadata.sources(), EXPORTS, metadata.source(), List.of(0, 1, 2), "shaderlodNotRegistered", 1, 1, 1, 1));
    }

    private static void actualMetadata(Path file) throws Exception {
        var metadata = WorkTraceEvidence.parse(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
        WorkTraceEvidence.validateSource(metadata);
    }

    private static void pinnedBytecode(Path iris) throws Exception {
        String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(iris)));
        check(digest.equals("3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83"), "Exact reviewed local Iris 1.8.12 bytecode artifact");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "javap").toString(),
                "-classpath", iris.toString(), "-c", "-p", "net.irisshaders.iris.pathways.FullScreenQuadRenderer",
                "net.irisshaders.iris.pipeline.CompositeRenderer", "net.irisshaders.iris.gl.framebuffer.GlFramebuffer").redirectErrorStream(true).start();
        String bytecode = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        check(process.waitFor() == 0, "Pinned javap succeeds");
        String quad = bytecode.substring(bytecode.indexOf("public void renderQuad();"), bytecode.indexOf("public void end();"));
        check(quad.contains("VertexBuffer.draw:()V") && quad.contains("restorePolygonMode:()V"), "TAIL follows actual quad draw");
        check(!quad.contains("viewport") && !quad.contains("Framebuffer") && !quad.contains("UseProgram")
                && !quad.contains("Program.use"), "No framebuffer/viewport/program change between draw and TAIL");
        String caller = bytecode.substring(bytecode.indexOf("public void renderAll();"), bytecode.indexOf("private net.irisshaders.iris.gl.program.Program createProgram"));
        int draw = caller.indexOf("FullScreenQuadRenderer.renderQuad:()V");
        check(draw > caller.indexOf("Program.use:()V") && draw > caller.indexOf("RenderSystem.viewport:(IIII)V"), "Actual program and viewport installed before draw");
        check(caller.indexOf("BlendModeOverride.restore:()V", draw) > draw
                && caller.indexOf("RenderTarget.bindWrite:(Z)V", draw) > draw, "Caller restores blend and main target after hooked draw");
        String framebuffer = bytecode.substring(bytecode.indexOf("public void addColorAttachment(int, int);"));
        check(framebuffer.contains("int 36064") && framebuffer.contains("framebufferTexture2D"), "Framebuffer uses explicit COLOR_ATTACHMENT0 indexed 2D textures");
        Files.writeString(Path.of("build/work-trace-pinned-bytecode-acceptance.txt"), bytecode);
        System.out.println("PASS pinned Iris bytecode SHA256 " + digest + ": TAIL draw, caller state lifetime, explicit attachments");
    }
    private static void check(boolean condition, String description) { if (!condition) throw new AssertionError(description); }
    private static void rejected(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected explicit contract rejection");
    }
}
