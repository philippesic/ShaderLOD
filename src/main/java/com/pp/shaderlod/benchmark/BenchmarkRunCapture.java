package com.pp.shaderlod.benchmark;

import com.pp.shaderlod.measurement.FrameMeasurement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded consumer of the shared runtime clock; no independent clock or FPS calculation. */
public final class BenchmarkRunCapture implements BenchmarkScenarioRun.Capture {
    public interface Clock {
        void start();
        void pause();
        void poll();
        int pending();
        List<FrameMeasurement.Sample> drain();
        void stop();
        default com.pp.shaderlod.measurement.CpuWindowMeasurement.Result cpuWindow() { return null; }
    }
    private final Clock clock;
    private final int maximumSamples;
    private final Map<String, List<FrameMeasurement.Sample>> samples = new LinkedHashMap<>();
    private final Map<String, com.pp.shaderlod.measurement.CpuWindowMeasurement.Result> cpuWindows = new LinkedHashMap<>();
    private final Map<String, Integer> discarded = new LinkedHashMap<>();
    private String current;
    private boolean recording, draining;
    private int count;

    public BenchmarkRunCapture(Clock clock, int maximumSamples) {
        this.clock = java.util.Objects.requireNonNull(clock);
        if (maximumSamples < 1) throw new IllegalArgumentException("Positive capture capacity required");
        this.maximumSamples = maximumSamples;
    }
    @Override public void startCapture(BenchmarkScenario.Case scenario) {
        if (recording || draining) throw new IllegalStateException("Another capture owns the clock");
        current = scenario.id();
        if (samples.containsKey(current)) throw new IllegalStateException("Case already captured");
        samples.put(current, new ArrayList<>());
        // Retain ownership before starting: a partially failed start still needs stop/recovery.
        recording = true;
        clock.start();
    }
    /** Client tick only; never exports, summarizes, sorts or reads sources while capturing. */
    public void collect() {
        if (current == null) return;
        var incoming = clock.drain();
        if (incoming.size() > maximumSamples - count) {
            discarded.merge(current, incoming.size(), Math::addExact);
            throw new IllegalStateException("Capture sample budget exhausted; partial result retained");
        }
        samples.get(current).addAll(incoming);
        count += incoming.size();
    }
    @Override public void stopCapture(BenchmarkScenario.Case scenario) {
        if (!recording) return;
        // Pause first so no new queries are issued while draining an asynchronous tail.
        clock.pause();
        rememberCpuWindow();
        recording = false;
        draining = true;
        collect();
    }
    @Override public boolean drained() {
        if (recording) throw new IllegalStateException("Stop recording before drain");
        if (!draining) return true;
        clock.poll();
        collect();
        if (clock.pending() != 0) return false;
        clock.stop();
        draining = false;
        current = null;
        return true;
    }
    /** Called by recovery even after a partial start/pause failure. */
    public void abort() {
        try {
            if (recording) { clock.pause(); rememberCpuWindow(); }
            if (current != null) collect();
        } finally {
            clock.stop();
            recording = draining = false;
            current = null;
        }
    }
    private void rememberCpuWindow() {
        var result=clock.cpuWindow();if(current!=null&&result!=null)cpuWindows.put(current,result);
    }
    public Map<String, com.pp.shaderlod.measurement.CpuWindowMeasurement.Result> cpuWindows() { return Map.copyOf(cpuWindows); }
    public boolean busy() { return recording || draining; }
    public Map<String, Integer> discardedSamples() { return Map.copyOf(discarded); }
    /** Exporters may only obtain immutable snapshots after all capture has stopped. */
    public Map<String, List<FrameMeasurement.Sample>> snapshot() {
        if (busy()) throw new IllegalStateException("Export is unavailable during capture/drain");
        var result = new LinkedHashMap<String, List<FrameMeasurement.Sample>>();
        samples.forEach((id, rows) -> result.put(id, List.copyOf(rows)));
        return java.util.Collections.unmodifiableMap(result);
    }
}
