package com.pp.shaderlod.benchmark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BenchmarkDhMaintenanceTest {
    @AfterEach void cleanup() { BenchmarkDhMaintenance.owner(false); }
    @Test void observedCooldownDoesNotHideOtherIoOrQueuedWork() {
        BenchmarkDhMaintenance.hookInstalled(); BenchmarkDhMaintenance.owner(true);
        var ticket = BenchmarkDhMaintenance.beginCooldown();
        assertEquals(1, BenchmarkDhMaintenance.cooldowns());
        assertEquals(BenchmarkReadiness.Signal.READY, BenchmarkDhMaintenance.fileSignal(0, 1, 1, 1));
        assertEquals(BenchmarkReadiness.Signal.BUSY, BenchmarkDhMaintenance.fileSignal(1, 1, 1, 1));
        assertEquals(BenchmarkReadiness.Signal.BUSY, BenchmarkDhMaintenance.fileSignal(0, 2, 1, 1));
        assertEquals(BenchmarkReadiness.Signal.BUSY, BenchmarkDhMaintenance.fileSignal(0, 1, 0, 1));
        BenchmarkDhMaintenance.endCooldown(ticket);
        assertEquals(BenchmarkReadiness.Signal.BUSY, BenchmarkDhMaintenance.fileSignal(0, 1, 0, 0));
        assertEquals(BenchmarkReadiness.Signal.UNAVAILABLE, BenchmarkDhMaintenance.fileSignal(-1, 0, 0, 0));
    }
    @Test void lateCallbacksCannotCrossOwnerGenerations() {
        BenchmarkDhMaintenance.owner(false); assertNull(BenchmarkDhMaintenance.beginCooldown());
        BenchmarkDhMaintenance.owner(true); var old = BenchmarkDhMaintenance.beginCooldown();
        BenchmarkDhMaintenance.owner(false); BenchmarkDhMaintenance.owner(true);
        var current = BenchmarkDhMaintenance.beginCooldown(); BenchmarkDhMaintenance.endCooldown(old);
        assertEquals(1, BenchmarkDhMaintenance.cooldowns());
        BenchmarkDhMaintenance.endCooldown(current); assertEquals(0, BenchmarkDhMaintenance.cooldowns());
    }
}
