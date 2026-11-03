package com.pp.shaderlod.optimization.framecopy.rotation;

/** A new plan owns a new latch. Only first canonical beforeQuad, after Program.use, may initialize. */
final class HistoryRotationSamplerLatch {
    private int unit = -1;
    int unit() { return unit; }
    int latch(int initializedUnit, int limit, boolean canonicalAfterUse) {
        if (unit >= 0) {
            if (unit != initializedUnit) throw new IllegalStateException("HISTORY_UNIT_CHANGED");
            return unit;
        }
        if (!canonicalAfterUse) throw new IllegalStateException("HISTORY_UNIT_REQUIRES_CANONICAL_AFTER_USE");
        if (initializedUnit < 0 || initializedUnit >= limit) throw new IllegalStateException("INVALID_HISTORY_UNIT");
        return unit = initializedUnit;
    }
}
