package com.pp.shaderlod.benchmark;

import com.pp.shaderlod.benchmark.mixin.BenchmarkObservationPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static com.pp.shaderlod.benchmark.BenchmarkReadiness.*;
import static com.pp.shaderlod.benchmark.BenchmarkScenario.Work;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkLiveObservationsTest {
    @AfterEach void stop() { BenchmarkLiveWork.disable(); }
    static BenchmarkLiveObservations.ViewFacts facts(boolean water) {
        return new BenchmarkLiveObservations.ViewFacts(true, true, water, false, true, true, true, false);
    }
    @Test void classificationsRequireFactsAndMatchingNativeWorkRatherThanRequestedCaseLabels() {
        var f = BenchmarkLiveWorkTest.frame(0, 0, 12, 360, 60);
        var evidence = BenchmarkLiveObservations.classify(f, true, facts(true));
        assertEquals(12, evidence.get(Work.TERRAIN).executed().orElseThrow());
        assertEquals(0, evidence.get(Work.WATER).executed().orElseThrow()); // no translucent dispatch
        assertEquals(Eligibility.UNAVAILABLE, evidence.get(Work.UNDERWATER).eligibility());
        assertEquals(Eligibility.UNAVAILABLE, evidence.get(Work.PBR).eligibility());
        assertTrue(evidence.get(Work.PBR).executed().isEmpty());
        assertEquals(Eligibility.UNAVAILABLE, evidence.get(Work.DISTANT_TERRAIN).eligibility());
        assertEquals(1, evidence.get(Work.DH_TERRAIN).executed().orElseThrow());
        assertEquals(Eligibility.UNAVAILABLE, BenchmarkLiveObservations.classify(f, false, facts(true)).get(Work.TERRAIN).eligibility());
    }
    @Test void shadowOnlySubmissionsCannotBecomeWorldCoverage() {
        BenchmarkLiveWork.hooksInstalled(true, true, true);
        BenchmarkLiveWork.beginFrame(true); BenchmarkLiveWork.enterLayer(true);
        BenchmarkLiveWork.sodiumSubmitted(10, 300, true); BenchmarkLiveWork.leaveLayer();
        BenchmarkLiveWork.dhRendererEntered(); BenchmarkLiveWork.dhSubmitted(100, true, true);
        BenchmarkLiveWork.endFrame(0, 0, new Object(), new Object(), 0);
        var e = BenchmarkLiveObservations.classify(BenchmarkLiveWork.latest(), true, facts(true));
        assertEquals(0, e.get(Work.TERRAIN).executed().orElseThrow());
        assertEquals(0, e.get(Work.WATER).executed().orElseThrow());
        assertEquals(0, e.get(Work.DH_TERRAIN).executed().orElseThrow());
    }
    @Test void queuesIncludeRunningTasksAndUnknownDoesNotBecomeDrained() {
        assertEquals(Signal.READY, BenchmarkLiveObservations.queueSignal(0, 0));
        assertEquals(Signal.BUSY, BenchmarkLiveObservations.queueSignal(0, 1));
        assertEquals(Signal.BUSY, BenchmarkLiveObservations.queueSignal(1, 0));
        assertEquals(Signal.UNAVAILABLE, BenchmarkLiveObservations.queueSignal(-1, 0));
    }
    @Test void nativePreRollRequiresClockProgressStableIdentityAndTimeThenRestartsOnReloadOrOwnerChange() {
        var policy = new BenchmarkLiveObservations.NativePreRoll(2, 100, 200);
        var f = BenchmarkLiveWorkTest.frame(0, 0, 1, 30, 60);
        assertEquals(Signal.BUSY, policy.observe("case", f, 0, true, f.pipeline(), f.source(), 0));
        assertEquals(Signal.BUSY, policy.observe("case", f, 100, true, f.pipeline(), f.source(), 0)); // no progress
        f = BenchmarkLiveWorkTest.frame(100, 2, 1, 30, 60);
        assertEquals(Signal.READY, policy.observe("case", f, 100, true, f.pipeline(), f.source(), 0));
        assertEquals(Signal.BUSY, policy.observe("case", f, 101, true, f.pipeline(), f.source(), 1));
        BenchmarkLiveWork.disable();
        f = BenchmarkLiveWorkTest.frame(110, 3, 1, 30, 60);
        assertEquals(Signal.BUSY, policy.observe("case", f, 110, true, f.pipeline(), f.source(), 0));
        assertEquals(Signal.BUSY, policy.observe("case", f, 400, true, f.pipeline(), f.source(), 0)); // stale
    }
    @Test void stoppedOrResetNativeClockAndPipelineChangesCannotRetainTemporalReadiness() {
        var policy = new BenchmarkLiveObservations.NativePreRoll(2, 100, 200);
        var f = BenchmarkLiveWorkTest.frame(0, 10, 1, 30, 60);
        policy.observe("case", f, 0, true, f.pipeline(), f.source(), 0);
        f = BenchmarkLiveWorkTest.frame(100, 12, 1, 30, 60);
        assertEquals(Signal.READY, policy.observe("case", f, 100, true, f.pipeline(), f.source(), 0));
        f = BenchmarkLiveWorkTest.frame(110, 12, 1, 30, 60);
        assertEquals(Signal.BUSY, policy.observe("case", f, 110, true, f.pipeline(), f.source(), 0));
        f = BenchmarkLiveWorkTest.frame(120, 0, 1, 30, 60);
        assertEquals(Signal.BUSY, policy.observe("case", f, 120, true, f.pipeline(), f.source(), 0));
        assertEquals(Signal.BUSY, policy.observe("case", f, 121, true, new Object(), f.source(), 0));
    }
    @Test void liveLastFrameGeometryContinuesToInvalidateSettledDhCapture() {
        var settings = new BenchmarkScenario.Settings(12, 1920, 1200, BenchmarkScenario.DhState.ENABLED);
        var original = BenchmarkScenario.fast(settings).cases().getFirst();
        var c = new BenchmarkScenario.Case("dh", 0, original.environment(), settings,
                original.workload(), 0, java.util.Set.of(Work.TERRAIN, Work.DH_TERRAIN), original.budgets());
        var gate = new BenchmarkReadiness(c, new Policy(Duration.ofNanos(1), Duration.ofNanos(2),
                Duration.ofNanos(100), Duration.ofNanos(1000), 3, 0, 0), 0);
        for (int i = 0; i <= 2; i++) {
            var f = BenchmarkLiveWorkTest.frame(i, i, 1, 30, 60);
            gate.observe(new Observation(i, true, settings, Signal.READY, Signal.READY, Signal.READY,
                    Signal.READY, settings.dh(), Signal.READY, Signal.READY,
                    new Geometry(f.dhDraws(), f.dhIndices(), f.dhFingerprint()), BenchmarkLiveObservations.classify(f, true, facts(false))));
        }
        assertEquals(State.READY, gate.result().state());
        gate.beginCapture(2);
        var changed = BenchmarkLiveWorkTest.frame(3, 3, 1, 30, 90);
        assertEquals(State.INVALID, gate.observe(new Observation(3, true, settings, Signal.READY, Signal.READY,
                Signal.READY, Signal.READY, settings.dh(), Signal.READY, Signal.READY,
                new Geometry(changed.dhDraws(), changed.dhIndices(), changed.dhFingerprint()),
                BenchmarkLiveObservations.classify(changed, true, facts(false)))).state());
    }
    @Test void pinnedAdmissionRefusesOtherVersionsAndAbsentDhDoesNotDisableSodiumObserver() {
        assertTrue(BenchmarkObservationPlugin.admitted("0.6.13+mc1.21.1", "1.8.12-snapshot+mc1.21.1-local", "", false));
        assertTrue(BenchmarkObservationPlugin.admitted("0.6.13+mc1.21.1", "1.8.12-snapshot+mc1.21.1-local", "3.3.3", true));
        assertFalse(BenchmarkObservationPlugin.admitted("0.6.13+mc1.21.1", "1.8.12-snapshot+mc1.21.1-local", "3.3.4", true));
        assertFalse(BenchmarkObservationPlugin.admitted("0.7.0", "1.8.12-snapshot+mc1.21.1-local", "3.3.3", false));
    }

    @Test void absentAndDisabledDhCanSettleFromActualWorldSubmissionsWithoutAnyDhRenderer() {
        for (var dh : new BenchmarkScenario.DhState[]{BenchmarkScenario.DhState.ABSENT,
                BenchmarkScenario.DhState.INSTALLED_DISABLED}) {
            var settings = new BenchmarkScenario.Settings(12, 1920, 1200, dh);
            var c = BenchmarkScenario.fast(settings).cases().getFirst();
            var gate = new BenchmarkReadiness(c, new Policy(Duration.ofNanos(1), Duration.ofNanos(2),
                    Duration.ofNanos(100), Duration.ofNanos(1000), 3, 0, 0), 0);
            for (int i = 0; i <= 2; i++) {
                BenchmarkLiveWork.hooksInstalled(true, true, false);
                BenchmarkLiveWork.beginFrame(true); BenchmarkLiveWork.enterLayer(false);
                BenchmarkLiveWork.sodiumSubmitted(5, 150, false); BenchmarkLiveWork.leaveLayer();
                BenchmarkLiveWork.endFrame(i, i, new Object(), new Object(), 0);
                var f = BenchmarkLiveWork.latest();
                assertFalse(f.dhRendererEntered());
                gate.observe(new Observation(i, true, settings, Signal.READY, Signal.READY,
                        Signal.READY, Signal.READY, dh, Signal.UNAVAILABLE, Signal.UNAVAILABLE,
                        null, BenchmarkLiveObservations.classify(f, true, facts(false))));
            }
            assertEquals(State.READY, gate.result().state());
        }
    }

    @Test void waterNeedsActualTranslucentDispatchAndVerifiedWaterInView() {
        BenchmarkLiveWork.hooksInstalled(true, true, false);
        BenchmarkLiveWork.beginFrame(true); BenchmarkLiveWork.enterLayer(true);
        BenchmarkLiveWork.sodiumSubmitted(3, 90, false); BenchmarkLiveWork.leaveLayer();
        BenchmarkLiveWork.endFrame(1, 1, new Object(), new Object(), 0);
        var f = BenchmarkLiveWork.latest();
        assertEquals(3, BenchmarkLiveObservations.classify(f, true, facts(true)).get(Work.WATER).executed().orElseThrow());
        assertEquals(Eligibility.UNAVAILABLE, BenchmarkLiveObservations.classify(f, true, facts(false)).get(Work.WATER).eligibility());
    }
}
