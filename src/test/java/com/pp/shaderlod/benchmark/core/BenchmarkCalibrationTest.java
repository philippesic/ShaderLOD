package com.pp.shaderlod.benchmark.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static com.pp.shaderlod.benchmark.core.BenchmarkAnalysis.*;
import static com.pp.shaderlod.benchmark.core.BenchmarkAnalysisTest.*;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkCalibrationTest {
    static List<Bracket> sessionBrackets(double before, double candidate, double returned) {
        return brackets(stats(before), stats(candidate), stats(returned)).stream()
                .map(b -> new Bracket(b.id(), "actual-session", b.before(), b.candidate(), b.returned())).toList();
    }
    @Test void boundedRealisticNullCalibratesOneSessionWithoutInventingIndependentJvms() {
        var p = new AnalysisPolicy(16, 16, 4, 1, 1, "", 20);
        var nulls = new ArrayList<Bracket>();
        // Slowly varying within-window blocks and small phase offset, not 24 IID frames.
        for (int k = 0; k < 2; k++) {
            var a = BenchmarkStatistics.summarize(samples(128, i -> 10 + (i / 16 % 2 == 0 ? -.02 : .02)), p);
            var b = BenchmarkStatistics.summarize(samples(128, i -> 10 + (i / 16 % 2 == 0 ? .02 : -.02)), p);
            nulls.add(new Bracket("null" + k, "actual-session", window("a" + k, a), window("b" + k, b), window("r" + k, a)));
        }
        var receipt = BenchmarkCalibration.withinSession("null-receipt", "actual-session", nulls, p, CRITERION);
        assertEquals(BenchmarkCalibration.Status.PASS, receipt.status());
        assertEquals(1, receipt.exploratoryPolicy().minimumIndependentRepeats());
        var d = analyze(new Request("screen", ControlKind.KNOWN_EFFECT, Availability.AVAILABLE, Activation.ACTIVE,
                List.of(sessionBrackets(10, 8, 10).getFirst()), CRITERION, receipt.exploratoryPolicy(), List.of()));
        assertEquals(Verdict.POSITIVE, d.verdict());
        assertEquals(1, d.independentRepeatCount());
        assertEquals(AnalysisPolicy.InferenceScope.WITHIN_SESSION, d.inferenceScope());
        assertTrue(d.assumptions().stream().anyMatch(s -> s.contains("cross-session effects remain unqualified")));
    }
    @Test void oneBracketWithoutCalibrationIsStillInconclusive() {
        var p = new AnalysisPolicy(16, 16, 4, 1, 1, "", 20);
        var d = analyze(new Request("screen", ControlKind.OPTIMIZATION, Availability.AVAILABLE, Activation.ACTIVE,
                List.of(sessionBrackets(10, 8, 10).getFirst()), CRITERION, p, List.of()));
        assertEquals(Verdict.INCONCLUSIVE, d.verdict());
    }
    @Test void noisyOrDriftingNullCannotCalibrateExploratoryWin() {
        var p = new AnalysisPolicy(16, 16, 4, 1, 1, "", 20);
        var noisy = BenchmarkCalibration.withinSession("noisy", "actual-session", sessionBrackets(10, 8, 10), p, CRITERION);
        assertEquals(BenchmarkCalibration.Status.FAILED, noisy.status()); assertNull(noisy.exploratoryPolicy());
        var drift = BenchmarkCalibration.withinSession("drift", "actual-session", sessionBrackets(10, 10, 11), p, CRITERION);
        assertNotEquals(BenchmarkCalibration.Status.PASS, drift.status()); assertNull(drift.exploratoryPolicy());
    }
    @Test void oneNullBracketOrFakeJvmIdsDoNotCalibrate() {
        var p = new AnalysisPolicy(16, 16, 4, 1, 1, "", 20);
        assertEquals(BenchmarkCalibration.Status.INCONCLUSIVE,
                BenchmarkCalibration.withinSession("one", "actual-session", List.of(sessionBrackets(10, 10, 10).getFirst()), p, CRITERION).status());
        assertEquals(BenchmarkCalibration.Status.FAILED,
                BenchmarkCalibration.withinSession("fake", "actual-session", brackets(stats(10), stats(10), stats(10)), p, CRITERION).status());
    }
    @Test void crossSessionPolicyRequiresMoreThanOneSessionUnit() {
        assertThrows(IllegalArgumentException.class, () -> new AnalysisPolicy(16, 16, 4, 1, 1, "receipt", 20,
                AnalysisPolicy.InferenceScope.CROSS_SESSION));
    }
}
