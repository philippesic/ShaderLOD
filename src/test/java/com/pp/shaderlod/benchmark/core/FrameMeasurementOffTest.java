package com.pp.shaderlod.benchmark.core;

import com.pp.shaderlod.measurement.FrameMeasurement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameMeasurementOffTest {
    @Test void disabledClockHasZeroClockReadsAndBackendCalls() {
        FrameMeasurement.GpuBackend forbidden = new FrameMeasurement.GpuBackend() {
            public String status() { throw new AssertionError("GPU status while off"); }
            public int counterBits() { throw new AssertionError("GPU counter bits while off"); }
            public int create() { throw new AssertionError("GPU create while off"); }
            public boolean busy() { throw new AssertionError("GPU busy while off"); }
            public void begin(int query) { throw new AssertionError("GPU begin while off"); }
            public void end() { throw new AssertionError("GPU end while off"); }
            public boolean available(int query) { throw new AssertionError("GPU available while off"); }
            public long nanoseconds(int query) { throw new AssertionError("GPU nanoseconds while off"); }
            public void delete(int query) { throw new AssertionError("GPU delete while off"); }
        };
        java.util.function.LongSupplier forbiddenClock = () -> { throw new AssertionError("clock read while off"); };
        var clock = new FrameMeasurement(forbidden, forbiddenClock, forbiddenClock, forbiddenClock);
        clock.enabled(false);
        for (int i = 0; i < 1000; i++) { clock.begin(); clock.end(); clock.poll(); }
        assertFalse(clock.enabled()); assertTrue(clock.drain().isEmpty()); assertEquals(0, clock.pendingQueries());
    }
}
