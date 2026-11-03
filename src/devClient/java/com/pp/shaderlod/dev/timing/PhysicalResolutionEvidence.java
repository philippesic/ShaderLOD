package com.pp.shaderlod.dev.timing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** CPU-only evidence state. Epochs describe observed storage transitions, never texture IDs. */
public final class PhysicalResolutionEvidence {
    public record Target(int index, boolean allocated, int width, int height, int mainTexture,
                         int altTexture, long mainStorageEpoch, long altStorageEpoch) {}
    public record Event(String kind, long boundaryEpoch, long allocationEpoch, long pipelineEpoch,
                        int width, int height) {}
    public record Main(int width, int height, int colorVersion, int depthVersion,
                       int colorTexture, int depthTexture) {}
    public record Snapshot(int evidenceVersion, boolean enabled, String boundaryLabel,
            long boundaryEpoch, long completedBoundaryEpoch, long allocationEpoch,
            long colorStorageEpoch, long depthStorageEpoch, long pipelineEpoch,
            String pipelineIdentity, String targetsIdentity, int irisWidth, int irisHeight,
            int mainWidth, int mainHeight, int mainColorVersion, int mainDepthVersion,
            int mainColorTexture, int mainDepthTexture, long fullClearSelections,
            long fullClearCompletions, long selectedAllocationEpoch, long completedAllocationEpoch,
            long beginCompletions, long droppedEvents, List<Target> targets, List<Event> events) {
        public Snapshot { targets = List.copyOf(targets); events = List.copyOf(events); }
    }

    private static final int MAX_EVENTS = 64;
    private final List<Event> events = new ArrayList<>();
    private Object pipeline, targetsOwner;
    private long pipelineEpoch, targetsEpoch, allocationEpoch, colorStorageEpoch, depthStorageEpoch;
    private String pipelineIdentity = "unobserved", targetsIdentity = "unobserved";
    private long boundaryEpoch, completedBoundaryEpoch, fullClearSelections, fullClearCompletions;
    private long selectedAllocationEpoch, completedAllocationEpoch, beginCompletions, droppedEvents;
    private int width = -1, height = -1;
    private boolean inBegin, selectedInBegin, selectedInConstructor;
    private long beginBoundary, beginPipeline;
    private String boundaryLabel = "unarmed";

    public void armBoundary(String label) {
        if (Objects.requireNonNull(label).isBlank() || label.length() > 128)
            throw new IllegalArgumentException("Require bounded boundary label");
        boundaryLabel = label; boundaryEpoch++; event("BOUNDARY_ARMED");
    }
    public void begin(Object pipeline, Object targets, int width, int height) {
        observePipeline(pipeline);
        observeTargets(targets, width, height);
        inBegin = true; selectedInBegin = false; selectedInConstructor = false;
        beginBoundary = boundaryEpoch; beginPipeline = pipelineEpoch;
    }
    private void observePipeline(Object pipeline) {
        if (this.pipeline != Objects.requireNonNull(pipeline)) {
            this.pipeline = pipeline; pipelineEpoch++;
            pipelineIdentity = identity(pipeline, pipelineEpoch); event("PIPELINE_OBSERVED");
        }
    }
    public void observeTargets(Object targets, int width, int height) {
        if (targetsOwner != Objects.requireNonNull(targets)) {
            targetsOwner = targets; targetsEpoch++; allocationEpoch++; colorStorageEpoch++; depthStorageEpoch++;
            targetsIdentity = identity(targets, targetsEpoch);
            this.width = width; this.height = height;
            completedAllocationEpoch = 0; selectedAllocationEpoch = 0;
            selectedInConstructor = false;
            event("TARGETS_FIRST_OBSERVED");
        }
    }
    public void targetCreated(Object targets, int width, int height) {
        observeTargets(targets, width, height);
        allocationEpoch++; colorStorageEpoch++; event("COLOR_TARGET_CREATED");
    }
    /** Return true means color storage resized; false may still resize/rebind depth. */
    public void resized(Object targets, int width, int height, boolean colorResized,
                        boolean depthStorageResized, boolean depthRebound) {
        observeTargets(targets, width, height);
        this.width = width; this.height = height;
        if (colorResized || depthStorageResized || depthRebound) {
            allocationEpoch++;
            if (colorResized) colorStorageEpoch++;
            if (depthStorageResized) depthStorageEpoch++;
            event(colorResized ? "COLOR_STORAGE_RESIZED" : depthStorageResized
                    ? "DEPTH_STORAGE_RESIZED" : "MAIN_DEPTH_REBOUND");
        }
    }
    /** Iris onFullClear selects full passes; it precedes their execution. */
    public void clearSelected(Object targets) {
        if (targets != targetsOwner) return;
        fullClearSelections++; selectedAllocationEpoch = allocationEpoch;
        if (inBegin) selectedInBegin = true;
        else selectedInConstructor = true;
        event("FULL_CLEAR_SELECTED");
    }
    /** Constructor may clear before a setup compute dispatch; completion requires that selection. */
    public void constructorCompleted(Object pipeline, Object targets) {
        if (inBegin || targets != targetsOwner || !selectedInConstructor
                || selectedAllocationEpoch != allocationEpoch) return;
        observePipeline(pipeline);
        fullClearCompletions++; completedAllocationEpoch = allocationEpoch;
        selectedInConstructor = false; event("CONSTRUCTOR_FULL_CLEAR_COMPLETED");
    }
    /** Hook only after normal beginLevelRendering return, after its clear loop. */
    public void beginCompleted(Object pipeline, Object targets) {
        if (!inBegin || this.pipeline != pipeline || targetsOwner != targets
                || beginPipeline != pipelineEpoch) return;
        beginCompletions++;
        if (selectedInBegin && selectedAllocationEpoch == allocationEpoch) {
            fullClearCompletions++; completedAllocationEpoch = allocationEpoch;
            event("FULL_CLEAR_COMPLETED");
        }
        if (beginBoundary == boundaryEpoch && completedAllocationEpoch == allocationEpoch)
            completedBoundaryEpoch = boundaryEpoch;
        inBegin = false; selectedInBegin = false;
    }
    public Snapshot snapshot(Main main, List<Target> targets, boolean includeEvents) {
        Objects.requireNonNull(main);
        return new Snapshot(1, true, boundaryLabel, boundaryEpoch, completedBoundaryEpoch,
                allocationEpoch, colorStorageEpoch, depthStorageEpoch, pipelineEpoch,
                pipelineIdentity, targetsIdentity, width, height,
                main.width(), main.height(), main.colorVersion(), main.depthVersion(),
                main.colorTexture(), main.depthTexture(), fullClearSelections, fullClearCompletions,
                selectedAllocationEpoch, completedAllocationEpoch, beginCompletions, droppedEvents,
                targets, includeEvents ? events : List.of());
    }
    public long colorStorageEpoch() { return colorStorageEpoch; }
    private static String identity(Object object, long epoch) {
        return object == null ? "unobserved" : object.getClass().getName() + "#" + epoch
                + "@" + Integer.toHexString(System.identityHashCode(object));
    }
    private void event(String kind) {
        if (events.size() < MAX_EVENTS)
            events.add(new Event(kind, boundaryEpoch, allocationEpoch, pipelineEpoch, width, height));
        else droppedEvents++;
    }
}
