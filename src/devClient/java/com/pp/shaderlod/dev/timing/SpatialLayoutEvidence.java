package com.pp.shaderlod.dev.timing;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;

/** Opt-in DEV synchronous evidence. No program discovery, registration, shader rewriting or timing. */
public final class SpatialLayoutEvidence {
    public static final String PROPERTY = "shaderlod.dev.spatialLayoutProbe";
    private static final com.google.gson.Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int[] PACK = {GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH,
            GL11.GL_PACK_SKIP_ROWS, GL11.GL_PACK_SKIP_PIXELS, GL11.GL_PACK_SWAP_BYTES,
            GL11.GL_PACK_LSB_FIRST, GL12.GL_PACK_IMAGE_HEIGHT, GL12.GL_PACK_SKIP_IMAGES};
    private SpatialLayoutEvidence() {}

    public enum Stage { PRODUCER, CONSUMER }
    public enum Role { SCENE, CACHE, OTHER }
    public record Tile(int x, int y) {}
    public record Output(String name, int location, int attachment, int logicalTarget, int internalFormat, Role role) {
        public Output { SpatialLayoutEvidence.name(name); range(location, 0, 7); range(attachment, 0, 7); range(logicalTarget, 0, 7); java.util.Objects.requireNonNull(role); }
    }
    /** Every sampler that can overlap a written logical target must be declared, even non-cache inputs. */
    public record Input(String name, int logicalTarget, int internalFormat, Role role) {
        public Input { SpatialLayoutEvidence.name(name); range(logicalTarget, 0, 15); java.util.Objects.requireNonNull(role); }
    }
    public record StageContract(String identity, String memberSha256, List<Output> outputs, List<Input> inputs) {
        public StageContract {
            name(identity); hash(memberSha256); outputs = List.copyOf(outputs); inputs = List.copyOf(inputs);
            range(outputs.size(), 1, 4); range(inputs.size(), 1, 16);
            unique(outputs.stream().map(Output::name).toList()); unique(outputs.stream().map(Output::location).toList());
            unique(outputs.stream().map(Output::attachment).toList()); unique(outputs.stream().map(Output::logicalTarget).toList()); unique(inputs.stream().map(Input::name).toList());
        }
    }
    /** Explicit binary32 lane contract, supplied by the reviewed adapter, never inferred from programs. */
    public record Codec(int tileSide, int certificateToken, int maximumEpoch,
                        float quantityInvalidAlpha, float maximumDistanceBlocks) {
        public Codec {
            range(tileSide, 2, 8); range(certificateToken, 1, 16777216); range(maximumEpoch, 0, 16777216);
            if (!Float.isFinite(quantityInvalidAlpha) || quantityInvalidAlpha >= 0
                    || !Float.isFinite(maximumDistanceBlocks) || maximumDistanceBlocks <= 0) bad("Invalid codec domain");
        }
    }
    public record Contract(String identity, String sourceSha256, String effectiveOptionsSha256,
                           StageContract producer, StageContract consumer, String epochUniform,
                           int width, int height, Codec codec, List<Tile> tiles, int maxRecords) {
        public Contract {
            name(identity); hash(sourceSha256); hash(effectiveOptionsSha256); name(epochUniform);
            java.util.Objects.requireNonNull(producer); java.util.Objects.requireNonNull(consumer); java.util.Objects.requireNonNull(codec);
            if (producer.identity().equals(consumer.identity())) bad("Distinct explicit stages required");
            range(width, 2, 8192); range(height, 2, 8192); range(maxRecords, 1, 16);
            tiles = List.copyOf(tiles); range(tiles.size(), 1, 32); unique(tiles);
            for (Tile tile : tiles) { range(tile.x(), 0, (width - 1) / codec.tileSide()); range(tile.y(), 0, (height - 1) / codec.tileSide()); }
            output(producer, Role.CACHE); output(producer, Role.SCENE); input(consumer, Role.CACHE); input(consumer, Role.SCENE);
            if (output(producer, Role.CACHE).internalFormat() != GL30.GL_RGBA32F
                    || input(consumer, Role.CACHE).internalFormat() != GL30.GL_RGBA32F
                    || output(producer, Role.CACHE).logicalTarget() != input(consumer, Role.CACHE).logicalTarget()
                    || output(producer, Role.SCENE).logicalTarget() != input(consumer, Role.SCENE).logicalTarget()) bad("Incompatible producer/consumer roles");
        }
    }
    /** Caller must obtain these exact values from verified runtime binding/options, and identify the actual draw. Untrusted claims are not source verification. */
    public record Draw(Stage stage, String stageIdentity, String memberSha256, String sourceSha256,
                       String effectiveOptionsSha256, boolean runtimeAvailable, int expectedProgram,
                       long pipelineGeneration, long renderFrame, long drawOrdinal, int epoch) {}
    public record Image(int object, int level, int internalFormat, int width, int height) {}
    public record Sampler(int location, int type, int size, int unit) {}
    public record InputImage(Input input, Sampler sampler, int samplerObject, Image image) {}
    public record OutputImage(Output output, Image image) {}
    private record Layout(int program, int framebuffer, List<Integer> viewport,
                          List<OutputImage> outputs, List<InputImage> inputs) {}
    private record Pending(Draw draw, Layout layout) {}
    private record Completed(Draw draw, Layout layout, JsonArray samples) {}

