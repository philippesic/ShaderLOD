package com.pp.shaderlod.lod;

/** Camera-relative Euclidean block distances, with a full transition width centered on each boundary. */
public record DistanceBands(double nearBlocks, double farBlocks, double transitionBlocks) {
    public DistanceBands {
        if (!Double.isFinite(nearBlocks) || !Double.isFinite(farBlocks)
                || !Double.isFinite(transitionBlocks) || nearBlocks <= 0
                || farBlocks <= nearBlocks || transitionBlocks < 0
                || transitionBlocks / 2 > nearBlocks
                || transitionBlocks > farBlocks - nearBlocks
                || !Double.isFinite(farBlocks + transitionBlocks / 2)) {
            throw new IllegalArgumentException("Require finite 0 < near < far and non-overlapping, non-negative bands");
        }
    }

    /** Zero for invalid distance, so invalid runtime positions retain full work. */
    public double mediumBlend(double distanceBlocks) {
        return blend(distanceBlocks, nearBlocks);
    }

    /** Zero for invalid distance, so invalid runtime positions retain full work. */
    public double farBlend(double distanceBlocks) {
        return blend(distanceBlocks, farBlocks);
    }

    private double blend(double distanceBlocks, double boundary) {
        if (!Double.isFinite(distanceBlocks) || distanceBlocks < 0) return 0;
        if (transitionBlocks == 0) return distanceBlocks < boundary ? 0 : 1;
        double start = boundary - transitionBlocks / 2;
        double t = Math.max(0, Math.min(1, (distanceBlocks - start) / transitionBlocks));
        return t * t * (3 - 2 * t);
    }
}
