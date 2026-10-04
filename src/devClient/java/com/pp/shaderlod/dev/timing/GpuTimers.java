package com.pp.shaderlod.dev.timing;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Render-thread-only, opt-in development GPU timer; never shipped in the mod jar. */
public final class GpuTimers {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String FRAME = "game-render";
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("shaderlod.dev.gpuTiming",
            System.getProperty("shaderlod.dev.compare", "").isEmpty() ? "false" : "true"));
    private static final int QUERY_RING_SIZE = 12;
    private static final int HISTORY = 240;
    private static final boolean PASS_TIMING = Boolean.parseBoolean(System.getProperty("shaderlod.dev.gpuPassTiming", "true"));
    private static final int SAMPLE_QUEUE_LIMIT = 8192;
    private static final ArrayDeque<Sample> COMPLETED = new ArrayDeque<>();
    private static final Map<String, Scope> SCOPES = new LinkedHashMap<>();
    private static final ArrayDeque<Group> GROUPS = new ArrayDeque<>();
    private static Backend backend = Backend.UNINITIALIZED;
    private static String status = ENABLED ? "awaiting world/render context" : "disabled";
    private static long frame;
    private static long epoch;
    private static boolean inWorld;
    private static boolean fullFrame;
    private static Slot frameSlot;
    private static Slot elapsedActive;
    private static int timestampBits;
    private static int elapsedBits;

    private GpuTimers() {}

    public static void beginFrame(boolean world) {
        if (!ENABLED) return;
        inWorld = world;
        frame++;
        if (!world) return;
        if (backend == Backend.UNINITIALIZED) initialize();
        if (!supported()) return;
        try {
            poll();
            fullFrame = backend == Backend.TIMESTAMP || !PASS_TIMING || frame % 2 == 0;
            frameSlot = fullFrame ? begin(FRAME) : null;
        } catch (RuntimeException exception) { fail("query start/poll failed", exception); }
    }

    public static void endFrame() {
        if (!ENABLED || !inWorld || !supported()) return;
        try {
            end(frameSlot);
            frameSlot = null;
            // Guards against driver errors without swallowing them silently. A renderer error
            // elsewhere in the frame can also trigger this conservative shutdown.
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) fail("OpenGL error 0x" + Integer.toHexString(error)
                    + " during measured frame (may originate in renderer)", null);
        } catch (RuntimeException exception) { fail("query end failed", exception); }
    }

    /** Pinned Iris renderAll debug groups: level 1 is a stage, level 2 a named pass. */
    public static void beginIrisGroup(String name) {
        if (!ENABLED || !PASS_TIMING || !inWorld || !supported()) return;
        boolean leaf = !GROUPS.isEmpty();
        String scope = leaf ? "iris/" + name : "iris-stage/" + name;
        Slot slot = null;
        try {
            if (backend == Backend.TIMESTAMP || (!fullFrame && leaf)) slot = begin(scope);
        } catch (RuntimeException exception) { fail("Iris pass query start failed", exception); }
        if (supported()) GROUPS.push(new Group(scope, slot));
    }

    public static void endIrisGroup() {
        if (!ENABLED || GROUPS.isEmpty()) return;
        Group group = GROUPS.pop();
        if (!supported()) return;
        try { end(group.slot); }
        catch (RuntimeException exception) { fail("Iris pass query end failed", exception); }
    }

    public static void beginStandalonePass(String name) {
        if (!ENABLED || !PASS_TIMING || !inWorld || !supported()) return;
        Slot slot = null;
        try { if (backend == Backend.TIMESTAMP || !fullFrame) slot = begin("iris/" + name); }
        catch (RuntimeException exception) { fail("standalone pass start failed", exception); }
        if (supported()) GROUPS.push(new Group("iris/" + name, slot));
    }

    public static void endStandalonePass() { endIrisGroup(); }

    /** Call on the render thread between benchmark phases, before sampling the new phase. */
    public static void resetSamples() {
        epoch++;
        COMPLETED.clear();
        SCOPES.values().forEach(scope -> { scope.history.clear(); scope.dropped = 0; scope.zeroResults = 0; });
    }

    /** Completed results only; never queries OpenGL or waits. Call on the render thread. */
    public static List<Sample> drainSamples() {
        List<Sample> samples = List.copyOf(COMPLETED);
        COMPLETED.clear();
        return samples;
    }

    public static Snapshot snapshot() {
        Map<String, ScopeStats> results = new LinkedHashMap<>();
        int pending = 0;
        for (var entry : SCOPES.entrySet()) {
            Scope scope = entry.getValue();
            for (Slot slot : scope.slots) if (slot.pending) pending++;
            var values = scope.history.values();
            results.put(entry.getKey(), new ScopeStats(values.samples(), values.totalSamples(),
                    values.meanMs(), values.medianMs(), values.p95Ms(), scope.dropped));
        }
        return new Snapshot(status, backend.name(), timestampBits, elapsedBits, frame,
                java.util.Collections.unmodifiableMap(results), pending);
    }

    public static String summary() {
        var data = snapshot();
        if (!supported()) return "GPU: " + data.status();
        var gpu = data.frame();
        if (gpu == null || gpu.samples() == 0) return "GPU: " + data.status();
        return String.format(Locale.ROOT, "GPU %.2f ms | median %.2f | p95 %.2f", gpu.meanMs(), gpu.medianMs(), gpu.p95Ms());
    }

    public static void close() {
        // A failed backend can still own query names from earlier successful frames.
        if (SCOPES.isEmpty()) return;
        if (elapsedActive != null) GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        elapsedActive = null;
        for (Scope scope : SCOPES.values()) for (Slot slot : scope.slots) {
            GL15.glDeleteQueries(slot.start);
            if (slot.finish != 0) GL15.glDeleteQueries(slot.finish);
        }
        SCOPES.clear();
        GROUPS.clear();
        COMPLETED.clear();
        frameSlot = null;
        backend = Backend.UNSUPPORTED;
        status = "closed";
    }

    private static void initialize() {
        try {
            var capabilities = GL.getCapabilities();
            if (!capabilities.OpenGL33 && !capabilities.GL_ARB_timer_query) {
                fail("timer-query capability unavailable", null); return;
            }
            int priorError = GL11.glGetError();
            if (priorError != GL11.GL_NO_ERROR) LOGGER.warn("GPU timer initialization observed pre-existing GL error 0x{}", Integer.toHexString(priorError));
            timestampBits = GL15.glGetQueryi(GL33.GL_TIMESTAMP, GL15.GL_QUERY_COUNTER_BITS);
            elapsedBits = GL15.glGetQueryi(GL33.GL_TIME_ELAPSED, GL15.GL_QUERY_COUNTER_BITS);
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) { fail("timer-query capability probe GL error 0x" + Integer.toHexString(error), null); return; }
            if (timestampBits > 0 && capabilities.glQueryCounter != 0) backend = Backend.TIMESTAMP;
            else if (elapsedBits > 0) backend = Backend.ELAPSED_ALTERNATING;
            else { fail("driver reports zero useful timer-query bits", null); return; }
            status = backend == Backend.TIMESTAMP ? "GPU timestamps" : PASS_TIMING
                    ? "GPU elapsed (frame/pass alternate)" : "GPU elapsed (frame only)";
            LOGGER.info("ShaderLOD GPU timers: backend={}, timestampBits={}, elapsedBits={}, GL={}, renderer={}",
                    backend, timestampBits, elapsedBits, GL11.glGetString(GL11.GL_VERSION), GL11.glGetString(GL11.GL_RENDERER));
        } catch (RuntimeException exception) { fail("capability probe failed", exception); }
    }

    private static Slot begin(String name) {
        Scope scope = SCOPES.computeIfAbsent(name, unused -> new Scope());
        Slot slot = null;
        for (Slot candidate : scope.slots) if (!candidate.pending && !candidate.active) { slot = candidate; break; }
        if (slot == null) { scope.dropped++; return null; }
        if (backend == Backend.ELAPSED_ALTERNATING) {
            // Never collide with another mod's elapsed query, and never nest our own.
            if (elapsedActive != null || GL15.glGetQueryi(GL33.GL_TIME_ELAPSED, GL15.GL_CURRENT_QUERY) != 0) {
                scope.dropped++; return null;
            }
            GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, slot.start);
            elapsedActive = slot;
        } else GL33.glQueryCounter(slot.start, GL33.GL_TIMESTAMP);
        slot.active = true;
        slot.frame = frame;
        slot.epoch = epoch;
        return slot;
    }

    private static void end(Slot slot) {
        if (slot == null || !slot.active) return;
        if (backend == Backend.TIMESTAMP) GL33.glQueryCounter(slot.finish, GL33.GL_TIMESTAMP);
        else {
            if (elapsedActive != slot) { fail("elapsed scope imbalance", null); return; }
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            elapsedActive = null;
        }
        slot.active = false;
        slot.pending = true;
    }

    private static void poll() {
        for (var entry : SCOPES.entrySet()) for (Slot slot : entry.getValue().slots) {
            Scope scope = entry.getValue();
            if (!slot.pending || frame - slot.frame < 2) continue;
            int lastQuery = backend == Backend.TIMESTAMP ? slot.finish : slot.start;
            if (GL15.glGetQueryObjecti(lastQuery, GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_FALSE) {
                if (frame - slot.frame > 600) { fail("query still unavailable after 600 render frames", null); return; }
                continue;
            }
            if (backend == Backend.TIMESTAMP && GL15.glGetQueryObjecti(slot.start, GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_FALSE) continue;
            long nanoseconds = GL33.glGetQueryObjectui64(lastQuery, GL15.GL_QUERY_RESULT);
            if (backend == Backend.TIMESTAMP) {
                nanoseconds -= GL33.glGetQueryObjectui64(slot.start, GL15.GL_QUERY_RESULT);
                if (timestampBits < 64) nanoseconds &= (1L << timestampBits) - 1;
            }
            slot.pending = false;
            // Reject wrap/invalid results rather than turning them into credible-looking data.
            if (nanoseconds < 0 || nanoseconds > 10_000_000_000L) { scope.dropped++; continue; }
            if (slot.epoch == epoch) {
                if (entry.getKey().equals(FRAME)) {
                    scope.zeroResults = nanoseconds == 0 ? scope.zeroResults + 1 : 0;
                    if (scope.zeroResults >= 16) { fail("driver returned 16 consecutive zero frame durations", null); return; }
                }
                scope.history.add(nanoseconds);
                if (COMPLETED.size() == SAMPLE_QUEUE_LIMIT) COMPLETED.removeFirst();
                COMPLETED.addLast(new Sample(entry.getKey(), nanoseconds / 1_000_000.0, slot.frame, epoch));
            }
        }
    }

    private static boolean supported() { return backend == Backend.TIMESTAMP || backend == Backend.ELAPSED_ALTERNATING; }

    private static void fail(String reason, Exception exception) {
        status = "unavailable: " + reason;
        if (elapsedActive != null) {
            try { GL15.glEndQuery(GL33.GL_TIME_ELAPSED); } catch (RuntimeException ignored) { }
            elapsedActive = null;
        }
        backend = Backend.UNSUPPORTED;
        GROUPS.clear();
        LOGGER.warn("ShaderLOD GPU timing {}", status, exception);
    }

    private enum Backend { UNINITIALIZED, TIMESTAMP, ELAPSED_ALTERNATING, UNSUPPORTED }
    private record Group(String name, Slot slot) {}
    private static final class Scope {
        final List<Slot> slots = new ArrayList<>(QUERY_RING_SIZE);
        final TimingStatistics history = new TimingStatistics(HISTORY);
        long dropped;
        int zeroResults;
        Scope() {
            for (int index = 0; index < QUERY_RING_SIZE; index++) {
                slots.add(new Slot(GL15.glGenQueries(), backend == Backend.TIMESTAMP ? GL15.glGenQueries() : 0));
            }
        }
    }
    private static final class Slot {
        final int start;
        final int finish;
        boolean pending;
        boolean active;
        long frame;
        long epoch;
        Slot(int start, int finish) { this.start = start; this.finish = finish; }
    }

    public record ScopeStats(int samples, long totalSamples, double meanMs, double medianMs, double p95Ms, long droppedQueries) {}
    public record Sample(String scope, double milliseconds, long frameId, long epoch) {}
    public record Snapshot(String status, String backend, int timestampBits, int elapsedBits, long renderFrames,
                           Map<String, ScopeStats> scopes, int pendingQueries) {
        public ScopeStats frame() { return scopes.get(FRAME); }
    }
}
