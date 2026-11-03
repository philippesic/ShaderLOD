package com.pp.shaderlod.benchmark.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared timing interpretation. This is not quality certification or authorization to promote. */
public final class BenchmarkAnalysis {
    public static final String ANALYSIS_VERSION = "chronological-block-envelope-v1";
    public static final String METHOD = "observed chronological block range, expanded by absolute return drift; no confidence coverage";
    public enum Availability { AVAILABLE, UNAVAILABLE, UNTESTED }
    public enum Activation { ACTIVE, INACTIVE, UNKNOWN }
    public enum Validity { VALID, INVALID, UNTESTED }
    public enum Verdict { POSITIVE, NEGATIVE, INCONCLUSIVE, INVALID, UNAVAILABLE, UNTESTED }
    public enum ControlKind { OPTIMIZATION, KNOWN_NULL, KNOWN_EFFECT }

    /** Explicit identity pins; use case/route/tuple/device/clock/observer/cache/temporal/control/warmth keys. */
    public record Stratum(Map<String, String> pins) {
        public Stratum { pins = Map.copyOf(pins); }
    }
    public record Window(String id, Stratum stratum, BenchmarkStatistics.Summary summary) {
        public Window {
            requireText(id, "window id"); Objects.requireNonNull(stratum); Objects.requireNonNull(summary);
        }
    }
    /** A repeat ID must identify an independent unit justified by policy evidence, not another frame/block. */
    public record Bracket(String id, String repeatId, Window before, Window candidate, Window returned) {
        public Bracket {
            requireText(id, "bracket id"); requireText(repeatId, "repeat id");
            Objects.requireNonNull(before); Objects.requireNonNull(candidate); Objects.requireNonNull(returned);
        }
    }
    public record Request(String comparisonId, ControlKind controlKind, Availability availability,
            Activation activation, List<Bracket> brackets, PracticalCriterion criterion,
            AnalysisPolicy policy, List<String> externalValidityReasons) {
        public Request {
            requireText(comparisonId, "comparison id"); Objects.requireNonNull(controlKind);
            Objects.requireNonNull(availability); Objects.requireNonNull(activation);
            brackets = List.copyOf(brackets); Objects.requireNonNull(criterion); Objects.requireNonNull(policy);
            externalValidityReasons = List.copyOf(externalValidityReasons);
        }
    }
    public record PairedEffect(String bracketId, String repeatId, double baselineMs,
            double candidateMs, double gainMs, double returnDriftMs, double lowerEnvelopeMs,
            double upperEnvelopeMs, double p95RegressionMs) {}
    public record Decision(int schemaVersion, String analysisVersion, String comparisonId, ControlKind controlKind,
            Availability availability, Activation activation, Validity validity, Verdict verdict,
            Double gainMs, Double lowerEnvelopeMs, Double upperEnvelopeMs, Double maximumReturnDriftMs,
            int matchedBracketCount, int independentRepeatCount, AnalysisPolicy.InferenceScope inferenceScope, String uncertaintyMethod,
            List<String> assumptions, PracticalCriterion criterion, AnalysisPolicy policy,
            List<PairedEffect> pairedEffects, List<String> reasons) {
        public Decision {
            if (schemaVersion != BenchmarkResult.SCHEMA_VERSION) throw new IllegalArgumentException("unsupported decision schema version");
            assumptions = List.copyOf(assumptions); pairedEffects = List.copyOf(pairedEffects);
            reasons = List.copyOf(reasons);
        }
        public boolean positive() { return verdict == Verdict.POSITIVE; }
    }
    private BenchmarkAnalysis() {}

