package com.pp.shaderlod.lod.adapter;

import java.util.Set;

/** Explicit accepted versions; compatibility with newer versions is never inferred. */
public record ProtocolRequirement(String id, Set<Integer> acceptedVersions) {
    public ProtocolRequirement {
        AdapterIdentity.requireKey(id);
        acceptedVersions = Set.copyOf(acceptedVersions);
        if (acceptedVersions.isEmpty() || acceptedVersions.stream().anyMatch(version -> version <= 0)) {
            throw new IllegalArgumentException("Declare at least one positive protocol version");
        }
    }
}