    /** Off returns without inspecting a contract/driver or doing file/native I/O. */
    public static Session open(Properties properties, Contract contract, Driver driver) {
        if (!Boolean.parseBoolean(properties.getProperty(PROPERTY, "false"))) return null;
        if (!Boolean.parseBoolean(properties.getProperty("shaderlod.dev.spatialLayoutOnly", "false"))) bad("Require spatialLayoutOnly=true");
        boolean comparing = !properties.getProperty("shaderlod.dev.compare", "").isEmpty();
        for (String key : List.of("gpuTiming", "cpuTiming", "cpuLayerTiming", "benchmark")) {
            String fallback = key.equals("cpuLayerTiming") || (comparing && (key.equals("gpuTiming") || key.equals("cpuTiming"))) ? "true" : "false";
            if (Boolean.parseBoolean(properties.getProperty("shaderlod.dev." + key, fallback))) bad("Spatial layout forbids timing/benchmarking: " + key);
        }
        return new Session(java.util.Objects.requireNonNull(contract), java.util.Objects.requireNonNull(driver));
    }

    public static final class Session {
        private final Contract contract;
        private final Driver driver;
        private final List<JsonObject> records = new ArrayList<>();
        private Pending pending;
        private Completed completed;
        private boolean armed;
        private Session(Contract contract, Driver driver) { this.contract = contract; this.driver = driver; }
        public void beginCapture() { pending = null; completed = null; armed = true; }
        public void endCapture() { armed = false; pending = null; completed = null; }

        /** Hook must explicitly identify the reviewed draw; arbitrary quad programs are never classified. */
        public void beforeDraw(Draw draw) {
            if (!armed || records.size() >= contract.maxRecords()) return;
            var row = row(draw, "BEFORE_DRAW");
            try {
                verify(draw);
                if (pending != null) bad("UNPAIRED_PREVIOUS_DRAW");
                Layout layout = inspect(draw, row);
                row.add("layout", JSON.toJsonTree(layout));
                if (draw.stage() == Stage.PRODUCER) completed = null;
                if (draw.stage() == Stage.CONSUMER) {
                    if (completed == null) bad("UNKNOWN_NO_COMPLETED_PRODUCER");
                    Draw prior = completed.draw();
                    if (draw.pipelineGeneration() != prior.pipelineGeneration() || draw.renderFrame() != prior.renderFrame()
                            || draw.epoch() != prior.epoch() || draw.drawOrdinal() <= prior.drawOrdinal()) bad("PRODUCER_CONSUMER_PROVENANCE_MISMATCH");
                    for (Role role : List.of(Role.SCENE, Role.CACHE)) {
                        Image read = layout.inputs().stream().filter(i -> i.input().role() == role).findFirst().orElseThrow().image();
                        Image written = completed.layout().outputs().stream().filter(i -> i.output().role() == role).findFirst().orElseThrow().image();
                        if (!read.equals(written)) bad("PRODUCER_CONSUMER_IMAGE_MISMATCH_" + role);
                    }
                    row.addProperty("producerDrawOrdinal", prior.drawOrdinal());
                    row.add("producerSamples", completed.samples().deepCopy());
                    // Use the already existing producer read attachment, never attach to a draw FBO.
                    readCache(completed.layout(), draw, row, "consumerCacheSamples");
                }
                pending = new Pending(draw, layout);
                row.addProperty("status", "OBSERVED_EXPLICIT_STAGE_LAYOUT");
            } catch (RuntimeException exception) {
                pending = null; completed = null; unknown(row, exception);
            }
            records.add(row);
        }

