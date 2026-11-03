package com.pp.shaderlod.benchmark.core;

/** Case-specific predeclared benefit and guardrails, in milliseconds, never universal percentages. */
public record PracticalCriterion(double minimumBenefitMs, double maximumReturnDriftMs,
        double maximumP95RegressionMs, String rationale) {
    public PracticalCriterion {
        if (!Double.isFinite(minimumBenefitMs) || minimumBenefitMs <= 0
                || !Double.isFinite(maximumReturnDriftMs) || maximumReturnDriftMs < 0
                || !Double.isFinite(maximumP95RegressionMs) || maximumP95RegressionMs < 0
                || rationale == null || rationale.isBlank())
            throw new IllegalArgumentException("explicit finite practical criterion and rationale required");
    }
}
