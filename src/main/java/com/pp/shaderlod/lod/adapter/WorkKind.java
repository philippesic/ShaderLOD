package com.pp.shaderlod.lod.adapter;

/** Stable identifiers, never enum ordinals. Operations and units are adapter declarations. */
public enum WorkKind {
    COUNT("count"), RATE("rate");

    private final String key;

    WorkKind(String key) { this.key = key; }

    public String key() { return key; }
}