    public static Decision analyze(Request request) {
        Objects.requireNonNull(request);
        var reasons = new ArrayList<>(request.externalValidityReasons());
        var effects = new ArrayList<PairedEffect>();
        var repeats = new HashSet<String>();
        if (request.availability() == Availability.UNAVAILABLE)
            return result(request, Validity.UNTESTED, Verdict.UNAVAILABLE, effects, repeats, List.of("required feature/clock/workload unavailable"));
        if (request.availability() == Availability.UNTESTED || request.activation() != Activation.ACTIVE)
            return result(request, Validity.UNTESTED, Verdict.UNTESTED, effects, repeats, List.of("required work activation untested or inactive"));
        if (!reasons.isEmpty()) return result(request, Validity.INVALID, Verdict.INVALID, effects, repeats, reasons);
        if (request.brackets().isEmpty())
            return result(request, Validity.UNTESTED, Verdict.UNTESTED, effects, repeats, List.of("no matched windows tested"));

        var windowIds = new HashSet<String>();
        var bracketIds = new HashSet<String>();
        Stratum stratum = request.brackets().getFirst().before().stratum();
        boolean invalid = false, insufficient = false, untested = false;
        if (stratum.pins().isEmpty()) { invalid = true; reasons.add("missing matched-stratum identity"); }
        for (var bracket : request.brackets()) {
            if (!bracketIds.add(bracket.id())) { invalid = true; reasons.add("duplicate bracket id"); }
            repeats.add(bracket.repeatId());
            for (var window : List.of(bracket.before(), bracket.candidate(), bracket.returned())) {
                var s = window.summary();
                if (!windowIds.add(window.id())) { invalid = true; reasons.add("reused window id: " + window.id()); }
                if (!stratum.equals(window.stratum())) { invalid = true; reasons.add("mismatched stratum: " + window.id()); }
                if (!request.policy().sameBlockDefinition(s.policy())) { invalid = true; reasons.add("analysis policy mismatch: " + window.id()); }
                if (!s.chronologyValid() || s.invalidCount() > 0) { invalid = true; reasons.add("invalid chronology/clock: " + window.id()); }
                if (s.missingCount() > 0 || s.reasons().stream().anyMatch(reason -> reason.startsWith("frame ID gaps"))) {
                    invalid = true; reasons.add("incomplete required total-frame coverage: " + window.id());
                    reasons.addAll(s.reasons());
                }
                if (s.count() == 0) {
                    if (s.requestedCount() == 0) { untested = true; reasons.add("untested window: " + window.id()); }
                    else { invalid = true; reasons.add("missing total-frame clock: " + window.id()); }
                }
                if (s.blocks().size() < request.policy().minimumBlocksPerWindow()) {
                    insufficient = true; reasons.add("insufficient chronological blocks: " + window.id());
                }
            }
            var a = bracket.before().summary(); var b = bracket.candidate().summary(); var c = bracket.returned().summary();
            if (a.count() == 0 || b.count() == 0 || c.count() == 0
                    || a.blocks().isEmpty() || b.blocks().isEmpty() || c.blocks().isEmpty()) continue;
            double baseline = (a.meanMs() + c.meanMs()) / 2;
            double drift = Math.abs(a.meanMs() - c.meanMs());
            effects.add(new PairedEffect(bracket.id(), bracket.repeatId(), baseline, b.meanMs(),
                    baseline - b.meanMs(), drift,
                    (min(a) + min(c)) / 2 - max(b) - drift,
                    (max(a) + max(c)) / 2 - min(b) + drift,
                    b.p95Ms() - Math.max(a.p95Ms(), c.p95Ms())));
        }
        if (invalid) return result(request, Validity.INVALID, Verdict.INVALID, effects, repeats, reasons);
        if (untested) return result(request, Validity.UNTESTED, Verdict.UNTESTED, effects, repeats, reasons);
        if (request.policy().independenceEvidence().isBlank()) {
            insufficient = true; reasons.add("missing calibration for block length and independent repeat units");
        }
        if (request.brackets().size() < request.policy().minimumMatchedBrackets()
                || repeats.size() < request.policy().minimumIndependentRepeats()) {
            insufficient = true; reasons.add("insufficient effective independent matched repeats; frames/blocks are not repeats");
        }
        boolean drift = effects.stream().anyMatch(e -> e.returnDriftMs() > request.criterion().maximumReturnDriftMs());
        if (drift) reasons.add("return drift exceeds predeclared case-specific allowance");
        if (insufficient || drift || effects.isEmpty())
            return result(request, Validity.VALID, Verdict.INCONCLUSIVE, effects, repeats, reasons);
        double lower = effects.stream().mapToDouble(PairedEffect::lowerEnvelopeMs).min().orElseThrow();
        double upper = effects.stream().mapToDouble(PairedEffect::upperEnvelopeMs).max().orElseThrow();
        boolean tailRegression = effects.stream().allMatch(e -> e.p95RegressionMs() > request.criterion().maximumP95RegressionMs());
        if (request.controlKind() == ControlKind.KNOWN_NULL) {
            reasons.add(lower > request.criterion().minimumBenefitMs()
                    ? "known-null control failure: apparent win suspends promotion and requires diagnosis"
                    : upper < -request.criterion().minimumBenefitMs() || tailRegression
                    ? "known-null control failure: apparent regression requires diagnosis"
                    : "known-null calibration only; cannot be an optimization win");
            return result(request, Validity.VALID, Verdict.INCONCLUSIVE, effects, repeats, reasons);
        }
        if (upper < -request.criterion().minimumBenefitMs()) {
            reasons.add("regression exceeds practical criterion across observed block envelopes");
            return result(request, Validity.VALID, Verdict.NEGATIVE, effects, repeats, reasons);
        }
        // A descriptive tail excursion blocks a win but is not independent tail inference.
        if (effects.stream().anyMatch(e -> e.p95RegressionMs() > request.criterion().maximumP95RegressionMs())) {
            reasons.add("descriptive p95 guard exceeded; tail inference/confirmation required");
            return result(request, Validity.VALID, Verdict.INCONCLUSIVE, effects, repeats, reasons);
        }
        if (lower > request.criterion().minimumBenefitMs()) {
            reasons.add(request.controlKind() == ControlKind.KNOWN_EFFECT
                    ? "known-effect timing calibration responded; not an optimization baseline"
                    : "benefit exceeds practical criterion across observed block envelopes; timing evidence only");
            return result(request, Validity.VALID, Verdict.POSITIVE, effects, repeats, reasons);
        }
        reasons.add("effect unresolved by practical criterion and observed block envelope");
        return result(request, Validity.VALID, Verdict.INCONCLUSIVE, effects, repeats, reasons);
    }

