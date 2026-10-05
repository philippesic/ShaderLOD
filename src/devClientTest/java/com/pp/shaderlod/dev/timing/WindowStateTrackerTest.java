package com.pp.shaderlod.dev.timing;

/** Pure fixture checks: does not call GLFW, create windows, or inspect a live client. */
public final class WindowStateTrackerTest {
    public static void main(String[] arguments) {
        var tracker = new WindowStateTracker();
        var pacing = new WindowStateTracker.Pacing(false, false, 260, false);
        var steady = new WindowStateTracker.State("available", "true", "false", "true", 1280, 720, 2560, 1440, pacing, "fixture");
        tracker.observeWarmup(steady, .05, 1);
        tracker.observeCapture(steady, 35.05, 700);
        var changed = new WindowStateTracker.State("available", "false", "true", "false", 640, 360, 1280, 720,
                new WindowStateTracker.Pacing(false, true, 120, true), "fixture");
        tracker.observeCapture(changed, 35.10, 701);
        var unknown = new WindowStateTracker.State("unavailable", "unknown", "unknown", "unknown", 640, 360, 1280, 720,
                changed.pacing(), "fixture unavailable");
        tracker.observeCapture(unknown, 35.15, 702);
        var capture = tracker.snapshot().capture();
        if (capture.observations() != 3 || capture.available_observations() != 2 || capture.unavailable_observations() != 1)
            throw new AssertionError("availability counters wrong");
        if (capture.focused().true_observations() != 1 || capture.focused().false_observations() != 1 || capture.focused().unknown_observations() != 1)
            throw new AssertionError("unknown focus treated as false");
        if (capture.transition_counts().get("focused") != 2 || capture.transition_counts().get("framebuffer_size") != 1
                || capture.transition_counts().get("vsync") != 1 || capture.transition_counts().get("configured_framerate_limit") != 1)
            throw new AssertionError("transition fields missing");
        if (capture.start().frame_id() != 700 || capture.end().frame_id() != 702
                || !capture.end().state().availability().equals("unavailable"))
            throw new AssertionError("phase boundaries lost");
        if (Math.abs(capture.max_observation_gap_seconds() - .05) > 1e-10) throw new AssertionError("observation gaps wrong");
        if (tracker.snapshot().warmup().observations() != 1) throw new AssertionError("warmup/capture mixed");
        if (capture.window_size_counts().get("1280x720") != 1 || capture.framebuffer_size_counts().get("1280x720") != 2)
            throw new AssertionError("logical/framebuffer dimensions mixed");
        try { capture.transition_counts().put("mutated", 1L); throw new AssertionError("mutable exported snapshot"); }
        catch (UnsupportedOperationException expected) { }
        boolean backwardsRejected = false;
        try { tracker.observeCapture(steady, 35.00, 703); }
        catch (IllegalArgumentException expected) { backwardsRejected = true; }
        if (!backwardsRejected) throw new AssertionError("backwards clock accepted");

        var bounded = new WindowStateTracker();
        for (int index = 0; index < 200; index++) bounded.observeCapture(index % 2 == 0 ? steady : changed, index * .05, index);
        if (bounded.snapshot().capture().transitions().size() != 128 || bounded.snapshot().capture().dropped_transitions() != 71
                || bounded.snapshot().capture().transition_counts().get("focused") != 199)
            throw new AssertionError("transition bound hides aggregate changes");
        var empty = new WindowStateTracker().snapshot();
        if (empty.capture().observations() != 0 || empty.capture().start() != null || empty.capture().end() != null)
            throw new AssertionError("empty phase invented a known state");
        System.out.println("Window telemetry phase/count/transition/unknown fixture checks passed");
    }
}
