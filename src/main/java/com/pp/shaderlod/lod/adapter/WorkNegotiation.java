package com.pp.shaderlod.lod.adapter;

import com.pp.shaderlod.lod.WorkBudget;
import java.util.Objects;

/** Requested policy and accepted adapter cap; neither value is measured physical work. */
public record WorkNegotiation(WorkBudget requestedBudget, int negotiatedWorkLimit, Reason reason) {
    public enum Reason {
        POLICY_APPLIED, GLOBAL_DISABLED, EFFECT_DISABLED, PROTECTED_LIGHTING, INVALID_RUNTIME_INPUT,
        UNREGISTERED_ADAPTER, MISSING_PROTOCOL, UNSUPPORTED_PROTOCOL, UNSUPPORTED_EFFECT,
        UNSUPPORTED_WORK, UNSUPPORTED_UPSTREAM_WORK, UNSUPPORTED_RATE
    }

    public WorkNegotiation {
        Objects.requireNonNull(requestedBudget, "requestedBudget");
        Objects.requireNonNull(reason, "reason");
        int upstream = requestedBudget.upstreamWork();
        if ((reason != Reason.POLICY_APPLIED && negotiatedWorkLimit != upstream)
                || (reason == Reason.POLICY_APPLIED && (requestedBudget.reason() != WorkBudget.Reason.POLICY || upstream < 0
                    || negotiatedWorkLimit != requestedBudget.workLimit()))) {
            throw new IllegalArgumentException("Fallback must retain upstream; accepted policy must retain its cap");
        }
    }

    public boolean reductionRequested() { return requestedBudget.reduced(); }

    public boolean reductionNegotiated() {
        return requestedBudget.upstreamWork() >= 0 && negotiatedWorkLimit < requestedBudget.upstreamWork();
    }
}
