package com.pp.shaderlod.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Serial, explicitly driven orchestration. No scheduler, polling thread, world copy or timer harness. */
public final class BenchmarkScenarioRun {
    public enum Stage { APPLY, ACKNOWLEDGE, WARM, ROUTE_ACKNOWLEDGE, CAPTURE, DRAIN, RESTORE, FINISHED }
    public enum Verdict { NOT_RUN, RUNNING, COMPLETE, INVALID, UNAVAILABLE, TIMED_OUT, CANCELLED, FAILED }
    public enum Outcome { RUNNING, COMPLETE, PARTIAL, CANCELLED, FAILED, RESTORATION_FAILED }

    /** All methods run on the caller's client thread and must return promptly; outer launcher owns hard kill. */
    public interface World {
        void apply(BenchmarkScenario.Case scenario);
        boolean acknowledged();
        void beginRoute(int durationTicks);
        boolean routeStarted();
        boolean routeComplete();
        void restore();
        boolean restored();
    }
    /**
     * Consumer owns clocks/samples. stopCapture must stop recording even on cancellation/failure,
     * and cancel pending work that cannot survive restoration. drained acknowledges the normal tail.
     */
    public interface Capture {
        void startCapture(BenchmarkScenario.Case scenario);
        void stopCapture(BenchmarkScenario.Case scenario);
        boolean drained();
    }
    public record CaseResult(BenchmarkScenario.Case requested, Verdict verdict,
                             BenchmarkReadiness.Result readiness, String reason) {}
    public record Result(Stage stage, Outcome outcome, List<CaseResult> cases,
                         BenchmarkReadiness.Result currentReadiness,
                         boolean restorationVerified, String reason) {
        public Result { cases = List.copyOf(cases); }
    }

    private final BenchmarkScenario.Schedule schedule;
    private final World world;
    private final Capture capture;
    private final Function<BenchmarkScenario.Case, BenchmarkReadiness.Policy> policies;
    private final List<CaseResult> results = new ArrayList<>();
    private final long started;
    private long lastTime, stageStarted, caseStarted, recoveryStarted;
    private int index;
    private BenchmarkReadiness readiness;
    private Stage stage = Stage.APPLY;
    private Outcome outcome = Outcome.RUNNING, completion = Outcome.COMPLETE;
    private boolean recording, restorationVerified, recorderStopFailed;
    private String reason = "Requested schedule";

    public BenchmarkScenarioRun(BenchmarkScenario.Schedule schedule, World world, Capture capture,
                               long startedAtNanos) {
        this(schedule, world, capture, BenchmarkReadiness.Policy::forCase, startedAtNanos);
    }
    public BenchmarkScenarioRun(BenchmarkScenario.Schedule schedule, World world, Capture capture,
                               Function<BenchmarkScenario.Case, BenchmarkReadiness.Policy> policies,
                               long startedAtNanos) {
        this.schedule = Objects.requireNonNull(schedule);
        this.world = Objects.requireNonNull(world);
        this.capture = Objects.requireNonNull(capture);
        this.policies = Objects.requireNonNull(policies);
        started = lastTime = stageStarted = caseStarted = startedAtNanos;
        for (var c : schedule.cases()) results.add(new CaseResult(c, Verdict.NOT_RUN, null, "Not executed"));
    }

