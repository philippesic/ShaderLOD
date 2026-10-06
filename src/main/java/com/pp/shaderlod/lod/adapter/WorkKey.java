package com.pp.shaderlod.lod.adapter;

import com.pp.shaderlod.lod.Effect;
import java.util.Objects;

/** A broad effect alone does not declare support for any operation. */
public record WorkKey(Effect effect, String operation, WorkKind kind) {
    public WorkKey {
        Objects.requireNonNull(effect, "effect");
        AdapterIdentity.requireKey(operation);
        Objects.requireNonNull(kind, "kind");
    }
}
