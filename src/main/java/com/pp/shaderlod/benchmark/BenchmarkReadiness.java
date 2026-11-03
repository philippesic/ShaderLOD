package com.pp.shaderlod.benchmark;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/** Pure observation gate. Does not poll a platform, freeze clocks, or infer GPU busy time. */
public final class BenchmarkReadiness {
    public enum Signal { READY, BUSY, UNAVAILABLE }
    public enum Eligibility { ELIGIBLE, UNAVAILABLE, UNKNOWN }
    public enum State { WARMING, READY, CAPTURING, COMPLETE, INVALID, UNAVAILABLE, TIMED_OUT, CANCELLED }
    /** Counts are observed executed work in the snapshot's documented scope, not requested work. */
    public record WorkEvidence(Eligibility eligibility, OptionalLong executed, String reason) {
        public WorkEvidence {
            Objects.requireNonNull(eligibility); Objects.requireNonNull(executed); Objects.requireNonNull(reason);
            if (executed.isPresent() && executed.getAsLong() < 0) throw new IllegalArgumentException("Negative work");
        }
    }
    /** Current rendered geometry, not cumulative lifetime draw calls. Generation detects rebuilds. */
    public record Geometry(long draws, long indices, long generation) {
        public Geometry {
            if (draws < 0 || indices < 0 || generation < 0) throw new IllegalArgumentException("Negative geometry");
        }
    }
    public record Observation(long atNanos, boolean cameraAcknowledged,
                              BenchmarkScenario.Settings actualSettings,
                              Signal meshing, Signal lighting, Signal pipeline, Signal temporalHistory,
                              BenchmarkScenario.DhState dh,
                              Signal dhLoading, Signal dhConversion, Geometry dhGeometry,
                              Map<BenchmarkScenario.Work, WorkEvidence> work) {
        public Observation {
            Objects.requireNonNull(meshing); Objects.requireNonNull(lighting);
            Objects.requireNonNull(pipeline); Objects.requireNonNull(temporalHistory);
            Objects.requireNonNull(dh); Objects.requireNonNull(dhLoading); Objects.requireNonNull(dhConversion);
            work = Map.copyOf(work);
        }
    }
    /** Platform/case-specific policy; defaults are provisional usability settings, not qualification. */
    public record Policy(Duration minimumWarm, Duration stableFor, Duration maximumObservationGap,
                         Duration timeout, int minimumObservations, long drawTolerance, long indexTolerance) {
        public Policy {
            BenchmarkScenario.positive(minimumWarm); BenchmarkScenario.positive(stableFor);
            BenchmarkScenario.positive(maximumObservationGap); BenchmarkScenario.positive(timeout);
            if (minimumObservations < 2 || drawTolerance < 0 || indexTolerance < 0)
                throw new IllegalArgumentException("Need multiple observations and nonnegative tolerances");
            if (timeout.compareTo(minimumWarm) < 0 || timeout.compareTo(stableFor) < 0)
                throw new IllegalArgumentException("Warm/stability interval exceeds timeout");
        }
        public static Policy forCase(BenchmarkScenario.Case scenario) {
            return new Policy(Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMillis(500),
                    scenario.budgets().warm(), 3, 0, 0);
        }
    }
    public record Coverage(BenchmarkScenario.Work requested, Eligibility eligible,
                           OptionalLong executed, String reason) {}
    /** Stability fields describe accepted pre-capture evidence; streaming never becomes steady coverage. */
    public record Result(State state, BenchmarkScenario.Workload workload,
                         List<String> reasons, List<Coverage> coverage,
                         Observation latest, int stableObservations, long stableNanos,
                         int captureObservations) {
        public Result { reasons = List.copyOf(reasons); coverage = List.copyOf(coverage); }
    }

    private final BenchmarkScenario.Case scenario;
    private final Policy policy;
    private final long started;
    private long lastTime, stableSince, clockTime;
    private int stableObservations;
    private int captureObservations;
    private long acceptedStableNanos;
    private boolean captureBegan;
    private final Map<BenchmarkScenario.Work, WorkEvidence> captureWork = new EnumMap<>(BenchmarkScenario.Work.class);
    private Geometry anchor;
    private Observation latest;
    private State state = State.WARMING;
    private List<String> reasons = List.of("No observations received");

    public BenchmarkReadiness(BenchmarkScenario.Case scenario, Policy policy, long startedAtNanos) {
        this.scenario = Objects.requireNonNull(scenario);
        this.policy = Objects.requireNonNull(policy);
        started = lastTime = clockTime = startedAtNanos;
        stableSince = startedAtNanos;
    }

