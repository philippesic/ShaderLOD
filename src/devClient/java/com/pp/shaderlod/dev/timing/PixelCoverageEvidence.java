package com.pp.shaderlod.dev.timing;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Pure Java guards/summary for one opt-in full scene-depth readback. No GL calls or hook installation. */
public final class PixelCoverageEvidence {
    public static final String PROPERTY = "shaderlod.dev.pixelCoverage";
    public static final int MAX_TEXELS = 16_777_216;
    private PixelCoverageEvidence() {}

    /** Pins are supplied by the explicit reviewed adapter, never discovered from uniform names. */
    public record Contract(String sourceSha256, String optionsSha256, String memberSha256,
                           String transformedVertexSha256, String transformedFragmentSha256,
                           String stage, String sourceMarker, String sceneCameraSha256,
                           String otherControlsSha256, int width, int height, int globalEnabled) {
        public Contract {
            for (String pin : List.of(sourceSha256, optionsSha256, memberSha256,
                    transformedVertexSha256, transformedFragmentSha256, sceneCameraSha256, otherControlsSha256)) hash(pin);
            text(stage); text(sourceMarker);
            if (width < 2 || height < 2 || (long) width * height > MAX_TEXELS) bad("UNBOUNDED_EXTENT");
            if (globalEnabled != 0 && globalEnabled != 1) bad("UNKNOWN_GLOBAL_MODE");
        }
        public float scale() { return globalEnabled == 0 ? 1f : .75f; }
        public int footprintWidth() { return (int) Math.ceil(width * (double) scale()); }
        public int footprintHeight() { return (int) Math.ceil(height * (double) scale()); }
    }

    /** Exact createProgram/source-object association and actual renderAll/pass/current-program identity. */
    public record Draw(String sourceSha256, String optionsSha256, String memberSha256,
                       String transformedVertexSha256, String transformedFragmentSha256,
                       String stage, String sourceMarker, String sceneCameraSha256, String otherControlsSha256,
                       int associatedProgram, int currentProgram, long pipelineGeneration, long frame,
                       long drawOrdinal, int linkedCoreVersion, int linkedGlobalEnabled,
                       boolean sourceObjectAssociated, boolean markerInCompiledSource,
                       boolean afterGbufferBeforeUpscale, boolean settledWarmupCompleted) {}

    /** Read current RenderTargets.getDepthTexture(), NOT Photon depthtex0 (which can be a 3D LUT). */
    public record Texture(int object, String origin, int level, int internalFormat, int width, int height,
                          int renderTargetsDepthObject, boolean queriedActualLevel) {}

    /** glGetTexImage touches pack state and one 2D binding; retain unchanged FBO/program/viewport too. */
    public record State(int currentProgram, int drawFramebuffer, int readFramebuffer, List<Integer> viewport,
                        int activeTexture, int textureBinding2D, int pixelPackBuffer,
                        Map<String, Integer> packState) {
        public State { viewport = List.copyOf(viewport); packState = Map.copyOf(packState); }
    }

    /** Native caller must attempt all restoration operations even after an individual failure. */
    public record Restoration(State before, State after, List<String> errors) {
        public Restoration { Objects.requireNonNull(before); Objects.requireNonNull(after); errors = List.copyOf(errors); }
    }
    public record Bounds(int minX, int minY, int maxX, int maxY) {}
    public record Summary(long texels, long geometryTexels, long clearTexels, long geometryInside,
                          long geometryOutside, long clearOutside, long outsideTexels,
                          Bounds geometryBounds, float minimumDepth, float maximumDepth,
                          long[] rowGeometry, long[] columnGeometry) {
        public Summary { rowGeometry = rowGeometry.clone(); columnGeometry = columnGeometry.clone(); }
        @Override public long[] rowGeometry() { return rowGeometry.clone(); }
        @Override public long[] columnGeometry() { return columnGeometry.clone(); }
        public boolean observedClearOutside() { return outsideTexels > 0 && geometryOutside == 0 && clearOutside == outsideTexels; }
    }

    public static boolean enabled(Properties properties) {
        if (!Boolean.parseBoolean(properties.getProperty(PROPERTY, "false"))) return false;
        for (String key : List.of("gpuTiming", "cpuTiming", "cpuLayerTiming"))
            if (!"false".equalsIgnoreCase(properties.getProperty("shaderlod.dev." + key, ""))) bad("REQUIRE_EXPLICIT_TIMING_OFF_" + key);
        for (String key : List.of("benchmark", "benchmarkOnly"))
            if (Boolean.parseBoolean(properties.getProperty("shaderlod.dev." + key, "false"))) bad("FORBID_" + key);
        return true;
    }

