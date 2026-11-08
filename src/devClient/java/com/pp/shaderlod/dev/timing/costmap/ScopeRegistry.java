package com.pp.shaderlod.dev.timing.costmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.pp.shaderlod.dev.timing.costmap.CostMapRecords.*;

/** Single-owner, bounded stage-1 collector. Later hooks must marshal worker records to their owner.
 * No live timer integration. IDs are never recycled. Full buffers stop this collector; drain does
 * not silently restart it. Preserve health with every export, including stopped captures.
 * GPU entries are append-only state transitions keyed by invocation ID (pending -> terminal).
 */
public final class ScopeRegistry {
    /** Each dimension has explicit availability even when it has no payload.
     * These are append-only observations, not replacements for other observations of the event.
     */
    public record Entry(Invocation invocation, Outcome outcome, CpuEvent cpu, GpuEvent gpu,
                        Outcome cpuOutcome, Outcome gpuOutcome) {
        public Entry(Invocation invocation, Outcome outcome, CpuEvent cpu, GpuEvent gpu) {
            this(invocation, outcome, cpu, gpu,
                    cpu != null ? cpu.sampling() : gpu == null ? outcome
                            : new Outcome(Status.UNKNOWN, "CPU not observed by this GPU record"),
                    gpu != null ? gpu.availability()
                            : new Outcome(Status.UNKNOWN, "GPU not observed by this CPU record"));
        }
        public Entry {
            java.util.Objects.requireNonNull(invocation); java.util.Objects.requireNonNull(outcome);
            java.util.Objects.requireNonNull(cpuOutcome); java.util.Objects.requireNonNull(gpuOutcome);
            if (cpu != null) require(cpu.invocation().equals(invocation) && cpu.sampling().equals(cpuOutcome), "CPU entry mismatch");
            else require(cpuOutcome.status() != Status.MEASURED, "measured CPU requires payload");
            if (gpu != null) require(gpu.invocation().equals(invocation) && gpu.availability().equals(gpuOutcome), "GPU entry mismatch");
            else require(gpuOutcome.status() != Status.MEASURED, "measured GPU requires payload");
        }
    }
    public record Batch(List<Entry> events, List<Coverage> coverage, Health health) {}
    public static final class CapacityException extends IllegalStateException {
        CapacityException(String reason) { super(reason); }
    }
    private record Mark(Invocation invocation, long startNs, Long cpuStartNs, Clock clock) {}
    private record Pending(Invocation invocation, Clock clock, Backend backend) {}
    private final String captureId;
    private final String cohortId;
    private final int scopeLimit, eventLimit, activeLimit, pendingLimit;
    private final Map<ScopeKey, Scope> scopes = new LinkedHashMap<>();
    private final Map<Integer, Scope> byId = new HashMap<>();
    private final ArrayDeque<Mark> stack = new ArrayDeque<>();
    private final Map<Long, Pending> pending = new LinkedHashMap<>();
    private final ArrayList<Entry> events = new ArrayList<>();
    private final ArrayList<Coverage> coverage = new ArrayList<>();
    private final Set<Integer> observed = new HashSet<>();
    private final Set<Long> gpuIssuedActive = new HashSet<>();
    private FrameId frame;
    private long nextEvent = 1, ordinal, conflicts, drops, imbalances, unresolved;
    private boolean stopped, closed;
    private String stopReason = "healthy";

    public ScopeRegistry(String captureId, String cohortId, int scopeLimit, int eventLimit,
                         int activeLimit, int pendingLimit) {
        text(captureId); text(cohortId); positive(scopeLimit); positive(eventLimit);
        positive(activeLimit); positive(pendingLimit);
        this.captureId = captureId; this.cohortId = cohortId; this.scopeLimit = scopeLimit;
        this.eventLimit = eventLimit; this.activeLimit = activeLimit; this.pendingLimit = pendingLimit;
    }
    public Scope register(ScopeKey key, String name, Set<String> features, String confidence, Outcome expected) {
        running();
        Scope previous = scopes.get(key);
        if (previous != null) {
            require(previous.name().equals(name) && previous.featureLinks().equals(features)
                    && previous.confidence().equals(confidence) && previous.expected().equals(expected), "conflicting scope registration");
            return previous;
        }
        if (scopes.size() == scopeLimit) fail("scope limit");
        Scope scope = new Scope(scopes.size() + 1, key, name, features, confidence, expected);
        scopes.put(key, scope); byId.put(scope.id(), scope);
        return scope;
    }
    public List<Scope> scopes() { return List.copyOf(scopes.values()); }