    public Result observe(Observation observation) {
        Objects.requireNonNull(observation);
        checkTime(observation.atNanos());
        if (terminal()) return result();
        if (state == State.WARMING && observation.atNanos() - started >= policy.timeout().toNanos())
            return finish(State.TIMED_OUT, append(reasons, "Readiness deadline exhausted"));
        if (latest != null && observation.atNanos() == lastTime)
            throw new IllegalArgumentException("A fresh observation needs a later monotonic timestamp");
        boolean gap = latest != null && observation.atNanos() - lastTime > policy.maximumObservationGap().toNanos();
        lastTime = observation.atNanos();
        latest = observation;
        var unavailable = unavailable(observation);
        if (!unavailable.isEmpty()) return finish(State.UNAVAILABLE, unavailable);
        if (state == State.CAPTURING) {
            captureObservations++;
            observation.work().forEach((work, evidence) -> captureWork.merge(work, evidence,
                    (a, b) -> a.executed().orElse(0) >= b.executed().orElse(0) ? a : b));
        }
        var pending = pending(observation, state == State.CAPTURING);
        if (gap) pending.add("Observation gap exceeds policy");
        boolean geometryChanged = geometryChanged(observation.dhGeometry());
        if (geometryChanged) pending.add("DH draws/indices or geometry generation changed");
        if (!pending.isEmpty()) {
            if (state == State.CAPTURING) return finish(State.INVALID, pending);
            state = State.WARMING;
            stableObservations = 0;
            anchor = null;
            stableSince = lastTime;
            reasons = List.copyOf(pending);
        } else if (state != State.CAPTURING) {
            if (stableObservations == 0) {
                stableSince = lastTime;
                anchor = observation.dhGeometry();
            }
            stableObservations++;
            if (lastTime - started >= policy.minimumWarm().toNanos()
                    && lastTime - stableSince >= policy.stableFor().toNanos()
                    && stableObservations >= policy.minimumObservations()) {
                state = State.READY;
                reasons = List.of("Acknowledged, required work observed and stability interval satisfied");
            } else reasons = List.of("Collecting observed warm/stability interval");
        }
        if (state == State.WARMING && lastTime - started >= policy.timeout().toNanos())
            return finish(State.TIMED_OUT, append(reasons, "Readiness deadline exhausted"));
        return result();
    }

    /** Explicit boundary. A first observation, even with all READY flags, cannot enter capture. */
    public Result beginCapture(long nowNanos) {
        checkTime(nowNanos);
        if (state != State.READY) throw new IllegalStateException("Capture requires observed readiness");
        if (nowNanos - lastTime > policy.maximumObservationGap().toNanos())
            return finish(State.INVALID, List.of("Readiness evidence stale at capture entry"));
        state = State.CAPTURING;
        captureBegan = true;
        acceptedStableNanos = lastTime - stableSince;
        reasons = List.of(scenario.workload() == BenchmarkScenario.Workload.STREAMING
                ? "Streaming capture: intended queue/geometry changes allowed" : "Steady capture: continued settlement required");
        return result();
    }

    /** Also call without observations so unavailable callbacks cannot leave an unbounded warm-up. */
    public Result tick(long nowNanos) {
        checkTime(nowNanos);
        if (terminal()) return result();
        if ((state == State.CAPTURING || state == State.READY)
                && nowNanos - lastTime > policy.maximumObservationGap().toNanos())
            return finish(State.INVALID, List.of("Readiness observation stream stopped"));
        if (state == State.WARMING && nowNanos - started >= policy.timeout().toNanos())
            return finish(State.TIMED_OUT, append(reasons, "Readiness deadline exhausted"));
        return result();
    }

    public Result complete(long nowNanos) {
        tick(nowNanos);
        if (state == State.CAPTURING) {
            var missing = new ArrayList<String>();
            if (captureObservations == 0) missing.add("No readiness observations during capture");
            for (var work : scenario.requestedWork()) {
                var evidence = captureWork.get(work);
                if (evidence == null || evidence.executed().orElse(0) == 0)
                    missing.add(work + " not observed executing during capture");
            }
            if (!missing.isEmpty()) return finish(State.INVALID, missing);
            return finish(State.COMPLETE, List.of("Capture ended with continuous readiness evidence"));
        }
        if (!terminal()) throw new IllegalStateException("No active capture to complete");
        return result();
    }
    public Result cancel() { return terminal() ? result() : finish(State.CANCELLED, List.of("Cancelled")); }
    public Result result() {
        var coverage = new ArrayList<Coverage>();
        // Enum declaration order makes exports stable, independent of Set/Map iteration order.
        for (var work : BenchmarkScenario.Work.values()) if (scenario.requestedWork().contains(work)) {
            var current = latest == null ? null : latest.work().get(work);
            var executed = captureBegan ? captureWork.get(work) : current;
            coverage.add(new Coverage(work, current == null ? Eligibility.UNKNOWN : current.eligibility(),
                    executed == null ? OptionalLong.empty() : executed.executed(),
                    current == null ? "No work evidence" : current.reason()));
        }
        return new Result(state, scenario.workload(), reasons, coverage, latest, stableObservations,
                captureBegan ? acceptedStableNanos : stableObservations == 0 ? 0 : lastTime - stableSince,
                captureObservations);
    }

