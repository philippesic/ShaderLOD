package com.pp.shaderlod.benchmark;

import com.pp.shaderlod.measurement.FrameMeasurement;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkRunCaptureTest {
    static class Clock implements BenchmarkRunCapture.Clock {
        boolean running, paused;
        int pending, starts, stops;
        final List<FrameMeasurement.Sample> ready = new ArrayList<>();
        public void start() { assertFalse(running); running = true; starts++; }
        public void pause() { assertTrue(running); paused = true; }
        public void poll() { assertTrue(paused); }
        public int pending() { return pending; }
        public List<FrameMeasurement.Sample> drain() { var copy = List.copyOf(ready); ready.clear(); return copy; }
        public void stop() { running = false; paused = false; pending = 0; stops++; }
    }
    static BenchmarkScenario.Case scenario() {
        return BenchmarkScenario.fast(new BenchmarkScenario.Settings(12, 1920, 1200, BenchmarkScenario.DhState.ABSENT)).cases().getFirst();
    }
    static FrameMeasurement.Sample sample(long frame) {
        return new FrameMeasurement.Sample(frame, 7, frame == 1 ? null : 16_000_000L, 8_000_000, 4_000_000L, 6_000_000L, null, "performance_off");
    }
    @Test void delayedTailCannotBeExportedOrMixedIntoAnotherWindow() {
        var clock = new Clock(); var capture = new BenchmarkRunCapture(clock, 100);
        capture.startCapture(scenario());
        clock.ready.add(sample(1)); capture.collect();
        assertThrows(IllegalStateException.class, capture::snapshot);
        assertThrows(IllegalStateException.class, () -> capture.startCapture(scenario()));
        clock.pending = 1;
        capture.stopCapture(scenario());
        assertFalse(capture.drained());
        assertTrue(clock.running);
        assertThrows(IllegalStateException.class, capture::snapshot);
        clock.ready.add(sample(2)); clock.pending = 0;
        assertTrue(capture.drained());
        assertFalse(clock.running);
        var rows = capture.snapshot().get(scenario().id());
        assertEquals(List.of(sample(1), sample(2)), rows);
        assertEquals(1, clock.starts); assertEquals(1, clock.stops);
        assertThrows(UnsupportedOperationException.class, () -> rows.clear());
    }
    @Test void cancelDuringTailReleasesClockAndPreservesAlreadyObservedRows() {
        var clock = new Clock(); var capture = new BenchmarkRunCapture(clock, 100);
        capture.startCapture(scenario()); clock.ready.add(sample(1));
        capture.stopCapture(scenario()); clock.pending = 1;
        capture.abort();
        assertFalse(capture.busy()); assertFalse(clock.running); assertEquals(0, clock.pending);
        assertEquals(List.of(sample(1)), capture.snapshot().get(scenario().id()));
    }
    @Test void budgetExhaustionIsExplicitAndDoesNotGrowCaptureWithoutBound() {
        var clock = new Clock(); var capture = new BenchmarkRunCapture(clock, 1);
        capture.startCapture(scenario()); clock.ready.add(sample(1)); capture.collect();
        clock.ready.add(sample(2));
        assertThrows(IllegalStateException.class, capture::collect);
        capture.abort();
        assertEquals(1, capture.snapshot().get(scenario().id()).size());
        assertEquals(1, capture.discardedSamples().get(scenario().id()));
    }
    @Test void cpuEndpointFreezesAtPauseBeforeAsynchronousDrainAndStop() {
        var cpu=new com.pp.shaderlod.measurement.CpuWindowMeasurement(new com.pp.shaderlod.measurement.CpuWindowMeasurement.Snapshot(7,100,10,20));
        cpu.frameStarted(7);cpu.frameCompleted(7);
        var frozen=cpu.finish(new com.pp.shaderlod.measurement.CpuWindowMeasurement.Snapshot(7,200,20,40));
        var clock=new Clock(){public com.pp.shaderlod.measurement.CpuWindowMeasurement.Result cpuWindow(){assertTrue(paused);return frozen;}};
        var capture=new BenchmarkRunCapture(clock,10);capture.startCapture(scenario());clock.pending=1;capture.stopCapture(scenario());
        assertSame(frozen,capture.cpuWindows().get(scenario().id()));assertFalse(capture.drained());
        clock.pending=0;assertTrue(capture.drained());assertSame(frozen,capture.cpuWindows().get(scenario().id()));
    }
    @Test void partialClockStartupFailureCanStillBeCancelled() {
        var clock = new Clock() {
            @Override public void start() { super.start(); throw new IllegalStateException("driver unavailable"); }
        };
        var capture = new BenchmarkRunCapture(clock, 100);
        assertThrows(IllegalStateException.class, () -> capture.startCapture(scenario()));
        capture.abort();
        assertFalse(clock.running); assertFalse(capture.busy());
    }
}
