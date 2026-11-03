package com.pp.shaderlod.dev.timing;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import static com.pp.shaderlod.dev.timing.SpatialLayoutEvidence.*;

/** Executable isolated fixtures. No Minecraft, native context, Gradle or frozen class writes. */
public final class SpatialLayoutEvidenceTest {
    private static final String SOURCE = "a".repeat(64), OPTIONS = "b".repeat(64), PRODUCER = "c".repeat(64), CONSUMER = "d".repeat(64);
    private static final int[] PACK = {GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH, GL11.GL_PACK_SKIP_ROWS,
            GL11.GL_PACK_SKIP_PIXELS, GL11.GL_PACK_SWAP_BYTES, GL11.GL_PACK_LSB_FIRST, GL12.GL_PACK_IMAGE_HEIGHT, GL12.GL_PACK_SKIP_IMAGES};
    private static int assertions;

    private static Contract contract(int width, int height, List<Tile> tiles, int limit) {
        return new Contract("fixture:spatial-v1", SOURCE, OPTIONS,
                new StageContract("world0/deferred4", PRODUCER,
                        List.of(new Output("shaderlodSkyColor", 0, 0, 0, GL30.GL_R11F_G11F_B10F, Role.SCENE),
                                new Output("shaderlodSpatialOutput", 1, 1, 2, GL30.GL_RGBA32F, Role.CACHE)),
                        List.of(new Input("colortex0", 0, GL30.GL_R11F_G11F_B10F, Role.SCENE),
                                new Input("colortex2", 2, GL30.GL_RGBA32F, Role.OTHER))),
                new StageContract("world0/deferred5", CONSUMER,
                        List.of(new Output("fixtureScene", 0, 0, 0, GL30.GL_R11F_G11F_B10F, Role.SCENE),
                                new Output("fixtureOverlay", 1, 1, 3, GL11.GL_RGBA8, Role.OTHER)),
                        List.of(new Input("colortex0", 0, GL30.GL_R11F_G11F_B10F, Role.SCENE),
                                new Input("colortex2", 2, GL30.GL_RGBA32F, Role.CACHE),
                                new Input("colortex3", 3, GL11.GL_RGBA8, Role.OTHER))),
                "frameCounter", width, height, new Codec(2, 258, 16777216, -1, 1000000), tiles, limit);
    }
    private static Contract contract() { return contract(8, 6, List.of(new Tile(0, 0), new Tile(2, 1)), 16); }
    private static Properties properties() {
        var p = new Properties(); p.setProperty(PROPERTY, "true"); p.setProperty("shaderlod.dev.spatialLayoutOnly", "true");
        p.setProperty("shaderlod.dev.cpuLayerTiming", "false"); return p;
    }
    private static Draw draw(Stage stage, long frame, long ordinal) {
        return new Draw(stage, stage == Stage.PRODUCER ? "world0/deferred4" : "world0/deferred5",
                stage == Stage.PRODUCER ? PRODUCER : CONSUMER, SOURCE, OPTIONS, true,
                stage == Stage.PRODUCER ? 21 : 22, 3, frame, ordinal, 37);
    }
    private static JsonObject record(Session s, int index) { return s.report().getAsJsonArray("records").get(index).getAsJsonObject(); }
    private static String status(Session s, int index) { return record(s, index).get("status").getAsString(); }
    private static void check(boolean ok, String message) { assertions++; if (!ok) throw new AssertionError(message); }
    private static void rejected(Runnable action) { try { action.run(); throw new AssertionError("Expected rejection"); } catch (IllegalArgumentException expected) { assertions++; } }

