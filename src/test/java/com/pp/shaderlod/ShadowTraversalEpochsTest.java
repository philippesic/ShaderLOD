package com.pp.shaderlod;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static com.pp.shaderlod.ShadowTraversalEpochs.Producer.*;
import static org.junit.jupiter.api.Assertions.*;

class ShadowTraversalEpochsTest {
    private static final ShadowTraversalEpochs.TaskState IDLE = new ShadowTraversalEpochs.TaskState(true, 0, 0, 0);

    private static ShadowTraversalEpochs epochs() {
        return new ShadowTraversalEpochs(new Object(), new Object(), new Object());
    }

    private static void mutation(ShadowTraversalEpochs state, ShadowTraversalEpochs.Producer producer) {
        state.beginMutation(producer);
        state.endMutation(producer);
    }

    @Test void unchangedCaptureHasStableGenerationFenceButCannotCertifyMissingCoverage() {
        var state = epochs();
        var before = state.beginCapture(IDLE);
        var captured = state.finishCapture(before, IDLE).orElseThrow();
        assertTrue(state.isCurrent(captured));
        assertEquals(before.structural(), captured.structural());
        assertEquals(before.task(), captured.task());
        assertEquals(before.lifetime(), captured.lifetime());
        assertEquals(5, captured.coverageGaps().size());
        assertThrows(UnsupportedOperationException.class, () -> captured.coverageGaps().clear());
        assertFalse(state.canCertify(captured, IDLE));
    }

    @Test void everyProducerInvalidatesBothExistingFenceAndInProgressCapture() {
        for (var producer : ShadowTraversalEpochs.Producer.values()) {
            var state = epochs();
            var before = state.beginCapture(IDLE);
            mutation(state, producer);
            assertFalse(state.isCurrent(before), producer.toString());
            assertTrue(state.finishCapture(before, IDLE).isEmpty(), producer.toString());
            assertTrue(state.isCurrent(state.beginCapture(IDLE)));
        }
    }

    @Test void sortAndMovementAreTaskProducersEvenWithoutGraphDirtiness() {
        for (var producer : new ShadowTraversalEpochs.Producer[]{SORT_SCHEDULED, SORT_MOVEMENT, TASK_SUBMISSION}) {
            var state = epochs();
            var before = state.beginCapture();
            mutation(state, producer);
            var after = state.beginCapture();
            assertEquals(before.structural(), after.structural());
            assertEquals(before.lifetime(), after.lifetime());
            assertTrue(after.task() > before.task());
        }
    }

    @Test void unknownGraphMarkIsStructuralEvenWhenRepeatedAndDoesNotPoisonLaterDiagnostics() {
        var state = epochs();
        var original = state.beginCapture();
        for (int i = 0; i < 3; i++) mutation(state, GRAPH_DIRTY_UNKNOWN);
        var after = state.beginCapture();
        assertEquals(original.structural() + 3, after.structural());
        assertEquals(original.task(), after.task());
        assertFalse(state.isCurrent(original));
        assertTrue(state.isCurrent(after));
        assertFalse(state.canCertify(after, IDLE));
    }

    @Test void sectionLifetimeChangesDoNotReuseSameManagerCertificate() {
        for (var producer : new ShadowTraversalEpochs.Producer[]{SECTION_ADDED, SECTION_REMOVED}) {
            var state = epochs();
            var before = state.beginCapture();
            mutation(state, producer);
            var after = state.beginCapture();
            assertTrue(after.lifetime() > before.lifetime());
            assertTrue(after.structural() > before.structural());
            assertTrue(after.task() > before.task());
        }
    }

    @Test void openAndNestedMutationsCannotProduceUsableFences() {
        var state = epochs();
        var before = state.beginCapture(IDLE);
        state.beginMutation(BUILD_RESULTS);
        state.beginMutation(SECTION_INFO);
        var middle = state.beginCapture(IDLE);
        assertFalse(middle.quiescent());
        assertFalse(state.isCurrent(before));
        assertFalse(state.isCurrent(middle));
        state.endMutation(SECTION_INFO);
        assertFalse(state.isCurrent(state.beginCapture(IDLE)));
        state.endMutation(BUILD_RESULTS);
        assertFalse(state.isCurrent(middle));
        assertTrue(state.isCurrent(state.beginCapture(IDLE)));
        assertTrue(state.finishCapture(before, IDLE).isEmpty());
    }

    @Test void exceptionalProducerWithoutReturnRemainsConservativelyOpen() {
        var state = epochs();
        state.beginMutation(REBUILD_SCHEDULED); // Represents a native producer throwing before RETURN.
        assertFalse(state.beginCapture().quiescent());
        mutation(state, SORT_SCHEDULED);
        assertFalse(state.isCurrent(state.beginCapture()));
    }

    @Test void wrongOrUnbalancedReturnPermanentlyInvalidates() {
        var state = epochs();
        state.beginMutation(SECTION_INFO);
        state.endMutation(SORT_SCHEDULED);
        assertEquals(ShadowTraversalEpochs.Invalidity.UNBALANCED_MUTATION, state.beginCapture().invalidity());
        state.endMutation(SECTION_INFO);
        assertFalse(state.isCurrent(state.beginCapture()));
        state = epochs();
        state.endMutation(SECTION_INFO);
        assertFalse(state.isCurrent(state.beginCapture()));
    }