    private static double min(BenchmarkStatistics.Summary s) { return Math.min(s.meanMs(), s.blocks().stream().mapToDouble(BenchmarkStatistics.Block::meanMs).min().orElseThrow()); }
    private static double max(BenchmarkStatistics.Summary s) { return Math.max(s.meanMs(), s.blocks().stream().mapToDouble(BenchmarkStatistics.Block::meanMs).max().orElseThrow()); }
    private static Decision result(Request r, Validity validity, Verdict verdict, List<PairedEffect> effects,
            java.util.Set<String> repeats, List<String> reasons) {
        return new Decision(BenchmarkResult.SCHEMA_VERSION, ANALYSIS_VERSION, r.comparisonId(), r.controlKind(), r.availability(), r.activation(),
                validity, verdict, effects.isEmpty() ? null : effects.stream().mapToDouble(PairedEffect::gainMs).average().orElseThrow(),
                effects.isEmpty() ? null : effects.stream().mapToDouble(PairedEffect::lowerEnvelopeMs).min().orElseThrow(),
                effects.isEmpty() ? null : effects.stream().mapToDouble(PairedEffect::upperEnvelopeMs).max().orElseThrow(),
                effects.isEmpty() ? null : effects.stream().mapToDouble(PairedEffect::returnDriftMs).max().orElseThrow(),
                r.brackets().size(), repeats.size(), r.policy().inferenceScope(), METHOD,
                List.of("block length and repeat-unit assumptions require caller calibration: " + r.policy().independenceEvidence(),
                        r.policy().inferenceScope() == AnalysisPolicy.InferenceScope.WITHIN_SESSION
                                ? "within-session exploratory evidence only; cross-session effects remain unqualified; all brackets from one session share one repeat ID"
                                : "cross-session inference requires independently calibrated session units; IDs alone do not prove independence",
                        "arrival chronology retained; gaps split blocks; partial blocks descriptive only",
                        "equal-weight bracket means; identical case/route/tuple/device/clock/observer/cache/temporal/control/warmth stratum required",
                        "envelope describes observed variation only; no confidence coverage or robustness guarantee",
                        "p95/p99 are descriptive; timing result does not establish quality, holdout, compatibility or restoration"),
                r.criterion(), r.policy(), effects, reasons);
    }
    private static void requireText(String text, String field) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException(field + " required");
    }
}
