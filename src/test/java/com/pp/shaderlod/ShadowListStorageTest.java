package com.pp.shaderlod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShadowListStorageTest {
    @Test void mainAndDisabledNeverConstructShadowAndActiveShadowAllocatesOnlyOnce() {
        var original = new ArrayList<Integer>();
        var storage = new ShadowListStorage<List<Integer>>(original);
        AtomicInteger constructions = new AtomicInteger();
        Function<Object, List<Integer>> factory = owner -> {
            constructions.incrementAndGet();
            return new ArrayList<>();
        };
        Object owner = new Object();
        assertSame(original, storage.select(false, false, owner, factory));
        assertSame(original, storage.select(false, true, owner, factory));
        assertSame(original, storage.select(true, false, owner, factory));
        assertNull(storage.shadowOrNull());
        assertEquals(0, constructions.get());
        List<Integer> shadow = storage.select(true, true, owner, factory);
        assertNotSame(original, shadow);
        assertSame(shadow, storage.select(true, true, owner, factory));
        assertEquals(1, constructions.get());
    }

    @Test void alternatingMembershipResetDoesNotMutateOtherPassAndTogglesRestoreOriginal() {
        var original = new ArrayList<>(List.of(1, 3));
        var storage = new ShadowListStorage<List<Integer>>(original);
        Object owner = new Object();
        List<Integer> shadow = storage.select(true, true, owner, ignored -> new ArrayList<>());
        shadow.addAll(List.of(2, 4));
        assertEquals(List.of(1, 3), original);
        original.clear();
        original.add(5);
        assertEquals(List.of(2, 4), shadow);
        assertSame(original, storage.select(false, true, owner, ignored -> fail("Unexpected construction")));
        assertSame(shadow, storage.select(true, true, owner, ignored -> fail("Unexpected reconstruction")));
        shadow.clear();
        assertEquals(List.of(5), original);
    }

    @Test void replacementOwnerGetsIndependentStorageWithoutGlobalRetainedMembership() {
        var oldStorage = new ShadowListStorage<List<Integer>>(new ArrayList<>());
        var newStorage = new ShadowListStorage<List<Integer>>(new ArrayList<>());
        List<Integer> oldShadow = oldStorage.select(true, true, new Object(), ignored -> new ArrayList<>());
        oldShadow.add(7);
        List<Integer> newShadow = newStorage.select(true, true, new Object(), ignored -> new ArrayList<>());
        assertNotSame(oldShadow, newShadow);
        assertTrue(newShadow.isEmpty());
        assertNotSame(oldStorage.original(), newStorage.original());
    }

    private static final class Membership {
        int generation;
        List<Integer> sprites;
        Membership(int generation, Integer... sprites) {
            reset(generation, sprites);
        }
        void reset(int generation, Integer... sprites) {
            this.generation = generation;
            this.sprites = List.of(sprites);
        }
    }

    @Test void separatedShadowSpriteTickMatchesLegacySharedArraysForOverlapAndUnvisitedRegions() {
        // Main container has regions A/B/C. This shadow traversal touches B/D.
        // Legacy arrays are overwritten for B; A/C retain their old main arrays.
        List<Membership> legacyMain = List.of(new Membership(10, 1),
                new Membership(10, 2), new Membership(10, 3));
        legacyMain.get(1).reset(11, 7, 8);
        var a = new ShadowListStorage<>(new Membership(10, 1));
        var b = new ShadowListStorage<>(new Membership(10, 2));
        var c = new ShadowListStorage<>(new Membership(10, 3));
        var d = new ShadowListStorage<>(new Membership(10, 4));
        // C has an old shadow list: it must not replace this frame's main data.
        c.select(true, true, new Object(), ignored -> new Membership(9, 99));
        b.select(true, true, new Object(), ignored -> new Membership(11, 7, 8));
        d.select(true, true, new Object(), ignored -> new Membership(11, 42));
        var expected = new ArrayList<Integer>();
        legacyMain.forEach(list -> expected.addAll(list.sprites));
        var observed = new ArrayList<Integer>();
        for (var storage : List.of(a, b, c)) {
            Membership shadow = storage.shadowOrNull();
            boolean useShadow = ShadowVisibilityListsController.useShadowSprites(true, true,
                    shadow != null, shadow == null ? 0 : shadow.generation, 11);
            observed.addAll((useShadow ? shadow : storage.original()).sprites);
        }
        assertEquals(expected, observed);
        assertEquals(List.of(1, 7, 8, 3), observed);
        assertEquals(List.of(2), b.original().sprites);
        assertFalse(observed.contains(42)); // D was not in the original main container.
        assertFalse(ShadowVisibilityListsController.useShadowSprites(false, true, true, 11, 11));
        assertFalse(ShadowVisibilityListsController.useShadowSprites(true, false, true, 11, 11));
        assertFalse(ShadowVisibilityListsController.useShadowSprites(true, true, true, Integer.MAX_VALUE, Integer.MIN_VALUE));
    }
}
