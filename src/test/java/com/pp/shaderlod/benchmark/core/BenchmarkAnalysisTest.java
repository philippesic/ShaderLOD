package com.pp.shaderlod.benchmark.core;

import com.pp.shaderlod.measurement.FrameMeasurement;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntToDoubleFunction;
import static com.pp.shaderlod.benchmark.core.BenchmarkAnalysis.*;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkAnalysisTest {
    static final AnalysisPolicy POLICY = new AnalysisPolicy(16, 16, 4, 3, 3,
            "synthetic fixture: independent repeat units; correlation confined to 16-frame blocks", 20);
    static final PracticalCriterion CRITERION = new PracticalCriterion(.2, .25, .5,
            "fixture target and return stability declared before generating samples");
    static final Stratum STRATUM = new Stratum(Map.of("case", "coast/day/still", "tuple", "fixture",
            "route", "v1", "device", "test", "clock", "total-frame", "observer", "coarse",
            "cache", "warm", "temporal", "native", "control", "same-state-no-reload", "warmth", "warm"));
    static List<FrameMeasurement.Sample> samples(int count, IntToDoubleFunction ms) {
        var result = new ArrayList<FrameMeasurement.Sample>();
        for (int i = 0; i < count; i++) result.add(sample(i, 0, (long)(ms.applyAsDouble(i) * 1e6)));
        return result;
    }
    static FrameMeasurement.Sample sample(long frame, long epoch, Long ns) {
        return new FrameMeasurement.Sample(frame, epoch, ns, 1, null, null, null, "GPU unavailable");
    }
    static BenchmarkStatistics.Summary stats(double ms) {
        return BenchmarkStatistics.summarize(samples(64, i -> ms), POLICY);
    }
    static Window window(String id, BenchmarkStatistics.Summary s) { return new Window(id, STRATUM, s); }
    static List<Bracket> brackets(BenchmarkStatistics.Summary before, BenchmarkStatistics.Summary candidate,
            BenchmarkStatistics.Summary returned) {
        var result = new ArrayList<Bracket>();
        for (int i = 0; i < 3; i++) result.add(new Bracket("b" + i, "independent-jvm-" + i,
                window("before" + i, before), window("candidate" + i, candidate), window("return" + i, returned)));
        return result;
    }
    static Request request(ControlKind kind, List<Bracket> brackets) {
        return new Request("comparison", kind, Availability.AVAILABLE, Activation.ACTIVE,
                brackets, CRITERION, POLICY, List.of());
    }
    @Test void calibratedKnownEffectRespondsWithNoConfidenceIntervalClaim() {
        var decision = analyze(request(ControlKind.KNOWN_EFFECT, brackets(stats(10), stats(8), stats(10))));
        assertEquals(Verdict.POSITIVE, decision.verdict());
        assertEquals(2, decision.gainMs(), 1e-9);
        assertEquals(2, decision.lowerEnvelopeMs(), 1e-9);
        assertEquals(3, decision.independentRepeatCount());
        assertTrue(decision.uncertaintyMethod().contains("no confidence coverage"));
        assertTrue(decision.reasons().getFirst().contains("not an optimization baseline"));
    }
    @Test void autocorrelatedNullIsInconclusiveInsteadOfFalsePrecision() {
        var a = BenchmarkStatistics.summarize(samples(64, i -> i / 16 % 2 == 0 ? 9 : 11), POLICY);
        var b = BenchmarkStatistics.summarize(samples(64, i -> i / 16 % 2 == 0 ? 11 : 9), POLICY);
        var d = analyze(request(ControlKind.OPTIMIZATION, brackets(a, b, a)));
        assertEquals(Verdict.INCONCLUSIVE, d.verdict());
        assertEquals(-2, d.lowerEnvelopeMs(), 1e-9);
        assertEquals(2, d.upperEnvelopeMs(), 1e-9);
    }
    @Test void driftRemainsInconclusiveEvenWithLargeApparentGain() {
        var d = analyze(request(ControlKind.OPTIMIZATION, brackets(stats(10), stats(5), stats(12))));
        assertEquals(Verdict.INCONCLUSIVE, d.verdict());
        assertEquals(Validity.VALID, d.validity());
        assertTrue(d.reasons().stream().anyMatch(s -> s.contains("return drift")));
    }
    @Test void twentyFourCorrelatedFramesDoNotProvideEnoughEffectiveEvidence() {
        var shortWindow = BenchmarkStatistics.summarize(samples(24, i -> 8), POLICY);
        var d = analyze(request(ControlKind.OPTIMIZATION, brackets(stats(10), shortWindow, stats(10))));
        assertEquals(Verdict.INCONCLUSIVE, d.verdict());
        assertTrue(d.reasons().stream().anyMatch(s -> s.contains("insufficient chronological blocks")));
    }
    @Test void manyFramesOrBracketsFromOneRepeatCannotReplaceIndependentRepeats() {
        var original = brackets(stats(10), stats(8), stats(10));
        var sameRepeat = original.stream().map(b -> new Bracket(b.id(), "one-jvm-one-unit", b.before(), b.candidate(), b.returned())).toList();
        var d = analyze(request(ControlKind.OPTIMIZATION, sameRepeat));
        assertEquals(Verdict.INCONCLUSIVE, d.verdict());
        assertEquals(1, d.independentRepeatCount());
    }
    @Test void knownNullNeverWinsAndExportsControlFailure() {
        var d = analyze(request(ControlKind.KNOWN_NULL, brackets(stats(10), stats(8), stats(10))));
        assertEquals(Verdict.INCONCLUSIVE, d.verdict());
        assertFalse(d.positive());
        assertTrue(d.reasons().stream().anyMatch(s -> s.contains("control failure")));
        assertNotEquals(Verdict.POSITIVE, analyze(request(ControlKind.KNOWN_NULL,
                brackets(stats(10), stats(10), stats(10)))).verdict());
    }
    @Test void missingClockInvalidIsDifferentFromValidNegativeAndUntested() {
        var missing = BenchmarkStatistics.summarize(List.of(sample(1, 0, null)), POLICY);
        var invalid = analyze(request(ControlKind.OPTIMIZATION, brackets(stats(10), missing, stats(10))));
        assertEquals(Verdict.INVALID, invalid.verdict()); assertEquals(Validity.INVALID, invalid.validity());
        var negative = analyze(request(ControlKind.OPTIMIZATION, brackets(stats(10), stats(12), stats(10))));
        assertEquals(Verdict.NEGATIVE, negative.verdict()); assertEquals(Validity.VALID, negative.validity());
        var untested = analyze(request(ControlKind.OPTIMIZATION, List.of()));
        assertEquals(Verdict.UNTESTED, untested.verdict()); assertEquals(Validity.UNTESTED, untested.validity());
    }
    @Test void missingIntervalsAndFrameIdGapsCannotHideSlowWork() {
        for (boolean explicitMissing : List.of(true, false)) {
            var rows = new ArrayList<FrameMeasurement.Sample>();
            for (int i = 0; i < 129; i++) {
                if (i == 64) { if (explicitMissing) rows.add(sample(i, 0, null)); }
                else rows.add(sample(i, 0, 8_000_000L));
            }
            var candidate = BenchmarkStatistics.summarize(rows, POLICY);
            assertTrue(candidate.blocks().size() >= POLICY.minimumBlocksPerWindow());
            var d = analyze(request(ControlKind.OPTIMIZATION, brackets(stats(10), candidate, stats(10))));
            assertEquals(Verdict.INVALID, d.verdict());
            assertTrue(d.reasons().stream().anyMatch(r -> r.contains("incomplete required total-frame coverage")));
        }
    }
    @Test void availabilityAndActivationAreSeparateFromEffect() {
        var absent = analyze(new Request("c", ControlKind.OPTIMIZATION, Availability.UNAVAILABLE,
                Activation.UNKNOWN, List.of(), CRITERION, POLICY, List.of()));
        assertEquals(Availability.UNAVAILABLE, absent.availability()); assertEquals(Verdict.UNAVAILABLE, absent.verdict());
        assertNull(absent.gainMs());
        var inactive = analyze(new Request("c", ControlKind.OPTIMIZATION, Availability.AVAILABLE,
                Activation.INACTIVE, List.of(), CRITERION, POLICY, List.of()));
        assertEquals(Verdict.UNTESTED, inactive.verdict()); assertEquals(Activation.INACTIVE, inactive.activation());
    }
    @Test void mixedStrataOrReusedWindowsAreInvalid() {
        var data = brackets(stats(10), stats(8), stats(10));
        var b = data.getFirst();
        var mismatch = new Bracket(b.id(), b.repeatId(), b.before(),
                new Window(b.candidate().id(), new Stratum(Map.of("case", "night")), stats(8)), b.returned());
        assertEquals(Verdict.INVALID, analyze(request(ControlKind.OPTIMIZATION, List.of(mismatch))).verdict());
        var reused = new Bracket("reused", "other", b.before(), b.candidate(), b.returned());
        assertEquals(Verdict.INVALID, analyze(request(ControlKind.OPTIMIZATION, List.of(b, reused))).verdict());
    }
    @Test void missingIndependenceCalibrationIsInconclusive() {
        var p = new AnalysisPolicy(16, 16, 4, 3, 3, "", 20);
        var r = request(ControlKind.OPTIMIZATION, brackets(stats(10), stats(8), stats(10)));
        // Policy identity itself is also checked, so summarize every window with this uncalibrated policy.
        var data = r.brackets().stream().map(b -> new Bracket(b.id(), b.repeatId(),
                window(b.before().id(), BenchmarkStatistics.summarize(samples(64, i -> 10), p)),
                window(b.candidate().id(), BenchmarkStatistics.summarize(samples(64, i -> 8), p)),
                window(b.returned().id(), BenchmarkStatistics.summarize(samples(64, i -> 10), p)))).toList();
        var d = analyze(new Request("c", ControlKind.OPTIMIZATION, Availability.AVAILABLE, Activation.ACTIVE,
                data, CRITERION, p, List.of()));
        assertEquals(Verdict.INCONCLUSIVE, d.verdict());
        assertTrue(d.reasons().stream().anyMatch(s -> s.contains("missing calibration")));
    }
    @Test void descriptiveP95ExcursionBlocksWinWithoutClaimingTailInference() {
        var candidate = BenchmarkStatistics.summarize(samples(64, i -> i % 16 == 0 ? 15 : 7), POLICY);
        var d = analyze(request(ControlKind.OPTIMIZATION, brackets(stats(10), candidate, stats(10))));
        assertEquals(Verdict.INCONCLUSIVE, d.verdict());
        assertTrue(d.reasons().stream().anyMatch(s -> s.contains("descriptive p95")));
    }
    @Test void p99RemainsDescriptiveAndCannotBeTheSoleDecisionGate() {
        var candidate = BenchmarkStatistics.summarize(samples(128, i -> i < 2 ? 15 : 5), POLICY);
        assertEquals(15, candidate.p99Ms());
        assertEquals(5, candidate.p95Ms());
        var d = analyze(request(ControlKind.OPTIMIZATION, brackets(stats(10), candidate, stats(10))));
        assertEquals(Verdict.POSITIVE, d.verdict());
        assertTrue(candidate.tailStatus().contains("descriptive-only"));
    }
    @Test void unblockedRemainderStillAffectsMeanAndCannotHideARegression() {
        var candidate = BenchmarkStatistics.summarize(samples(65, i -> i == 64 ? 1000 : 8), POLICY);
        assertEquals(1, candidate.unblockedValidCount());
        assertTrue(candidate.meanMs() > 10);
        var d = analyze(request(ControlKind.OPTIMIZATION, brackets(stats(10), candidate, stats(10))));
        assertNotEquals(Verdict.POSITIVE, d.verdict());
    }
    @Test void externalSettlementOrClockFailureCannotBecomeNegative() {
        var d = analyze(new Request("c", ControlKind.OPTIMIZATION, Availability.AVAILABLE, Activation.ACTIVE,
                brackets(stats(10), stats(12), stats(10)), CRITERION, POLICY,
                List.of("DH geometry changed during steady-state capture")));
        assertEquals(Verdict.INVALID, d.verdict());
        assertTrue(d.reasons().getFirst().contains("DH geometry"));
    }
}
