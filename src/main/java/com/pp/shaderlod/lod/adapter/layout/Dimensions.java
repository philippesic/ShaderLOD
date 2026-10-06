package com.pp.shaderlod.lod.adapter.layout;

/** Positive level-zero allocation or full-target viewport dimensions. */
public record Dimensions(int width, int height) {
    public Dimensions {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive");
        }
    }
}
