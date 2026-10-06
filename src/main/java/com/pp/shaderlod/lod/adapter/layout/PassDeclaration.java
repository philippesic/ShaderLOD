package com.pp.shaderlod.lod.adapter.layout;

import java.util.List;
import java.util.Objects;

/** Explicit reads and simultaneous draw groups; every group is a full-target draw. */
public record PassDeclaration(String id, List<Read> reads, List<DrawGroup> draws,
                              FeedbackPolicy feedbackPolicy) {
    /** Integer variants declare integer texelFetch; FLOAT_TEXEL_FETCH declares unfiltered integer addressing. */
    public enum Fetch { FLOAT_OR_NORMALIZED, FLOAT_TEXEL_FETCH, SIGNED_INTEGER, UNSIGNED_INTEGER }
    public enum FeedbackPolicy { UNSUPPORTED, DISTINCT_IMAGES_EXTERNALLY_VERIFIED }

    public record Read(String target, Fetch fetch) {
        public Read {
            TargetDeclaration.requireKey(target);
            Objects.requireNonNull(fetch, "fetch");
        }
    }

    public record DrawGroup(List<String> attachments, TargetSize viewport) {
        public DrawGroup {
            attachments = List.copyOf(attachments);
            if (attachments.isEmpty()) throw new IllegalArgumentException("Empty draw group");
            attachments.forEach(TargetDeclaration::requireKey);
            if (attachments.stream().distinct().count() != attachments.size()) {
                throw new IllegalArgumentException("Duplicate attachment in draw group");
            }
            Objects.requireNonNull(viewport, "viewport");
        }
    }

    public PassDeclaration {
        TargetDeclaration.requireKey(id);
        reads = List.copyOf(reads);
        draws = List.copyOf(draws);
        if (draws.isEmpty()) throw new IllegalArgumentException("Pass needs a draw group");
        Objects.requireNonNull(feedbackPolicy, "feedbackPolicy");
    }
}
