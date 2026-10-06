package com.pp.shaderlod.dev.timing;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pp.shaderlod.integration.AdapterRuntimeBinding;
import com.pp.shaderlod.integration.CapabilityExport;
import com.pp.shaderlod.integration.IrisAdapterRuntime;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.integration.SourceBinding;
import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.lod.adapter.AdapterDescriptor;
import com.pp.shaderlod.lod.adapter.AdapterIdentity;
import com.pp.shaderlod.lod.adapter.ProtocolRequirement;
import com.pp.shaderlod.lod.adapter.WorkCapability;
import com.pp.shaderlod.lod.adapter.WorkKey;
import com.pp.shaderlod.lod.adapter.WorkKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/** Development-only synchronous raw attachment readback. Never a performance measurement. */
public final class WorkTraceEvidence {
    public static final String PROPERTY = "shaderlod.dev.workTrace";
    private static final int[] PACK = {GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH,
            GL11.GL_PACK_SKIP_ROWS, GL11.GL_PACK_SKIP_PIXELS, GL11.GL_PACK_SWAP_BYTES,
            GL11.GL_PACK_LSB_FIRST, GL12.GL_PACK_IMAGE_HEIGHT, GL12.GL_PACK_SKIP_IMAGES};
    private static Session session;

    private WorkTraceEvidence() {}

    /** Constructor-time registration, before Iris seals common-uniform contracts. Off does no I/O or GL. */
    public static void initialize() {
        if (!Boolean.getBoolean(PROPERTY)) return;
        if (session != null) throw new IllegalStateException("Work trace already initialized");
        requireDiagnosticProperties(System.getProperties());
        try {
            var metadataPath = Path.of(requiredProperty("shaderlod.dev.workTraceMetadata"));
            var rawMetadata = JsonParser.parseString(Files.readString(metadataPath)).getAsJsonObject();
            var metadata = parse(rawMetadata);
            validateSource(metadata);
            IrisAdapterRuntime.register(metadata.descriptor(), metadata.sources(), metadata.exports());
            var output = Path.of(requiredProperty("shaderlod.dev.workTraceOutput"));
            session = new Session(metadata, output);
            session.write(header(rawMetadata, metadataPath));
        } catch (IOException exception) {
            throw new IllegalStateException("Work trace metadata/source/output is unreadable", exception);
        }
    }

    static void requireDiagnosticProperties(java.util.Properties properties) {
        if (!Boolean.parseBoolean(properties.getProperty(PROPERTY, "false"))) return;
        if (!Boolean.parseBoolean(properties.getProperty("shaderlod.dev.workTraceOnly", "false"))) {
            throw new IllegalArgumentException("Work trace requires workTraceOnly=true; discard all performance rows");
        }
        boolean comparing = !properties.getProperty("shaderlod.dev.compare", "").isEmpty();
        if (Boolean.parseBoolean(properties.getProperty("shaderlod.dev.gpuTiming", comparing ? "true" : "false"))
                || Boolean.parseBoolean(properties.getProperty("shaderlod.dev.cpuTiming", comparing ? "true" : "false"))
                || Boolean.parseBoolean(properties.getProperty("shaderlod.dev.cpuLayerTiming", "true"))
                || Boolean.parseBoolean(properties.getProperty("shaderlod.dev.benchmark", "false"))) {
            throw new IllegalArgumentException("Work trace forbids GPU/CPU timing and benchmarking");
        }
    }

    private static String requiredProperty(String name) {
        var value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Require " + name);
        return value;
    }

