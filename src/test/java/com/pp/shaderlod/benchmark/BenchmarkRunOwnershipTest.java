package com.pp.shaderlod.benchmark;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkRunOwnershipTest {
    @Test void failedRestorationBlocksNewOwnerUntilVerifiedRecovery() {
        var lease = new BenchmarkRunOwnership(); var first = new Object(); var second = new Object();
        lease.acquire(first);
        assertTrue(lease.active());
        assertThrows(IllegalStateException.class, () -> lease.acquire(second));
        lease.recovering(first);
        assertFalse(lease.active()); assertTrue(lease.restorationPending());
        assertThrows(IllegalStateException.class, () -> lease.release(first, false));
        assertThrows(IllegalStateException.class, () -> lease.acquire(second));
        assertThrows(IllegalStateException.class, () -> lease.release(second, true));
        assertTrue(lease.restorationPending());
        lease.release(first, true); lease.acquire(second);
        assertTrue(lease.active()); assertFalse(lease.restorationPending());
    }
}
