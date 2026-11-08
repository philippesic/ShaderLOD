package com.pp.shaderlod.dev.timing;

import com.mojang.logging.LogUtils;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** CPU wall and current render-thread CPU clocks. Independent stream from GPU queries. */
public final class CpuTimers {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("shaderlod.dev.cpuTiming",
            System.getProperty("shaderlod.dev.compare", "").isEmpty() ? "false" : "true"));
    private static final boolean LAYERS = Boolean.parseBoolean(System.getProperty("shaderlod.dev.cpuLayerTiming", "true"));
    private static final int CAPACITY = 16384;
    private static final int LAYER_INTERVAL = Math.max(1,Integer.getInteger("shaderlod.dev.cpuLayerSampleInterval",1));
    private static boolean recordingRequested = !Boolean.getBoolean("shaderlod.dev.manualProfiling");
    private static boolean recording = recordingRequested;
    private static boolean layerFrame = true;
    private static final ThreadMXBean THREAD = ManagementFactory.getThreadMXBean();
    private static final ArrayDeque<Mark> MARKS = new ArrayDeque<>();
    private static final ArrayDeque<String> IRIS_SCOPES = new ArrayDeque<>();
    private static final ArrayDeque<Sample> COMPLETED = new ArrayDeque<>();
    private static final Map<String, History> HISTORIES = new LinkedHashMap<>();
    private static boolean initialized;
    private static boolean threadClock;
    private static boolean world;
    private static long frame;
    private static long epoch;
    private static long queueDrops;

    private CpuTimers() {}

    /** Control changes latch at the next frame boundary, keeping nested scopes balanced. */
    public static void setRecording(boolean enabled) { recordingRequested=enabled; }
    public static boolean recording() { return ENABLED&&recording; }
    public static void beginFrame(long frameId, boolean hasWorld) {
        recording=recordingRequested && !com.pp.shaderlod.measurement.RuntimeMeasurement.requested();
        if (!MARKS.isEmpty()) { queueDrops+=MARKS.size(); MARKS.clear(); IRIS_SCOPES.clear(); }
        layerFrame=(frameId-1)%LAYER_INTERVAL==0;
        frame = frameId;
        world = hasWorld;
        if (!ENABLED || initialized) return;
        initialized = true;
        try {
            threadClock = THREAD.isCurrentThreadCpuTimeSupported();
            if (threadClock && !THREAD.isThreadCpuTimeEnabled()) THREAD.setThreadCpuTimeEnabled(true);
            threadClock &= THREAD.isThreadCpuTimeEnabled();
        } catch (UnsupportedOperationException | SecurityException exception) { threadClock = false; }
        LogUtils.getLogger().info("ShaderLOD CPU timers: currentThreadCpuSupported={}, layers={}", threadClock, LAYERS);
    }

    public static void begin(String scope) { begin(scope, false); }
    public static void beginLayer(String scope) { begin(scope, true); }

    public static void beginIrisGroup(String name) {
        if (!ENABLED || !recording || !world || !LAYERS || !layerFrame) return;
        String scope = (IRIS_SCOPES.isEmpty() ? "iris-stage/" : "iris/") + name;
        if(IRIS_SCOPES.size()>=128){queueDrops++;return;}
        IRIS_SCOPES.push(scope);
        beginLayer(scope);
    }

    public static void endIrisGroup() {
        if (!ENABLED || !recording || !world || !LAYERS || !layerFrame || IRIS_SCOPES.isEmpty()) return;
        endLayer(IRIS_SCOPES.pop());
    }

    private static void begin(String scope, boolean layer) {
        if (!ENABLED || !recording || !world || (layer && (!LAYERS || !layerFrame))) return;
        long wall = System.nanoTime();
        long cpu = threadClock ? THREAD.getCurrentThreadCpuTime() : -1;
        if (MARKS.size()>=128 || (!HISTORIES.containsKey(scope) && HISTORIES.size()>=512)) { queueDrops++; return; }
        MARKS.push(new Mark(scope, wall, cpu, frame, epoch, CostMapSession.beginCpu(scope,wall,cpu<0?null:cpu)));
    }

    public static void end(String scope) { end(scope, false); }
    public static void endLayer(String scope) { end(scope, true); }

    private static void end(String scope, boolean layer) {
        if (!ENABLED || !recording || !world || (layer && (!LAYERS || !layerFrame))) return;
        long cpu = threadClock ? THREAD.getCurrentThreadCpuTime() : -1;
        long wall = System.nanoTime();
        if (MARKS.isEmpty() || !MARKS.peek().scope.equals(scope)) {
            LogUtils.getLogger().warn("CPU timing scope imbalance: expected {}", scope);
            MARKS.clear();
            return;
        }
        Mark mark = MARKS.pop();
        CostMapSession.endCpu(mark.eventId,wall,cpu<0?null:cpu);
        if (mark.epoch != epoch) return;
        long wallNs = Math.max(0, wall - mark.wall);
        Long cpuNs = cpu >= mark.cpu && mark.cpu >= 0 ? cpu - mark.cpu : null;
        if(!HISTORIES.containsKey(scope) && HISTORIES.size()>=512){queueDrops++;return;}
        History history = HISTORIES.computeIfAbsent(scope, ignored -> new History());
        history.wall.add(wallNs);
        if (cpuNs != null) history.thread.add(cpuNs);
        if (COMPLETED.size() == CAPACITY) { COMPLETED.removeFirst(); queueDrops++; }
        COMPLETED.addLast(new Sample(scope, wallNs / 1_000_000.0,
                cpuNs == null ? null : cpuNs / 1_000_000.0, mark.frame, epoch));
    }

    public static void disarm() { recordingRequested=recording=false; queueDrops+=MARKS.size(); MARKS.clear(); IRIS_SCOPES.clear(); }

    public static void resetSamples() {
        epoch++;
        COMPLETED.clear();
        queueDrops = 0;
        HISTORIES.values().forEach(history -> { history.wall.clear(); history.thread.clear(); });
        // Keep open marks: epoch filtering discards them on return without unbalancing the stack.
    }

    public static List<Sample> drainSamples() {
        var samples = List.copyOf(COMPLETED);
        COMPLETED.clear();
        return samples;
    }

    public static Snapshot snapshot() {
        Map<String, ScopeStats> values = new LinkedHashMap<>();
        HISTORIES.forEach((name, history) -> {
            var wall = history.wall.values();
            var cpu = history.thread.values();
            values.put(name, new ScopeStats(wall.samples(), wall.totalSamples(), wall.meanMs(), wall.medianMs(), wall.p95Ms(),
                    cpu.samples() == 0 ? null : cpu.meanMs(), cpu.samples() == 0 ? null : cpu.medianMs(), cpu.samples() == 0 ? null : cpu.p95Ms()));
        });
        return new Snapshot(ENABLED ? "CPU wall + " + (threadClock ? "thread CPU" : "thread CPU unavailable") : "disabled",
                threadClock, frame, java.util.Collections.unmodifiableMap(values), queueDrops);
    }

    public static String summary() {
        var scope = snapshot().scopes().get("run-tick");
        if (scope == null || scope.samples() == 0) return "CPU: warming";
        return String.format(Locale.ROOT, "CPU frame wall %.2f ms | thread %s", scope.meanWallMs(),
                scope.meanThreadMs() == null ? "unavailable" : String.format(Locale.ROOT, "%.2f ms", scope.meanThreadMs()));
    }

    public record Sample(String scope, double wallMilliseconds, Double threadMilliseconds, long frameId, long epoch) {}
    public record ScopeStats(int samples, long totalSamples, double meanWallMs, double medianWallMs, double p95WallMs,
                             Double meanThreadMs, Double medianThreadMs, Double p95ThreadMs) {}
    public record Snapshot(String status, boolean currentThreadCpuSupported, long frameId,
                           Map<String, ScopeStats> scopes, long queueDrops) {}
    private record Mark(String scope, long wall, long cpu, long frame, long epoch, Long eventId) {}
    private static final class History {
        final TimingStatistics wall = new TimingStatistics(240);
        final TimingStatistics thread = new TimingStatistics(240);
    }
}