    /** Fill every lane before GL readback. A truncated/no-op read leaves nonfinite sentinels and fails. */
    public static void fillSentinel(FloatBuffer target, int width, int height) {
        long count = (long) width * height;
        if (width < 2 || height < 2 || count > MAX_TEXELS || count != target.remaining()) bad("SENTINEL_EXTENT_MISMATCH");
        for (int i = target.position(); i < target.limit(); i++) target.put(i, Float.NaN);
    }

    public static Summary inspect(Contract contract, Draw draw, Texture texture, float[] depth,
                                  Restoration restoration, boolean readbackReturned) {
        verify(contract, draw, texture, restoration, readbackReturned);
        return summarize(depth, contract.width(), contract.height(), contract.footprintWidth(), contract.footprintHeight());
    }

    public record Capture(int schema, String status, Contract contract, Draw draw, Texture texture,
                          Restoration restoration, Summary summary, Map<String, Object> raw,
                          boolean performanceEvidence) {
        public Capture { raw = Map.copyOf(raw); }
    }

    /** Save only after complete guards. Caller retains failed raw/exception records separately. */
    public static Capture rawCapture(Path path, Contract contract, Draw draw, Texture texture, float[] depth,
                                     Restoration restoration, boolean readbackReturned) throws IOException {
        Summary summary = inspect(contract, draw, texture, depth, restoration, readbackReturned);
        Map<String, Object> raw = writeRaw(path, depth, contract.width(), contract.height());
        return new Capture(1, "OBSERVED_FULL_NATIVE_SCENE_DEPTH_ONLY", contract, draw, texture, restoration, summary, raw, false);
    }

    private static void verify(Contract c, Draw d, Texture t, Restoration r, boolean returned) {
        Objects.requireNonNull(c); Objects.requireNonNull(d); Objects.requireNonNull(t); Objects.requireNonNull(r);
        if (!c.sourceSha256().equals(d.sourceSha256()) || !c.optionsSha256().equals(d.optionsSha256())
                || !c.memberSha256().equals(d.memberSha256()) || !c.transformedVertexSha256().equals(d.transformedVertexSha256())
                || !c.transformedFragmentSha256().equals(d.transformedFragmentSha256()) || !c.stage().equals(d.stage())
                || !c.sourceMarker().equals(d.sourceMarker()) || !c.sceneCameraSha256().equals(d.sceneCameraSha256())
                || !c.otherControlsSha256().equals(d.otherControlsSha256()) || !d.sourceObjectAssociated()
                || !d.markerInCompiledSource() || !d.afterGbufferBeforeUpscale() || !d.settledWarmupCompleted()
                || d.associatedProgram() <= 0 || d.currentProgram() != d.associatedProgram()
                || d.pipelineGeneration() < 0 || d.frame() < 0 || d.drawOrdinal() < 0
                || d.linkedCoreVersion() != 2 || d.linkedGlobalEnabled() != c.globalEnabled()) bad("SOURCE_PROGRAM_OR_SETTLED_DRAW_MISMATCH");
        // Sized GL depth formats, not color/R32F, stencil-only or undefined storage.
        if (t.object() <= 0 || !"IRIS_RENDER_TARGETS_CURRENT_DEPTH".equals(t.origin())
                || t.object() != t.renderTargetsDepthObject() || t.level() != 0 || !t.queriedActualLevel()
                || !List.of(0x81A5, 0x81A6, 0x81A7, 0x8CAC, 0x88F0, 0x8CAD).contains(t.internalFormat())
                || t.width() != c.width() || t.height() != c.height()) bad("UNKNOWN_OR_NON_NATIVE_SCENE_DEPTH_STORAGE");
        State before = r.before();
        if (before.currentProgram() != d.currentProgram() || before.drawFramebuffer() <= 0
                || !before.viewport().equals(List.of(0, 0, c.width(), c.height()))
                || !before.packState().keySet().equals(java.util.Set.of("ALIGNMENT", "ROW_LENGTH", "SKIP_ROWS", "SKIP_PIXELS",
                    "SWAP_BYTES", "LSB_FIRST", "IMAGE_HEIGHT", "SKIP_IMAGES"))) bad("INCOMPLETE_DRAW_OR_RESTORATION_STATE");
        if (!returned) bad("READBACK_DID_NOT_RETURN");
        if (!r.errors().isEmpty() || !before.equals(r.after())) bad("GL_STATE_RESTORATION_UNPROVED");
    }

