package com.pp.shaderlod.benchmark;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalLong;

import static com.pp.shaderlod.benchmark.BenchmarkReadiness.*;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkReadinessTest {
    static final BenchmarkScenario.Settings DH = new BenchmarkScenario.Settings(12, 1920, 1200,
            BenchmarkScenario.DhState.ENABLED);
    static final Policy POLICY = new Policy(Duration.ofMillis(100), Duration.ofMillis(200),
            Duration.ofMillis(150), Duration.ofSeconds(1), 3, 0, 0);
    static long ms(long value) { return value * 1_000_000; }
    static BenchmarkScenario.Case steady() { return BenchmarkScenario.fast(DH).cases().getFirst(); }
    static Map<BenchmarkScenario.Work, WorkEvidence> work(BenchmarkScenario.Case c, long executed) {
        var out = new EnumMap<BenchmarkScenario.Work, WorkEvidence>(BenchmarkScenario.Work.class);
        c.requestedWork().forEach(w -> out.put(w,
                new WorkEvidence(Eligibility.ELIGIBLE, OptionalLong.of(executed), "injected test scope")));
        return out;
    }
    static Observation observation(BenchmarkScenario.Case c, long atMs, Signal queue, Geometry geometry) {
        return new Observation(ms(atMs), true, c.settings(), Signal.READY, Signal.READY,
                Signal.READY, Signal.READY, c.settings().dh(), queue, queue, geometry, work(c, 1));
    }
    static final Geometry GEOMETRY = new Geometry(10, 300, 1);
    static BenchmarkReadiness settled(BenchmarkScenario.Case c) {
        var gate = new BenchmarkReadiness(c, POLICY, 0);
        gate.observe(observation(c, 0, Signal.READY, GEOMETRY));
        gate.observe(observation(c, 100, Signal.READY, GEOMETRY));
        assertEquals(State.READY, gate.observe(observation(c, 200, Signal.READY, GEOMETRY)).state());
        return gate;
    }

    @Test void loadingRequiresQueuesAndObservedStabilityRatherThanFirstFrameOrDwell() {
        var c = steady();
        var gate = new BenchmarkReadiness(c, POLICY, 0);
        assertEquals(State.WARMING, gate.observe(observation(c, 0, Signal.BUSY, GEOMETRY)).state());
        assertThrows(IllegalStateException.class, () -> gate.beginCapture(0));
        assertEquals(State.WARMING, gate.observe(observation(c, 100, Signal.READY, GEOMETRY)).state());
        assertEquals(State.WARMING, gate.observe(observation(c, 200, Signal.READY, GEOMETRY)).state());
        assertEquals(State.READY, gate.observe(observation(c, 300, Signal.READY, GEOMETRY)).state());
    }
    @Test void postWarmGeometryChangeRestartsWarmAndInvalidatesCapture() {
        var c = steady();
        var gate = settled(c);
        assertEquals(State.WARMING, gate.observe(observation(c, 300, Signal.READY,
                new Geometry(11, 300, 1))).state());
        gate = settled(c);
        gate.beginCapture(ms(200));
        assertEquals(State.INVALID, gate.observe(observation(c, 300, Signal.READY,
                new Geometry(10, 303, 1))).state());
    }
    @Test void drainedQueuesWithChangingGeometryNeverSettle() {
        var c = steady();
        var gate = new BenchmarkReadiness(c, POLICY, 0);
        for (int i = 0; i < 10; i++) assertEquals(State.WARMING,
                gate.observe(observation(c, i * 100, Signal.READY, new Geometry(10 + i, 300 + i, i))).state());
        assertEquals(State.TIMED_OUT, gate.tick(ms(1000)).state());
    }
    @Test void aGeometryGenerationChangeInvalidatesEvenIdenticalCounts() {
        var c = steady(); var gate = settled(c); gate.beginCapture(ms(200));
        assertEquals(State.INVALID, gate.observe(observation(c, 300, Signal.READY,
                new Geometry(10, 300, 2))).state());
    }
    @Test void aQueueReopeningInvalidatesSteadyCapture() {
        var c = steady(); var gate = settled(c); gate.beginCapture(ms(200));
        assertEquals(State.INVALID, gate.observe(observation(c, 300, Signal.BUSY, GEOMETRY)).state());
    }
    @Test void missingDhQueueOrWorkCapabilitiesAreUnavailableNotZeroWork() {
        var c = steady(); var gate = new BenchmarkReadiness(c, POLICY, 0);
        assertEquals(State.UNAVAILABLE, gate.observe(observation(c, 0, Signal.UNAVAILABLE, GEOMETRY)).state());
        gate = new BenchmarkReadiness(c, POLICY, 0);
        var o = observation(c, 0, Signal.READY, GEOMETRY);
        assertEquals(State.UNAVAILABLE, gate.observe(new Observation(0, true, DH, o.meshing(), o.lighting(),
                o.pipeline(), o.temporalHistory(), DH.dh(), Signal.READY, Signal.READY, GEOMETRY, Map.of())).state());
    }
    @Test void enabledDhWithoutActuallyDrawnTerrainCannotSettle() {
        var c = steady(); var gate = new BenchmarkReadiness(c, POLICY, 0);
        assertEquals(State.WARMING, gate.observe(observation(c, 0, Signal.READY, new Geometry(0, 0, 0))).state());
        assertEquals(State.TIMED_OUT, gate.tick(ms(1000)).state());
    }
    @Test void absentAndInstalledDisabledAreDifferentAndDoNotNeedDhQueues() {
        for (var dh : new BenchmarkScenario.DhState[]{BenchmarkScenario.DhState.ABSENT,
                BenchmarkScenario.DhState.INSTALLED_DISABLED}) {
            var c = BenchmarkScenario.fast(new BenchmarkScenario.Settings(12, 1920, 1200, dh)).cases().getFirst();
            var gate = new BenchmarkReadiness(c, POLICY, 0);
            for (int at : new int[]{0, 100, 200}) gate.observe(observation(c, at, Signal.UNAVAILABLE, null));
            assertEquals(State.READY, gate.result().state());
        }
        var c = steady(); var gate = new BenchmarkReadiness(c, POLICY, 0);
        assertEquals(State.UNAVAILABLE, gate.observe(new Observation(0, true, DH, Signal.READY, Signal.READY,
                Signal.READY, Signal.READY, BenchmarkScenario.DhState.ABSENT,
                Signal.UNAVAILABLE, Signal.UNAVAILABLE, null, work(c, 1))).state());
    }
    @Test void noObservationsTimeoutAndStaleCaptureEvidenceInvalidates() {
        assertEquals(State.TIMED_OUT, new BenchmarkReadiness(steady(), POLICY, 0).tick(ms(1000)).state());
        assertEquals(State.INVALID, settled(steady()).beginCapture(ms(351)).state());
        var gate = settled(steady()); gate.beginCapture(ms(200));
        assertEquals(State.INVALID, gate.tick(ms(351)).state());
    }
    @Test void streamingPermitsIntendedChangesAndCollectsExecutedRouteCoverage() {
        var c = BenchmarkScenario.fast(DH).cases().getLast();
        var gate = settled(c); gate.beginCapture(ms(200));
        var moving = new Geometry(20, 900, 2);
        assertEquals(State.CAPTURING, gate.observe(observation(c, 300, Signal.BUSY, moving)).state());
        // Native route movement can remove all requested geometry from a later frustum.
        var o = observation(c, 400, Signal.BUSY, new Geometry(0, 0, 3));
        gate.observe(new Observation(o.atNanos(), true, DH, Signal.BUSY, Signal.BUSY,
                Signal.READY, Signal.READY, DH.dh(), Signal.BUSY, Signal.BUSY, o.dhGeometry(), work(c, 0)));
        assertEquals(State.COMPLETE, gate.complete(ms(400)).state());
        assertEquals(BenchmarkScenario.Workload.STREAMING, gate.result().workload());
        assertEquals(ms(200), gate.result().stableNanos());
        assertTrue(gate.result().coverage().stream().allMatch(w -> w.executed().orElseThrow() == 1));
    }
    @Test void routeCoverageNotExecutedDuringCaptureIsInvalidEvenIfWarmupSawIt() {
        var c = BenchmarkScenario.fast(DH).cases().getLast();
        var gate = settled(c); gate.beginCapture(ms(200));
        var o = observation(c, 300, Signal.READY, GEOMETRY);
        gate.observe(new Observation(o.atNanos(), true, DH, o.meshing(), o.lighting(), o.pipeline(),
                o.temporalHistory(), DH.dh(), o.dhLoading(), o.dhConversion(), GEOMETRY, work(c, 0)));
        assertEquals(State.INVALID, gate.complete(ms(300)).state());
    }
    @Test void cannotCertifyAnUnobservedCaptureOrReuseATimestamp() {
        var gate = settled(steady()); gate.beginCapture(ms(200));
        assertEquals(State.INVALID, gate.complete(ms(200)).state());
        var another = settled(steady());
        assertThrows(IllegalArgumentException.class,
                () -> another.observe(observation(steady(), 200, Signal.READY, GEOMETRY)));
        another.tick(ms(250));
        assertThrows(IllegalArgumentException.class,
                () -> another.observe(observation(steady(), 240, Signal.READY, GEOMETRY)));
    }
    @Test void cancelIsTerminalAndReportsRequestedUntestedCoverage() {
        var gate = new BenchmarkReadiness(steady(), POLICY, 0);
        assertEquals(State.CANCELLED, gate.cancel().state());
        assertTrue(gate.result().coverage().stream().allMatch(w -> w.eligible() == Eligibility.UNKNOWN
                && w.executed().isEmpty()));
        assertEquals(State.CANCELLED, gate.tick(ms(2000)).state());
    }

    @Test void toleranceIsAnchoredRatherThanAllowingUnboundedGradualDrift() {
        var c = steady();
        var policy = new Policy(Duration.ofMillis(100), Duration.ofMillis(200),
                Duration.ofMillis(150), Duration.ofSeconds(1), 3, 2, 0);
        var gate = new BenchmarkReadiness(c, policy, 0);
        gate.observe(observation(c, 0, Signal.READY, new Geometry(10, 300, 1)));
        gate.observe(observation(c, 100, Signal.READY, new Geometry(11, 300, 1)));
        assertEquals(State.READY, gate.observe(observation(c, 200, Signal.READY, new Geometry(12, 300, 1))).state());
        assertEquals(State.WARMING, gate.observe(observation(c, 300, Signal.READY, new Geometry(13, 300, 1))).state());
    }

    @Test void materialPanelWithoutPbrActivationEvidenceIsUnavailable() {
        var c = BenchmarkScenario.pbrDiagnostic(DH);
        var observed = work(c, 1);
        observed.remove(BenchmarkScenario.Work.PBR);
        var o = observation(c, 0, Signal.READY, GEOMETRY);
        var gate = new BenchmarkReadiness(c, POLICY, 0);
        assertEquals(State.UNAVAILABLE, gate.observe(new Observation(0, true, DH, o.meshing(), o.lighting(),
                o.pipeline(), o.temporalHistory(), DH.dh(), o.dhLoading(), o.dhConversion(), GEOMETRY, observed)).state());
    }

    @Test void settingsAndCameraAcknowledgementRemainCaptureValidityConditions() {
        var c = steady(); var gate = settled(c); gate.beginCapture(ms(200));
        var wrongResolution = new BenchmarkScenario.Settings(12, 2560, 1600, DH.dh());
        var o = observation(c, 300, Signal.READY, GEOMETRY);
        assertEquals(State.INVALID, gate.observe(new Observation(o.atNanos(), true, wrongResolution,
                o.meshing(), o.lighting(), o.pipeline(), o.temporalHistory(), DH.dh(),
                o.dhLoading(), o.dhConversion(), GEOMETRY, o.work())).state());
        gate = settled(c); gate.beginCapture(ms(200));
        assertEquals(State.INVALID, gate.observe(new Observation(o.atNanos(), false, DH,
                o.meshing(), o.lighting(), o.pipeline(), o.temporalHistory(), DH.dh(),
                o.dhLoading(), o.dhConversion(), GEOMETRY, o.work())).state());
    }
}
