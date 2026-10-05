package com.pp.shaderlod.lod;

import java.util.Objects;

/** A requested loop/work cap, not evidence that an adapter or GPU consumed it. */
public record WorkBudget(int upstreamWork, int workLimit, double retentionRatio, Reason reason) {
    public enum Reason {
        GLOBAL_DISABLED,
        EFFECT_DISABLED,
        PROTECTED_LIGHTING,
        INVALID_RUNTIME_INPUT,
        POLICY
    }

    public WorkBudget {
        Objects.requireNonNull(reason, "reason");
        if (!Double.isFinite(retentionRatio) || retentionRatio < 0 || retentionRatio > 1
                || (reason != Reason.POLICY && (workLimit != upstreamWork || retentionRatio != 1))
                || (upstreamWork >= 0 && (workLimit < 0 || workLimit > upstreamWork))
                || (upstreamWork < 0 && (workLimit != upstreamWork
                    || reason != Reason.INVALID_RUNTIME_INPUT || retentionRatio != 1))) {
            throw new IllegalArgumentException("A work cap must preserve invalid input or stay within the upstream budget");
        }
    }

    public boolean reduced() {
        return upstreamWork >= 0 && workLimit < upstreamWork;
    }
}
