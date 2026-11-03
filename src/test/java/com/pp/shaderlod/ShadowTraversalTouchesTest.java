package com.pp.shaderlod;

import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.pp.shaderlod.ShadowTraversalTouches.*;
import static org.junit.jupiter.api.Assertions.*;

class ShadowTraversalTouchesTest {
    private Capture current;

    private Capture capture(int sections, int writes) {
        var previous = System.getProperty(PROPERTY);
        try {
            System.setProperty(PROPERTY, "true");
            return current = begin(this, 7, sections, writes);
        } finally {
            if (previous == null) System.clearProperty(PROPERTY);
            else System.setProperty(PROPERTY, previous);
        }
    }

    @AfterEach void cleanScope() { abort(current); }

    private static final class Section {
        int stamp = -1, directions = 0;
        boolean disposed;
        @Override public boolean equals(Object other) { return other instanceof Section; }
        @Override public int hashCode() { return 1; }
    }

    private static final class Target implements ReplayTarget {
        int stampWrites, directionWrites;
        private final IdentityHashMap<Object, Boolean> registered = new IdentityHashMap<>();
        Target(Section... sections) { for (var section : sections) registered.put(section, true); }
        public boolean isLive(Object object) {
            return registered.containsKey(object) && object instanceof Section section && !section.disposed;
        }
        public void setVisibilityStamp(Object object, int frame) {
            ((Section) object).stamp = frame;
            stampWrites++;
        }
        public void setIncomingDirections(Object object, int directions) {
            ((Section) object).directions = directions;
            directionWrites++;
        }
    }

    private static void stamp(Section section, int frame) {
        section.stamp = frame;
        recordStamp(section, section.stamp, section.disposed);
    }

    private static void reset(Section section, int directions) {
        section.directions = directions;
        recordDirections(section, section.directions, section.disposed, Writer.DIRECTION_SET);
    }

    private static void union(Section section, int directions) {
        section.directions |= directions;
        recordDirections(section, section.directions, section.disposed, Writer.DIRECTION_UNION);
    }

    @Test void nearbyStampOnlyPreservesInterveningMainPassDirections() {
        var nearby = new Section();
        nearby.directions = 5;
        var scope = capture(8, 32);
        stamp(nearby, 7); // addNearbySections neither resets nor unions directions.
        var snapshot = finish(scope);
        var entry = snapshot.entries().getFirst();
        assertEquals(STAMP_WRITE, entry.writeMask());
        assertEquals(Writer.STAMP_SET.mask(), entry.writerMask());
        assertTrue(entry.incomingDirections().isEmpty());
        nearby.stamp = 8;
        nearby.directions = 42; // Main traversal between captured shadow and replay.
        var target = new Target(nearby);
        assertEquals(ReplayResult.APPLIED, replay(snapshot, 9, target));
        assertEquals(9, nearby.stamp);
        assertEquals(42, nearby.directions);
        assertEquals(1, target.stampWrites);
        assertEquals(0, target.directionWrites);
    }

    @Test void originEnqueuedRejectedAndEmptyWritesCollapseInFirstTouchOrder() {
        var origin = new Section();
        var rejectedAfterEnqueue = new Section();
        var emptyVisited = new Section();
        var scope = capture(8, 32);
        stamp(origin, 7);
        reset(origin, 0);
        stamp(rejectedAfterEnqueue, 7);
        reset(rejectedAfterEnqueue, 0);
        union(rejectedAfterEnqueue, 1);
        stamp(emptyVisited, 7);
        reset(emptyVisited, 0);
        union(emptyVisited, 2);
        union(rejectedAfterEnqueue, 4); // Native writes happen before queue visibility rejection.
        var snapshot = finish(scope);
        assertTrue(snapshot.complete());
        assertEquals(9, snapshot.observedWrites());
        assertEquals(3, snapshot.entries().size());
        assertSame(origin, snapshot.entries().get(0).section());
        assertSame(rejectedAfterEnqueue, snapshot.entries().get(1).section());
        assertSame(emptyVisited, snapshot.entries().get(2).section());
        var rejected = snapshot.entries().get(1);
        assertEquals(STAMP_WRITE | DIRECTION_WRITE, rejected.writeMask());
        assertEquals(7, rejected.writerMask());
        assertEquals(1, rejected.stampWrites());
        assertEquals(3, rejected.directionWrites());
        assertEquals(2, rejected.firstWrite());
        assertEquals(8, rejected.lastWrite());
        assertEquals(5, rejected.incomingDirections().orElseThrow());
        assertEquals(0, snapshot.entries().getFirst().incomingDirections().orElseThrow());
        origin.directions = rejectedAfterEnqueue.directions = emptyVisited.directions = 63;
        var target = new Target(origin, rejectedAfterEnqueue, emptyVisited);
        assertEquals(ReplayResult.APPLIED, replay(snapshot, 11, target));
        assertEquals(0, origin.directions);
        assertEquals(5, rejectedAfterEnqueue.directions);
        assertEquals(2, emptyVisited.directions);
        assertEquals(3, target.stampWrites);
        assertEquals(3, target.directionWrites);
    }

