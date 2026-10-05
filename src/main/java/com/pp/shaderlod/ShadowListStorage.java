package com.pp.shaderlod;

import java.util.Objects;
import java.util.function.Function;

/** One owner-local main list and an optional lazily created shadow list. */
public final class ShadowListStorage<T> {
    private final T original;
    private T shadow;

    public ShadowListStorage(T original) { this.original = Objects.requireNonNull(original); }

    public <O> T select(boolean enabled, boolean shadowPhase, O owner,
                        Function<? super O, ? extends T> factory) {
        if (!enabled || !shadowPhase) return original;
        if (shadow == null) shadow = Objects.requireNonNull(factory.apply(owner));
        return shadow;
    }

    public T original() { return original; }
    public T shadowOrNull() { return shadow; }
}
