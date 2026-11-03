package com.pp.shaderlod.benchmark;

import com.pp.shaderlod.benchmark.core.BenchmarkAnalysis;
import com.pp.shaderlod.benchmark.core.BenchmarkResult;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import static org.junit.jupiter.api.Assertions.*;

/** Real scenario lifecycle + bounded shared-clock consumer + versioned post-capture projection. */
class BenchmarkRunFlowTest {
    static long ms(long value) { return value * 1_000_000; }
    static final class Fixture implements BenchmarkScenarioRun.World {
        final BenchmarkRunCaptureTest.Clock clock = new BenchmarkRunCaptureTest.Clock();
        final BenchmarkRunCapture capture = new BenchmarkRunCapture(clock, 100);
        final List<String> applied = new ArrayList<>();
        final BenchmarkScenario.Schedule schedule;
        final BenchmarkScenarioRun run;
        boolean restored;
        int restoreRequests;
        Fixture() {
            var fast = BenchmarkScenario.fast(new BenchmarkScenario.Settings(12, 1920, 1200, BenchmarkScenario.DhState.ABSENT));
            var cases = List.of(fast.cases().getFirst(), fast.cases().get(5)).stream().map(c -> new BenchmarkScenario.Case(
                    c.id(), c.poseIndex(), c.environment(), c.settings(), c.workload(), c.routeTicks(), c.requestedWork(),
                    new BenchmarkScenario.Budgets(Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1),
                            Duration.ofMillis(100), Duration.ofSeconds(1), Duration.ofSeconds(5)))).toList();
            schedule = new BenchmarkScenario.Schedule("FAST-selected-test", cases, Duration.ofSeconds(15), Duration.ofSeconds(2));
            run = new BenchmarkScenarioRun(schedule, this, capture, c -> new BenchmarkReadiness.Policy(Duration.ofMillis(100),
                    Duration.ofMillis(100), Duration.ofMillis(250), Duration.ofSeconds(1), 2, 0, 0), 0);
        }
        public void apply(BenchmarkScenario.Case c) { applied.add(c.id()); }
        public boolean acknowledged() { return true; }
        public void beginRoute(int ticks) { fail("Still cases only"); }
        public boolean routeStarted() { return false; }
        public boolean routeComplete() { return false; }
        public void restore() { capture.abort(); restoreRequests++; }
        public boolean restored() { return restored; }
        BenchmarkScenarioRun.Result tick(long atMillis) {
            var c = run.currentCase().orElse(null);
            BenchmarkReadiness.Observation observation = null;
            if (c != null) {
                var work = new EnumMap<BenchmarkScenario.Work, BenchmarkReadiness.WorkEvidence>(BenchmarkScenario.Work.class);
                c.requestedWork().forEach(w -> work.put(w, new BenchmarkReadiness.WorkEvidence(BenchmarkReadiness.Eligibility.ELIGIBLE,
                        OptionalLong.of(1), "Injected executed test work")));
                observation = new BenchmarkReadiness.Observation(ms(atMillis), true, c.settings(), BenchmarkReadiness.Signal.READY,
                        BenchmarkReadiness.Signal.READY, BenchmarkReadiness.Signal.READY, BenchmarkReadiness.Signal.READY,
                        c.settings().dh(), BenchmarkReadiness.Signal.UNAVAILABLE, BenchmarkReadiness.Signal.UNAVAILABLE, null, work);
            }
            capture.collect();
            return run.tick(ms(atMillis), observation);
        }
        void enterCapture(long start) {
            tick(start); tick(start + 10); tick(start + 20); tick(start + 120);
            assertEquals(BenchmarkScenarioRun.Stage.CAPTURE, run.result().stage());
        }
        void finishCapture(long at) {
            clock.ready.add(BenchmarkRunCaptureTest.sample(1));
            clock.ready.add(BenchmarkRunCaptureTest.sample(2));
            clock.ready.add(BenchmarkRunCaptureTest.sample(3));
            tick(at);
            assertEquals(BenchmarkScenarioRun.Stage.DRAIN, run.result().stage());
            tick(at + 10);
        }
        BenchmarkResult.Manifest report(String reason) {
            return BenchmarkRunReport.manifest(null, "flow", schedule, run.result(), capture.snapshot(), Map.of(), Map.of(), Map.of(), reason, ms(1000));
        }
    }
    @Test void environmentCasesCycleAndExportOnlyAfterVerifiedRestoration() {
        var f = new Fixture(); f.enterCapture(0); f.finishCapture(220);
        f.enterCapture(240); f.finishCapture(460);
        assertEquals(List.of("day-terrain", "sunset-clear"), f.applied);
        assertEquals(BenchmarkScenarioRun.Stage.RESTORE, f.run.result().stage());
        assertEquals(1, f.restoreRequests); assertFalse(f.clock.running);
        assertThrows(IllegalStateException.class, () -> f.report(""));
        f.restored = true; f.tick(480);
        var report = f.report("");
        assertEquals(BenchmarkScenarioRun.Outcome.COMPLETE, f.run.result().outcome());
        assertTrue(f.run.result().restorationVerified());
        assertEquals(2, report.windows().size());
        for (var window : report.windows()) {
            assertEquals(BenchmarkAnalysis.Validity.VALID, window.validity());
            assertEquals(16, window.statistics().meanMs());
            assertEquals(62.5, window.statistics().throughputFps());
            assertEquals(3, window.samples().size());
        }
        assertTrue(report.decisions().isEmpty());
        assertEquals(BenchmarkResult.EvidenceVerdict.UNTESTED, report.evidence().quality());
    }
    @Test void cancelStopsClockBeforeRecoveryAndLeavesRemainingCasesUntested() {
        var f = new Fixture(); f.enterCapture(0);
        f.clock.ready.add(BenchmarkRunCaptureTest.sample(1)); f.clock.pending = 1;
        f.run.cancel(ms(150));
        assertFalse(f.clock.running); assertEquals(0, f.clock.pending);
        assertEquals(1, f.restoreRequests);
        assertEquals(BenchmarkScenarioRun.Verdict.CANCELLED, f.run.result().cases().getFirst().verdict());
        assertEquals(BenchmarkScenarioRun.Verdict.NOT_RUN, f.run.result().cases().get(1).verdict());
        assertThrows(IllegalStateException.class, () -> f.report("Cancelled"));
        f.restored = true; f.tick(160);
        var report = f.report("Cancelled");
        assertEquals(BenchmarkScenarioRun.Outcome.CANCELLED, f.run.result().outcome());
        assertEquals(1, report.windows().getFirst().samples().size());
        assertEquals(BenchmarkAnalysis.Validity.INVALID, report.windows().getFirst().validity());
        assertEquals(BenchmarkAnalysis.Availability.UNTESTED, report.windows().get(1).availability());
    }
}