    /** A boundary never reassigns open invocations or pending queries to the new frame/graph. */
    public void beginFrame(FrameId next) {
        running();
        require(next.captureId().equals(captureId) && next.cohortId().equals(cohortId), "foreign owner");
        if (frame != null) {
            require(next.epoch() > frame.epoch() || (next.epoch() == frame.epoch()
                    && next.frameId() > frame.frameId()), "frame counter reset needs a new epoch");
            require(next.graphGeneration() >= frame.graphGeneration(), "graph generation regressed");
            finishFrame("partial frame at boundary");
        }
        frame = next; ordinal = 0; observed.clear();
    }
    /** Scope ID only on invocation paths. No hot-path name lookup. */
    public Invocation begin(int scopeId, long threadId, String contextId, String dhStage,
                            String worldShadow, String opacity, long wallNs, Long threadCpuNs, Clock clock) {
        running(); require(frame != null, "frame required");
        Scope scope = scope(scopeId);
        require(scope.key().graphGeneration() == frame.graphGeneration(), "scope belongs to old graph");
        if (stack.size() == activeLimit) fail("active scope limit");
        if (!stack.isEmpty()) require(stack.peek().invocation.threadId() == threadId
                && stack.peek().invocation.contextId().equals(contextId), "cross-thread/context nesting");
        if (threadCpuNs != null) require(threadCpuNs >= 0, "thread CPU clock");
        java.util.Objects.requireNonNull(clock);
        if (nextEvent == Long.MAX_VALUE || ordinal == Long.MAX_VALUE) fail("invocation counter exhausted");
        Invocation invocation = new Invocation(nextEvent++, stack.isEmpty() ? null : stack.peek().invocation.eventId(),
                frame, scopeId, ++ordinal, threadId, contextId, dhStage, worldShadow, opacity,
                new Outcome(Status.UNKNOWN, "execution observed; measurement not yet completed"));
        stack.push(new Mark(invocation, wallNs, threadCpuNs, clock)); observed.add(scopeId);
        return invocation;
    }
    public void end(long eventId, long wallNs, Long threadCpuNs) {
        running();
        if (stack.isEmpty() || stack.peek().invocation.eventId() != eventId) {
            conflicts++;
            invalidateOpen("unbalanced scope end");
            throw new IllegalArgumentException("unbalanced scope end");
        }
        Mark mark = stack.pop();
        gpuIssuedActive.remove(eventId);
        if (wallNs < mark.startNs || (threadCpuNs != null && (threadCpuNs < 0
                || (mark.cpuStartNs != null && threadCpuNs < mark.cpuStartNs)))) {
            emit(new Entry(mark.invocation, new Outcome(Status.INVALID, "CPU clock reset"), null, null)); return;
        }
        Long delta = mark.cpuStartNs == null || threadCpuNs == null ? null : threadCpuNs - mark.cpuStartNs;
        Outcome outcome = new Outcome(Status.MEASURED, "balanced CPU scope");
        CpuEvent cpu;
        try {
            cpu = new CpuEvent(mark.invocation, mark.startNs, wallNs, delta, mark.clock, outcome, "unknown", "scope boundary");
        } catch (ArithmeticException | IllegalArgumentException invalid) {
            emit(new Entry(mark.invocation, new Outcome(Status.INVALID, "CPU duration overflow or inconsistent thread clock"), null, null)); return;
        }
        emit(new Entry(mark.invocation, outcome, cpu, null));
    }
    public void endMissing(long eventId, Outcome outcome) {
        running();
        require(outcome.status() != Status.MEASURED && outcome.status() != Status.PENDING, "terminal missing status required");
        if (stack.isEmpty() || stack.peek().invocation.eventId() != eventId) {
            conflicts++; invalidateOpen("unbalanced missing end");
            throw new IllegalArgumentException("unbalanced missing end");
        }
        Mark mark = stack.pop(); gpuIssuedActive.remove(eventId);
        emit(new Entry(mark.invocation, outcome, new CpuEvent(mark.invocation, null, null, null,
                mark.clock, outcome, "unknown", "measurement unavailable"), null));
    }
    public void issueGpu(Invocation invocation, Clock clock, Backend backend) {
        running();
        // Only an active owned invocation can issue; callers cannot fabricate ownership.
        require(stack.stream().anyMatch(m -> m.invocation.equals(invocation)), "GPU issue requires active invocation");
        require(!gpuIssuedActive.contains(invocation.eventId()), "duplicate GPU issue");
        if (pending.size() == pendingLimit) fail("pending limit");
        Outcome outcome = new Outcome(Status.PENDING, "awaiting backend result");
        GpuEvent event = new GpuEvent(invocation, null, null, null, null, clock, backend, outcome);
        emit(new Entry(invocation, outcome, null, event));
        pending.put(invocation.eventId(), new Pending(invocation, clock, backend));
        gpuIssuedActive.add(invocation.eventId());
    }
    /** Old-epoch/graph results retain issue identity. Retrieval may occur in a later frame. */
    public void resolveGpu(long eventId, FrameId retrievalFrame, Long begin, Long end, Long duration, Outcome outcome) {
        running();
        Pending owned = pending.get(eventId); require(owned != null, "unknown/duplicate late result");
        require(outcome.status() != Status.PENDING, "result must be terminal");
        GpuEvent event = new GpuEvent(owned.invocation, retrievalFrame, begin, end, duration, owned.clock, owned.backend, outcome);
        emit(new Entry(owned.invocation, outcome, null, event)); pending.remove(eventId);
    }
    /** Explicit row for unsampled/disabled/unsupported/etc. Never infer absence from no invocation. */
    public void missing(int scopeId, Outcome outcome) {
        running(); require(frame != null, "frame required");
        Scope scope = scope(scopeId);
        require(scope.key().graphGeneration() == frame.graphGeneration(), "old graph coverage");
        require(outcome.status() != Status.MEASURED && outcome.status() != Status.PENDING, "missing status required");
        require(!observed.contains(scopeId), "scope already observed/classified");
        addCoverage(scopeId, outcome); observed.add(scopeId);
    }
    private void addCoverage(int scopeId, Outcome outcome) {
        room(); coverage.add(new Coverage(frame, scopeId, new Counts(null, outcome.status() == Status.NOT_EXECUTED ? 0L : null, null, null, null), outcome,
                "unknown", "unmapped; stage 3", "unobserved"));
    }
    private void finishFrame(String reason) {
        invalidateOpen(reason);
        for (Scope scope : scopes.values()) if (scope.key().graphGeneration() == frame.graphGeneration()
                && !observed.contains(scope.id())) {
            // Even expected-disabled configuration needs a per-frame verified observation.
            addCoverage(scope.id(), new Outcome(Status.UNKNOWN, "no execution/eligibility evidence for frame"));
        }
    }
    private void invalidateOpen(String reason) {
        while (!stack.isEmpty()) {
            Mark mark = stack.pop(); gpuIssuedActive.remove(mark.invocation.eventId()); imbalances++;
            emit(new Entry(mark.invocation, new Outcome(Status.INVALID, reason), null, null));
        }
    }
    /** Caller supplies the partial-boundary reason. Bounded tail finalization, no polling or waiting. */
    public void close(String reason) {
        text(reason);
        if (closed) return;
        if (!stopped) {
            finishIfPresent(reason);
            for (Pending item : List.copyOf(pending.values())) {
                Outcome outcome = new Outcome(Status.DROPPED, "unresolved tail: " + reason);
                emit(new Entry(item.invocation, outcome, null,
                        new GpuEvent(item.invocation, null, null, null, null, item.clock, item.backend, outcome)));
                pending.remove(item.invocation.eventId());
                unresolved++;
            }
        }
        closed = true;
    }
    private void finishIfPresent(String reason) { if (frame != null) finishFrame(reason); }
    public Health health() {
        return new Health(scopeLimit, eventLimit, activeLimit, pendingLimit, null, conflicts, 0,
                drops, null, unresolved + pending.size(), imbalances + (stopped ? stack.size() : 0),
                null, null, null, null, new Outcome(stopped ? Status.DROPPED : closed ? Status.UNKNOWN : Status.PENDING,
                stopped ? stopReason : closed ? "closed; consult per-event validity" : "collecting"));
    }
    public Batch drain() {
        Batch batch = new Batch(List.copyOf(events), List.copyOf(coverage), health());
        events.clear(); coverage.clear(); return batch;
    }
    private Scope scope(int id) { Scope result = byId.get(id); require(result != null, "unregistered scope"); return result; }
    private void emit(Entry entry) { room(); events.add(entry); }
    private void room() { if (events.size() + coverage.size() == eventLimit) fail("event buffer full"); }
    private void fail(String reason) { stopped = true; drops++; stopReason = reason; throw new CapacityException(reason); }
    private void running() { if (stopped || closed) throw new IllegalStateException("collector stopped/closed: " + stopReason); }
}
