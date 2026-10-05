package com.pp.shaderlod.lod;

import java.util.Objects;

/** Immutable per-effect override. It neither registers uniforms nor changes a shader. */
public record EffectPolicy(boolean enabled, boolean preserveSalientLighting, DistanceBands bands,
                           double mediumWorkRatio, double farWorkRatio) {
    public EffectPolicy {
        Objects.requireNonNull(bands, "bands");
        if (!Double.isFinite(mediumWorkRatio) || !Double.isFinite(farWorkRatio)
                || farWorkRatio < 0 || mediumWorkRatio > 1 || farWorkRatio > mediumWorkRatio) {
            throw new IllegalArgumentException("Require 0 <= farWorkRatio <= mediumWorkRatio <= 1");
        }
    }

    /** New overrides are off with unity budgets; these distances are starting points, not validated optima. */
    public static EffectPolicy defaultsFor(Effect effect) {
        Objects.requireNonNull(effect, "effect");
        DistanceBands bands = effect == Effect.WATER_SURFACE
                ? new DistanceBands(96, 256, 32) : new DistanceBands(32, 96, 8);
        return new EffectPolicy(false, true, bands, 1, 1);
    }

    /**
     * Caps a caller's actual upstream work count, retaining at least its legal minimum.
     * A minimum of zero explicitly permits skipping all work. Invalid runtime inputs preserve upstream.
     * Ceil quantization retains up to one more work item than the continuous requested cap.
     */
    public WorkBudget decide(boolean globalEnabled, Effect effect, double distanceBlocks,
                             int upstreamWork, int minimumRetainedWork) {
        if (effect == null || !Double.isFinite(distanceBlocks) || distanceBlocks < 0
                || upstreamWork < 0 || minimumRetainedWork < 0 || minimumRetainedWork > upstreamWork) {
            return unchanged(upstreamWork, WorkBudget.Reason.INVALID_RUNTIME_INPUT);
        }
        if (!globalEnabled) return unchanged(upstreamWork, WorkBudget.Reason.GLOBAL_DISABLED);
        if (!enabled) return unchanged(upstreamWork, WorkBudget.Reason.EFFECT_DISABLED);
        if (preserveSalientLighting && effect.salientLighting()) {
            return unchanged(upstreamWork, WorkBudget.Reason.PROTECTED_LIGHTING);
        }
        double ratio = retentionRatio(distanceBlocks);
        int requested = (int) Math.ceil(upstreamWork * ratio);
        int limit = Math.min(upstreamWork, Math.max(minimumRetainedWork, requested));
        return new WorkBudget(upstreamWork, limit, ratio, WorkBudget.Reason.POLICY);
    }

    private double retentionRatio(double distanceBlocks) {
        double mediumBlend = bands.mediumBlend(distanceBlocks);
        double farBlend = bands.farBlend(distanceBlocks);
        double medium = (1 - mediumBlend) + mediumWorkRatio * mediumBlend;
        return Math.max(farWorkRatio, Math.min(1, medium * (1 - farBlend) + farWorkRatio * farBlend));
    }

    private static WorkBudget unchanged(int upstreamWork, WorkBudget.Reason reason) {
        return new WorkBudget(upstreamWork, upstreamWork, 1, reason);
    }
}
