package com.pp.shaderlod.dev.timing.costmap;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import static com.pp.shaderlod.dev.timing.costmap.CostMapRecords.*;

/** CPU-only acceptance fixtures: no Minecraft, GL, clock reads, native code or display. */
public final class CostMapContractTest {
    private static final Clock CPU = new Clock("cpu.monotonic", 64, 1, 0);
    private static final Clock GPU = new Clock("gpu.context1", 8, 1, 0);
    private static final Outcome UNKNOWN = new Outcome(Status.UNKNOWN, "synthetic unknown");
    private static final Outcome MEASURED = new Outcome(Status.MEASURED, "synthetic measured");
    private static int checks;
    public static void main(String[] args) {
        repeatedNames(); scopeBalance(); graphChangesAndLateResults(); counters(); partialBoundaries();
        overflow(); malformedRecords(); missingnessAndDh();
        System.out.println("Cost-map stage 1: " + checks + " checks passed (8 acceptance groups)");
    }
    private static ScopeRegistry registry(int events) { return new ScopeRegistry("capture", "cohort", 8, events, 4, 4); }
    private static FrameId frame(long epoch, long id, long graph) { return new FrameId("capture", "cohort", 1, epoch, id, graph); }
    private static Scope scope(ScopeRegistry r, long graph, String program) {
        return r.register(new ScopeKey(graph, "composite", program, Operation.DRAW_GROUP, "fixture"),
                "repeated name", Set.of("mixed"), "unknown", UNKNOWN);
    }
    private static Invocation begin(ScopeRegistry r, Scope s) {
        return r.begin(s.id(), 1, "context1", "main", "world", "mixed", 10, null, CPU);
    }
    private static void repeatedNames() {
        ScopeRegistry r = registry(32);
        Scope a = scope(r, 1, "a"), b = scope(r, 1, "b");
        check(a.id() != b.id(), "same display name distinct programs");
        check(scope(r, 1, "a").id() == a.id(), "stable registration");
        r.beginFrame(frame(1, 1, 1));
        Invocation parent = begin(r, a), child = begin(r, a);
        check(child.parentEventId() == parent.eventId(), "same-name recursive parent");
        r.end(child.eventId(), 10, null); r.end(parent.eventId(), 20, null);
        Invocation repeat = begin(r, a); r.end(repeat.eventId(), 11, null);
        check(repeat.executionOrdinal() == 3 && repeat.eventId() != parent.eventId(), "unique ordinal/event");
        var entries = r.drain().events();
        check(entries.getFirst().gpuOutcome().status() == Status.UNKNOWN, "CPU-only record cannot imply zero GPU cost");
        check(entries.getFirst().cpu().wallEndNs() == 10 && entries.getFirst().cpu().threadCpuNs() == null,
                "measured zero wall and unavailable thread CPU preserved");
    }
    private static void scopeBalance() {
        ScopeRegistry r = registry(32); Scope s = scope(r, 1, "a"); r.beginFrame(frame(1, 1, 1));
        Invocation a = begin(r, s); begin(r, s);
        rejects(() -> r.end(a.eventId(), 20, null));
        var batch = r.drain();
        check(batch.events().size() == 2 && batch.events().stream().allMatch(e -> e.outcome().status() == Status.INVALID), "imbalances invalidate all open scopes");
        check(batch.health().imbalancedScopes() == 2 && batch.health().conflicts() == 1, "balance health");
        Invocation recovered = begin(r, s); r.end(recovered.eventId(), 20, null);
        rejects(() -> r.end(recovered.eventId(), 20, null));
        check(r.drain().health().conflicts() == 2, "duplicate end visible");
    }
    private static void graphChangesAndLateResults() {
        ScopeRegistry r = registry(32); Scope old = scope(r, 1, "a"); r.beginFrame(frame(1, 1, 1));
        Invocation issued = begin(r, old); r.issueGpu(issued, GPU, Backend.TIMESTAMP); r.end(issued.eventId(), 20, null);
        Scope next = scope(r, 2, "a"); check(next.id() != old.id(), "graph scopes separate");
        FrameId retrieval = frame(2, 1, 2); r.beginFrame(retrieval);
        rejects(() -> begin(r, old));
        r.resolveGpu(issued.eventId(), retrieval, 250L, 5L, 11L, MEASURED);
        var batch = r.drain(); var gpu = batch.events().getLast().gpu();
        check(gpu.invocation().frame().equals(frame(1, 1, 1)) && gpu.retrievalFrame().equals(retrieval), "late query keeps issue owner and retrieval frame");
        rejects(() -> r.resolveGpu(issued.eventId(), retrieval, 250L, 5L, 11L, MEASURED));
        rejects(() -> r.beginFrame(frame(2, 1, 2)));
        Invocation current = begin(r, next);
        r.issueGpu(current, GPU, Backend.ELAPSED); r.end(current.eventId(), 20, null);
        rejects(() -> r.resolveGpu(current.eventId(), new FrameId("other", "cohort", 1, 2, 2, 2), null, null, 1L, MEASURED));
        r.resolveGpu(current.eventId(), retrieval, null, null, 0L, MEASURED);
        check(r.drain().events().getLast().gpu().elapsedNs() == 0, "elapsed valid zero has no endpoints");
    }
    private static void counters() {
        check(CounterDelta.decode(250, 5, GPU, GPU, 100).elapsedNs() == 11, "8-bit wrap");
        Clock wide = new Clock("gpu", 64, 1, 0);
        check(CounterDelta.decode(-4, 3, wide, wide, 100).elapsedNs() == 7, "unsigned 64-bit wrap");
        check(CounterDelta.decode(5, 5, GPU, GPU, 10).elapsedNs() == 0, "counter zero valid");
        check(CounterDelta.decode(5, 2, GPU, new Clock("gpu.context1", 8, 1, 1), 100).outcome().status() == Status.INVALID, "reset epoch");
        check(CounterDelta.decode(5, 2, GPU, GPU, 100).outcome().status() == Status.INVALID, "implausible interval");
        check(CounterDelta.decode(0, 1, GPU, GPU, 256).outcome().status() == Status.INVALID, "ambiguous multiple wraps");
        check(CounterDelta.decode(256, 1, GPU, GPU, 100).outcome().status() == Status.INVALID, "out of width");
    }
    private static void partialBoundaries() {
        ScopeRegistry r = registry(32); Scope s = scope(r, 1, "a"), unseen = scope(r, 1, "b");
        r.beginFrame(frame(1, 1, 1)); Invocation a = begin(r, s); r.issueGpu(a, GPU, Backend.ELAPSED);
        r.beginFrame(frame(1, 2, 1));
        var boundary = r.drain();
        check(boundary.events().getLast().outcome().status() == Status.INVALID, "partial open scope invalid");
        check(boundary.coverage().stream().anyMatch(c -> c.scopeId() == unseen.id() && c.outcome().status() == Status.UNKNOWN), "unseen scope explicitly unknown");
        r.close("partial capture end"); var tail = r.drain();
        check(tail.events().getLast().gpu().availability().status() == Status.DROPPED && tail.health().unresolvedTail() == 1, "unresolved tail explicit");
        rejects(() -> r.resolveGpu(a.eventId(), frame(1, 2, 1), null, null, 1L, MEASURED));
        Frame f = new Frame(frame(1, 1, 1), 10, null, null, null, false, true, true, true,
                Set.of(1), UNKNOWN, "partial boundaries");
        check(f.partialStart() && f.partialEnd() && f.nextStartNs() == null, "partial frame schema");
    }
    private static void overflow() {
        ScopeRegistry r = registry(1); Scope s = scope(r, 1, "a"); r.beginFrame(frame(1, 1, 1));
        Invocation a = begin(r, s); r.end(a.eventId(), 20, null);
        Invocation b = begin(r, s); rejects(() -> r.end(b.eventId(), 20, null));
        var batch = r.drain();
        check(batch.events().size() == 1 && batch.health().droppedEvents() == 1
                && batch.health().outcome().status() == Status.DROPPED, "full event buffer retains evidence and loss");
        rejects(() -> begin(r, s)); check(r.drain().health().droppedEvents() == 1, "draining cannot restart stopped collector");
        ScopeRegistry scopes = new ScopeRegistry("capture", "cohort", 1, 8, 1, 1);
        Scope one = scope(scopes, 1, "a"); rejects(() -> scope(scopes, 1, "b"));
        check(scopes.scopes().size() == 1 && scopes.health().droppedEvents() == 1, "scope capacity");
        ScopeRegistry active = new ScopeRegistry("capture", "cohort", 1, 8, 1, 1);
        Scope sa = scope(active, 1, "a"); active.beginFrame(frame(1, 1, 1)); begin(active, sa);
        rejects(() -> begin(active, sa)); check(active.health().imbalancedScopes() == 1, "active capacity accounted");
        ScopeRegistry pending = new ScopeRegistry("capture", "cohort", 1, 8, 1, 1);
        Scope sp = scope(pending, 1, "a"); pending.beginFrame(frame(1, 1, 1));
        Invocation p1 = begin(pending, sp); pending.issueGpu(p1, GPU, Backend.ELAPSED); pending.end(p1.eventId(), 20, null);
        Invocation p2 = begin(pending, sp); rejects(() -> pending.issueGpu(p2, GPU, Backend.ELAPSED));
        check(pending.health().unresolvedTail() == 1 && pending.health().droppedEvents() == 1, "pending capacity");
        ScopeRegistry closing = registry(1); Scope sc = scope(closing, 1, "a"); closing.beginFrame(frame(1, 1, 1));
        Invocation ic = begin(closing, sc); closing.issueGpu(ic, GPU, Backend.ELAPSED);
        closing.drain(); closing.end(ic.eventId(), 20, null);
        rejects(() -> closing.close("full tail"));
        check(closing.health().unresolvedTail() == 1 && closing.health().droppedEvents() == 1, "tail overflow preserves pending loss once");
    }
    private static void malformedRecords() {
        rejects(() -> new Outcome(Status.UNKNOWN, "")); rejects(() -> frame(0, 1, 1));
        rejects(() -> new Clock("gpu", 65, 1, 0));
        rejects(() -> new DhIdentity(DhState.UNKNOWN, "not observed", 0, Map.of(), Map.of()));
        rejects(() -> new Counts(-1L, null, null, null, null));
        rejects(() -> new Aggregate("cohort", 1, "gpu", "eligible frames", 1, Set.of(1L), Set.of("process"),
                0L, 0L, 0L, 0L, 0.0, Map.of(), "no zero fill", "unknown", "fixture", UNKNOWN));
        Aggregate unavailable = new Aggregate("cohort", 1, "gpu", "eligible frames", 1, Set.of(1L), Set.of("process"),
                null, null, null, null, null, Map.of(Status.UNSAMPLED, 1L), "no zero fill", "unknown", "fixture", UNKNOWN);
        check(unavailable.meanNs() == null && unavailable.missingness().get(Status.UNSAMPLED) == 1, "aggregate missingness contract");
        ScopeRegistry r = registry(32); Scope s = scope(r, 1, "a"); r.beginFrame(frame(1, 1, 1)); Invocation i = begin(r, s);
        rejects(() -> new CpuEvent(i, 10L, 9L, null, CPU, MEASURED, "unknown", "test"));
        rejects(() -> new CpuEvent(i, 10L, 20L, -1L, CPU, MEASURED, "unknown", "test"));
        rejects(() -> new CpuEvent(i, 10L, 20L, 100L, CPU, MEASURED, "unknown", "test"));
        rejects(() -> new Frame(frame(1, 1, 1), 10, 9L, null, null, false, false, false, false, Set.of(), UNKNOWN, "invalid clock"));
        rejects(() -> new Frame(frame(1, 1, 1), 10, null, 10L, null, false, true, true, false, Set.of(), UNKNOWN, "partial root"));
        rejects(() -> new ScopeRegistry.Entry(i, MEASURED, null, null));
        rejects(() -> new CpuEvent(i, 0L, 0L, 0L, CPU, UNKNOWN, "unknown", "test"));
        rejects(() -> new GpuEvent(i, frame(1, 1, 1), null, null, 0L, GPU, Backend.TIMESTAMP, MEASURED));
        rejects(() -> new GpuEvent(i, null, null, null, 0L, GPU, Backend.ELAPSED, UNKNOWN));
        rejects(() -> new Capture(2, "capture", "cohort", "run", "process", Map.of(),
                dh(DhState.ABSENT), 1, 16));
        rejects(() -> new Capture(1, "capture", "cohort", "run", "process", Map.of(),
                dh(DhState.ABSENT), 1, 16));
        rejects(() -> r.register(s.key(), "conflicting name", Set.of("mixed"), "unknown", UNKNOWN));
        rejects(() -> new GpuEvent(i, frame(1, 1, 1), null, null, -1L, GPU, Backend.ELAPSED, MEASURED));
        r.issueGpu(i, GPU, Backend.ELAPSED);
        r.resolveGpu(i.eventId(), frame(1, 1, 1), null, null, 1L, MEASURED);
        rejects(() -> r.issueGpu(i, GPU, Backend.ELAPSED));
        r.end(i.eventId(), 9, null); check(r.drain().events().getLast().outcome().status() == Status.INVALID, "CPU reset invalid");
    }
    private static void missingnessAndDh() {
        for (Status status : Status.values()) {
            if (status == Status.MEASURED || status == Status.PENDING) continue;
            ScopeRegistry r = registry(8); Scope s = scope(r, 1, "a"); r.beginFrame(frame(1, 1, 1));
            r.missing(s.id(), new Outcome(status, "explicit fixture reason")); r.close("end");
            var batch = r.drain(); check(batch.coverage().size() == 1 && batch.coverage().getFirst().outcome().status() == status, "missing status retained: " + status);
        }
        ScopeRegistry executed = registry(8); Scope s = scope(executed, 1, "a"); executed.beginFrame(frame(1, 1, 1));
        Invocation invocation = begin(executed, s);
        executed.endMissing(invocation.eventId(), new Outcome(Status.UNSAMPLED, "execution census only"));
        var cpu = executed.drain().events().getFirst().cpu();
        check(cpu.threadCpuNs() == null && cpu.wallStartNs() == null && cpu.sampling().status() == Status.UNSAMPLED,
                "executed unsampled invocation explicitly missing");
        for (DhState state : DhState.values()) {
            DhIdentity dh = dh(state);
            if (state == DhState.ABSENT) check(dh.renderingApplicability().status() == Status.NOT_APPLICABLE, "absent DH not fake zero");
            EnumMap<CaptureField, Fact> fields = new EnumMap<>(CaptureField.class);
            for (CaptureField key : CaptureField.values()) fields.put(key, new Fact("unknown", "synthetic not observed"));
            Capture capture = new Capture(1, "capture", "cohort", "run", "process", fields, dh, 123, 16);
            fields.clear(); check(capture.identity().size() == CaptureField.values().length, "manifest immutable complete: " + state);
        }
    }
    private static DhIdentity dh(DhState state) {
        EnumMap<DhField, Fact> facts = new EnumMap<>(DhField.class);
        for (DhField field : DhField.values()) facts.put(field, new Fact("unknown", "synthetic not observed"));
        return new DhIdentity(state, "synthetic state evidence", 100, facts,
                Map.of("workers", new Fact("unknown", "not observed")));
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        checks++; try { action.run(); } catch (IllegalArgumentException | IllegalStateException | NullPointerException expected) { return; }
        throw new AssertionError("malformed operation accepted");
    }
}
