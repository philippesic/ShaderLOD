package com.pp.shaderlod.optimization.sourcecache;

/** A hook without any linked owner cannot issue a permission grant.
 * Every successful grant is cleared by its synchronous draw wrapper's finally,
 * before ownership can be invalidated on the render thread. No mutable GL state
 * is cached. Complex receiver/temporal runtimes keep their existing cleanup.
 */
public final class InactiveDrawElision {
    private InactiveDrawElision() {}
    private static boolean enabled = true;

    public static boolean elide(boolean noOwners) {
        return enabled && noOwners;
    }

    /** Dev-only causal control; callers stop measurement before switching. */
    public static void configure(boolean value) {
        if (!Boolean.getBoolean("shaderlod.dev.inactiveDrawElision")
                || com.pp.shaderlod.measurement.RuntimeMeasurement.requested())
            throw new IllegalStateException("Explicit untimed dev diagnostic required");
        enabled = value;
    }
}
