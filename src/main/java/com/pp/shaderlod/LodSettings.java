package com.pp.shaderlod;

/** Validated block distances shared by diagnostics and the Iris uniform bridge. */
public record LodSettings(boolean enabled, double near, double far, double transition, boolean debug) {
    public LodSettings {
        if (!Double.isFinite(near) || !Double.isFinite(far) || !Double.isFinite(transition)
                || near <= 0 || far <= near || transition < 0
                || transition > Math.min(near * 2, far - near)) {
            throw new IllegalArgumentException("Require 0 < near < far and non-overlapping transition bands");
        }
    }

    public int tier(double distance) {
        if (!Double.isFinite(distance) || distance < 0) {
            throw new IllegalArgumentException("Distance must be finite and non-negative");
        }
        return !enabled || distance < near ? 0 : distance < far ? 1 : 2;
    }
}