    @Test void directionOnlyWriteDoesNotInventStampAndLastResetWinsOverPreviousUnions() {
        var section = new Section();
        section.directions = 8;
        var scope = capture(1, 8);
        union(section, 4);
        union(section, 2);
        reset(section, 1);
        var snapshot = finish(scope);
        var entry = snapshot.entries().getFirst();
        assertEquals(DIRECTION_WRITE, entry.writeMask());
        assertTrue(entry.stamp().isEmpty());
        assertEquals(1, entry.incomingDirections().orElseThrow());
        section.stamp = 200;
        assertEquals(ReplayResult.APPLIED, replay(snapshot, 300, new Target(section)));
        assertEquals(200, section.stamp);
    }

    @Test void sectionIdentityWinsOverValueEqualityAndSnapshotCopiesPrimitives() {
        var first = new Section();
        var equal = new Section();
        assertEquals(first, equal);
        var scope = capture(2, 8);
        stamp(first, 7);
        reset(first, 3);
        stamp(equal, 7);
        reset(equal, 4);
        var snapshot = finish(scope);
        assertEquals(2, snapshot.entries().size());
        first.directions = 31;
        equal.stamp = 21;
        recordDirections(first, 31, false, Writer.DIRECTION_SET); // Outside scope is ignored.
        assertEquals(3, snapshot.entries().getFirst().incomingDirections().orElseThrow());
        assertEquals(7, snapshot.entries().get(1).stamp().orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.invalidities().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.coverageGaps().clear());
    }

    @Test void identityAndWriteBoundsFailClosedWithoutUnboundedGrowth() {
        var scope = capture(1, 16);
        stamp(new Section(), 7);
        stamp(new Section(), 7);
        var sectionOverflow = finish(scope);
        assertEquals(1, sectionOverflow.entries().size());
        assertTrue(sectionOverflow.invalidities().contains(Invalidity.SECTION_LIMIT));
        assertEquals(ReplayResult.INCOMPLETE, replay(sectionOverflow, 8, new Target()));
        scope = capture(8, 2);
        var section = new Section();
        stamp(section, 7);
        reset(section, 0);
        for (int i = 0; i < 100; i++) union(section, 1);
        var writeOverflow = finish(scope);
        assertEquals(2, writeOverflow.observedWrites());
        assertEquals(1, writeOverflow.entries().getFirst().directionWrites());
        assertTrue(writeOverflow.invalidities().contains(Invalidity.WRITE_LIMIT));
        assertFalse(writeOverflow.complete());
    }

    @Test void disposalAndUnknownWriterOrSectionAndForeignFrameAreIncomplete() {
        var scope = capture(8, 16);
        var disposed = new Section();
        disposed.disposed = true;
        stamp(disposed, 7);
        recordDisposal(new Section());
        recordStamp(null, 7, false);
        recordDirections(disposed, 1, false, null);
        recordDirections(disposed, 1, false, Writer.STAMP_SET);
        stamp(new Section(), 20);
        var snapshot = finish(scope);
        assertTrue(snapshot.invalidities().contains(Invalidity.DISPOSED_SECTION));
        assertTrue(snapshot.invalidities().contains(Invalidity.UNKNOWN_SECTION));
        assertTrue(snapshot.invalidities().contains(Invalidity.UNKNOWN_WRITER));
        assertTrue(snapshot.invalidities().contains(Invalidity.FOREIGN_FRAME));
        assertFalse(snapshot.complete());
    }

    @Test void disposedOrUnregisteredReplayIdentityRejectsBeforeAnyTargetMutation() {
        var first = new Section();
        var second = new Section();
        var scope = capture(2, 8);
        stamp(first, 7);
        stamp(second, 7);
        var snapshot = finish(scope);
        var target = new Target(first, second);
        second.disposed = true;
        assertEquals(ReplayResult.INVALID_TARGET, replay(snapshot, 8, target));
        assertEquals(0, target.stampWrites);
        second.disposed = false;
        assertEquals(ReplayResult.INVALID_TARGET, replay(snapshot, 8, new Target(first, new Section())));
        assertEquals(7, first.stamp);
        assertEquals(ReplayResult.INVALID_TARGET, replay(snapshot, 8, null));
    }

