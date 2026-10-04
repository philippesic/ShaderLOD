package com.pp.shaderlod;

/** An explicit cache acknowledgement belongs to one world instance, never all later worlds. */
public final class GeometryCoverageGuard {
    private Object confirmedWorld;
    private boolean armed = true;

    public boolean confirmed(Object world, boolean acknowledgement) {
        if (!acknowledgement) {
            confirmedWorld = null;
            armed = true;
        } else if (armed && world != null) {
            confirmedWorld = world;
            armed = false;
        }
        return acknowledgement && world != null && confirmedWorld == world;
    }
}