        /** Call only immediately after the actual draw returns; this is an observation, not a bytecode inference. */
        public void afterDraw(Draw draw) {
            if (!armed || records.size() >= contract.maxRecords()) { pending = null; completed = null; return; }
            var row = row(draw, "AFTER_DRAW");
            try {
                verify(draw);
                if (pending == null || !pending.draw().equals(draw)) bad("UNKNOWN_UNPAIRED_DRAW");
                Layout layout = inspect(draw, row);
                row.add("layout", JSON.toJsonTree(layout));
                if (!layout.equals(pending.layout())) bad("DRAW_STATE_CHANGED_BETWEEN_HOOKS");
                if (draw.stage() == Stage.PRODUCER) {
                    JsonArray samples = readCache(layout, draw, row, "producerSamples");
                    completed = new Completed(draw, layout, samples.deepCopy());
                }
                row.addProperty("status", "OBSERVED_COMPLETED_EXPLICIT_DRAW");
            } catch (RuntimeException exception) { completed = null; unknown(row, exception); }
            finally { pending = null; }
            records.add(row);
        }

        public JsonObject report() {
            var document = new JsonObject(); document.addProperty("evidenceVersion", 1);
            document.addProperty("kind", "synchronous_raw_spatial_layout_diagnostic");
            document.addProperty("performanceEvidence", false); document.addProperty("diagnosticOnly", true);
            document.addProperty("captureArmed", armed); document.addProperty("glErrors", "unobserved; error flags are not consumed");
            document.addProperty("terminalStatus", records.isEmpty() ? "UNKNOWN_NO_EXPLICIT_DRAW" : "OBSERVATIONS_ONLY");
            document.add("contract", JSON.toJsonTree(contract)); document.add("records", JSON.toJsonTree(records));
            return document;
        }
        public void write(Path path) throws IOException { Files.writeString(path, JSON.toJson(report())); }

        private void verify(Draw draw) {
            if (draw == null || draw.stage() == null) bad("UNKNOWN_STAGE");
            StageContract stage = draw.stage() == Stage.PRODUCER ? contract.producer() : contract.consumer();
            if (!draw.runtimeAvailable() || !contract.sourceSha256().equals(draw.sourceSha256())
                    || !contract.effectiveOptionsSha256().equals(draw.effectiveOptionsSha256())
                    || !stage.identity().equals(draw.stageIdentity()) || !stage.memberSha256().equals(draw.memberSha256())
                    || draw.expectedProgram() <= 0 || draw.pipelineGeneration() < 0 || draw.renderFrame() < 0
                    || draw.drawOrdinal() < 0 || draw.epoch() < 0 || draw.epoch() > contract.codec().maximumEpoch()) bad("UNKNOWN_OR_MISMATCHED_SOURCE_STAGE_CONTRACT");
            if (driver.integer(GL20.GL_CURRENT_PROGRAM) != draw.expectedProgram()
                    || !Integer.valueOf(draw.epoch()).equals(driver.scalarInt(draw.expectedProgram(), contract.epochUniform()))) bad("PROGRAM_OR_TYPED_EPOCH_MISMATCH");
        }

