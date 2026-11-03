package com.pp.shaderlod.optimization.depthprime;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Pipeline construction and destruction revoke only that SodiumPrograms owner's receipts. */
final class DepthOwnerScopes<S> {
    private final Map<Object, S> scopes = new IdentityHashMap<>();
    private final Consumer<S> invalidate;

    DepthOwnerScopes(Consumer<S> invalidate) { this.invalidate = invalidate; }
    S get(Object owner) { return scopes.get(owner); }
    void put(Object owner, S scope) { scopes.put(Objects.requireNonNull(owner), Objects.requireNonNull(scope)); }
    List<S> values() { return List.copyOf(scopes.values()); }
    boolean isEmpty() { return scopes.isEmpty(); }
    void release(Object owner) {
        S scope = scopes.remove(owner);
        if (scope != null) invalidate.accept(scope);
    }
    /** Explicit disable/rearm revokes proof, retaining live owners until their actual teardown. */
    void invalidate() { values().forEach(invalidate); }
}
