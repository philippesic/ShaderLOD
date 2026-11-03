package com.pp.shaderlod.dev.timing;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure event-order fixtures; no Minecraft, GLFW or GL initialization. */
class PhysicalResolutionEvidenceTest {
    public static void main(String[] arguments) {
        var summary = new org.junit.platform.launcher.listeners.SummaryGeneratingListener();
        var launcher = org.junit.platform.launcher.core.LauncherFactory.create();
        launcher.registerTestExecutionListeners(summary);
        launcher.execute(org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
                .selectors(org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(PhysicalResolutionEvidenceTest.class)).build());
        summary.getSummary().printTo(new java.io.PrintWriter(System.out, true));
        summary.getSummary().printFailuresTo(new java.io.PrintWriter(System.out, true));
        if (summary.getSummary().getTestsFoundCount() != 6 || summary.getSummary().getTestsFailedCount() != 0)
            throw new AssertionError("Physical-resolution evidence checks failed");
    }
    private static final PhysicalResolutionEvidence.Main MAIN =
            new PhysicalResolutionEvidence.Main(2976, 1672, 4, 5, 41, 42);
    private static PhysicalResolutionEvidence.Snapshot read(PhysicalResolutionEvidence state) {
        return state.snapshot(MAIN, List.of(), true);
    }
    @Test void selectionIsNotCompletionAndResizeNeedsNewClear() {
        var state = new PhysicalResolutionEvidence(); var pipeline = new Object(); var targets = new Object();
        state.armBoundary("native"); state.begin(pipeline, targets, 2976, 1672);
        state.clearSelected(targets);
        assertEquals(1, read(state).fullClearSelections()); assertEquals(0, read(state).fullClearCompletions());
        assertEquals(0, read(state).completedBoundaryEpoch());
        state.beginCompleted(pipeline, targets);
        var nativeState = read(state);
        assertEquals(nativeState.boundaryEpoch(), nativeState.completedBoundaryEpoch());
        state.armBoundary("physical75"); state.begin(pipeline, targets, 2976, 1672);
        state.resized(targets, 2232, 1254, true, true, true);
        assertTrue(read(state).allocationEpoch() > nativeState.allocationEpoch());
        assertEquals(nativeState.targetsIdentity(), read(state).targetsIdentity());
        assertEquals(nativeState.pipelineIdentity(), read(state).pipelineIdentity());
        state.beginCompleted(pipeline, targets);
        assertNotEquals(read(state).boundaryEpoch(), read(state).completedBoundaryEpoch());
        state.begin(pipeline, targets, 2232, 1254); state.clearSelected(targets); state.beginCompleted(pipeline, targets);
        assertEquals(read(state).allocationEpoch(), read(state).completedAllocationEpoch());
        assertEquals(read(state).boundaryEpoch(), read(state).completedBoundaryEpoch());
        assertEquals(2232, read(state).irisWidth()); assertEquals(2976, read(state).mainWidth());
        assertEquals(4, read(state).mainColorVersion()); assertEquals(5, read(state).mainDepthVersion());
    }
    @Test void wrongOwnerAndAbortedBeginCannotCompleteClear() {
        var state = new PhysicalResolutionEvidence(); var p = new Object(); var t = new Object();
        state.armBoundary("one"); state.clearSelected(t); state.begin(p, t, 10, 10);
        state.clearSelected(new Object()); state.beginCompleted(new Object(), t);
        assertEquals(0, read(state).fullClearCompletions());
        state.clearSelected(t); state.begin(p, t, 10, 10); state.beginCompleted(p, t);
        assertEquals(0, read(state).fullClearCompletions());
        state.clearSelected(t); state.beginCompleted(p, t);
        assertEquals(0, read(state).fullClearCompletions());
    }
    @Test void depthOnlyAndLazyAllocationInvalidatePreviouslyCompletedStorage() {
        var state = new PhysicalResolutionEvidence(); var p = new Object(); var t = new Object();
        state.begin(p, t, 10, 10); state.clearSelected(t); state.beginCompleted(p, t);
        var before = read(state);
        state.resized(t, 10, 10, false, true, false);
        assertEquals(before.colorStorageEpoch(), read(state).colorStorageEpoch());
        assertTrue(read(state).depthStorageEpoch() > before.depthStorageEpoch());
        assertNotEquals(read(state).allocationEpoch(), read(state).completedAllocationEpoch());
        long depth = read(state).depthStorageEpoch(); long allocation = read(state).allocationEpoch();
        state.resized(t, 10, 10, false, false, true);
        assertEquals(depth, read(state).depthStorageEpoch()); assertTrue(read(state).allocationEpoch() > allocation);
        long color = read(state).colorStorageEpoch(); state.targetCreated(t, 10, 10);
        assertTrue(read(state).colorStorageEpoch() > color);
        var stable = read(state); state.resized(t, 10, 10, false, false, false);
        assertEquals(stable.allocationEpoch(), read(state).allocationEpoch());
    }
    @Test void pipelineRecreationAndMidBeginBoundaryAreExported() {
        var state = new PhysicalResolutionEvidence(); var p = new Object(); var t = new Object();
        state.armBoundary("one"); state.begin(p, t, 10, 10); state.clearSelected(t);
        state.armBoundary("two"); state.beginCompleted(p, t);
        assertEquals(0, read(state).completedBoundaryEpoch());
        var before = read(state); var nextP = new Object(); var nextT = new Object();
        state.begin(nextP, nextT, 10, 10); state.beginCompleted(nextP, nextT);
        assertTrue(read(state).pipelineEpoch() > before.pipelineEpoch());
        assertNotEquals(before.pipelineIdentity(), read(state).pipelineIdentity());
        assertNotEquals(before.targetsIdentity(), read(state).targetsIdentity());
        assertEquals(0, read(state).completedAllocationEpoch());
    }
    @Test void exportsAreBoundedImmutableAndSignatureOmitsEventCopy() {
        var state = new PhysicalResolutionEvidence();
        assertEquals("unobserved", read(state).pipelineIdentity());
        for (int i = 0; i < 100; i++) state.armBoundary("boundary" + i);
        assertEquals(64, read(state).events().size()); assertEquals(36, read(state).droppedEvents());
        assertThrows(UnsupportedOperationException.class, () -> read(state).events().clear());
        assertTrue(state.snapshot(MAIN, List.of(), false).events().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> state.armBoundary(" "));
        assertThrows(IllegalArgumentException.class, () -> state.armBoundary("x".repeat(129)));
    }
    @Test void constructorReturnOnlyCompletesAnActuallySelectedUnchangedClear() {
        var state = new PhysicalResolutionEvidence(); var p = new Object(); var t = new Object();
        state.observeTargets(t, 10, 10); state.constructorCompleted(p, t);
        assertEquals(0, read(state).fullClearCompletions());
        state.clearSelected(t); state.constructorCompleted(new Object(), new Object());
        assertEquals(0, read(state).fullClearCompletions());
        state.constructorCompleted(p, t);
        assertEquals(1, read(state).fullClearCompletions());
        assertEquals(read(state).allocationEpoch(), read(state).completedAllocationEpoch());
        state.armBoundary("initialNative"); state.begin(p, t, 10, 10); state.beginCompleted(p, t);
        assertEquals(read(state).boundaryEpoch(), read(state).completedBoundaryEpoch());
        state.clearSelected(t); state.resized(t, 9, 9, true, true, false); state.constructorCompleted(p, t);
        assertEquals(1, read(state).fullClearCompletions());
        state.begin(p, t, 9, 9); state.clearSelected(t); state.constructorCompleted(p, t);
        assertEquals(1, read(state).fullClearCompletions());
    }
}