    private static final class Mock implements Driver {
        final Map<Integer, Integer> values = new HashMap<>(), selectors = new HashMap<>(), textures = new HashMap<>();
        final Map<Integer, Image> images = new HashMap<>();
        final Map<Integer, Map<Integer, Integer>> attachments = new HashMap<>();
        final Map<String, Integer> locations = new HashMap<>();
        int width, height, readFbo = 12, active = GL13.GL_TEXTURE0 + 5, reads, calls, textureQueries;
        int epoch = 37, failPixelAt = -1, restoredPacks, failLocalRestoreUnit = -1;
        boolean localRestoreFailed, insideLocalRestore;
        boolean scissor, blend, mask, complete = true, failTexture, failRestore, failAttachment, samplerWrongType;
        float[] q = {3.5f, 4.25f, 5.125f, .75f};
        Mock(int width, int height) {
            this.width = width; this.height = height;
            selectors.put(12, GL30.GL_COLOR_ATTACHMENT0 + 4); selectors.put(23, GL30.GL_COLOR_ATTACHMENT0 + 6); selectors.put(24, GL30.GL_COLOR_ATTACHMENT0 + 7);
            values.put(GL21.GL_PIXEL_PACK_BUFFER_BINDING, 47); values.put(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS, 16);
            for (int i = 0; i < PACK.length; i++) values.put(PACK[i], 8 + i);
            for (int t : new int[]{100, 101, 200, 201}) images.put(t, new Image(t, 0, GL30.GL_R11F_G11F_B10F, width, height));
            for (int t : new int[]{102, 202}) images.put(t, new Image(t, 0, GL30.GL_RGBA32F, width, height));
            for (int t : new int[]{103, 203}) images.put(t, new Image(t, 0, GL11.GL_RGBA8, width, height));
            attachments.put(23, new HashMap<>(Map.of(GL30.GL_COLOR_ATTACHMENT0, 200, GL30.GL_COLOR_ATTACHMENT0 + 1, 202)));
            attachments.put(24, new HashMap<>(Map.of(GL30.GL_COLOR_ATTACHMENT0, 100, GL30.GL_COLOR_ATTACHMENT0 + 1, 203)));
            locations.put("shaderlodSkyColor", 0); locations.put("shaderlodSpatialOutput", 1); locations.put("fixtureScene", 0); locations.put("fixtureOverlay", 1);
            textures.put(GL13.GL_TEXTURE0 + 5, 101); producer();
        }
        Mock() { this(8, 6); }
        void producer() {
            values.put(GL20.GL_CURRENT_PROGRAM, 21); values.put(GL30.GL_DRAW_FRAMEBUFFER_BINDING, 23);
            values.put(GL20.GL_DRAW_BUFFER0, GL30.GL_COLOR_ATTACHMENT0); values.put(GL20.GL_DRAW_BUFFER0 + 1, GL30.GL_COLOR_ATTACHMENT0 + 1);
            textures.put(GL13.GL_TEXTURE0 + 1, 100); textures.put(GL13.GL_TEXTURE0 + 2, 102); textures.put(GL13.GL_TEXTURE0 + 3, 103);
        }
        void consumer() {
            values.put(GL20.GL_CURRENT_PROGRAM, 22); values.put(GL30.GL_DRAW_FRAMEBUFFER_BINDING, 24);
            textures.put(GL13.GL_TEXTURE0 + 1, 200); textures.put(GL13.GL_TEXTURE0 + 2, 202);
        }
        String state() { return values.toString() + selectors + textures + readFbo + ":" + active; }
        public int integer(int name) {
            calls++;
            if (name == GL30.GL_READ_FRAMEBUFFER_BINDING) return readFbo;
            if (name == GL11.GL_READ_BUFFER) return selectors.get(readFbo);
            if (name == GL13.GL_ACTIVE_TEXTURE) return active;
            if (name == GL11.GL_TEXTURE_BINDING_2D) return textures.getOrDefault(active, 0);
            return values.getOrDefault(name, 0);
        }
        public int[] viewport() { return new int[]{0, 0, width, height}; }
        public boolean enabled(int name) { return scissor && name == GL11.GL_SCISSOR_TEST; }
        public boolean blendEnabled(int output) { return blend; }
        public boolean colorWritable(int output) { return !mask; }
        public int fragmentLocation(int program, String name) { return locations.getOrDefault(name, -1); }
        public Integer scalarInt(int program, String name) { return epoch; }
        public Sampler sampler(int program, String name) {
            int unit = switch (name) { case "colortex0" -> 1; case "colortex2" -> 2; case "colortex3" -> 3; default -> -1; };
            return unit < 0 ? null : new Sampler(unit + 30, samplerWrongType ? GL20.GL_SAMPLER_3D : GL20.GL_SAMPLER_2D, 1, unit);
        }
        public int samplerObject(int unit) { return unit + 60; }
        public void activeTexture(int unit) { active = unit; }
        public void bindTexture2D(int object) {
            if (active == failLocalRestoreUnit && insideLocalRestore && !localRestoreFailed && object == 102) {
                localRestoreFailed = true; throw new IllegalStateException("local unit restoration failed once");
            }
            textures.put(active, object);
        }
        public void bindReadFramebuffer(int fbo) { readFbo = fbo;  }
        public void readBuffer(int selector) {
            if (failRestore && readFbo == 23 && selector == GL30.GL_COLOR_ATTACHMENT0 + 6) throw new IllegalStateException("target selector restoration failed");
            selectors.put(readFbo, selector);
        }
        public void bindPackBuffer(int object) { values.put(GL21.GL_PIXEL_PACK_BUFFER_BINDING, object); }
        public void pack(int name, int value) { values.put(name, value); if (value >= 8) restoredPacks++; }
        public boolean complete() { return complete; }
        public Image attachment(int selector) { if (failAttachment) throw new IllegalStateException("attachment failed"); return texture(attachments.get(readFbo).get(selector), 0); }
        public Image texture(int object, int level) {
            textureQueries++; int previous = integer(GL11.GL_TEXTURE_BINDING_2D);
            try { bindTexture2D(object); if (failTexture) throw new IllegalStateException("texture query failed"); return images.get(object); }
            finally {
                insideLocalRestore = true;
                try { bindTexture2D(previous); } finally { insideLocalRestore = false; }
            }
        }
        public float[] pixel(int x, int y) {
            reads++;
            check(readFbo == 23 && selectors.get(readFbo) == GL30.GL_COLOR_ATTACHMENT0 + 1, "Read cache logical2 at physical attachment1");
            check(values.get(GL21.GL_PIXEL_PACK_BUFFER_BINDING) == 0, "Detach pack PBO");
            for (int i = 0; i < PACK.length; i++) check(values.get(PACK[i]) == (i == 0 ? 1 : 0), "Tight pack state");
            check(x >= 0 && y >= 0 && x < width && y < height, "Bounded 1x1 coordinates");
            if (reads == failPixelAt) throw new IllegalStateException("read failed");
            if (x % 2 == 0) return q.clone();
            return new float[]{37, 258, (x - 1) / 2 + ((width + 1) / 2) * (y / 2), 90};
        }
    }

