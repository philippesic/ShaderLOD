package com.pp.shaderlod.lod.adapter;

import java.util.Objects;

/** Exact adapter revision identity. A declaration is not evidence of a loaded shader. */
public record AdapterIdentity(String id, String revision) {
    public AdapterIdentity {
        requireKey(id);
        Objects.requireNonNull(revision, "revision");
        if (revision.isBlank() || !revision.equals(revision.strip())) {
            throw new IllegalArgumentException("An exact, non-blank revision is required");
        }
    }

    static void requireKey(String key) {
        Objects.requireNonNull(key, "key");
        if (!key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Require a namespaced stable key: " + key);
        }
    }
}
