package com.pp.shaderlod.benchmark;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.pp.shaderlod.benchmark.BenchmarkReadinessTest.*;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkScenarioRunTest {
    static final class World implements BenchmarkScenarioRun.World {
        final List<String> applied = new ArrayList<>();
        boolean acknowledged = true, restored, routeStarted = true, routeComplete, failRestore;
        int restores, routeTicks;
        @Override public void apply(BenchmarkScenario.Case c) { applied.add(c.id()); }
        @Override public boolean acknowledged() { return acknowledged; }
        @Override public void beginRoute(int ticks) { routeTicks = ticks; }
        @Override public boolean routeStarted() { return routeStarted; }
        @Override public boolean routeComplete() { return routeComplete; }
        @Override public void restore() { restores++; if (failRestore) throw new IllegalStateException("recovery failed"); }
        @Override public boolean restored() { return restored; }
    }
    static final class Capture implements BenchmarkScenarioRun.Capture {
        int starts, stops;
        boolean drained = true, failStart, failStop;
        @Override public void startCapture(BenchmarkScenario.Case c) {
            starts++;
            if (failStart) throw new IllegalStateException("partially started recorder");
        }
        @Override public void stopCapture(BenchmarkScenario.Case c) {
            stops++;
            if (failStop) throw new IllegalStateException("recorder still uncertain");
        }
        @Override public boolean drained() { return drained; }
    }
    static BenchmarkScenario.Schedule schedule(BenchmarkScenario.Case... cases) {
        return new BenchmarkScenario.Schedule("test", List.of(cases), Duration.ofSeconds(30), Duration.ofSeconds(2));
    }
    static BenchmarkScenarioRun run(World world, Capture capture, BenchmarkScenario.Case... cases) {
        return new BenchmarkScenarioRun(schedule(cases), world, capture, ignored -> POLICY, 0);
    }
    static void warm(BenchmarkScenarioRun run, BenchmarkScenario.Case c) {
        run.tick(0, null); // apply exactly once
        run.tick(ms(10), null); // acknowledged boundary
        for (int t : new int[]{20, 120, 220})
            run.tick(ms(t), observation(c, t, BenchmarkReadiness.Signal.READY, GEOMETRY));
    }

    @Test void completeCaseDrainsThenRestoresAndVerifiesBeforeRelease() {
        var c = steady(); var world = new World(); var capture = new Capture(); var run = run(world, capture, c);
        warm(run, c);
        assertEquals(BenchmarkScenarioRun.Stage.CAPTURE, run.result().stage());
        assertEquals(1, capture.starts);
        for (int t = 320; t <= 3220; t += 100)
            run.tick(ms(t), observation(c, t, BenchmarkReadiness.Signal.READY, GEOMETRY));
        assertEquals(BenchmarkScenarioRun.Stage.DRAIN, run.result().stage());
        assertEquals(1, capture.stops);
        assertEquals(0, world.restores);
        run.tick(ms(3230), null);
        assertEquals(BenchmarkScenarioRun.Stage.RESTORE, run.result().stage());
        assertFalse(run.result().restorationVerified());
        world.restored = true;
        var done = run.tick(ms(3240), null);
        assertEquals(BenchmarkScenarioRun.Outcome.COMPLETE, done.outcome());
        assertTrue(done.restorationVerified());
        assertEquals(BenchmarkScenarioRun.Verdict.COMPLETE, done.cases().getFirst().verdict());
        assertEquals(List.of(c.id()), world.applied);
    }
    @Test void cancelStopsRecorderAndPreservesUntestedCasesUntilVerifiedRestoration() {
        var c = steady(); var next = BenchmarkScenario.fast(DH).cases().get(1);
        var world = new World(); var capture = new Capture(); var run = run(world, capture, c, next);
        warm(run, c);
        var cancel = run.cancel(ms(230));
        assertEquals(BenchmarkScenarioRun.Stage.RESTORE, cancel.stage());
        assertEquals(1, capture.stops);
        assertEquals(1, world.restores);
        assertEquals(BenchmarkScenarioRun.Verdict.CANCELLED, cancel.cases().getFirst().verdict());
        assertEquals(BenchmarkScenarioRun.Verdict.NOT_RUN, cancel.cases().get(1).verdict());
        assertEquals(BenchmarkReadiness.State.CANCELLED, cancel.cases().getFirst().readiness().state());
        world.restored = true;
        assertEquals(BenchmarkScenarioRun.Outcome.CANCELLED, run.tick(ms(240), null).outcome());
        run.cancel(ms(250));
        assertEquals(1, world.restores);
    }
    @Test void cancelBeforeFirstMutationStillHandsOwnedWorldBackForRestoration() {
        var world = new World(); var capture = new Capture(); var run = run(world, capture, steady());
        run.cancel(0);
        assertTrue(world.applied.isEmpty());
        assertEquals(1, world.restores);
        assertEquals(0, capture.starts);
    }
    @Test void missingCapabilityAbortsWithoutStartingMeasurement() {
        var c = steady(); var world = new World(); var capture = new Capture(); var run = run(world, capture, c);
        run.tick(0, null); run.tick(ms(10), null);
        var unavailable = run.tick(ms(20), observation(c, 20, BenchmarkReadiness.Signal.UNAVAILABLE, null));
        assertEquals(BenchmarkScenarioRun.Verdict.UNAVAILABLE, unavailable.cases().getFirst().verdict());
        assertEquals(BenchmarkScenarioRun.Stage.RESTORE, unavailable.stage());
        assertEquals(0, capture.starts);
        world.restored = true;
        assertEquals(BenchmarkScenarioRun.Outcome.PARTIAL, run.tick(ms(30), null).outcome());
    }
    @Test void unavailableCaseDoesNotDiscardOtherScenarioCoverage() {
        var c = steady(); var next = BenchmarkScenario.fast(DH).cases().get(1);
        var world = new World(); var capture = new Capture(); var run = run(world, capture, c, next);
        run.tick(0, null); run.tick(ms(10), null);
        var partial = run.tick(ms(20), observation(c, 20, BenchmarkReadiness.Signal.UNAVAILABLE, null));
        assertEquals(BenchmarkScenarioRun.Verdict.UNAVAILABLE, partial.cases().getFirst().verdict());
        assertEquals(BenchmarkScenarioRun.Stage.APPLY, partial.stage());
        assertEquals(0, capture.starts); assertEquals(0, world.restores);
        run.tick(ms(30), null); run.tick(ms(40), null);
        for (int t : new int[]{50, 150, 250})
            run.tick(ms(t), observation(next, t, BenchmarkReadiness.Signal.READY, GEOMETRY));
        assertEquals(BenchmarkScenarioRun.Stage.CAPTURE, run.result().stage());
        assertEquals(List.of(c.id(), next.id()), world.applied);
        assertEquals(1, capture.starts);
    }
    @Test void acknowledgementAndRecoveryDeadlinesAreFinite() {
        var world = new World(); world.acknowledged = false;
        var run = run(world, new Capture(), steady());
        run.tick(0, null);
        var timeout = run.tick(ms(3000), null);
        assertEquals(BenchmarkScenarioRun.Verdict.TIMED_OUT, timeout.cases().getFirst().verdict());
        assertEquals(BenchmarkScenarioRun.Stage.RESTORE, timeout.stage());
        var recovery = run.tick(ms(5000), null);
        assertEquals(BenchmarkScenarioRun.Outcome.RESTORATION_FAILED, recovery.outcome());
        assertFalse(recovery.restorationVerified());
    }
    @Test void unchangedStateDoesNotHideStoppedObservationStream() {
        var c = steady(); var world = new World(); var capture = new Capture(); var run = run(world, capture, c);
        warm(run, c);
        var failed = run.tick(ms(400), null);
        assertEquals(BenchmarkScenarioRun.Verdict.INVALID, failed.cases().getFirst().verdict());
        assertEquals(1, capture.stops);
        assertEquals(1, world.restores);
    }
    @Test void preparedRouteAcknowledgesMotionAndWaitsForActualRouteCompletion() {
        var c = BenchmarkScenario.fast(DH).cases().getLast();
        var world = new World(); var capture = new Capture(); var run = run(world, capture, c);
        warm(run, c);
        assertEquals(BenchmarkScenarioRun.Stage.ROUTE_ACKNOWLEDGE, run.result().stage());
        assertEquals(240, world.routeTicks);
        assertEquals(0, capture.starts);
        run.tick(ms(230), null);
        assertEquals(1, capture.starts);
        run.tick(ms(300), observation(c, 300, BenchmarkReadiness.Signal.BUSY,
                new BenchmarkReadiness.Geometry(20, 600, 2)));
        assertEquals(BenchmarkScenarioRun.Stage.CAPTURE, run.result().stage());
        world.routeComplete = true;
        run.tick(ms(400), observation(c, 400, BenchmarkReadiness.Signal.BUSY, GEOMETRY));
        assertEquals(BenchmarkScenarioRun.Stage.DRAIN, run.result().stage());
    }
    @Test void partiallyStartedRecorderAndFailedRestorationNeverReportSuccess() {
        var c = steady(); var world = new World(); world.failRestore = true;
        var capture = new Capture(); capture.failStart = true;
        var run = run(world, capture, c);
        warm(run, c);
        assertEquals(BenchmarkScenarioRun.Outcome.RESTORATION_FAILED, run.result().outcome());
        assertFalse(run.result().restorationVerified());
        assertEquals(1, capture.stops);
        assertEquals(BenchmarkScenarioRun.Verdict.FAILED, run.result().cases().getFirst().verdict());
    }
    @Test void scheduleCutoffReservesRecoveryAndLeavesRemainingCoverageUntested() {
        var c = steady(); var next = BenchmarkScenario.fast(DH).cases().get(1);
        var world = new World(); var capture = new Capture();
        var shortSchedule = new BenchmarkScenario.Schedule("short", List.of(c, next),
                Duration.ofSeconds(4), Duration.ofSeconds(2));
        var run = new BenchmarkScenarioRun(shortSchedule, world, capture, ignored -> POLICY, 0);
        warm(run, c);
        var timeout = run.tick(ms(2000), null);
        assertEquals(BenchmarkScenarioRun.Verdict.TIMED_OUT, timeout.cases().getFirst().verdict());
        assertEquals(BenchmarkScenarioRun.Verdict.NOT_RUN, timeout.cases().get(1).verdict());
        assertEquals(1, capture.stops);
        assertEquals(1, world.restores);
    }

    @Test void uncertainRecorderStopCannotReleaseRecoveryOwnership() {
        var c = steady(); var world = new World(); world.restored = true;
        var capture = new Capture(); capture.failStop = true;
        var run = run(world, capture, c);
        warm(run, c);
        var failed = run.cancel(ms(230));
        assertEquals(BenchmarkScenarioRun.Outcome.RESTORATION_FAILED, failed.outcome());
        assertFalse(failed.restorationVerified());
        assertEquals(1, world.restores);
        assertEquals(1, capture.stops);
    }
}
