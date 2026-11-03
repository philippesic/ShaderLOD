package com.pp.shaderlod.benchmark;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkLiveWorkTest {
    private static final Object PIPELINE = new Object(), SOURCE = new Object();
    @AfterEach void stop() { BenchmarkLiveWork.disable(); }
    static BenchmarkLiveWork.Frame frame(long now, int nativeFrame, long entries, long indices, long dhIndices) {
        BenchmarkLiveWork.hooksInstalled(true, true, true);
        BenchmarkLiveWork.beginFrame(true);
        BenchmarkLiveWork.enterLayer(false);
        BenchmarkLiveWork.sodiumSubmitted(entries, indices, false);
        BenchmarkLiveWork.leaveLayer();
        BenchmarkLiveWork.dhRendererEntered();
        if (dhIndices > 0) BenchmarkLiveWork.dhSubmitted((int)dhIndices, true, false);
        BenchmarkLiveWork.endFrame(now, nativeFrame, PIPELINE, SOURCE, 0);
        return BenchmarkLiveWork.latest();
    }
    @Test void idleHooksDoNotCreateSamplesOrCountWork() {
        BenchmarkLiveWork.beginFrame(false);
        BenchmarkLiveWork.sodiumSubmitted(10, 300, false);
        BenchmarkLiveWork.dhSubmitted(300, true, false);
        BenchmarkLiveWork.endFrame(1, 1, PIPELINE, SOURCE, 0);
        assertNull(BenchmarkLiveWork.latest());
        assertFalse(BenchmarkLiveWork.enabled());
    }
    @Test void completedFrameCountsResetAndWorldShadowTranslucencyStaySeparate() {
        BenchmarkLiveWork.hooksInstalled(true, true, true);
        BenchmarkLiveWork.beginFrame(true);
        BenchmarkLiveWork.enterLayer(false);
        BenchmarkLiveWork.sodiumSubmitted(3, 90, false);
        BenchmarkLiveWork.sodiumSubmitted(8, 240, true);
        BenchmarkLiveWork.leaveLayer();
        BenchmarkLiveWork.enterLayer(true);
        BenchmarkLiveWork.sodiumSubmitted(2, 60, false);
        BenchmarkLiveWork.leaveLayer();
        BenchmarkLiveWork.dhRendererEntered();
        BenchmarkLiveWork.dhSubmitted(100, true, false);
        BenchmarkLiveWork.dhSubmitted(200, false, true);
        BenchmarkLiveWork.endFrame(10, 1, PIPELINE, SOURCE, 0);
        var f = BenchmarkLiveWork.latest();
        assertTrue(f.valid());
        assertEquals(5, f.worldEntries()); assertEquals(150, f.worldIndices());
        assertEquals(8, f.shadowEntries()); assertEquals(240, f.shadowIndices());
        assertEquals(2, f.translucentEntries());
        assertEquals(1, f.dhDraws()); assertEquals(100, f.dhIndices());
        assertEquals(1, f.dhShadowDraws()); assertEquals(200, f.dhShadowIndices());
        var next = frame(20, 2, 1, 30, 60);
        assertEquals(1, next.worldEntries()); assertEquals(0, next.shadowEntries());
        assertEquals(60, next.dhIndices()); assertEquals(0, next.dhShadowDraws());
        assertNotEquals(f.dhFingerprint(), next.dhFingerprint());
    }
    @Test void unfinishedFramesAreNotPublishedAndMalformedScopesAreInvalid() {
        var complete = frame(10, 1, 1, 30, 60);
        BenchmarkLiveWork.beginFrame(true);
        assertSame(complete, BenchmarkLiveWork.latest());
        BenchmarkLiveWork.sodiumSubmitted(1, 30, false); // no layer classification
        BenchmarkLiveWork.endFrame(20, 2, PIPELINE, SOURCE, 0);
        assertFalse(BenchmarkLiveWork.latest().valid());
    }
    @Test void reacquiringOwnershipStartsANewEpochAndCleanupClearsEvidence() {
        var first = frame(10, 1, 1, 30, 60);
        BenchmarkLiveWork.disable();
        assertNull(BenchmarkLiveWork.latest());
        assertNotEquals(first.ownerEpoch(), frame(20, 2, 1, 30, 60).ownerEpoch());
    }
}
