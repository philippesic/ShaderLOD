package com.pp.shaderlod.dev;

/** Exact Iris render-counter protocol; no client-tick or elapsed-time transition approximation. */
final class PixelHistoryTimeline {
    record Values(int core, int global, int temporal, int reset) {}
    record Action(String capture, Boolean switchTo, boolean done, int phase, int offset) {}
    private int previous = -1, phase, offset = -1;
    private boolean done;
    Action frame(int counter, Values d2, Values c4) {
        if (done) throw new IllegalStateException("FRAME_AFTER_DONE");
        if (counter < 0 || (previous >= 0 && counter != previous + 1))
            throw new IllegalStateException("RENDER_COUNTER_DISCONTINUITY");
        if (!d2.equals(c4)) throw new IllegalStateException("CONSUMER_FRAME_VALUES_DISAGREE");
        if (d2.core != 2 || d2.temporal != 1) throw new IllegalStateException("UNKNOWN_TRANSPORT");
        int expectedGlobal = phase == 1 ? 1 : 0;
        int expectedReset = phase != 0 && offset == -1 ? 1 : 0;
        if (d2.global != expectedGlobal || d2.reset != expectedReset)
            throw new IllegalStateException("UNEXPECTED_GLOBAL_OR_RESET: phase=" + phase + " offset=" + (offset + 1) + " actual=" + d2);
        previous = counter;
        if (phase == 0) {
            phase = 1;
            return new Action("off-pre-switch", true, false, 0, -1);
        }
        offset++;
        int observedPhase = phase;
        String label = switch (offset) {
            case 0, 1, 4, 16, 60 -> (phase == 1 ? "on" : "off") + "-frame-plus-" + offset;
            default -> null;
        };
        Boolean switchTo = null;
        int observedOffset = offset;
        if (offset == 60) {
            if (phase == 1) { phase = 2; offset = -1; switchTo = false; }
            else done = true;
        }
        return new Action(label, switchTo, done, observedPhase, observedOffset);
    }
}
