package com.pp.shaderlod.benchmark.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded same-session negative-control check. No CI, JVM independence or future stability claim. */
public final class BenchmarkCalibration {
    public enum Status { PASS, FAILED, INCONCLUSIVE }
    public record Receipt(String id, Status status, String sessionId, BenchmarkAnalysis.Stratum stratum,
            int nullBracketCount, Double maximumObservedNullEnvelopeMs, Double maximumReturnDriftMs,
            PracticalCriterion criterion, AnalysisPolicy exploratoryPolicy, List<String> reasons) {
        public Receipt { reasons = List.copyOf(reasons); }
    }
    private BenchmarkCalibration() {}

    /**
     * At least two fresh unchanged-state return brackets must bound observed null variation below
     * the desired detection target. All belong to one real session ID. The returned policy permits
     * exploratory single brackets only in that same stratum/session; callers retain this receipt.
     * Window IDs must be unique; never relabel an optimization trial as a null control.
     */
    public static Receipt withinSession(String receiptId, String sessionId,
            List<BenchmarkAnalysis.Bracket> nullBrackets, AnalysisPolicy settings, PracticalCriterion criterion) {
        Objects.requireNonNull(nullBrackets); Objects.requireNonNull(settings); Objects.requireNonNull(criterion);
        if (receiptId == null || receiptId.isBlank() || sessionId == null || sessionId.isBlank())
            throw new IllegalArgumentException("receipt and actual session IDs required");
        var reasons = new ArrayList<String>();
        var stratum = nullBrackets.isEmpty() ? new BenchmarkAnalysis.Stratum(java.util.Map.of())
                : nullBrackets.getFirst().before().stratum();
        if (settings.inferenceScope() != AnalysisPolicy.InferenceScope.WITHIN_SESSION
                || nullBrackets.stream().anyMatch(b -> !sessionId.equals(b.repeatId())))
            return new Receipt(receiptId, Status.FAILED, sessionId, stratum, nullBrackets.size(), null, null,
                    criterion, null, List.of("all null brackets must share the actual within-session repeat ID"));
        // Check capture validity using the exact supplied settings; missing prior calibration is expected.
        var d = BenchmarkAnalysis.analyze(new BenchmarkAnalysis.Request(receiptId,
                BenchmarkAnalysis.ControlKind.KNOWN_NULL, BenchmarkAnalysis.Availability.AVAILABLE,
                BenchmarkAnalysis.Activation.ACTIVE, nullBrackets, criterion, settings, List.of()));
        if (d.validity() != BenchmarkAnalysis.Validity.VALID)
            return new Receipt(receiptId, Status.FAILED, sessionId, stratum, nullBrackets.size(), null, null,
                    criterion, null, d.reasons());
        boolean blocksEnough = nullBrackets.stream().allMatch(b -> List.of(b.before(), b.candidate(), b.returned())
                .stream().allMatch(w -> w.summary().blocks().size() >= settings.minimumBlocksPerWindow()));
        if (nullBrackets.size() < 2 || !blocksEnough || d.pairedEffects().size() != nullBrackets.size())
            return new Receipt(receiptId, Status.INCONCLUSIVE, sessionId, stratum, nullBrackets.size(), null, null,
                    criterion, null, List.of("need at least two complete same-state null brackets with duration-qualified blocks"));
        double nullEnvelope = d.pairedEffects().stream().mapToDouble(e -> Math.max(Math.abs(e.lowerEnvelopeMs()), Math.abs(e.upperEnvelopeMs())))
                .max().orElseThrow();
        double drift = d.pairedEffects().stream().mapToDouble(BenchmarkAnalysis.PairedEffect::returnDriftMs).max().orElseThrow();
        boolean spuriousEffect = d.pairedEffects().stream().anyMatch(e -> e.lowerEnvelopeMs() > criterion.minimumBenefitMs()
                || e.upperEnvelopeMs() < -criterion.minimumBenefitMs());
        if (spuriousEffect) reasons.add("same-state null shows apparent practical effect; suspend promotion and diagnose");
        if (drift > criterion.maximumReturnDriftMs()) reasons.add("null return drift exceeds case-specific allowance");
        if (nullEnvelope >= criterion.minimumBenefitMs()) reasons.add("observed null envelope does not resolve the requested practical benefit");
        if (!reasons.isEmpty()) return new Receipt(receiptId, spuriousEffect ? Status.FAILED : Status.INCONCLUSIVE,
                sessionId, stratum, nullBrackets.size(), nullEnvelope, drift, criterion, null, reasons);
        var policy = new AnalysisPolicy(settings.blockFrames(), settings.minimumBlockDurationMs(),
                settings.minimumBlocksPerWindow(), 1, 1,
                "within-session null receipt " + receiptId + "; session=" + sessionId
                        + "; same stratum only; " + nullBrackets.size() + " null brackets; observed envelope=" + nullEnvelope
                        + "ms; block length remains caller-assumed; no cross-session claim",
                settings.stutterThresholdMs(), AnalysisPolicy.InferenceScope.WITHIN_SESSION);
        return new Receipt(receiptId, Status.PASS, sessionId, stratum, nullBrackets.size(), nullEnvelope,
                drift, criterion, policy, List.of("bounded observed null check passed; exploratory within-session timing only; no confidence coverage"));
    }
}
