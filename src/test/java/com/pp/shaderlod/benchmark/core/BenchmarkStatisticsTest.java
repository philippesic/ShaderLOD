package com.pp.shaderlod.benchmark.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static com.pp.shaderlod.benchmark.core.BenchmarkAnalysisTest.*;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkStatisticsTest {
    @Test void chronologyIsPreservedWhileQuantilesSortOnlyACopy() {
        var input = samples(64, i -> i < 32 ? 12 : 4);
        var s = BenchmarkStatistics.summarize(input, POLICY);
        assertEquals(List.of(12.0, 12.0, 4.0, 4.0), s.blocks().stream().map(BenchmarkStatistics.Block::meanMs).toList());
        assertEquals(12_000_000L, input.getFirst().frameWallNs());
        assertEquals(8, s.meanMs()); assertEquals(8, s.medianMs());
        assertEquals(12, s.p95Ms()); assertEquals(12, s.p99Ms());
        assertTrue(BenchmarkStatistics.completeCoverage(s));
        assertEquals(125, s.throughputFps()); // reciprocal mean, not mean instantaneous FPS
        assertEquals(512, s.durationMs());
    }
    @Test void gapsMissingAndPartialBlocksNeverJoinAcrossCaptureHoles() {
        var input = new ArrayList<>(samples(40, i -> 10));
        input.set(8, sample(8, 0, null));
        var s = BenchmarkStatistics.summarize(input, POLICY);
        assertEquals(1, s.missingCount()); assertEquals(39, s.count());
        assertFalse(BenchmarkStatistics.completeCoverage(s));
        assertEquals(1, s.blocks().size()); assertEquals(9, s.blocks().getFirst().firstFrame());
        assertEquals(24, s.blocks().getFirst().lastFrame()); assertEquals(23, s.unblockedValidCount());
        var gap = new ArrayList<>(samples(8, i -> 10));
        for (int i = 10; i < 26; i++) gap.add(sample(i, 0, 10_000_000L));
        var g = BenchmarkStatistics.summarize(gap, POLICY);
        assertFalse(BenchmarkStatistics.completeCoverage(g));
        assertEquals(1, g.blocks().size()); assertEquals(10, g.blocks().getFirst().firstFrame());
    }
    @Test void mixedEpochsAndReorderedSamplesAreInvalidChronologyEvenAroundNulls() {
        var input = new ArrayList<>(samples(64, i -> 10));
        input.set(32, sample(32, 1, 10_000_000L));
        assertFalse(BenchmarkStatistics.summarize(input, POLICY).chronologyValid());
        input.set(32, null); input.set(33, sample(1, 0, 10_000_000L));
        assertFalse(BenchmarkStatistics.summarize(input, POLICY).chronologyValid());
    }
    @Test void missingAndNonpositiveClocksAreNotZeroFilled() {
        var s = BenchmarkStatistics.summarize(List.of(sample(0, 0, null), sample(1, 0, 0L), sample(2, 0, -1L)), POLICY);
        assertEquals(0, s.count()); assertEquals(1, s.missingCount()); assertEquals(2, s.invalidCount());
        assertTrue(Double.isNaN(s.meanMs())); assertTrue(Double.isNaN(s.throughputFps()));
        assertTrue(s.blocks().isEmpty());
    }
    @Test void shortWindowP99IncludesSupportAndDescriptiveLimitations() {
        var s = BenchmarkStatistics.summarize(samples(24, i -> i == 23 ? 30 : 10), POLICY);
        assertEquals(30, s.p99Ms()); assertEquals(1, s.p99TailObservations());
        assertEquals(1, s.stutterCount()); assertEquals(20, s.stutterThresholdMs());
        assertTrue(s.tailStatus().contains("descriptive-only")); assertTrue(s.tailStatus().contains("no tail inference"));
        assertEquals(260, s.durationMs());
    }
    @Test void durationQualificationCannotBeReplacedByFastFrameCount() {
        var p = new AnalysisPolicy(16, 1000, 4, 3, 3, "fixture", 20);
        var s = BenchmarkStatistics.summarize(samples(256, i -> 1), p);
        assertTrue(s.blocks().isEmpty()); assertEquals(256, s.unblockedValidCount());
    }
    @Test void callerMustDeclarePracticalCriterionAndAnalysisSettings() {
        assertThrows(IllegalArgumentException.class, () -> new PracticalCriterion(.2, .2, .5, ""));
        assertThrows(IllegalArgumentException.class, () -> new PracticalCriterion(Double.NaN, .2, .5, "fixture"));
        assertThrows(IllegalArgumentException.class, () -> new AnalysisPolicy(1, 1, 1, 1, 1, "", 20));
    }
}
