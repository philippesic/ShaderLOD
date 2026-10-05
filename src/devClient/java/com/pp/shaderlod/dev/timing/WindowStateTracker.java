package com.pp.shaderlod.dev.timing;

import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Read-only window observations once per workload tick, outside frame/shader timing hooks. */
public final class WindowStateTracker {
    private static final int TRANSITION_LIMIT = 128;
    private final Phase warmup = new Phase();
    private final Phase capture = new Phase();

    public static State readWindow(long handle, int windowWidth, int windowHeight,
                                    int framebufferWidth, int framebufferHeight, Pacing pacing) {
        if (handle == 0) return unknown("window handle unavailable", windowWidth, windowHeight, framebufferWidth, framebufferHeight, pacing);
        try {
            // No GL queries, readbacks, focus setters, event pumping or host commands.
            String focused = flag(GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED));
            String iconified = flag(GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED));
            String visible = flag(GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_VISIBLE));
            boolean available = !focused.equals("unknown") && !iconified.equals("unknown") && !visible.equals("unknown");
            return new State(available ? "available" : "unavailable", focused, iconified, visible,
                    windowWidth, windowHeight, framebufferWidth, framebufferHeight, pacing,
                    available ? "GLFW attributes and cached Minecraft sizes" : "GLFW returned a non-boolean attribute");
        } catch (RuntimeException | LinkageError failure) {
            return unknown("GLFW attributes unavailable: " + failure.getClass().getSimpleName(),
                    windowWidth, windowHeight, framebufferWidth, framebufferHeight, pacing);
        }
    }

    private static State unknown(String reason, int windowWidth, int windowHeight,
                                  int framebufferWidth, int framebufferHeight, Pacing pacing) {
        return new State("unavailable", "unknown", "unknown", "unknown", windowWidth, windowHeight,
                framebufferWidth, framebufferHeight, pacing, reason);
    }

    private static String flag(int value) {
        return value == GLFW.GLFW_TRUE ? "true" : value == GLFW.GLFW_FALSE ? "false" : "unknown";
    }

    public void observeWarmup(State state, double elapsedSeconds, long frameId) { warmup.observe(state, elapsedSeconds, frameId); }
    public void observeCapture(State state, double elapsedSeconds, long frameId) { capture.observe(state, elapsedSeconds, frameId); }

    public Snapshot snapshot() {
        return new Snapshot(1, "GLFW focus/iconified/visible + cached Minecraft sizes/options, once per existing workload tick",
                "Read-only sampled states; changes between observations may be missed. Counts are observation counts, not time fractions. Configured pacing is not observed scheduling or host clocks/thermal state.",
                warmup.snapshot(), capture.snapshot());
    }

    public record Pacing(boolean pause_on_lost_focus, boolean fullscreen, int configured_framerate_limit, boolean vsync) {}
    public record State(String availability, String focused, String iconified, String visible,
                        int window_width, int window_height, int framebuffer_width, int framebuffer_height,
                        Pacing pacing, String note) {}
    public record Observation(double elapsed_workload_seconds, long frame_id, State state) {}
    public record FlagCounts(long true_observations, long false_observations, long unknown_observations) {}
    public record Transition(double elapsed_workload_seconds, long frame_id, List<String> changed_fields, State from, State to) {}
    public record PhaseSnapshot(long observations, long available_observations, long unavailable_observations,
                                Observation start, Observation end, FlagCounts focused, FlagCounts iconified, FlagCounts visible,
                                Map<String, Long> window_size_counts, Map<String, Long> framebuffer_size_counts,
                                Map<String, Long> transition_counts, List<Transition> transitions, long dropped_transitions,
                                Map<String, Long> unavailable_reasons, double max_observation_gap_seconds) {}
    public record Snapshot(int schema, String source, String note, PhaseSnapshot warmup, PhaseSnapshot capture) {}

    private static final class Phase {
        long observations;
        long available;
        long unavailable;
        Observation start;
        Observation end;
        final long[] focused = new long[3];
        final long[] iconified = new long[3];
        final long[] visible = new long[3];
        final Map<String, Long> windows = new LinkedHashMap<>();
        final Map<String, Long> framebuffers = new LinkedHashMap<>();
        final Map<String, Long> transitionCounts = new LinkedHashMap<>();
        final Map<String, Long> reasons = new LinkedHashMap<>();
        final List<Transition> transitions = new ArrayList<>();
        long droppedTransitions;
        double maxGap;

        void observe(State state, double elapsedSeconds, long frameId) {
            if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0) throw new IllegalArgumentException("Invalid observation time");
            if (end != null && elapsedSeconds < end.elapsed_workload_seconds()) throw new IllegalArgumentException("Observation clock moved backwards");
            var observation = new Observation(elapsedSeconds, frameId, Objects.requireNonNull(state));
            observations++;
            if (state.availability().equals("available")) available++;
            else { unavailable++; reasons.merge(state.note(), 1L, Long::sum); }
            addFlag(focused, state.focused()); addFlag(iconified, state.iconified()); addFlag(visible, state.visible());
            windows.merge(size(state.window_width(), state.window_height()), 1L, Long::sum);
            framebuffers.merge(size(state.framebuffer_width(), state.framebuffer_height()), 1L, Long::sum);
            if (end != null) {
                maxGap = Math.max(maxGap, elapsedSeconds - end.elapsed_workload_seconds());
                List<String> changed = changed(end.state(), state);
                if (!changed.isEmpty()) {
                    changed.forEach(field -> transitionCounts.merge(field, 1L, Long::sum));
                    if (transitions.size() < TRANSITION_LIMIT) transitions.add(new Transition(elapsedSeconds, frameId, List.copyOf(changed), end.state(), state));
                    else droppedTransitions++;
                }
            }
            if (start == null) start = observation;
            end = observation;
        }

        PhaseSnapshot snapshot() {
            return new PhaseSnapshot(observations, available, unavailable, start, end,
                    counts(focused), counts(iconified), counts(visible), Map.copyOf(windows), Map.copyOf(framebuffers),
                    Map.copyOf(transitionCounts), List.copyOf(transitions), droppedTransitions, Map.copyOf(reasons), maxGap);
        }

        private static void addFlag(long[] counts, String flag) { counts[flag.equals("true") ? 0 : flag.equals("false") ? 1 : 2]++; }
        private static FlagCounts counts(long[] values) { return new FlagCounts(values[0], values[1], values[2]); }
        private static String size(int width, int height) { return width > 0 && height > 0 ? width + "x" + height : "unknown_or_nonpositive"; }

        private static List<String> changed(State from, State to) {
            List<String> fields = new ArrayList<>();
            if (!from.availability().equals(to.availability())) fields.add("availability");
            if (!from.focused().equals(to.focused())) fields.add("focused");
            if (!from.iconified().equals(to.iconified())) fields.add("iconified");
            if (!from.visible().equals(to.visible())) fields.add("visible");
            if (from.window_width() != to.window_width() || from.window_height() != to.window_height()) fields.add("window_size");
            if (from.framebuffer_width() != to.framebuffer_width() || from.framebuffer_height() != to.framebuffer_height()) fields.add("framebuffer_size");
            if (from.pacing().pause_on_lost_focus() != to.pacing().pause_on_lost_focus()) fields.add("pause_on_lost_focus");
            if (from.pacing().fullscreen() != to.pacing().fullscreen()) fields.add("fullscreen");
            if (from.pacing().configured_framerate_limit() != to.pacing().configured_framerate_limit()) fields.add("configured_framerate_limit");
            if (from.pacing().vsync() != to.pacing().vsync()) fields.add("vsync");
            return fields;
        }
    }
}