    private List<String> unavailable(Observation o) {
        var out = new ArrayList<String>();
        if (o.meshing() == Signal.UNAVAILABLE) out.add("Meshing observation unavailable");
        if (o.lighting() == Signal.UNAVAILABLE) out.add("Lighting observation unavailable");
        if (o.pipeline() == Signal.UNAVAILABLE) out.add("Pipeline observation unavailable");
        if (o.temporalHistory() == Signal.UNAVAILABLE) out.add("Temporal-history observation unavailable");
        if (o.dh() == BenchmarkScenario.DhState.UNKNOWN) out.add("DH capability/state unavailable");
        if (scenario.settings().dh() != BenchmarkScenario.DhState.ABSENT
                && o.dh() == BenchmarkScenario.DhState.ABSENT) out.add("Required DH installation is absent");
        if (scenario.settings().dh() == BenchmarkScenario.DhState.ENABLED) {
            if (o.dhLoading() == Signal.UNAVAILABLE) out.add("DH loading queue unavailable");
            if (o.dhConversion() == Signal.UNAVAILABLE) out.add("DH conversion queue unavailable");
            if (o.dhGeometry() == null) out.add("DH draw/index geometry observation unavailable");
        }
        for (var requested : BenchmarkScenario.Work.values()) if (scenario.requestedWork().contains(requested)
                && !(state == State.CAPTURING && scenario.workload() == BenchmarkScenario.Workload.STREAMING)) {
            var evidence = o.work().get(requested);
            if (evidence == null || evidence.eligibility() != Eligibility.ELIGIBLE || evidence.executed().isEmpty())
                out.add(requested + " eligibility/executed-work observation unavailable"
                        + (evidence == null ? "" : ": " + evidence.reason()));
        }
        return out;
    }
    private ArrayList<String> pending(Observation o, boolean capturing) {
        var out = new ArrayList<String>();
        if (!o.cameraAcknowledged()) out.add("Camera/environment not acknowledged");
        if (!scenario.settings().equals(o.actualSettings()) || o.dh() != scenario.settings().dh())
            out.add("Selected RD/framebuffer/DH expectations not acknowledged");
        boolean streaming = capturing && scenario.workload() == BenchmarkScenario.Workload.STREAMING;
        if (!streaming && (o.meshing() != Signal.READY || o.lighting() != Signal.READY))
            out.add("Chunk meshing/lighting pending");
        if (o.pipeline() != Signal.READY || o.temporalHistory() != Signal.READY)
            out.add("Pipeline/native temporal pre-roll pending");
        if (!streaming && scenario.settings().dh() == BenchmarkScenario.DhState.ENABLED
                && (o.dhLoading() != Signal.READY || o.dhConversion() != Signal.READY))
            out.add("DH loading/conversion pending");
        // Work must be observed before entry. During streaming it may leave the frustum deliberately.
        if (scenario.workload() == BenchmarkScenario.Workload.STEADY) for (var work : scenario.requestedWork()) {
            var evidence = o.work().get(work);
            if (evidence.executed().orElse(0) == 0) out.add(work + " has no observed executed work");
        }
        if (!streaming && scenario.requestedWork().contains(BenchmarkScenario.Work.DH_TERRAIN)
                && o.dhGeometry() != null && (o.dhGeometry().draws() == 0 || o.dhGeometry().indices() == 0))
            out.add("Expected DH terrain is not drawn");
        return out;
    }
    private boolean geometryChanged(Geometry geometry) {
        if (scenario.settings().dh() != BenchmarkScenario.DhState.ENABLED || anchor == null || geometry == null)
            return false;
        if (state == State.CAPTURING && scenario.workload() == BenchmarkScenario.Workload.STREAMING) return false;
        return geometry.generation() != anchor.generation()
                || difference(geometry.draws(), anchor.draws()) > policy.drawTolerance()
                || difference(geometry.indices(), anchor.indices()) > policy.indexTolerance();
    }
    private static long difference(long a, long b) { return Math.max(a, b) - Math.min(a, b); }
    private void checkTime(long now) {
        if (now < clockTime) throw new IllegalArgumentException("Monotonic time moved backwards");
        clockTime = now;
    }
    private boolean terminal() {
        return switch (state) {
            case COMPLETE, INVALID, UNAVAILABLE, TIMED_OUT, CANCELLED -> true;
            default -> false;
        };
    }
    private Result finish(State next, List<String> why) { state = next; reasons = List.copyOf(why); return result(); }
    private static List<String> append(List<String> input, String reason) {
        var out = new ArrayList<>(input); out.add(reason); return out;
    }
}
