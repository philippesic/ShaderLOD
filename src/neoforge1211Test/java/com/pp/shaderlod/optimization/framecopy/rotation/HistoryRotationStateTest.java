package com.pp.shaderlod.optimization.framecopy.rotation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Distinguishable physical images, no GL. Root executes only after installing the frozen bundle. */
class HistoryRotationStateTest {
    private static HistoryRotationState warm() {
        var s = new HistoryRotationState();
        s.begin(false); draw(s, true); assertFalse(s.beginFinal(true));
        s.nativeCopyReturned(); s.finalReturned(); assertTrue(s.snapshot().warm()); return s;
    }
    private static void draw(HistoryRotationState s, boolean full) {
        s.bound(); assertFalse(s.beforeDraw(full)); s.drawStarted(); s.drawReturned();
    }
    private static void rotate(HistoryRotationState s) {
        s.begin(true); draw(s, true); assertTrue(s.beginFinal(true)); s.finalReturned();
    }
    private static void copy(int[][] images, HistoryRotationState.Direction d) {
        int from = d == HistoryRotationState.Direction.A_TO_B ? 0 : 1;
        images[1 - from] = images[from].clone();
    }
    @Test void neverOmitBeforeCompletedCanonicalWarmCopy() {
        var s = new HistoryRotationState(); assertThrows(IllegalStateException.class, () -> s.begin(true));
        s.begin(false); draw(s, true); s.beginFinal(true);
        assertThrows(IllegalStateException.class, s::finalReturned);
        assertFalse(s.snapshot().warm()); assertEquals(0, s.snapshot().parity());
        s.nativeCopyReturned(); s.finalReturned(); assertTrue(s.snapshot().warm());
    }
    @Test void commitsExactlyOnceAfterFinalReturnRatherThanDrawOrFrameCounter() {
        var s = warm(); s.begin(true); draw(s, true);
        assertEquals(0, s.snapshot().parity()); s.beginFinal(true); assertEquals(0, s.snapshot().parity());
        s.finalReturned(); assertEquals(1, s.snapshot().parity());
        assertThrows(IllegalStateException.class, s::finalReturned);
        rotate(s); assertEquals(0, s.snapshot().parity()); assertEquals(2, s.snapshot().omitted());
    }
    @Test void idleOffRestoresLatestImageAtBothOddAndEvenParity() {
        for (int frames : new int[]{1, 2, 3, 4}) {
            var s = warm(); int[][] images = {{0, 1, 2}, {0, 1, 2}};
            for (int f = 1; f <= frames; f++) {
                int write = s.snapshot().parity() == 0 ? 1 : 0;
                images[write] = new int[]{f * 10, f * 10 + 1, f * 10 + 2}; rotate(s);
            }
            copy(images, s.seedDirection()); s.repaired(); s.close();
            assertArrayEquals(new int[]{frames * 10, frames * 10 + 1, frames * 10 + 2}, images[0]);
            assertArrayEquals(images[0], images[1]); assertEquals(0, s.snapshot().parity());
        }
    }
    @Test void parityOnePostDrawUsesOppositeDirectionFromIdleOff() {
        var s = warm(); rotate(s); assertEquals(1, s.snapshot().parity());
        assertEquals(HistoryRotationState.Direction.B_TO_A, s.seedDirection());
        s.begin(true); draw(s, true); assertFalse(s.beginFinal(false));
        assertEquals(HistoryRotationState.Direction.A_TO_B, s.drainDirection());
        int[][] correct = {{2}, {1}}; copy(correct, s.drainDirection());
        assertArrayEquals(new int[]{2}, correct[1]);
        int[][] wrong = {{2}, {1}}; copy(wrong, HistoryRotationState.Direction.B_TO_A);
        assertArrayEquals(new int[]{1}, wrong[0]); // Concrete original-B->A regression destroys H2.
        s.nativeCopyReturned(); s.finalReturned(); assertEquals(0, s.snapshot().parity());
    }
    @Test void partialMaskOrScissorSeedsLatestH1AtBothParitiesWithoutRebinding() {
        for (int parity : new int[]{0, 1}) for (String veto : new String[]{"RGB_MASK", "SCISSOR"}) {
            var s = warm(); if (parity == 1) rotate(s); else { rotate(s); rotate(s); }
            int read = parity == 0 ? 0 : 1, write = 1 - read;
            int[][] images = new int[2][];
            images[read] = new int[]{11, 12, 13, 14, 15, 16}; // H1: latest committed RGB pixels.
            images[write] = new int[]{1, 2, 3, 4, 5, 6};     // H0: stale output storage.
            int[] nativeExpected = images[read].clone();
            boolean[] writes = veto.equals("RGB_MASK") ? new boolean[]{false, true, true, false, true, true}
                : new boolean[]{true, true, true, false, false, false};
            s.begin(true); s.bound(); assertTrue(s.beforeDraw(false));
            copy(images, s.seedDirection()); s.seeded();
            assertEquals(parity, s.snapshot().parity()); // Already-bound FBO/supplier stay valid.
            s.drawStarted();
            for (int i = 0; i < writes.length; i++) if (writes[i]) images[write][i] = nativeExpected[i] = 100 + i;
            s.drawReturned(); assertFalse(s.beginFinal(true)); copy(images, s.drainDirection());
            s.nativeCopyReturned(); s.finalReturned();
            assertArrayEquals(nativeExpected, images[0]); assertArrayEquals(nativeExpected, images[1]);
            assertEquals(0, s.snapshot().parity());
            for (int i = 0; i < writes.length; i++) if (!writes[i]) assertEquals(11 + i, images[0][i]);
        }
    }
    @Test void partialWriteWithoutSeedCannotStartAndCannotCommit() {
        var s = warm(); rotate(s); s.begin(true); s.bound(); assertTrue(s.beforeDraw(false));
        assertThrows(IllegalStateException.class, s::drawStarted);
        assertThrows(IllegalStateException.class, () -> s.beginFinal(true)); assertEquals(1, s.snapshot().parity());
    }
    @Test void throwingDrawRollsBackCommittedRNotPartialWAndNeverToggles() {
        var s = warm(); rotate(s); s.begin(true); s.bound(); s.beforeDraw(true); s.drawStarted();
        int[][] images = {{999, 0}, {11, 12}};
        assertTrue(s.needsRepair()); assertEquals(1, s.snapshot().parity());
        copy(images, s.seedDirection()); s.repaired();
        assertArrayEquals(new int[]{11, 12}, images[0]); assertArrayEquals(images[0], images[1]);
        assertEquals(1, s.snapshot().omitted()); assertEquals(0, s.snapshot().parity());
    }
    @Test void firstShadowDrawFailureAlsoRequiresRollbackWithoutQualifyingWarmth() {
        var s = new HistoryRotationState(); s.begin(false); s.bound(); s.beforeDraw(true); s.drawStarted();
        assertTrue(s.needsRepair()); assertFalse(s.warm());
        int[][] images = {{17}, {999}}; copy(images, s.seedDirection()); s.repaired();
        assertArrayEquals(new int[]{17}, images[1]); assertFalse(s.warm()); assertEquals(0, s.parity());
        assertThrows(IllegalStateException.class, () -> s.begin(true));
    }
    @Test void finalFailureBeforeNativeCopyDiscardsWButAfterCopyPreservesNewEquality() {
        var s = warm(); rotate(s); s.begin(true); draw(s, true); s.beginFinal(false);
        assertTrue(s.needsRepair()); assertEquals(1, s.snapshot().parity());
        s.nativeCopyReturned(); assertFalse(s.needsRepair());
        // Root abort after this returned copy must NOT restore stale history from a saved image.
        s.repaired(); assertEquals(0, s.snapshot().parity()); assertEquals(1, s.snapshot().omitted());
    }
    @Test void missingRepeatedDrawAndReentrantFrameAreRefused() {
        var s = warm(); s.begin(true);
        assertThrows(IllegalStateException.class, () -> s.begin(true));
        assertThrows(IllegalStateException.class, () -> s.beginFinal(true));
        draw(s, true); assertThrows(IllegalStateException.class, s::bound);
        assertThrows(IllegalStateException.class, s::drawReturned); assertEquals(0, s.snapshot().parity());
    }
    @Test void incompleteNativeStreamAndUnknownCopyResultNeverQualify() {
        var s = warm(); rotate(s); s.begin(true); draw(s, true); s.beginFinal(false);
        assertThrows(IllegalStateException.class, s::finalReturned); assertEquals(1, s.snapshot().parity());
        s.suspend(); assertThrows(IllegalStateException.class, () -> s.begin(true));
        assertThrows(IllegalStateException.class, s::close); assertEquals(1, s.snapshot().parity());
    }
    @Test void reloadResizeClearAndControlRestorationRetireOldParityAndRequireFreshWarmFrame() {
        for (String control : new String[]{"OFF", "QUALITY", "CANCEL", "FAILED_APPLY", "RESIZE", "FULL_CLEAR", "WORLD_SWITCH"}) {
            var old = warm(); rotate(old); int[][] images = {{0}, {17}};
            copy(images, old.seedDirection()); old.repaired(); old.close();
            assertArrayEquals(new int[]{17}, images[0], control); assertArrayEquals(images[0], images[1], control);
            assertThrows(IllegalStateException.class, () -> old.begin(true), control);
            var replacement = new HistoryRotationState();
            assertThrows(IllegalStateException.class, () -> replacement.begin(true), control);
            // A failed reload can keep old native storage, but cannot retain the experimental plan.
            assertEquals(0, old.parity()); assertFalse(old.warm());
        }
    }
}
