package com.pp.shaderlod.benchmark;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BenchmarkLightingHistoryTest {
    @Test void pendingCheckRetainsOnlyFreshActualServerObservation() {
        var history = new BenchmarkLightingHistory();
        assertEquals(BenchmarkReadiness.Signal.BUSY, history.signal(0));
        history.completed(10, true);
        assertEquals(BenchmarkReadiness.Signal.READY, history.signal(100));
        assertEquals(BenchmarkReadiness.Signal.BUSY, history.signal(10 + BenchmarkLightingHistory.MAXIMUM_AGE_NANOS + 1));
        history.completed(600_000_000, false);
        assertEquals(BenchmarkReadiness.Signal.BUSY, history.signal(600_000_001));
    }
    @Test void failureAndOwnerResetCannotReusePriorReadyEvidence() {
        var history = new BenchmarkLightingHistory(); history.completed(0, true); history.failed();
        assertEquals(BenchmarkReadiness.Signal.UNAVAILABLE, history.signal(1));
        history.completed(2, true); assertEquals(BenchmarkReadiness.Signal.READY, history.signal(3));
        history.reset(); assertEquals(BenchmarkReadiness.Signal.BUSY, history.signal(4));
    }
}
