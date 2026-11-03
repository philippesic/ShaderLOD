package com.pp.shaderlod.benchmark.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static com.pp.shaderlod.benchmark.core.BenchmarkAnalysis.ANALYSIS_VERSION;
import com.pp.shaderlod.benchmark.core.BenchmarkAnalysis.Activation;
import com.pp.shaderlod.benchmark.core.BenchmarkAnalysis.Availability;
import com.pp.shaderlod.benchmark.core.BenchmarkAnalysis.Validity;
import static com.pp.shaderlod.benchmark.core.BenchmarkAnalysisTest.*;
import static com.pp.shaderlod.benchmark.core.BenchmarkResult.*;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkResultWriterTest {
    @TempDir Path temporary;
    static Window capturedWindow() {
        return new Window(SCHEMA_VERSION, "w", "c", "session", 0, Arm.MASTER_ON_NO_REQUESTS,
                Map.of(), 1L, 1L, 0, Temperature.WARM, Stage.CONTROL, Motion.STILL,
                new Scenario("FAST", "1", "route1", "coast", "overworld", Map.of("animations", "native")),
                new Configuration("native", "source-receipt", Map.of(), Map.of(), "native", 1920, 1200, 12, 5, "absent", "warm"),
                "coarse", Map.of("calibration", "untested"),
                List.of(new Clock("total-frame", "render-start intervals", "total-frame; presentation included", "wall", Availability.AVAILABLE,
                        null, "every frame", "drained outside capture", 1, 0, 0, 1, List.of("render-start"), List.of("missing first interval"))),
                new Warmup(1000.0, true, List.of("chunks"), List.of(), List.of(), List.of("geometry-receipt")),
                Map.of("GC", "unavailable"), List.of(new Work("GL submissions", "draws", null, null, null,
                        Availability.UNAVAILABLE, "render", List.of(), List.of("not collected"))),
                1L, 1L, List.of(Sample.from(sample(1, 0, null))),
                BenchmarkStatistics.summarize(List.of(sample(1, 0, null)), POLICY), Availability.AVAILABLE,
                Activation.ACTIVE, Validity.INVALID, List.of("missing clock"));
    }
    static Manifest manifest(String runId) {
        return new Manifest(SCHEMA_VERSION, ANALYSIS_VERSION, runId, "2026-10-25T00:00:00Z",
                new Environment("fixture", Map.of("z", "last", "a", "first"), "Mac", "macOS", "driver", "21"),
                List.of(capturedWindow()), List.of(),
                new Evidence(EvidenceVerdict.UNTESTED, EvidenceVerdict.UNTESTED, List.of(), List.of(), List.of(), List.of("not captured")),
                new Recovery(RecoveryStatus.RESTORED, "journal", true, List.of()),
                new SearchCoverage(0, 0, List.of(), List.of(), false, List.of()), List.of("evidence/path"), List.of("fixture only"));
    }
    @Test void deterministicJsonPreservesUnavailableValuesEscapingAndLimitations() {
        var m = manifest("run\n\"\\\t\u0001\ud83d\udc08");
        String json = BenchmarkResultWriter.toJson(m);
        assertEquals(json, BenchmarkResultWriter.toJson(m));
        assertTrue(json.contains("\"a\":\"first\",\"z\":\"last\""));
        assertTrue(json.contains("run\\n\\\"\\\\\\t\\u0001\\ud83d\\udc08"));
        assertTrue(json.contains("\"totalFrameNs\":null")); assertTrue(json.contains("\"gpuElapsedNs\":null"));
        assertTrue(json.contains("\"meanMs\":null")); assertFalse(json.contains("NaN")); assertFalse(json.contains("Infinity"));
        assertTrue(json.contains("\"qualificationBlockers\"")); assertTrue(json.contains("\"validity\":\"INVALID\""));
        assertTrue(json.contains("descriptive-only"));
    }
    @Test void writerReplacesWholeManifestAndLeavesNoTemporaryFile() throws Exception {
        Path target = temporary.resolve("nested/result.json");
        Files.createDirectories(target.getParent()); Files.writeString(target, "old");
        BenchmarkResultWriter.write(target, manifest("new"));
        assertEquals(BenchmarkResultWriter.toJson(manifest("new")), Files.readString(target));
        try (var files = Files.list(target.getParent())) { assertEquals(List.of(target), files.toList()); }
    }
    @Test void serializationFailurePreservesPriorEvidence() throws Exception {
        Path target = temporary.resolve("result.json"); Files.writeString(target, "prior evidence");
        assertThrows(NullPointerException.class, () -> BenchmarkResultWriter.write(target, null));
        assertEquals("prior evidence", Files.readString(target));
    }
    @Test void untestedQualityAndInvalidClocksRemainExportableButUnqualified() {
        var blockers = manifest("r").qualificationBlockers();
        assertTrue(blockers.stream().anyMatch(s -> s.contains("quality/compatibility")));
        assertTrue(blockers.stream().anyMatch(s -> s.contains("invalid/untested")));
        assertTrue(blockers.contains("no analyzed comparisons"));
    }
    @Test void schemaAndClockCountsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new Sample(2, 1, 0, null, null, null, null, null, "missing"));
        assertThrows(IllegalArgumentException.class, () -> new Clock("c", "d", "s", "b", Availability.AVAILABLE,
                null, "every", "none", 1, 1, 1, 0, List.of(), List.of()));
    }
}
