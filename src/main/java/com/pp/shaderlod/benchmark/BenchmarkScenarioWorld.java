package com.pp.shaderlod.benchmark;

/**
 * Client-thread bridge to the existing owned world. Open BenchmarkWorld before constructing a run.
 * This port does not change RD/resolution/DH: the consumer must select and acknowledge them.
 * Restoration closes only the owned private save and restores the original camera type via
 * BenchmarkWorld. Personal worlds/time/weather are never entered or mutated. The private save's
 * environment is disposable scenario state, not a personal configuration snapshot.
 */
public final class BenchmarkScenarioWorld implements BenchmarkScenarioRun.World {
    private static BenchmarkScenarioWorld recoveryOwner;

    public BenchmarkScenarioWorld() {
        if (!BenchmarkWorld.ready()) throw new IllegalStateException("Prepare the owned benchmark world first");
        if (recoveryOwner != null) throw new IllegalStateException("Another scenario still owns world recovery");
        recoveryOwner = this;
    }

    @Override public void apply(BenchmarkScenario.Case scenario) {
        requireOwner();
        if (!BenchmarkWorld.ready()) throw new IllegalStateException("Open and prepare the owned benchmark world first");
        BenchmarkWorld.pose(scenario.poseIndex());
    }
    @Override public boolean acknowledged() {
        return BenchmarkWorld.poseReady() || BenchmarkWorld.flythroughStarted();
    }
    @Override public void beginRoute(int durationTicks) {
        requireOwner();
        BenchmarkWorld.beginPreparedFlythrough(durationTicks);
    }
    @Override public boolean routeStarted() { return BenchmarkWorld.flythroughStarted(); }
    @Override public boolean routeComplete() { return BenchmarkWorld.flythroughDone(); }
    public int routeElapsedTicks() { return BenchmarkWorld.flythroughElapsedTicks(); }
    @Override public void restore() { requireOwner(); BenchmarkWorld.exit(); }
    @Override public boolean restored() {
        requireOwner();
        if (BenchmarkWorld.active()) return false;
        recoveryOwner = null;
        return true;
    }
    private void requireOwner() {
        if (recoveryOwner != this) throw new IllegalStateException("Scenario no longer owns world recovery");
    }
}