    /** Observation may be null; deadlines still advance. Do not feed fabricated READY observations. */
    public Result tick(long nowNanos, BenchmarkReadiness.Observation observation) {
        time(nowNanos);
        if (stage == Stage.FINISHED) return result();
        if (observation != null && observation.atNanos() != nowNanos)
            throw new IllegalArgumentException("Observation timestamp must match this tick");
        try {
            if (stage == Stage.RESTORE) {
                if (world.restored()) {
                    restorationVerified = true;
                    outcome = completion;
                    stage = Stage.FINISHED;
                } else if (nowNanos - recoveryStarted >= schedule.recoveryBudget().toNanos()) {
                    outcome = Outcome.RESTORATION_FAILED;
                    reason = "Recovery deadline exhausted; ownership must remain with recovery owner";
                    stage = Stage.FINISHED;
                }
                return result();
            }
            long executionBudget = schedule.totalBudget().minus(schedule.recoveryBudget()).toNanos();
            if (nowNanos - started >= executionBudget) {
                record(Verdict.TIMED_OUT, "Schedule execution budget exhausted; remaining cases untested");
                restore(nowNanos, Outcome.PARTIAL);
                return result();
            }
            if (nowNanos - caseStarted >= current().budgets().total().toNanos()) {
                record(Verdict.TIMED_OUT, "Case budget exhausted");
                restore(nowNanos, Outcome.PARTIAL);
                return result();
            }
            if (stageExpired(nowNanos)) {
                record(Verdict.TIMED_OUT, stage + " deadline exhausted");
                restore(nowNanos, Outcome.PARTIAL);
                return result();
            }
            switch (stage) {
                case APPLY -> {
                    record(Verdict.RUNNING, "Applying requested scenario");
                    world.apply(current());
                    enter(Stage.ACKNOWLEDGE, nowNanos);
                }
                case ACKNOWLEDGE -> {
                    if (world.acknowledged()) {
                        readiness = new BenchmarkReadiness(current(), policies.apply(current()), nowNanos);
                        enter(Stage.WARM, nowNanos);
                    }
                }
                case WARM -> {
                    var r = observation == null ? readiness.tick(nowNanos) : readiness.observe(observation);
                    if (r.state() == BenchmarkReadiness.State.READY) {
                        if (current().workload() == BenchmarkScenario.Workload.STREAMING) {
                            world.beginRoute(current().routeTicks());
                            enter(Stage.ROUTE_ACKNOWLEDGE, nowNanos);
                        } else beginCapture(nowNanos);
                    } else handleReadinessFailure(r, nowNanos);
                }
                case ROUTE_ACKNOWLEDGE -> {
                    // Prepared route retains the warmed origin; acknowledgement must still be fresh.
                    if (world.routeStarted()) beginCapture(nowNanos);
                    else handleReadinessFailure(readiness.tick(nowNanos), nowNanos);
                }
                case CAPTURE -> {
                    var r = observation == null ? readiness.tick(nowNanos) : readiness.observe(observation);
                    if (handleReadinessFailure(r, nowNanos)) break;
                    boolean streaming = current().workload() == BenchmarkScenario.Workload.STREAMING;
                    boolean done = streaming ? world.routeComplete()
                            : nowNanos - stageStarted >= current().budgets().capture().toNanos();
                    if (done) {
                        r = readiness.complete(nowNanos);
                        if (handleReadinessFailure(r, nowNanos)) break;
                        stopCapture();
                        enter(Stage.DRAIN, nowNanos);
                    } else if (streaming && nowNanos - stageStarted >= current().budgets().capture().toNanos()) {
                        record(Verdict.TIMED_OUT, "Route did not finish within capture budget");
                        restore(nowNanos, Outcome.PARTIAL);
                    }
                }
                case DRAIN -> {
                    if (capture.drained()) {
                        record(Verdict.COMPLETE, "Scenario capture and drain complete; effect verdict belongs to consumer");
                        index++;
                        if (index == schedule.cases().size()) restore(nowNanos, completion);
                        else {
                            readiness = null;
                            caseStarted = nowNanos;
                            enter(Stage.APPLY, nowNanos);
                        }
                    }
                }
                default -> { }
            }
        } catch (RuntimeException failure) {
            if (stage == Stage.RESTORE) {
                outcome = Outcome.RESTORATION_FAILED;
                stage = Stage.FINISHED;
                reason = "Recovery failed; retain recovery ownership: " + failure.getMessage();
            } else {
                record(Verdict.FAILED, "Scenario callback failed: " + failure.getMessage());
                restore(nowNanos, Outcome.FAILED);
            }
        }
        return result();
    }