    @Test void issuerAndOwnerIdentityCannotBeSubstitutedWithEqualObjectsOrGenerations() {
        var manager = new String("manager");
        var world = new String("world");
        var renderer = new String("renderer");
        var first = new ShadowTraversalEpochs(manager, world, renderer);
        var second = new ShadowTraversalEpochs(manager, world, renderer);
        var snapshot = first.beginCapture(IDLE);
        assertTrue(first.matchesOwners(manager, world, renderer));
        assertFalse(first.matchesOwners(new String("manager"), world, renderer));
        assertFalse(first.matchesOwners(manager, new String("world"), renderer));
        assertFalse(first.matchesOwners(manager, world, new String("renderer")));
        assertFalse(second.isCurrent(snapshot));
        assertTrue(second.finishCapture(snapshot, IDLE).isEmpty());
    }

    @Test void unknownProducerAndDisposalAreStickyAndInvalidateOldAndNewSnapshots() {
        var state = epochs();
        var before = state.beginCapture(IDLE);
        state.unknownProducer();
        mutation(state, SECTION_INFO);
        assertFalse(state.isCurrent(before));
        assertFalse(state.isCurrent(state.beginCapture(IDLE)));
        assertEquals(ShadowTraversalEpochs.Invalidity.UNKNOWN_PRODUCER, state.beginCapture().invalidity());
        state = epochs();
        before = state.beginCapture();
        state.dispose();
        assertTrue(state.beginCapture().lifetime() > before.lifetime());
        assertEquals(ShadowTraversalEpochs.Invalidity.DISPOSED, state.beginCapture().invalidity());
        assertFalse(state.isCurrent(before));
        assertFalse(state.isCurrent(state.beginCapture()));
    }

    @Test void allCounterLimitsSaturateAndCannotRecycleCertificates() {
        for (int counter = 0; counter < 4; counter++) {
            long s = counter == 0 ? Long.MAX_VALUE : 1;
            long t = counter == 1 ? Long.MAX_VALUE : 1;
            long l = counter == 2 ? Long.MAX_VALUE : 1;
            long q = counter == 3 ? Long.MAX_VALUE : 1;
            var state = new ShadowTraversalEpochs(new Object(), new Object(), new Object(), s, t, l, q);
            var before = state.beginCapture();
            mutation(state, SECTION_ADDED);
            var after = state.beginCapture();
            assertEquals(ShadowTraversalEpochs.Invalidity.COUNTER_EXHAUSTED, after.invalidity());
            assertTrue(after.structural() >= before.structural());
            assertTrue(after.task() >= before.task());
            assertTrue(after.lifetime() >= before.lifetime());
            assertTrue(after.sequence() >= before.sequence());
            assertFalse(state.isCurrent(before));
            assertFalse(state.isCurrent(after));
        }
    }

    @Test void currentTaskObservationsMustMatchAndUnknownOrBusyNeverCertify() {
        var state = epochs();
        var before = state.beginCapture(IDLE);
        for (var changed : new ShadowTraversalEpochs.TaskState[]{
                ShadowTraversalEpochs.TaskState.unknown(),
                new ShadowTraversalEpochs.TaskState(true, 1, 0, 0),
                new ShadowTraversalEpochs.TaskState(true, 0, 1, 0),
                new ShadowTraversalEpochs.TaskState(true, 0, 0, 1),
                new ShadowTraversalEpochs.TaskState(true, -1, 0, 0)}) {
            assertFalse(changed.isKnownIdle());
            assertTrue(state.finishCapture(before, changed).isEmpty());
            assertFalse(state.canCertify(before, changed));
        }
        assertTrue(state.finishCapture(state.beginCapture(), null).isPresent(), "Unknown is diagnostic only");
        assertFalse(state.canCertify(state.beginCapture(), IDLE));
    }

    @Test void traversalFrameWrapResetOrDuplicatePermanentlyInvalidates() {
        for (int next : new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE}) {
            var state = epochs();
            state.observeTraversalFrame(Integer.MAX_VALUE);
            var before = state.beginCapture();
            state.observeTraversalFrame(next);
            assertEquals(ShadowTraversalEpochs.Invalidity.FRAME_REUSE, state.beginCapture().invalidity());
            assertFalse(state.isCurrent(before));
        }
        var state = epochs();
        state.observeTraversalFrame(7);
        state.observeTraversalFrame(8);
        assertTrue(state.isCurrent(state.beginCapture()));
    }

    @Test void wrongThreadCannotMutateOrValidateOwnerCertificate() throws InterruptedException {
        var state = epochs();
        var before = state.beginCapture();
        var accepted = new AtomicReference<Boolean>();
        var worker = new Thread(() -> accepted.set(state.isCurrent(before)));
        worker.start();
        worker.join();
        assertEquals(Boolean.FALSE, accepted.get());
        assertEquals(ShadowTraversalEpochs.Invalidity.UNKNOWN_PRODUCER, state.beginCapture().invalidity());
        assertFalse(state.isCurrent(before));
    }

    @Test void constructionGateDefaultsOffAndOnlyExplicitTrueEnables() {
        var previous = System.getProperty(ShadowTraversalEpochs.PROPERTY);
        try {
            System.clearProperty(ShadowTraversalEpochs.PROPERTY);
            assertFalse(ShadowTraversalEpochs.enabledAtConstruction());
            System.setProperty(ShadowTraversalEpochs.PROPERTY, "false");
            assertFalse(ShadowTraversalEpochs.enabledAtConstruction());
            System.setProperty(ShadowTraversalEpochs.PROPERTY, "true");
            assertTrue(ShadowTraversalEpochs.enabledAtConstruction());
        } finally {
            if (previous == null) System.clearProperty(ShadowTraversalEpochs.PROPERTY);
            else System.setProperty(ShadowTraversalEpochs.PROPERTY, previous);
        }
    }
}