    public record Metadata(AdapterDescriptor descriptor, List<SourceBinding> sources,
                           List<CapabilityExport> exports, Path source, List<Integer> attachments,
                           String markerUniform, int markerValue,
                           int gridSide, int maxCaptures, int intervalDraws) {
        public Metadata {
            sources = List.copyOf(sources);
            exports = List.copyOf(exports);
            attachments = List.copyOf(attachments);
            if (sources.isEmpty() || sources.stream().anyMatch(s -> !s.adapter().equals(descriptor.identity()))) {
                throw new IllegalArgumentException("Require source bindings for this exact descriptor");
            }
            if (attachments.size() != 3 || Set.copyOf(attachments).size() != 3) throw new IllegalArgumentException("Require three distinct simultaneous attachments");
            attachments.forEach(a -> bounded(a, 0, 7, "attachment"));
            bounded(gridSide, 1, 8, "gridSide");
            bounded(maxCaptures, 1, 16, "maxCaptures"); bounded(intervalDraws, 1, 100000, "intervalDraws");
            if (!source.isAbsolute() || markerValue != 1 || exports.stream().noneMatch(e -> e.uniformName().equals(markerUniform))) {
                throw new IllegalArgumentException("Require absolute exact source and registered schema-1 marker export");
            }
            // Reuse the same public registration validator without mutating the production singleton.
            var registration = new com.pp.shaderlod.integration.AdapterRegistration(Map.of(
                    "shaderlod:core", 2, "shaderlod:effects", 2, "shaderlod:capabilities", 1));
            registration.register(descriptor, sources, exports);
        }
    }

    static Metadata parse(JsonObject root) {
        if (root.get("metadataVersion").getAsInt() != 1) throw new IllegalArgumentException("Unsupported metadata version");
        var d = root.getAsJsonObject("descriptor");
        var identity = new AdapterIdentity(d.get("id").getAsString(), d.get("revision").getAsString());
        var protocols = new ArrayList<ProtocolRequirement>();
        d.getAsJsonObject("protocols").entrySet().forEach(e -> protocols.add(new ProtocolRequirement(e.getKey(), Set.of(e.getValue().getAsInt()))));
        var capabilities = new ArrayList<WorkCapability>();
        for (var entry : d.getAsJsonArray("capabilities")) {
            var c = entry.getAsJsonObject();
            capabilities.add(new WorkCapability(key(c), new WorkCapability.CountFloor(c.get("minimumRetainedWork").getAsInt())));
        }
        var descriptor = new AdapterDescriptor(identity, protocols, capabilities);
        var sources = new ArrayList<SourceBinding>();
        for (var entry : root.getAsJsonArray("sourceBindings")) {
            var s = entry.getAsJsonObject();
            var members = new LinkedHashMap<String, String>();
            s.getAsJsonObject("memberSha256").entrySet().forEach(e -> members.put(e.getKey(), e.getValue().getAsString()));
            var options = new LinkedHashMap<String, Set<String>>();
            s.getAsJsonObject("requiredOptions").entrySet().forEach(e -> {
                var values = new java.util.HashSet<String>();
                e.getValue().getAsJsonArray().forEach(v -> values.add(v.getAsString()));
                options.put(e.getKey(), values);
            });
            sources.add(new SourceBinding(identity, s.get("sourceSha256").getAsString(), members, options));
        }
        var exports = new ArrayList<CapabilityExport>();
        for (var entry : root.getAsJsonArray("exports")) {
            var e = entry.getAsJsonObject();
            exports.add(new CapabilityExport(key(e), e.get("uniformName").getAsString()));
        }
        var c = root.getAsJsonObject("capture");
        var attachments = new ArrayList<Integer>();
        c.getAsJsonArray("attachments").forEach(a -> attachments.add(a.getAsInt()));
        return new Metadata(descriptor, sources, exports, Path.of(c.get("source").getAsString()),
                attachments, c.get("markerUniform").getAsString(), c.get("markerValue").getAsInt(),
                c.get("gridSide").getAsInt(), c.get("maxCaptures").getAsInt(), c.get("intervalDraws").getAsInt());
    }

    private static WorkKey key(JsonObject value) {
        return new WorkKey(Effect.valueOf(value.get("effect").getAsString()), value.get("operation").getAsString(),
                WorkKind.valueOf(value.get("kind").getAsString()));
    }

    static void validateSource(Metadata metadata) throws IOException {
        var fingerprint = PackSourceReader.read(metadata.source());
        if (metadata.sources().stream().filter(s -> s.matches(fingerprint)).count() != 1) {
            throw new IllegalArgumentException("Metadata must bind this exact archive and every pinned member uniquely");
        }
    }

