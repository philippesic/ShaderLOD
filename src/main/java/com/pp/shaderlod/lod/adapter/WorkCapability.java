package com.pp.shaderlod.lod.adapter;

import java.util.Objects;
import java.util.Set;

/** Declared legal work domain, not proof of compilation, consumption, quality or savings. */
public record WorkCapability(WorkKey key, Domain domain) {
    public sealed interface Domain permits CountFloor, DiscreteRates {}

    /** Absolute minimum for a non-empty upstream operation; zero explicitly permits skipping. */
    public record CountFloor(int minimumRetainedWork) implements Domain {
        public CountFloor {
            if (minimumRetainedWork < 0) throw new IllegalArgumentException("Negative count floor");
        }
    }

    /** Exact retained evaluation ratios, including full work. No implicit rate snapping. */
    public record DiscreteRates(Set<Double> retainedRates) implements Domain {
        public DiscreteRates {
            retainedRates = Set.copyOf(retainedRates);
            if (!retainedRates.contains(1.0) || retainedRates.stream().anyMatch(rate ->
                    !Double.isFinite(rate) || rate < 0 || rate > 1)) {
                throw new IllegalArgumentException("Declare finite rates in [0, 1], including full work");
            }
        }
    }

    public WorkCapability {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(domain, "domain");
        if ((key.kind() == WorkKind.COUNT) != (domain instanceof CountFloor)) {
            throw new IllegalArgumentException("Work kind must match its legal domain");
        }
    }
}
