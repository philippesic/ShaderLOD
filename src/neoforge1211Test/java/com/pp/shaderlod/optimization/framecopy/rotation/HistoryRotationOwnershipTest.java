package com.pp.shaderlod.optimization.framecopy.rotation;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoryRotationOwnershipTest {
    @Test void freshSamplerDoesNotCachePreUseDefaultZero() {
        var latch = new HistoryRotationSamplerLatch();
        assertEquals(-1, latch.unit());
        assertEquals(3, latch.latch(3, 16, true));
        assertEquals(3, latch.unit());
        assertEquals(3, latch.latch(3, 16, true));
        assertThrows(IllegalStateException.class, () -> latch.latch(0, 16, true));
    }
    @Test void initializedZeroIsAllowedButNeverAssumedBeforeUse() {
        var latch = new HistoryRotationSamplerLatch();
        assertEquals(-1, latch.unit());
        assertEquals(0, latch.latch(0, 16, true));
    }
    @Test void samplerReloadAndInvalidFirstBindingRemainClosed() {
        var old = new HistoryRotationSamplerLatch(); old.latch(3, 16, true);
        var fresh = new HistoryRotationSamplerLatch();
        assertEquals(-1, fresh.unit());
        assertThrows(IllegalStateException.class, () -> fresh.latch(3, 16, false));
        assertThrows(IllegalStateException.class, () -> fresh.latch(16, 16, true));
        assertEquals(-1, fresh.unit());
        assertEquals(4, fresh.latch(4, 16, true));
    }

    @Test void deletesOnlyTheTwoNewFbosExactlyOnceNeverTexturesOrCanonicalObjects() {
        Object canonicalFbo = new Object(), nativeFromB = new Object(), textureA = new Object(), textureB = new Object();
        Object twin = new Object(), fromA = new Object(); var deleted = new ArrayList<Object>();
        var owned = new HistoryRotationResources<>(twin, fromA, deleted::add);
        owned.close(); owned.close();
        assertEquals(java.util.List.of(twin, fromA), deleted); assertTrue(owned.closed());
        assertFalse(deleted.contains(canonicalFbo)); assertFalse(deleted.contains(nativeFromB));
        assertFalse(deleted.contains(textureA)); assertFalse(deleted.contains(textureB));
    }
    @Test void deletionFailureRetainsFailedOwnershipWithoutRepeatingSuccessfulDeletion() {
        Object twin = new Object(), fromA = new Object(); var deleted = new ArrayList<Object>(); int[] attempts = {0};
        var owned = new HistoryRotationResources<>(twin, fromA, resource -> {
            if (resource == twin && attempts[0]++ == 0) throw new IllegalStateException("CONTEXT_FAILURE");
            deleted.add(resource);
        });
        assertThrows(IllegalStateException.class, owned::close); assertFalse(owned.closed());
        owned.close(); assertEquals(java.util.List.of(fromA, twin), deleted); assertTrue(owned.closed());
        assertThrows(IllegalArgumentException.class, () -> new HistoryRotationResources<>(twin, twin, deleted::add));
    }
    @Test void identityAndGenerationPreventHandleReuseFromRequalifyingAnOldPlan() {
        Object pipeline = new Object(), pack = new Object(), world = new Object();
        var old = epoch(pipeline, pack, world, 1, 1, 100, true);
        assertTrue(old.matches(epoch(pipeline, pack, world, 1, 1, 100, true)));
        assertFalse(old.matches(epoch(new Object(), pack, world, 1, 1, 100, true)));
        assertFalse(old.matches(epoch(pipeline, new Object(), world, 1, 1, 100, true)));
        assertFalse(old.matches(epoch(pipeline, pack, new Object(), 1, 1, 100, true)));
        assertFalse(old.matches(epoch(pipeline, pack, world, 2, 1, 100, true)));
        assertFalse(old.matches(epoch(pipeline, pack, world, 1, 2, 100, true)));
        assertFalse(old.matches(epoch(pipeline, pack, world, 1, 1, 101, true)));
        assertFalse(old.matches(epoch(pipeline, pack, world, 1, 1, 100, false)));
        assertFalse(old.matches(new HistoryRotationPlan.Epoch(pipeline, pack, world, 1, 1, 100, 80, true, false, true, true)));
        assertFalse(old.matches(new HistoryRotationPlan.Epoch(pipeline, pack, world, 1, 1, 100, 80, true, true, false, true)));
        assertFalse(old.matches(new HistoryRotationPlan.Epoch(pipeline, pack, world, 1, 1, 100, 80, true, true, true, false)));
        assertFalse(old.matches(null));
    }
    private static HistoryRotationPlan.Epoch epoch(Object pipeline, Object pack, Object world,
                                                    long generation, long allocation, int width, boolean g1) {
        return new HistoryRotationPlan.Epoch(pipeline, pack, world, generation, allocation, width, 80, g1, true, true, true);
    }
    @Test void everySourceLiveAndLifecycleJoinIsMandatory() {
        assertFalse(new HistoryRotationPlan.Proof(true, true, false, true, true, true, true, true, true, "evidence").closed());
        assertFalse(new HistoryRotationPlan.Proof(true, true, true, true, true, true, false, true, true, "evidence").closed());
        assertFalse(new HistoryRotationPlan.Proof(true, true, true, true, true, true, true, true, true, "").closed());
        assertTrue(new HistoryRotationPlan.Proof(true, true, true, true, true, true, true, true, true, "evidence").closed());
    }
}