    private static void bounded(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) throw new IllegalArgumentException(name + " outside " + minimum + ".." + maximum);
    }

    /** Called only by the separately gated renderQuad()V TAIL mixin. */
    public static void afterQuadDraw() {
        var current = session;
        if (current == null || !current.armed || current.records.size() >= current.metadata.maxCaptures()) return;
        long draw = ++current.draws;
        var metadata = current.metadata;
        var binding = IrisAdapterRuntime.snapshot();
        if (!eligible(metadata, binding)) {
            current.append(unknown("RUNTIME_BINDING_" + binding.reason(), draw));
            return;
        }
        try {
            var driver = new NativeDriver();
            int program = driver.integer(GL20.GL_CURRENT_PROGRAM);
            if (!driver.marker(program, metadata.markerUniform(), metadata.markerValue())) { current.skippedPrograms++; return; }
            if (current.matchingDraws++ % metadata.intervalDraws() != 0) return;
            var result = capture(metadata, driver);
            result.add("schemaUniform", driver.uniform(program, metadata.markerUniform(), GL20.GL_INT));
            result.addProperty("drawOrdinal", draw);
            result.addProperty("renderFrameOrdinal", GpuTimers.frameId());
            result.addProperty("utc", Instant.now().toString());
            result.addProperty("monotonicNanos", System.nanoTime());
            var client = Minecraft.getInstance();
            if (client.level != null) result.addProperty("worldGameTime", client.level.getGameTime());
            result.add("frameCounter", driver.uniform(program, "frameCounter", GL20.GL_INT));
            result.add("frameTimeCounter", driver.uniform(program, "frameTimeCounter", GL11.GL_FLOAT));
            current.append(result);
        } catch (RuntimeException exception) {
            current.append(unknown("READBACK_EXCEPTION: " + exception, draw));
        }
    }

    /** WorkloadSession calls this after warmup and input fingerprinting, at its existing capture boundary. */
    public static void beginCapture() {
        if (session != null) { session.armed = true; session.draws = 0; session.matchingDraws = 0; }
    }

    public static void endCapture() {
        if (session == null) return;
        session.armed = false;
        session.header.addProperty("captureWindowFinished", true);
        session.header.addProperty("terminalStatus", session.records.isEmpty() ? "UNKNOWN_NO_MATCHING_DIAGNOSTIC_DRAW" : "CAPTURED");
        try { session.write(session.header); } catch (IOException e) { throw new IllegalStateException("Cannot save terminal work trace", e); }
    }

    static boolean eligible(Metadata metadata, AdapterRuntimeBinding.Snapshot snapshot) {
        return snapshot.reason() == AdapterRuntimeBinding.Reason.AVAILABLE && snapshot.descriptor() != null
                && snapshot.descriptor().equals(metadata.descriptor())
                && metadata.exports().stream().allMatch(e -> snapshot.available(e.operation()));
    }

    /** GL-independent policy entry point used by restoration/failure fixtures. */
    static JsonObject capture(Metadata metadata, Driver driver) {
        var result = new JsonObject();
        result.addProperty("performanceEvidence", false);
        result.addProperty("status", "UNKNOWN");
        result.addProperty("glErrors", "unobserved; error flags are not consumed");
        int program = driver.integer(GL20.GL_CURRENT_PROGRAM);
        int drawFbo = driver.integer(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = driver.viewport();
        result.addProperty("program", program); result.addProperty("drawFramebuffer", drawFbo);
        result.add("viewport", new com.google.gson.Gson().toJsonTree(viewport));
        result.add("attachments", new com.google.gson.Gson().toJsonTree(metadata.attachments()));
        if (program <= 0 || drawFbo <= 0 || viewport.length != 4 || viewport[0] < 0 || viewport[1] < 0
                || viewport[2] < 1 || viewport[3] < 1) return failed(result, "INVALID_PROGRAM_FRAMEBUFFER_VIEWPORT");
        for (int page = 0; page < 3; page++) {
            if (driver.integer(GL20.GL_DRAW_BUFFER0 + page) != GL30.GL_COLOR_ATTACHMENT0 + metadata.attachments().get(page)
                    || driver.blendEnabled(page) || !driver.colorWritable(page)) return failed(result, "OUTPUT_MAPPING_BLEND_OR_COLOR_MASK_UNSUPPORTED");
        }
        if (driver.enabled(GL11.GL_SCISSOR_TEST) || driver.enabled(GL30.GL_RASTERIZER_DISCARD)
                || driver.enabled(GL11.GL_DEPTH_TEST) || driver.enabled(GL11.GL_STENCIL_TEST)) {
            return failed(result, "PARTIAL_OR_DISCARDED_FULLSCREEN_DRAW_UNSUPPORTED");
        }
        int readFbo = driver.integer(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int readBuffer = driver.integer(GL11.GL_READ_BUFFER);
        int pbo = driver.integer(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] pack = new int[PACK.length];
        for (int i = 0; i < PACK.length; i++) pack[i] = driver.integer(PACK[i]);
        Integer targetReadBuffer = null;
        try {
            driver.bindReadFramebuffer(drawFbo);
            targetReadBuffer = driver.integer(GL11.GL_READ_BUFFER);
            if (!driver.complete()) return failed(result, "FRAMEBUFFER_INCOMPLETE");
            var images = new JsonArray();
            Image first = null;
            for (int attachment : metadata.attachments()) {
                var image = driver.image(GL30.GL_COLOR_ATTACHMENT0 + attachment);
                images.add(new com.google.gson.Gson().toJsonTree(image));
                if (image.internalFormat() != GL30.GL_RGBA32F || image.width() < 1 || image.height() < 1
                        || (long) viewport[0] + viewport[2] > image.width()
                        || (long) viewport[1] + viewport[3] > image.height()
                        || (first != null && (first.width() != image.width() || first.height() != image.height()))) {
                    result.add("attachmentImages", images);
                    return failed(result, "ATTACHMENT_FORMAT_OR_EXTENT_UNSUPPORTED");
                }
                first = image;
            }
            result.add("attachmentImages", images);
            driver.bindPackBuffer(0);
            for (int i = 0; i < PACK.length; i++) driver.pack(PACK[i], i == 0 ? 1 : 0);
            var samples = new JsonArray();
            for (int[] point : grid(viewport, metadata.gridSide())) {
                var sample = new JsonObject();
                sample.addProperty("x", point[0]); sample.addProperty("y", point[1]);
                var pages = new JsonArray();
                float[][] raw = new float[3][];
                for (int page = 0; page < 3; page++) {
                    driver.readBuffer(GL30.GL_COLOR_ATTACHMENT0 + metadata.attachments().get(page));
                    float[] rgba = driver.pixel(point[0], point[1]); raw[page] = rgba;
                    var channels = new JsonArray();
                    for (float value : rgba) { if (Float.isFinite(value)) channels.add(value); else channels.add(com.google.gson.JsonNull.INSTANCE); }
                    pages.add(channels);
                }
                sample.add("pages", pages);
                sample.add("decoded", decode(raw));
                samples.add(sample);
            }
            result.add("samples", samples);
            result.addProperty("status", "RAW_SAME_DRAW_CAPTURE");
            result.addProperty("schema", 1);
        } catch (RuntimeException exception) {
            failed(result, "READBACK_EXCEPTION: " + exception);
        } finally {
            // Restore target FBO's own read-buffer state before returning to the original read FBO.
            final Integer target = targetReadBuffer;
            var restorations = new ArrayList<Runnable>();
            if (target != null) restorations.add(() -> driver.readBuffer(target));
            restorations.add(() -> driver.bindReadFramebuffer(readFbo));
            restorations.add(() -> driver.readBuffer(readBuffer));
            restorations.add(() -> driver.bindPackBuffer(pbo));
            for (int i = 0; i < PACK.length; i++) {
                final int index = i;
                restorations.add(() -> driver.pack(PACK[index], pack[index]));
            }
            var errors = new JsonArray();
            for (var restoration : restorations) {
                try { restoration.run(); } catch (RuntimeException exception) { errors.add(exception.toString()); }
            }
            if (!errors.isEmpty()) { result.add("restoreErrors", errors); failed(result, "STATE_RESTORATION_FAILED"); }
        }
        return result;
    }

    static List<int[]> grid(int[] viewport, int side) {
        bounded(side, 1, 8, "gridSide");
        if (viewport.length != 4 || viewport[0] < 0 || viewport[1] < 0 || viewport[2] < 1 || viewport[3] < 1
                || (long) viewport[0] + viewport[2] > Integer.MAX_VALUE || (long) viewport[1] + viewport[3] > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid viewport");
        }
        var points = new ArrayList<int[]>();
        int nx = Math.min(side, viewport[2]), ny = Math.min(side, viewport[3]);
        for (int y = 0; y < ny; y++) for (int x = 0; x < nx; x++) {
            points.add(new int[]{viewport[0] + (int) (((2L * x + 1) * viewport[2]) / (2L * nx)),
                    viewport[1] + (int) (((2L * y + 1) * viewport[3]) / (2L * ny))});
        }
        return points;
    }

    /** Per-page validation only; schema validity for counts requires the separate page-2 observation. */
    static String pageStatus(int page, float[] values) {
        if (values.length != 4) return "UNKNOWN_CHANNEL_COUNT";
        boolean invalid = true;
        for (float value : values) invalid &= value == -1;
        if (invalid) return "INVALID_SENTINEL";
        for (int i = 0; i < 4; i++) {
            float value = values[i];
            if (!Float.isFinite(value) || value < 0 || value > 16777215) return "UNKNOWN_RANGE_OR_NONFINITE";
            if ((page != 1 || i == 0 || i == 3) && value != Math.rint(value)) return "UNKNOWN_FRACTIONAL_INTEGER";
        }
        if (page == 1 && values[3] > 3) return "UNKNOWN_PATH_TAG";
        if (page == 2 && (values[0] != 1 || values[2] != 1 || values[3] != 0)) return "UNKNOWN_SCHEMA_VALID_RESERVED";
        return "VALID_PAGE_ONLY";
    }

    static JsonObject decode(float[][] pages) {
        var result = new JsonObject();
        if (pages.length != 3) return failed(result, "MISSING_PAGES");
        boolean sentinel = true;
        for (int page = 0; page < 3; page++) {
            String status = pageStatus(page, pages[page]);
            sentinel &= status.equals("INVALID_SENTINEL");
            if (!status.equals("VALID_PAGE_ONLY") && !status.equals("INVALID_SENTINEL")) return failed(result, status);
        }
        if (sentinel) return failed(result, "INVALID_SENTINEL");
        for (int page = 0; page < 3; page++) if (!pageStatus(page, pages[page]).equals("VALID_PAGE_ONLY")) return failed(result, "PARTIAL_INVALID_SENTINEL");
        result.addProperty("status", "VALID_SCHEMA_1");
        String[] counts = {"primaryIterations", "densityEvaluations", "secondaryVisits", "textureEvaluations"};
        for (int i = 0; i < 4; i++) result.addProperty(counts[i], (int) pages[0][i]);
        result.addProperty("originalWork", (int) pages[1][0]);
        result.addProperty("continuousRequestedWork", pages[1][1]);
        result.addProperty("continuousSelectedWork", pages[1][2]);
        result.addProperty("selectedCeilTarget", (int) Math.ceil(pages[1][2]));
        result.addProperty("pathTag", (int) pages[1][3]); result.addProperty("reasonTag", (int) pages[2][1]);
        return result;
    }

    private static JsonObject failed(JsonObject result, String reason) {
        result.addProperty("status", "UNKNOWN"); result.addProperty("reason", reason); return result;
    }

    private static JsonObject unknown(String reason, long draw) {
        var result = failed(new JsonObject(), reason); result.addProperty("performanceEvidence", false);
        result.addProperty("drawOrdinal", draw); return result;
    }

    private static JsonObject header(JsonObject metadata, Path metadataPath) {
        var result = new JsonObject();
        result.addProperty("evidenceVersion", 1); result.addProperty("kind", "synchronous_raw_work_trace_diagnostic");
        result.addProperty("performanceEvidence", false); result.addProperty("hook", "FullScreenQuadRenderer.renderQuad()V TAIL");
        result.addProperty("metadataPath", metadataPath.toString());
        result.add("metadata", metadata.deepCopy());
        result.addProperty("captureWindowFinished", false);
        return result;
    }

    record Image(int object, int level, int internalFormat, int width, int height) {}
    interface Driver {
        int integer(int name); int[] viewport(); boolean blendEnabled(int outputLocation);
        boolean enabled(int name); boolean colorWritable(int outputLocation);
        void bindReadFramebuffer(int framebuffer); void readBuffer(int attachment); void bindPackBuffer(int buffer);
        void pack(int name, int value); boolean complete(); Image image(int attachment); float[] pixel(int x, int y);
    }

    private static final class NativeDriver implements Driver {
        public int integer(int name) { return GL11.glGetInteger(name); }
        public int[] viewport() { int[] values = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, values); return values; }
        public boolean blendEnabled(int location) { return GL30.glIsEnabledi(GL11.GL_BLEND, location); }
        public boolean enabled(int name) { return GL11.glIsEnabled(name); }
        public boolean colorWritable(int location) {
            try (var stack = MemoryStack.stackPush()) {
                var mask = stack.malloc(4); GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK, location, mask);
                return mask.get(0) != 0 && mask.get(1) != 0 && mask.get(2) != 0 && mask.get(3) != 0;
            }
        }
        public void bindReadFramebuffer(int framebuffer) { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer); }
        public void readBuffer(int attachment) { GL11.glReadBuffer(attachment); }
        public void bindPackBuffer(int buffer) { GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffer); }
        public void pack(int name, int value) { GL11.glPixelStorei(name, value); }
        public boolean complete() { return GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE; }
        public Image image(int attachment) {
            int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (type != GL11.GL_TEXTURE) throw new IllegalArgumentException("Require 2D texture attachment");
            int object = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            int level = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
            int binding = integer(GL11.GL_TEXTURE_BINDING_2D);
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, object);
                if (integer(GL11.GL_TEXTURE_BINDING_2D) != object) throw new IllegalArgumentException("Attachment is not bindable as a 2D texture");
                return new Image(object, level, GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_INTERNAL_FORMAT),
                        GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH),
                        GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT));
            } finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding); }
        }
        public float[] pixel(int x, int y) {
            try (var stack = MemoryStack.stackPush()) {
                var buffer = stack.mallocFloat(4);
                for (int i = 0; i < 4; i++) buffer.put(i, Float.NaN);
                GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, buffer);
                return new float[]{buffer.get(0), buffer.get(1), buffer.get(2), buffer.get(3)};
            }
        }
        boolean marker(int program, String name, int expected) {
            var uniform = uniform(program, name, GL20.GL_INT);
            return uniform.has("value") && uniform.get("value").getAsInt() == expected;
        }
        JsonObject uniform(int program, String name, int expectedType) {
            var result = new JsonObject(); result.addProperty("name", name);
            result.addProperty("status", "UNKNOWN_MISSING_OR_TYPE");
            int count = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
            if (count < 0 || count > 4096) return result;
            try (var stack = MemoryStack.stackPush()) {
                var size = stack.mallocInt(1); var type = stack.mallocInt(1);
                for (int index = 0; index < count; index++) {
                    String active = GL20.glGetActiveUniform(program, index, 1024, size, type);
                    if (!name.equals(active)) continue;
                    int location = GL20.glGetUniformLocation(program, name);
                    if (size.get(0) != 1 || type.get(0) != expectedType || location < 0) return result;
                    result.addProperty("location", location); result.addProperty("type", type.get(0));
                    if (expectedType == GL20.GL_INT) result.addProperty("value", GL20.glGetUniformi(program, location));
                    else {
                        float value = GL20.glGetUniformf(program, location);
                        if (!Float.isFinite(value)) return result;
                        result.addProperty("value", value);
                    }
                    result.addProperty("status", "QUERIED_WITHOUT_GL_ERROR_CONSUMPTION"); return result;
                }
            }
            return result;
        }
    }

    private static final class Session {
        final Metadata metadata; final Path output; final List<JsonObject> records = new ArrayList<>();
        JsonObject header; long draws, matchingDraws, skippedPrograms; boolean armed;
        Session(Metadata metadata, Path output) { this.metadata = metadata; this.output = output; }
        void append(JsonObject record) { records.add(record); try { write(header); } catch (IOException e) { throw new IllegalStateException("Work trace output failed", e); } }
        void write(JsonObject header) throws IOException {
            this.header = header;
            var document = header.deepCopy(); document.add("captures", new com.google.gson.Gson().toJsonTree(records));
            document.addProperty("drawsObserved", draws); document.addProperty("matchingDraws", matchingDraws);
            document.addProperty("drawsWithUnknownOrWrongProgramMarker", skippedPrograms);
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(document));
        }
    }
}
