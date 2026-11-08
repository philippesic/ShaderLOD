package com.pp.shaderlod.benchmark;

import com.pp.shaderlod.benchmark.core.CaptureMotionClock;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class CaptureMotionClockTest {
    @Test void renderStallsDoNotSkipTheSixHzPathOrEndEarly() {
        var clock = new CaptureMotionClock(true);
        var slots = new HashSet<Integer>();
        int physicalTicks = 0;
        while (clock.ticks() <= 400) {
            // Simulate a 700 ms stall and Minecraft's multi-tick catch-up twice.
            int catchUp = clock.ticks() == 160 || clock.ticks() == 350 ? 14 : 1;
            for (int i = 0; i < catchUp; i++) { clock.advanceTick(); physicalTicks++; }
            for (float partial : new float[]{0, .5f, 1}) {
                slots.add((int)Math.floor(clock.pathSeconds(20, partial) * 6 + 1e-6));
                clock.rendered();
            }
        }
        assertEquals(121, slots.size());
        for (int slot = 0; slot <= 120; slot++) assertTrue(slots.contains(slot));
        assertEquals(26, clock.heldTicks());
        assertEquals(401, clock.ticks());
        assertEquals(427, physicalTicks); // The path stretches; real time is not fabricated.
        assertEquals(20, clock.pathSeconds(20, 1));
    }

    @Test void legacyUnboundedClockStillAdvancesEveryTick() {
        var clock = new CaptureMotionClock(false);
        for (int i = 0; i < 14; i++) clock.advanceTick();
        assertEquals(.7, clock.pathSeconds(20, 1), 1e-12);
        assertEquals(0, clock.heldTicks());
        assertThrows(IllegalArgumentException.class, () -> clock.pathSeconds(20, Float.NaN));
    }

    @Test void pendingWriterHoldsThePathWithoutConsumingCaptureSlots() {
        var clock = new CaptureMotionClock(true);
        clock.advanceTick();
        clock.rendered();
        for (int i = 0; i < 10; i++) {
            clock.advanceTick(false);
            clock.rendered();
        }
        assertEquals(1, clock.ticks());
        assertEquals(10, clock.writerHeldTicks());
        assertEquals(.05, clock.pathSeconds(20, 1), 1e-12);
        assertEquals(2, clock.advanceTick(true));
    }
}
