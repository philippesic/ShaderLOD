package com.pp.shaderlod.lod.adapter.layout;

import java.util.Objects;
import java.util.List;

/** One image allocation with explicit quantity views; packing and basis/units need adapter proof. */
public record TargetDeclaration(String id, TargetSize size, TargetFormat format, List<Quantity> quantities) {
    public enum Role {
        GENERAL, LINEAR_QUANTITY, EXACT_SIGNED_INT32_METADATA,
        EXACT_UNSIGNED_INT32_METADATA, EXACT_BINARY32_BOUNDED_INTEGER_METADATA, BINARY32_MINIMUM_DISTANCE
    }

    /** Channels belong to this view; separate views may occupy different texels of the image. */
    public record Quantity(String key, Role role, int channels, long maximumIntegerMagnitude) {
        public Quantity(String key, Role role, int channels) {
            this(key, role, channels, 0);
        }

        public Quantity {
            requireKey(key);
            Objects.requireNonNull(role, "role");
            if (channels < 1 || channels > 4) {
                throw new IllegalArgumentException("Quantity must use one to four channels");
            }
            if (role == Role.EXACT_BINARY32_BOUNDED_INTEGER_METADATA) {
                if (maximumIntegerMagnitude < 1 || maximumIntegerMagnitude > (1L << 24)) {
                    throw new IllegalArgumentException("Binary32 integer metadata requires an explicit magnitude in [1, 2^24]");
                }
            } else if (maximumIntegerMagnitude != 0) {
                throw new IllegalArgumentException("Integer magnitude belongs only to bounded binary32 metadata");
            }
        }
    }

    public TargetDeclaration(String id, TargetSize size, TargetFormat format, Quantity quantity) {
        this(id, size, format, List.of(quantity));
    }

    public TargetDeclaration {
        requireKey(id);
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(format, "format");
        quantities = List.copyOf(quantities);
        if (quantities.isEmpty()) throw new IllegalArgumentException("Target needs a declared quantity");
        if (quantities.stream().map(Quantity::key).distinct().count() != quantities.size()) {
            throw new IllegalArgumentException("Duplicate quantity key");
        }
    }

    static void requireKey(String key) {
        Objects.requireNonNull(key, "key");
        if (!key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Require a namespaced stable key: " + key);
        }
    }
}