    public static void main(String[] args) {
        gating(); pairedStages(); contractsAndLayout(); provenance(); failuresAndRestoration(); rawCodec(); boundsAndLifecycle();
        System.out.println("PASS SpatialLayoutEvidenceTest: 7 no-GL fixture groups, " + assertions + " assertions");
    }
    private static void gating() {
        check(open(new Properties(), null, null) == null, "Off touches no driver/contract");
        var p = properties(); p.remove("shaderlod.dev.cpuLayerTiming"); rejected(() -> open(p, contract(), new Mock()));
        for (String key : List.of("gpuTiming", "cpuTiming", "cpuLayerTiming", "benchmark")) {
            var current = properties(); current.setProperty("shaderlod.dev." + key, "true"); rejected(() -> open(current, contract(), new Mock()));
        }
        var only = properties(); only.remove("shaderlod.dev.spatialLayoutOnly"); rejected(() -> open(only, contract(), new Mock()));
        var compare = properties(); compare.setProperty("shaderlod.dev.compare", "on"); rejected(() -> open(compare, contract(), new Mock()));
    }
    private static void pairedStages() {
        var d = new Mock(); String state = d.state(); var s = open(properties(), contract(), d); s.beginCapture();
        Draw p = draw(Stage.PRODUCER, 10, 1), c = draw(Stage.CONSUMER, 10, 2);
        s.beforeDraw(p); check(d.reads == 0 && state.equals(d.state()), "Before producer observes no pixels and restores state");
        s.afterDraw(p); check(status(s, 1).equals("OBSERVED_COMPLETED_EXPLICIT_DRAW") && d.reads == 4, "Completed producer bounded reads");
        check(state.equals(d.state()), "Independent read selectors, PBO, pack, active/unit bindings restored");
        d.consumer(); state = d.state(); s.beforeDraw(c); s.afterDraw(c);
        var row = record(s, 2); check(status(s, 2).equals("OBSERVED_EXPLICIT_STAGE_LAYOUT") && d.reads == 8, "Consumer cache read after producer with exact identities");
        check(row.getAsJsonArray("producerSamples").size() == 2 && row.getAsJsonArray("consumerCacheSamples").size() == 2, "Both raw stages retained");
        check(row.getAsJsonArray("consumerCacheSamples").get(0).getAsJsonObject().get("status").getAsString().equals("VALID_DECLARED_RAW_LANES_ONLY"), "Declared raw lanes decoded");
        check(state.equals(d.state()), "Consumer draw state untouched"); check(!s.report().get("performanceEvidence").getAsBoolean(), "Always diagnostic");
    }
    private static void contractsAndLayout() {
        for (int failure = 0; failure < 11; failure++) {
            var d = new Mock();
            switch (failure) {
                case 0 -> d.locations.put("shaderlodSpatialOutput", -1);
                case 1 -> d.values.put(GL20.GL_DRAW_BUFFER0 + 1, GL30.GL_COLOR_ATTACHMENT0 + 2);
                case 2 -> d.textures.put(GL13.GL_TEXTURE0 + 2, 202);
                case 3 -> d.images.put(202, new Image(202, 0, GL11.GL_RGBA8, 8, 6));
                case 4 -> d.images.put(202, new Image(202, 0, GL30.GL_RGBA32F, 7, 6));
                case 5 -> d.width = 9;
                case 6 -> d.scissor = true;
                case 7 -> d.blend = true;
                case 8 -> d.mask = true;
                case 9 -> d.complete = false;
                case 10 -> d.samplerWrongType = true;
            }
            String initial = d.state(); var s = open(properties(), contract(), d); s.beginCapture(); s.beforeDraw(draw(Stage.PRODUCER, 10, 1));
            check(status(s, 0).equals("UNKNOWN") && d.reads == 0, "Layout rejects failure " + failure);
            check(initial.equals(d.state()), "Rejected layout restores " + failure);
        }
        rejected(() -> contract(1, 6, List.of(new Tile(0, 0)), 16));
        rejected(() -> contract(8, 6, List.of(new Tile(0, 0), new Tile(0, 0)), 16));
        rejected(() -> contract(8, 6, List.of(new Tile(4, 0)), 16));
    }
    private static void provenance() {
        Draw valid = draw(Stage.PRODUCER, 10, 1);
        for (int failure = 0; failure < 8; failure++) {
            var d = new Mock(); var s = open(properties(), contract(), d); s.beginCapture();
            Draw wrong = new Draw(Stage.PRODUCER, failure == 0 ? "unknown" : valid.stageIdentity(), failure == 1 ? SOURCE : PRODUCER,
                    failure == 2 ? OPTIONS : SOURCE, failure == 3 ? SOURCE : OPTIONS, failure != 4, failure == 5 ? 123 : 21,
                    3, 10, 1, failure == 6 ? 38 : 37);
            if (failure == 7) d.epoch = -1;
            s.beforeDraw(wrong); check(status(s, 0).equals("UNKNOWN") && d.reads == 0, "Source/program/typed epoch rejection " + failure);
        }
        var orphan = open(properties(), contract(), new Mock()); orphan.beginCapture(); orphan.afterDraw(valid); check(status(orphan, 0).equals("UNKNOWN"), "Unpaired tail cannot prove completion");
        for (int mismatch = 0; mismatch < 5; mismatch++) {
            var d = new Mock(); var s = open(properties(), contract(), d); s.beginCapture(); s.beforeDraw(valid); s.afterDraw(valid); d.consumer();
            Draw consumer = draw(Stage.CONSUMER, mismatch == 0 ? 11 : 10, mismatch == 1 ? 1 : 2);
            if (mismatch == 2) d.textures.put(GL13.GL_TEXTURE0 + 2, 102);
            if (mismatch == 3) d.attachments.get(23).put(GL30.GL_COLOR_ATTACHMENT0 + 1, 102);
            if (mismatch == 4) consumer = new Draw(Stage.CONSUMER, "world0/deferred5", CONSUMER, SOURCE, OPTIONS, true, 22, 4, 10, 2, 37);
            s.beforeDraw(consumer); check(status(s, 2).equals("UNKNOWN") && d.reads == 4, "Stale cache/provenance rejected " + mismatch);
        }
        var changed = new Mock(); var s = open(properties(), contract(), changed); s.beginCapture(); s.beforeDraw(valid); changed.attachments.get(23).put(GL30.GL_COLOR_ATTACHMENT0, 201); s.afterDraw(valid);
        check(status(s, 1).equals("UNKNOWN") && changed.reads == 0, "Layout mutation between hooks rejected");
    }
    private static void failuresAndRestoration() {
        for (int failure = 0; failure < 4; failure++) {
            var d = new Mock(); var s = open(properties(), contract(), d); s.beginCapture(); Draw p = draw(Stage.PRODUCER, 10, 1);
            s.beforeDraw(p); String initial = d.state();
            if (failure == 0) d.failTexture = true;
            if (failure == 1) d.failPixelAt = 2;
            if (failure == 2) d.failRestore = true;
            if (failure == 3) d.failAttachment = true;
            s.afterDraw(p); check(status(s, 1).equals("UNKNOWN"), "Exceptions cannot establish successful readback " + failure);
            if (failure != 2) check(initial.equals(d.state()), "Failure restores all state " + failure);
            else {
                check(record(s, 1).has("restoreErrors"), "Restore failures explicit");
                check(d.readFbo == 12 && d.active == GL13.GL_TEXTURE0 + 5 && d.values.get(GL21.GL_PIXEL_PACK_BUFFER_BINDING) == 47 && d.restoredPacks >= 8, "Other restoration attempts survive target selector failure");
            }
            if (failure == 1) check(record(s, 1).getAsJsonArray("producerSamples").get(0).getAsJsonObject().has("rawQuantity"), "Raw quantity survives subsequent certificate read failure");
        }
        var local = new Mock(); String initial = local.state();
        // Fail the local finally on a visited unit. The outer scope must retry that unit as well as the initial one.
        local.failLocalRestoreUnit = GL13.GL_TEXTURE0 + 2;
        var s = open(properties(), contract(), local); s.beginCapture(); s.beforeDraw(draw(Stage.PRODUCER, 10, 1));
        check(status(s, 0).equals("UNKNOWN") && local.localRestoreFailed, "Local texture restore failure remains unknown");
        check(initial.equals(local.state()), "Visited unit binding independently restored after local restore failure");
    }
    private static void rawCodec() {
        var c = contract(); var tile = new Tile(0, 0); float[] q = {1, 2, 3, .5f}, cert = {37, 258, 0, 90};
        check(decode(c, tile, 37, q, cert).equals("VALID_DECLARED_RAW_LANES_ONLY"), "Exact codec");
        for (int lane = 0; lane < 4; lane++) { var wrong = cert.clone(); wrong[lane] += lane == 3 ? -100 : .5f; check(!decode(c, tile, 37, q, wrong).startsWith("VALID"), "Certificate channel rejection " + lane); }
        check(decode(c, tile, 37, new float[]{0, 0, 0, -1}, cert).equals("INVALID_QUANTITY_SENTINEL"), "Alpha sentinel never zero lighting");
        var d = new Mock(); d.q = new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1};
        var s = open(properties(), c, d); s.beginCapture(); Draw p = draw(Stage.PRODUCER, 10, 1); s.beforeDraw(p); s.afterDraw(p);
        var raw = record(s, 1).getAsJsonArray("producerSamples").get(0).getAsJsonObject();
        check(raw.getAsJsonArray("rawQuantity").get(0).getAsString().equals("NaN") && raw.get("status").getAsString().equals("UNKNOWN_NONFINITE_QUANTITY"), "Nonfinite raw outcomes retained distinctly");
    }
    private static void boundsAndLifecycle() {
        var odd = contract(7, 5, List.of(new Tile(0, 0), new Tile(3, 2)), 4); var d = new Mock(7, 5); var s = open(properties(), odd, d);
        Draw p = draw(Stage.PRODUCER, 10, 1); s.beforeDraw(p); check(d.calls == 0, "Unarmed no GL");
        s.beginCapture(); s.beforeDraw(p); s.afterDraw(p);
        check(d.reads == 2 && record(s, 1).getAsJsonArray("producerSamples").get(1).getAsJsonObject().get("status").getAsString().equals("UNKNOWN_INCOMPLETE_TILE_NO_READ"), "Odd tile retained without OOB fetch");
        d.consumer(); Draw consumer = draw(Stage.CONSUMER, 10, 2); s.beforeDraw(consumer); s.afterDraw(consumer);
        int calls = d.calls; s.beforeDraw(consumer); s.afterDraw(consumer); check(d.calls == calls && s.report().getAsJsonArray("records").size() == 4, "Bounded records cease GL work");
        s.endCapture(); check(!s.report().get("captureArmed").getAsBoolean(), "End disarms");
    }
}