    @Test void nestedCaptureInvalidatesBothAndDoesNotDisplaceOuterContext() {
        var outer = capture(2, 8);
        var inner = capture(2, 8);
        current = outer;
        assertFalse(finish(inner).complete());
        assertTrue(isCapturing());
        stamp(new Section(), 7);
        var snapshot = finish(outer);
        assertFalse(snapshot.complete());
        assertEquals(1, snapshot.entries().size());
        assertTrue(snapshot.invalidities().contains(Invalidity.NESTED_CAPTURE));
        assertFalse(isCapturing());
    }

    @Test void wrongThreadFinishInvalidatesOwnerAndForeignThreadWritesAreIgnored() throws InterruptedException {
        var scope = capture(2, 8);
        var foreign = new Section();
        var result = new AtomicReference<Snapshot>();
        var worker = new Thread(() -> {
            stamp(foreign, 7);
            assertFalse(isCapturing());
            result.set(finish(scope));
        });
        worker.start();
        worker.join();
        assertTrue(result.get().invalidities().contains(Invalidity.WRONG_THREAD));
        assertTrue(isCapturing());
        assertFalse(finish(scope).complete());
        assertTrue(result.get().entries().isEmpty());
        assertFalse(isCapturing());
    }

    @Test void abortedAndRepeatedFinishNeverYieldReusableSnapshots() {
        var scope = capture(1, 4);
        stamp(new Section(), 7);
        abort(scope);
        assertFalse(isCapturing());
        var repeated = finish(scope);
        assertTrue(repeated.invalidities().contains(Invalidity.ABORTED));
        assertTrue(repeated.invalidities().contains(Invalidity.ALREADY_FINISHED));
        assertTrue(repeated.entries().isEmpty());
        assertEquals(ReplayResult.INCOMPLETE, replay(repeated, 8, new Target()));
    }

    @Test void replayRejectsActiveScopeAndWrongThread() throws InterruptedException {
        var section = new Section();
        var scope = capture(1, 4);
        stamp(section, 7);
        var snapshot = finish(scope);
        scope = capture(1, 4);
        assertEquals(ReplayResult.ACTIVE_CAPTURE, replay(snapshot, 8, new Target(section)));
        finish(scope);
        var result = new AtomicReference<ReplayResult>();
        var worker = new Thread(() -> result.set(replay(snapshot, 8, new Target(section))));
        worker.start();
        worker.join();
        assertEquals(ReplayResult.WRONG_THREAD, result.get());
        assertEquals(7, section.stamp);
    }

    @Test void defaultOffAndInvalidOwnerOrBoundsNeverOpenContext() {
        var previous = System.getProperty(PROPERTY);
        try {
            System.clearProperty(PROPERTY);
            var disabled = begin(this, 7);
            assertFalse(isCapturing());
            stamp(new Section(), 7);
            var snapshot = finish(disabled);
            assertFalse(snapshot.complete());
            assertTrue(snapshot.entries().isEmpty());
            assertTrue(snapshot.invalidities().contains(Invalidity.DISABLED));
            System.setProperty(PROPERTY, "true");
            var invalid = begin(null, 7, 0, 0);
            assertFalse(isCapturing());
            snapshot = finish(invalid);
            assertTrue(snapshot.invalidities().contains(Invalidity.UNKNOWN_OWNER));
            assertTrue(snapshot.invalidities().contains(Invalidity.INVALID_BOUND));
            snapshot = finish(begin(this, 7, Integer.MAX_VALUE, Integer.MAX_VALUE));
            assertTrue(snapshot.invalidities().contains(Invalidity.INVALID_BOUND));
            assertFalse(isCapturing());
        } finally {
            if (previous == null) System.clearProperty(PROPERTY);
            else System.setProperty(PROPERTY, previous);
        }
    }

    @Test void emptyJournalIsLocallyCompleteButExplicitlyLacksCertificates() {
        var snapshot = finish(capture(1, 1));
        assertTrue(snapshot.complete());
        assertTrue(snapshot.entries().isEmpty());
        assertEquals(3, snapshot.coverageGaps().size());
        assertEquals(ReplayResult.APPLIED, replay(snapshot, 8, new Target()));
    }
}