    public Result cancel(long nowNanos) {
        time(nowNanos);
        if (stage == Stage.RESTORE || stage == Stage.FINISHED) return result();
        if (readiness != null) readiness.cancel();
        record(Verdict.CANCELLED, "Cancelled; unexecuted cases remain NOT_RUN");
        restore(nowNanos, Outcome.CANCELLED);
        return result();
    }
    public Result result() {
        return new Result(stage, outcome, results, readiness == null ? null : readiness.result(),
                restorationVerified, reason);
    }
    public Optional<BenchmarkScenario.Case> currentCase() {
        return stage == Stage.RESTORE || stage == Stage.FINISHED ? Optional.empty() : Optional.of(current());
    }

    private void beginCapture(long now) {
        var r = readiness.beginCapture(now);
        if (handleReadinessFailure(r, now)) return;
        // Set before callback so a partially started recorder is stopped on exceptions.
        recording = true;
        capture.startCapture(current());
        enter(Stage.CAPTURE, now);
    }
    private boolean handleReadinessFailure(BenchmarkReadiness.Result r, long now) {
        Verdict verdict = switch (r.state()) {
            case UNAVAILABLE -> Verdict.UNAVAILABLE;
            case INVALID -> Verdict.INVALID;
            case TIMED_OUT -> Verdict.TIMED_OUT;
            case CANCELLED -> Verdict.CANCELLED;
            default -> null;
        };
        if (verdict == null) return false;
        record(verdict, String.join("; ", r.reasons()));
        if (verdict == Verdict.UNAVAILABLE && !recording) {
            completion = Outcome.PARTIAL;
            index++;
            if (index == schedule.cases().size()) restore(now, completion);
            else { readiness = null; caseStarted = now; enter(Stage.APPLY, now); }
        } else restore(now, verdict == Verdict.CANCELLED ? Outcome.CANCELLED : Outcome.PARTIAL);
        return true;
    }
    private void stopCapture() {
        if (recording) {
            recording = false;
            try { capture.stopCapture(current()); }
            catch (RuntimeException failure) { recorderStopFailed = true; throw failure; }
        }
    }
    private void restore(long now, Outcome after) {
        completion = after;
        recoveryStarted = now;
        enter(Stage.RESTORE, now);
        try { stopCapture(); }
        catch (RuntimeException failure) {
            reason += "; recorder stop failed; retain recovery ownership: " + failure.getMessage();
        }
        try { world.restore(); }
        catch (RuntimeException failure) {
            outcome = Outcome.RESTORATION_FAILED;
            stage = Stage.FINISHED;
            reason += "; restore request failed; retain ownership: " + failure.getMessage();
        }
        if (recorderStopFailed) {
            reason += "; recorder stop unverified; retain recovery ownership";
            outcome = Outcome.RESTORATION_FAILED;
            stage = Stage.FINISHED;
        }
    }
    private boolean stageExpired(long now) {
        var b = current().budgets();
        var budget = switch (stage) {
            case APPLY -> b.apply();
            case ACKNOWLEDGE, ROUTE_ACKNOWLEDGE -> b.acknowledge();
            case WARM -> b.warm();
            case DRAIN -> b.drain();
            default -> null;
        };
        return budget != null && now - stageStarted >= budget.toNanos();
    }
    private BenchmarkScenario.Case current() { return schedule.cases().get(index); }
    private void record(Verdict verdict, String why) {
        if (index < results.size()) results.set(index,
                new CaseResult(current(), verdict, readiness == null ? null : readiness.result(), why));
        reason = why;
    }
    private void enter(Stage next, long now) { stage = next; stageStarted = now; }
    private void time(long now) {
        if (now < lastTime) throw new IllegalArgumentException("Monotonic time moved backwards");
        lastTime = now;
    }
}
