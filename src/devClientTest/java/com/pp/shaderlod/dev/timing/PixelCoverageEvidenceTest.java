package com.pp.shaderlod.dev.timing;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import static com.pp.shaderlod.dev.timing.PixelCoverageEvidence.*;

/** Standalone pure Java fixture; no native context, Gradle, client or shared classes touched. */
public final class PixelCoverageEvidenceTest {
    private static int assertions;
    private static final String PIN = "a".repeat(64);
    private static Contract contract(int global) {
        return new Contract(PIN, PIN, PIN, PIN, PIN, "world0/deferred4", "PROGRAM_DEFERRED4",
                PIN, PIN, 8, 8, global);
    }
    private static Draw draw(int global) {
        return new Draw(PIN, PIN, PIN, PIN, PIN, "world0/deferred4", "PROGRAM_DEFERRED4", PIN, PIN,
                7, 7, 1, 100, 2, 2, global, true, true, true, true);
    }
    private static Texture texture() { return new Texture(9, "IRIS_RENDER_TARGETS_CURRENT_DEPTH", 0, 0x81A6, 8, 8, 9, true); }
    private static State state() {
        return new State(7, 3, 4, List.of(0, 0, 8, 8), 0x84C0 + 5, 11, 17,
                Map.of("ALIGNMENT", 8, "ROW_LENGTH", 12, "SKIP_ROWS", 2, "SKIP_PIXELS", 3,
                        "SWAP_BYTES", 1, "LSB_FIRST", 1, "IMAGE_HEIGHT", 12, "SKIP_IMAGES", 4));
    }
    private static Restoration restoration() { return new Restoration(state(), state(), List.of()); }
    private static Properties properties() {
        var p = new Properties(); p.setProperty(PROPERTY, "true");
        for (String key : List.of("gpuTiming", "cpuTiming", "cpuLayerTiming")) p.setProperty("shaderlod.dev." + key, "false");
        return p;
    }
    private static float[] plane(int side) {
        float[] values = new float[64]; Arrays.fill(values, 1);
        for (int y = 0; y < side; y++) for (int x = 0; x < side; x++) values[y * 8 + x] = .4f;
        return values;
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Expected guarded rejection"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
    public static void main(String[] args) throws Exception {
        gates(); fullDepth(); provenance(); restorationGuards(); pairedCoverage(); rawFormat(Path.of(args[0]));
        System.out.println("PASS PixelCoverageEvidenceTest: 6 fixture groups, " + assertions + " assertions; no GL or runtime coverage claim");
    }
    private static void gates() {
        check(!enabled(new Properties()), "Default off"); check(enabled(properties()), "Explicit query-free opt in");
        for (String key : List.of("gpuTiming", "cpuTiming", "cpuLayerTiming", "benchmark", "benchmarkOnly")) {
            var p = properties(); p.setProperty("shaderlod.dev." + key, "true"); rejects(() -> enabled(p));
        }
        var p = properties(); p.remove("shaderlod.dev.cpuLayerTiming"); rejects(() -> enabled(p));
        rejects(() -> new Contract(PIN, PIN, PIN, PIN, PIN, "stage", "marker", PIN, PIN, 8192, 8192, 1));
        rejects(() -> new Contract(PIN, PIN, PIN, PIN, PIN, "stage", "marker", PIN, PIN, 8, 8, 2));
    }
    private static void fullDepth() {
        var s = inspect(contract(1), draw(1), texture(), plane(6), restoration(), true);
        check(s.texels() == 64 && s.geometryTexels() == 36 && s.clearTexels() == 28, "Whole allocation accounted");
        check(s.geometryInside() == 36 && s.geometryOutside() == 0 && s.clearOutside() == 28 && s.observedClearOutside(), "Actual lower-left footprint");
        check(s.geometryBounds().equals(new Bounds(0, 0, 5, 5)), "All geometry bounds observed");
        long[] rows = s.rowGeometry(); rows[0] = 999; check(s.rowGeometry()[0] == 6, "Summary arrays defensively immutable");
        var empty = summarize(new float[]{1, 1, 1, 1}, 2, 2, 1, 1); check(empty.geometryBounds() == null, "No invented sky bounds");
        rejects(() -> summarize(new float[63], 8, 8, 6, 6));
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -.01f, 1.001f}) {
            var values = plane(6); values[63] = bad; rejects(() -> summarize(values, 8, 8, 6, 6));
        }
        FloatBuffer sentinel = FloatBuffer.allocate(64); fillSentinel(sentinel, 8, 8);
        check(sentinel.position() == 0 && Float.isNaN(sentinel.get(63)), "NaN sentinel preserves buffer position");
        for (int i = 0; i < 63; i++) sentinel.put(i, .2f);
        rejects(() -> summarize(sentinel.array(), 8, 8, 6, 6));
        rejects(() -> fillSentinel(FloatBuffer.allocate(63), 8, 8));
    }
    private static void provenance() {
        for (int failure = 0; failure < 12; failure++) {
            final int f = failure;
            Draw d = new Draw(f == 0 ? "b".repeat(64) : PIN, PIN, PIN, PIN, PIN,
                    f == 1 ? "unknown" : "world0/deferred4", "PROGRAM_DEFERRED4", PIN, PIN,
                    7, f == 2 ? 8 : 7, f == 3 ? -1 : 1, 100, 2, f == 4 ? 1 : 2,
                    f == 5 ? 0 : 1, f != 6, f != 7, f != 8, f != 9);
            Texture t = f == 10 ? new Texture(9, "DEPTH_TEX0_UNIFORM", 0, 0x81A6, 8, 8, 9, true)
                    : f == 11 ? new Texture(9, "IRIS_RENDER_TARGETS_CURRENT_DEPTH", 0, 0x81A6, 7, 8, 9, true) : texture();
            rejects(() -> inspect(contract(1), d, t, plane(6), restoration(), true));
        }
        for (Texture t : List.of(new Texture(9, "IRIS_RENDER_TARGETS_CURRENT_DEPTH", 0, 0x822E, 8, 8, 9, true),
                new Texture(9, "IRIS_RENDER_TARGETS_CURRENT_DEPTH", 1, 0x81A6, 8, 8, 9, true),
                new Texture(9, "IRIS_RENDER_TARGETS_CURRENT_DEPTH", 0, 0x81A6, 8, 8, 10, true),
                new Texture(9, "IRIS_RENDER_TARGETS_CURRENT_DEPTH", 0, 0x81A6, 8, 8, 9, false)))
            rejects(() -> inspect(contract(1), draw(1), t, plane(6), restoration(), true));
    }
    private static void restorationGuards() {
        State s = state();
        for (Restoration r : List.of(new Restoration(s, s, List.of("restore bind failed")),
                new Restoration(s, new State(7, 3, 4, s.viewport(), s.activeTexture(), 999, s.pixelPackBuffer(), s.packState()), List.of()),
                new Restoration(s, new State(7, 3, 4, s.viewport(), s.activeTexture(), s.textureBinding2D(), 0, s.packState()), List.of())))
            rejects(() -> inspect(contract(1), draw(1), texture(), plane(6), r, true));
        State wrong = new State(7, 3, 4, List.of(0, 0, 6, 6), s.activeTexture(), s.textureBinding2D(), s.pixelPackBuffer(), s.packState());
        rejects(() -> inspect(contract(1), draw(1), texture(), plane(6), new Restoration(wrong, wrong, List.of()), true));
        rejects(() -> inspect(contract(1), draw(1), texture(), plane(6), restoration(), false));
    }
    private static void pairedCoverage() {
        Comparison c = compare(contract(0), plane(8), contract(1), plane(6));
        check(c.status().equals("OBSERVED_MATCHED_SCENE_RESTRICTED_DEPTH_FOOTPRINT") && c.baselineGeometryOutsideCandidate() == 28, "Native control fills regions clear in candidate");
        check(!c.performanceEvidence(), "Never inferred speed");
        check(compare(contract(0), plane(6), contract(1), plane(6)).status().startsWith("INCONCLUSIVE_CONTROL"), "Sky/holes not footprint proof");
        check(compare(contract(0), plane(8), contract(1), plane(0)).status().startsWith("INCONCLUSIVE_CANDIDATE"), "Empty candidate not success");
        var stray = plane(6); stray[63] = .9f;
        check(compare(contract(0), plane(8), contract(1), stray).status().startsWith("ADVERSE"), "One stray pixel retained");
        Contract mismatch = new Contract(PIN, PIN, PIN, PIN, PIN, "world0/deferred4", "PROGRAM_DEFERRED4", "b".repeat(64), PIN, 8, 8, 1);
        rejects(() -> compare(contract(0), plane(8), mismatch, plane(6)));
    }
    private static void rawFormat(Path directory) throws Exception {
        Files.createDirectories(directory); Path path = directory.resolve("fixture-depth.f32le");
        Map<String, Object> m = writeRaw(path, plane(6), 8, 8); byte[] bytes = Files.readAllBytes(path);
        check(bytes.length == 256 && m.get("bytes").equals(256), "Exact full extent raw length");
        FloatBuffer decoded = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        check(decoded.get(0) == .4f && decoded.get(63) == 1f && m.get("sha256").toString().length() == 64, "Raw binary32 exact no tone mapping");
        try { writeRaw(path, plane(8), 8, 8); throw new AssertionError("Must preserve previous capture"); }
        catch (java.nio.file.FileAlreadyExistsException expected) { assertions++; }
        Capture capture = rawCapture(directory.resolve("capture-depth.f32le"), contract(1), draw(1), texture(), plane(6), restoration(), true);
        check(capture.schema() == 1 && capture.summary().observedClearOutside() && !capture.performanceEvidence(), "Combined capture retains guarded provenance and exact raw");
    }
}
