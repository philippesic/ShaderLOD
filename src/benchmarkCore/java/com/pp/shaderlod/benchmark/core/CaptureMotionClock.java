package com.pp.shaderlod.benchmark.core;

/** Untimed camera clock: catch-up ticks may hold the path until another render. */
public final class CaptureMotionClock {
    private final boolean boundToRenders;
    private int tick, lastRenderedTick, heldTicks, writerHeldTicks;

    public CaptureMotionClock(boolean boundToRenders) {
        this.boundToRenders = boundToRenders;
    }

    public int advanceTick() {
        return advanceTick(true);
    }

    public int advanceTick(boolean captureCapacity) {
        if (boundToRenders && !captureCapacity) { heldTicks++; writerHeldTicks++; }
        else if (boundToRenders && tick > lastRenderedTick) heldTicks++;
        else tick++;
        return tick;
    }

    public void rendered() { lastRenderedTick = tick; }
    public int ticks() { return tick; }
    public int heldTicks() { return heldTicks; }
    public int writerHeldTicks() { return writerHeldTicks; }

    public double pathSeconds(double duration, float partialTick) {
        if (!Float.isFinite(partialTick) || partialTick < 0 || partialTick > 1)
            throw new IllegalArgumentException("Finite render interpolation in [0,1] required");
        return tick == 0 ? 0 : Math.min(duration, (tick - 1 + partialTick) / 20.0);
    }
}