        private Layout inspect(Draw draw, JsonObject row) {
            int fbo = driver.integer(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int[] viewport = driver.viewport();
            var observed = new JsonObject(); observed.addProperty("program", draw.expectedProgram()); observed.addProperty("framebuffer", fbo);
            observed.add("viewport", JSON.toJsonTree(viewport));
            var rawOutputs = new JsonArray(); var rawInputs = new JsonArray();
            observed.add("outputs", rawOutputs); observed.add("inputs", rawInputs); row.add("layoutObservation", observed);
            if (fbo <= 0 || !java.util.Arrays.equals(viewport, new int[]{0, 0, contract.width(), contract.height()})) bad("FRAMEBUFFER_OR_FULL_VIEWPORT_MISMATCH");
            for (int flag : new int[]{GL11.GL_SCISSOR_TEST, GL30.GL_RASTERIZER_DISCARD, GL11.GL_DEPTH_TEST, GL11.GL_STENCIL_TEST})
                if (driver.enabled(flag)) bad("PARTIAL_OR_DISCARDED_DRAW_UNSUPPORTED");
            StageContract stage = draw.stage() == Stage.PRODUCER ? contract.producer() : contract.consumer();
            var outputs = new ArrayList<OutputImage>(); var inputs = new ArrayList<InputImage>();
            withState(state -> {
                state.bindReadFramebuffer(fbo);
                if (!driver.complete()) bad("FRAMEBUFFER_INCOMPLETE");
                for (Output output : stage.outputs()) {
                    if (driver.fragmentLocation(draw.expectedProgram(), output.name()) != output.location()
                            || driver.integer(GL20.GL_DRAW_BUFFER0 + output.location()) != GL30.GL_COLOR_ATTACHMENT0 + output.attachment()
                            || driver.blendEnabled(output.location()) || !driver.colorWritable(output.location())) bad("OUTPUT_ROLE_MAPPING_OR_WRITE_STATE_MISMATCH");
                    Image image = driver.attachment(GL30.GL_COLOR_ATTACHMENT0 + output.attachment());
                    var entry = new OutputImage(output, image); rawOutputs.add(JSON.toJsonTree(entry));
                    checkImage(image, output.internalFormat()); outputs.add(entry);
                }
                int units = driver.integer(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
                if (units < 1 || units > 4096) bad("UNKNOWN_TEXTURE_UNIT_LIMIT");
                for (Input input : stage.inputs()) {
                    Sampler sampler = driver.sampler(draw.expectedProgram(), input.name());
                    if (sampler == null || sampler.type() != GL20.GL_SAMPLER_2D || sampler.size() != 1 || sampler.location() < 0
                            || sampler.unit() < 0 || sampler.unit() >= units) bad("UNKNOWN_OR_UNSUPPORTED_SAMPLER_ROLE");
                    state.activeTexture(GL13.GL_TEXTURE0 + sampler.unit());
                    int texture = driver.integer(GL11.GL_TEXTURE_BINDING_2D);
                    Image image = driver.texture(texture, 0);
                    var entry = new InputImage(input, sampler, driver.samplerObject(sampler.unit()), image); rawInputs.add(JSON.toJsonTree(entry));
                    checkImage(image, input.internalFormat()); inputs.add(entry);
                }
                for (OutputImage output : outputs) for (InputImage input : inputs)
                    if (output.image().object() == input.image().object()) bad("READ_WRITE_IMAGE_FEEDBACK");
                unique(outputs.stream().map(i -> i.image().object()).toList());
            });
            return new Layout(draw.expectedProgram(), fbo, List.of(viewport[0], viewport[1], viewport[2], viewport[3]), List.copyOf(outputs), List.copyOf(inputs));
        }
        private void checkImage(Image image, int format) {
            if (image == null || image.object() <= 0 || image.level() != 0 || image.internalFormat() != format
                    || image.width() != contract.width() || image.height() != contract.height()) bad("TEXTURE_FORMAT_LEVEL_OR_EXTENT_MISMATCH");
        }

        private JsonArray readCache(Layout producer, Draw observation, JsonObject row, String field) {
            var samples = new JsonArray(); row.add(field, samples);
            OutputImage cache = producer.outputs().stream().filter(i -> i.output().role() == Role.CACHE).findFirst().orElseThrow();
            withState(state -> {
                state.bindReadFramebuffer(producer.framebuffer());
                if (!driver.complete() || !cache.image().equals(driver.attachment(GL30.GL_COLOR_ATTACHMENT0 + cache.output().attachment()))) bad("UNKNOWN_PRODUCER_ATTACHMENT_REUSED");
                driver.readBuffer(GL30.GL_COLOR_ATTACHMENT0 + cache.output().attachment());
                driver.bindPackBuffer(0);
                for (int i = 0; i < PACK.length; i++) driver.pack(PACK[i], i == 0 ? 1 : 0);
                for (Tile tile : contract.tiles()) {
                    var sample = new JsonObject(); sample.add("tile", JSON.toJsonTree(tile));
                    sample.addProperty("observedStage", observation.stage().name()); sample.addProperty("observedDrawOrdinal", observation.drawOrdinal());
                    sample.addProperty("epoch", observation.epoch()); sample.addProperty("texture", cache.image().object());
                    int x = tile.x() * contract.codec().tileSide(), y = tile.y() * contract.codec().tileSide();
                    sample.add("quantityCoordinate", JSON.toJsonTree(new int[]{x, y}));
                    sample.add("certificateCoordinate", JSON.toJsonTree(new int[]{x + 1, y}));
                    if (x + contract.codec().tileSide() > contract.width() || y + contract.codec().tileSide() > contract.height()) {
                        sample.addProperty("status", "UNKNOWN_INCOMPLETE_TILE_NO_READ"); samples.add(sample); continue;
                    }
                    sample.addProperty("status", "UNKNOWN_READ_NOT_COMPLETED"); samples.add(sample);
                    float[] quantity = driver.pixel(x, y); sample.add("rawQuantity", raw(quantity));
                    float[] certificate = driver.pixel(x + 1, y); sample.add("rawCertificate", raw(certificate));
                    sample.addProperty("status", decode(contract, tile, observation.epoch(), quantity, certificate));
                }
            });
            return samples;
        }

        /** Saves FBO-local selectors separately. Every restoration runs even after another fails. */
        private void withState(java.util.function.Consumer<State> operation) {
            int readFbo = driver.integer(GL30.GL_READ_FRAMEBUFFER_BINDING), readBuffer = driver.integer(GL11.GL_READ_BUFFER);
            int pbo = driver.integer(GL21.GL_PIXEL_PACK_BUFFER_BINDING), active = driver.integer(GL13.GL_ACTIVE_TEXTURE);
            int binding = driver.integer(GL11.GL_TEXTURE_BINDING_2D);
            int[] pack = new int[PACK.length]; for (int i = 0; i < PACK.length; i++) pack[i] = driver.integer(PACK[i]);
            var state = new State(); state.selectors.put(readFbo, readBuffer); state.bindings.put(active, binding);
            RuntimeException failure = null;
            try {
                operation.accept(state);
            } catch (RuntimeException exception) { failure = exception; }
            finally {
                var restores = new ArrayList<Runnable>();
                state.selectors.forEach((fbo, selector) -> {
                    restores.add(() -> driver.bindReadFramebuffer(fbo)); restores.add(() -> driver.readBuffer(selector));
                });
                restores.add(() -> driver.bindReadFramebuffer(readFbo)); restores.add(() -> driver.readBuffer(readBuffer));
                restores.add(() -> driver.bindPackBuffer(pbo));
                for (int i = 0; i < PACK.length; i++) { int index = i; restores.add(() -> driver.pack(PACK[index], pack[index])); }
                state.bindings.forEach((unit, texture) -> {
                    restores.add(() -> driver.activeTexture(unit)); restores.add(() -> driver.bindTexture2D(texture));
                });
                restores.add(() -> driver.activeTexture(active));
                for (Runnable restore : restores) try { restore.run(); } catch (RuntimeException exception) {
                    if (failure == null) failure = new IllegalStateException("STATE_RESTORATION_FAILED"); failure.addSuppressed(exception);
                }
            }
            if (failure != null) throw failure;
        }
        private final class State {
            final Map<Integer, Integer> selectors = new LinkedHashMap<>(), bindings = new LinkedHashMap<>();
            void bindReadFramebuffer(int fbo) {
                driver.bindReadFramebuffer(fbo); selectors.putIfAbsent(fbo, driver.integer(GL11.GL_READ_BUFFER));
            }
            void activeTexture(int unit) {
                driver.activeTexture(unit); bindings.putIfAbsent(unit, driver.integer(GL11.GL_TEXTURE_BINDING_2D));
            }
        }
    }

    static String decode(Contract contract, Tile tile, int epoch, float[] q, float[] c) {
        if (q == null || c == null || q.length != 4 || c.length != 4) return "UNKNOWN_CHANNEL_COUNT";
        for (float value : q) if (!Float.isFinite(value)) return "UNKNOWN_NONFINITE_QUANTITY";
        for (float value : c) if (!Float.isFinite(value)) return "UNKNOWN_NONFINITE_CERTIFICATE";
        if (q[3] == contract.codec().quantityInvalidAlpha()) return "INVALID_QUANTITY_SENTINEL";
        if (q[3] < 0 || q[3] > 1) return "UNKNOWN_QUANTITY_ALPHA_DOMAIN";
        int columns = (contract.width() + contract.codec().tileSide() - 1) / contract.codec().tileSide();
        long tileIndex = tile.x() + (long) columns * tile.y();
        if (tileIndex > 16777215 || c[0] != epoch || c[1] != contract.codec().certificateToken()
                || c[2] != tileIndex || c[3] <= 0 || c[3] > contract.codec().maximumDistanceBlocks()) return "INVALID_OR_MISMATCHED_CERTIFICATE";
        return "VALID_DECLARED_RAW_LANES_ONLY";
    }
    private static JsonArray raw(float[] values) {
        var array = new JsonArray(); if (values == null) return array;
        for (float value : values) { if (Float.isFinite(value)) array.add(value); else array.add(Float.toString(value)); }
        return array;
    }
    private static JsonObject row(Draw draw, String phase) {
        var row = new JsonObject(); row.addProperty("performanceEvidence", false); row.addProperty("phase", phase);
        row.add("draw", JSON.toJsonTree(draw)); row.addProperty("utc", Instant.now().toString()); row.addProperty("monotonicNanos", System.nanoTime());
        row.addProperty("status", "UNKNOWN"); return row;
    }
    private static void unknown(JsonObject row, RuntimeException exception) {
        row.addProperty("status", "UNKNOWN"); row.addProperty("reason", exception.toString());
        var errors = new JsonArray(); for (Throwable error : exception.getSuppressed()) errors.add(error.toString());
        if (!errors.isEmpty()) row.add("restoreErrors", errors);
    }
    private static Output output(StageContract stage, Role role) {
        var found = stage.outputs().stream().filter(o -> o.role() == role).toList();
        if (found.size() != 1) bad("Require one output role " + role); return found.getFirst();
    }
    private static Input input(StageContract stage, Role role) {
        var found = stage.inputs().stream().filter(i -> i.role() == role).toList();
        if (found.size() != 1) bad("Require one input role " + role); return found.getFirst();
    }
    private static void unique(List<?> values) { if (new HashSet<>(values).size() != values.size()) bad("Duplicate contract roles/locations/images"); }
    private static void hash(String value) { if (value == null || !value.matches("[0-9a-f]{64}")) bad("Require exact SHA-256"); }
    private static void name(String value) { if (value == null || value.isBlank() || value.length() > 1024) bad("Invalid contract name"); }
    private static void range(int value, int minimum, int maximum) { if (value < minimum || value > maximum) bad("Outside bound " + minimum + ".." + maximum); }
    private static void bad(String reason) { throw new IllegalArgumentException(reason); }

    public interface Driver {
        int integer(int name); int[] viewport(); boolean enabled(int name); boolean blendEnabled(int output); boolean colorWritable(int output);
        int fragmentLocation(int program, String name); Integer scalarInt(int program, String name); Sampler sampler(int program, String name);
        int samplerObject(int unit); void activeTexture(int unit); void bindTexture2D(int object);
        void bindReadFramebuffer(int fbo); void readBuffer(int selector); void bindPackBuffer(int object); void pack(int name, int value);
        boolean complete(); Image attachment(int selector); Image texture(int object, int level); float[] pixel(int x, int y);
    }

    /** Actual driver remains unverified until the exclusive runtime owner integrates and runs the hooks. */
    public static final class NativeDriver implements Driver {
        public int integer(int name) { return GL11.glGetInteger(name); }
        public int[] viewport() { int[] v = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, v); return v; }
        public boolean enabled(int name) { return GL11.glIsEnabled(name); }
        public boolean blendEnabled(int output) { return GL30.glIsEnabledi(GL11.GL_BLEND, output); }
        public boolean colorWritable(int output) {
            try (var stack = MemoryStack.stackPush()) {
                var b = stack.malloc(4); GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK, output, b);
                return b.get(0) != 0 && b.get(1) != 0 && b.get(2) != 0 && b.get(3) != 0;
            }
        }
        public int fragmentLocation(int program, String name) { return GL30.glGetFragDataLocation(program, name); }
        public Integer scalarInt(int program, String name) { Sampler u = uniform(program, name); return u != null && u.type() == GL20.GL_INT && u.size() == 1 ? u.unit() : null; }
        public Sampler sampler(int program, String name) { return uniform(program, name); }
        private Sampler uniform(int program, String name) {
            int count = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS); if (count < 0 || count > 4096) return null;
            try (var stack = MemoryStack.stackPush()) {
                var size = stack.mallocInt(1); var type = stack.mallocInt(1);
                for (int index = 0; index < count; index++) {
                    if (!name.equals(GL20.glGetActiveUniform(program, index, 1024, size, type))) continue;
                    int location = GL20.glGetUniformLocation(program, name);
                    if (location < 0) return null;
                    if (type.get(0) != GL20.GL_INT && type.get(0) != GL20.GL_SAMPLER_2D) return null;
                    return new Sampler(location, type.get(0), size.get(0), GL20.glGetUniformi(program, location));
                }
            }
            return null;
        }
        public int samplerObject(int unit) {
            int saved = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            try {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                return GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            } finally { GL13.glActiveTexture(saved); }
        }
        public void activeTexture(int unit) { GL13.glActiveTexture(unit); }
        public void bindTexture2D(int object) { GL11.glBindTexture(GL11.GL_TEXTURE_2D, object); }
        public void bindReadFramebuffer(int fbo) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
        }
        public void readBuffer(int selector) { GL11.glReadBuffer(selector); }
        public void bindPackBuffer(int object) { GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, object); }
        public void pack(int name, int value) { GL11.glPixelStorei(name, value); }
        public boolean complete() { return GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE; }
        public Image attachment(int selector) {
            if (GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, selector, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL11.GL_TEXTURE) bad("Require 2D texture attachment");
            if (GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, selector, GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE) != 0
                    || GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, selector, GL32.GL_FRAMEBUFFER_ATTACHMENT_LAYERED) != 0) bad("Layered/cube attachments unsupported");
            int object = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, selector, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            int level = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, selector, GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
            return texture(object, level);
        }
        public Image texture(int object, int level) {
            if (object <= 0 || !GL11.glIsTexture(object)) bad("Unknown texture object");
            int binding = integer(GL11.GL_TEXTURE_BINDING_2D);
            try {
                bindTexture2D(object); if (integer(GL11.GL_TEXTURE_BINDING_2D) != object) bad("Texture not bindable as 2D");
                return new Image(object, level, GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_INTERNAL_FORMAT),
                        GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH), GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT));
            } finally { bindTexture2D(binding); }
        }
        public float[] pixel(int x, int y) {
            try (var stack = MemoryStack.stackPush()) {
                var b = stack.mallocFloat(4); for (int i = 0; i < 4; i++) b.put(i, Float.NaN);
                GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, b);
                return new float[]{b.get(0), b.get(1), b.get(2), b.get(3)};
            }
        }
    }
}