    /** Full allocation scan; exact depth==1 is clear. Sky/holes remain observational ambiguity. */
    public static Summary summarize(float[] depth, int width, int height, int footprintWidth, int footprintHeight) {
        if (depth == null || width < 2 || height < 2 || (long) width * height > MAX_TEXELS
                || depth.length != (long) width * height || footprintWidth < 1 || footprintWidth > width
                || footprintHeight < 1 || footprintHeight > height) bad("MISSING_OR_TRUNCATED_FULL_EXTENT");
        long populated = 0, clear = 0, inside = 0, outside = 0, clearOutside = 0;
        int minX = width, minY = height, maxX = -1, maxY = -1;
        float min = 1f, max = 0f;
        long[] rows = new long[height], columns = new long[width];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            float value = depth[y * width + x];
            if (!Float.isFinite(value) || value < 0f || value > 1f) bad("NONFINITE_SENTINEL_OR_INVALID_DEPTH_AT_" + x + "_" + y);
            min = Math.min(min, value); max = Math.max(max, value);
            boolean in = x < footprintWidth && y < footprintHeight;
            if (value == 1f) { clear++; if (!in) clearOutside++; }
            else {
                populated++; if (in) inside++; else outside++;
                rows[y]++; columns[x]++;
                minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
            }
        }
        return new Summary(depth.length, populated, clear, inside, outside, clearOutside,
                (long) width * height - (long) footprintWidth * footprintHeight,
                populated == 0 ? null : new Bounds(minX, minY, maxX, maxY), min, max, rows, columns);
    }

    public record Comparison(String status, long baselineGeometryOutsideCandidate, long candidateGeometryOutside,
                             long candidateOutsideTexels, boolean performanceEvidence) {}

    /** Caller supplies matched full camera/control receipts; no few-point or sky-only extrapolation. */
    public static Comparison compare(Contract off, float[] baselineDepth, Contract on, float[] candidateDepth) {
        if (off.globalEnabled() != 0 || on.globalEnabled() != 1 || off.width() != on.width() || off.height() != on.height()
                || !off.sourceSha256().equals(on.sourceSha256()) || !off.optionsSha256().equals(on.optionsSha256())
                || !off.memberSha256().equals(on.memberSha256()) || !off.transformedVertexSha256().equals(on.transformedVertexSha256())
                || !off.transformedFragmentSha256().equals(on.transformedFragmentSha256()) || !off.stage().equals(on.stage())
                || !off.sourceMarker().equals(on.sourceMarker()) || !off.sceneCameraSha256().equals(on.sceneCameraSha256())
                || !off.otherControlsSha256().equals(on.otherControlsSha256())) bad("UNMATCHED_NATIVE_CANDIDATE_RECEIPTS");
        Summary baseline = summarize(baselineDepth, off.width(), off.height(), on.footprintWidth(), on.footprintHeight());
        Summary candidate = summarize(candidateDepth, on.width(), on.height(), on.footprintWidth(), on.footprintHeight());
        long expectedOutside = (long) on.width() * on.height() - (long) on.footprintWidth() * on.footprintHeight();
        String status = baseline.geometryOutside() == 0 ? "INCONCLUSIVE_CONTROL_HAS_NO_GEOMETRY_OUTSIDE"
                : candidate.geometryTexels() == 0 ? "INCONCLUSIVE_CANDIDATE_HAS_NO_GEOMETRY"
                : candidate.observedClearOutside() ? "OBSERVED_MATCHED_SCENE_RESTRICTED_DEPTH_FOOTPRINT"
                : "ADVERSE_GEOMETRY_OUTSIDE_CANDIDATE_FOOTPRINT";
        return new Comparison(status, baseline.geometryOutside(), candidate.geometryOutside(), expectedOutside, false);
    }

    /** Full raw depth, row-major OpenGL lower-left origin, little-endian IEEE binary32. No transformed image. */
    public static Map<String, Object> writeRaw(Path path, float[] depth, int width, int height) throws IOException {
        summarize(depth, width, height, width, height);
        ByteBuffer bytes = ByteBuffer.allocate(Math.multiplyExact(depth.length, 4)).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : depth) bytes.putFloat(value);
        // Caller chooses a fresh bounded diagnostic destination; preserve any pre-existing raw capture.
        Files.write(path, bytes.array(), java.nio.file.StandardOpenOption.CREATE_NEW);
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("path", path.toString()); metadata.put("sha256", digest(bytes.array()));
        metadata.put("format", "GL_DEPTH_COMPONENT/GL_FLOAT:f32le"); metadata.put("origin", "lower-left");
        metadata.put("width", width); metadata.put("height", height); metadata.put("bytes", bytes.capacity());
        metadata.put("fullExtent", true); metadata.put("performanceEvidence", false);
        return Map.copyOf(metadata);
    }

    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static void hash(String value) { if (value == null || !value.matches("[0-9a-f]{64}")) bad("INVALID_SHA256_PIN"); }
    private static void text(String value) { if (value == null || value.isBlank() || value.length() > 256) bad("INVALID_IDENTITY"); }
    private static void bad(String message) { throw new IllegalArgumentException(message); }
}
