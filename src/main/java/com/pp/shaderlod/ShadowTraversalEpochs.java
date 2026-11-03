package com.pp.shaderlod;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Manager-local producer fences for the pinned 1.21.1 no-skip prototype.
 * A stable fence is diagnostic evidence, not a traversal-reuse certificate.
 * R1 deliberately retains all unproven producer coverage gates.
 */
public final class ShadowTraversalEpochs {
    public static final String PROPERTY = "shaderlod.dev.shadowTraversalCertificate";

    public interface ManagerAccess {
        /** Null when the construction-time property gate was disabled. */
        ShadowTraversalEpochs shaderlod$shadowTraversalEpochs();
    }

    public enum Producer {
        SECTION_ADDED(true, true, true),
        SECTION_REMOVED(true, true, true),
        REBUILD_SCHEDULED(true, true, false),
        SECTION_INFO(true, false, false),
        BUILD_RESULTS(true, true, false),
        SORT_SCHEDULED(false, true, false),
        SORT_MOVEMENT(false, true, false),
        TASK_SUBMISSION(false, true, false),
        RESULT_COLLECTION(false, true, false),
        GRAPH_DIRTY_UNKNOWN(true, false, false);

        private final boolean structural;
        private final boolean task;
        private final boolean lifetime;

        Producer(boolean structural, boolean task, boolean lifetime) {
            this.structural = structural;
            this.task = task;
            this.lifetime = lifetime;
        }
    }

    /** These gates cannot be removed by a property, caller assertion or empty queue read. */
    public enum CoverageGap {
        ASYNC_RESULT_ENQUEUE,
        DIRECT_SECTION_AND_CANCELLATION_WRITERS,
        CURRENT_MAIN_AND_IRIS_SHADOW_TASK_MAPS,
        TASK_COLLECTOR_LIFETIME,
        TRANSFORMED_HOOK_COVERAGE
    }

    public enum Invalidity {
        NONE, COUNTER_EXHAUSTED, UNBALANCED_MUTATION, UNKNOWN_PRODUCER, FRAME_REUSE, DISPOSED
    }

    /** Observation supplied by the eventual typed current-task adapter; unknown is never idle. */
    public record TaskState(boolean known, int pending, int active, int queuedResults) {
        public static TaskState unknown() { return new TaskState(false, 0, 0, 0); }
        public boolean isKnownIdle() {
            return known && pending == 0 && active == 0 && queuedResults == 0;
        }
    }

    /** Opaque issuer and identity prevent equal-valued epochs from another manager being reused. */
    public static final class Snapshot {
        private final ShadowTraversalEpochs issuer;
        private final long structural;
        private final long task;
        private final long lifetime;
        private final long sequence;
        private final boolean quiescent;
        private final Invalidity invalidity;
        private final TaskState tasks;

        private Snapshot(ShadowTraversalEpochs issuer, TaskState tasks) {
            this.issuer = issuer;
            this.structural = issuer.structural;
            this.task = issuer.task;
            this.lifetime = issuer.lifetime;
            this.sequence = issuer.sequence;
            this.quiescent = issuer.mutationDepth == 0;
            this.invalidity = issuer.invalidity;
            this.tasks = tasks;
        }

        public long structural() { return structural; }
        public long task() { return task; }
        public long lifetime() { return lifetime; }
        public long sequence() { return sequence; }
        public boolean quiescent() { return quiescent; }
        public Invalidity invalidity() { return invalidity; }
        public TaskState tasks() { return tasks; }
        public Set<CoverageGap> coverageGaps() { return issuer.coverageGaps; }
    }

    private final Object manager;
    private final Object world;
    private final Object renderer;
    private final Thread ownerThread;
    private final Set<CoverageGap> coverageGaps = Collections.unmodifiableSet(EnumSet.allOf(CoverageGap.class));
    private final int[] producerDepth = new int[Producer.values().length];
    private long structural = 1;
    private long task = 1;
    private long lifetime = 1;
    private long sequence = 1;
    private int mutationDepth;
    private boolean hasFrame;
    private int lastFrame;
    private Invalidity invalidity = Invalidity.NONE;

    public ShadowTraversalEpochs(Object manager, Object world, Object renderer) {
        this.manager = Objects.requireNonNull(manager);
        this.world = Objects.requireNonNull(world);
        this.renderer = Objects.requireNonNull(renderer);
        this.ownerThread = Thread.currentThread();
    }

    // Bounded overflow fixture; never used by live hooks. Coverage gates stay identical.
    ShadowTraversalEpochs(Object manager, Object world, Object renderer,
                          long structural, long task, long lifetime, long sequence) {
        this(manager, world, renderer);
        if (structural < 1 || task < 1 || lifetime < 1 || sequence < 1) {
            throw new IllegalArgumentException("Generations must be positive");
        }
        this.structural = structural;
        this.task = task;
        this.lifetime = lifetime;
        this.sequence = sequence;
    }

    /** Missing/false property is off. Changing it later cannot attach a partial-lifetime tracker. */
    public static boolean enabledAtConstruction() { return Boolean.getBoolean(PROPERTY); }

    public synchronized boolean matchesOwners(Object manager, Object world, Object renderer) {
        return this.manager == manager && this.world == world && this.renderer == renderer;
    }

    /** Call before the first native producer write, including a potentially failing operation. */
    public synchronized void beginMutation(Producer producer) {
        Objects.requireNonNull(producer);
        if (!onOwnerThread()) return;
        if (mutationDepth == Integer.MAX_VALUE || producerDepth[producer.ordinal()] == Integer.MAX_VALUE) {
            invalidate(Invalidity.COUNTER_EXHAUSTED);
            return;
        }
        advance(producer);
        mutationDepth++;
        producerDepth[producer.ordinal()]++;
    }

    /** RETURN-only hooks leave the fence open on exceptions, conservatively preventing acceptance. */
    public synchronized void endMutation(Producer producer) {
        Objects.requireNonNull(producer);
        if (!onOwnerThread()) return;
        if (mutationDepth == 0 || producerDepth[producer.ordinal()] == 0) {
            invalidate(Invalidity.UNBALANCED_MUTATION);
            return;
        }
        producerDepth[producer.ordinal()]--;
        mutationDepth--;
        // A capture begun within a mutation must never match a post-return snapshot.
        sequence = increment(sequence);
    }

    /** Unknown notifications cannot become an Iris-force exemption through a shadow-context flag. */
    public synchronized void unknownProducer() {
        if (!onOwnerThread()) return;
        structural = increment(structural);
        task = increment(task);
        sequence = increment(sequence);
        invalidate(Invalidity.UNKNOWN_PRODUCER);
    }

    /** Called at the actual createTerrainRenderList boundary after Sodium increments its frame. */
    public synchronized void observeTraversalFrame(int frame) {
        if (!onOwnerThread()) return;
        if (hasFrame && frame <= lastFrame) invalidate(Invalidity.FRAME_REUSE);
        hasFrame = true;
        lastFrame = frame;
    }

    public synchronized void dispose() {
        if (!onOwnerThread()) return;
        lifetime = increment(lifetime);
        structural = increment(structural);
        task = increment(task);
        sequence = increment(sequence);
        invalidate(Invalidity.DISPOSED);
    }

    public synchronized Snapshot beginCapture() { return beginCapture(TaskState.unknown()); }

    public synchronized Snapshot beginCapture(TaskState tasks) {
        onOwnerThread();
        return new Snapshot(this, tasks == null ? TaskState.unknown() : tasks);
    }

    /** A no-skip capture fence only; coverage and current-task proof are separate requirements. */
    public synchronized Optional<Snapshot> finishCapture(Snapshot before, TaskState currentTasks) {
        if (!isCurrent(before)) return Optional.empty();
        TaskState after = currentTasks == null ? TaskState.unknown() : currentTasks;
        if (!before.tasks.equals(after)) return Optional.empty();
        return Optional.of(new Snapshot(this, after));
    }

    /** Equality of tracked generations, never a declaration of complete producer coverage. */
    public synchronized boolean isCurrent(Snapshot snapshot) {
        if (!onOwnerThread()) return false;
        return snapshot != null && snapshot.issuer == this && snapshot.quiescent
                && snapshot.invalidity == Invalidity.NONE && invalidity == Invalidity.NONE
                && mutationDepth == 0 && snapshot.structural == structural && snapshot.task == task
                && snapshot.lifetime == lifetime && snapshot.sequence == sequence;
    }

    /** R1 always refuses certification because its explicitly listed producer gates remain open. */
    public synchronized boolean canCertify(Snapshot snapshot, TaskState currentTasks) {
        return isCurrent(snapshot) && coverageGaps.isEmpty() && snapshot.tasks.isKnownIdle()
                && currentTasks != null && currentTasks.isKnownIdle();
    }

    private void advance(Producer producer) {
        if (producer.structural) structural = increment(structural);
        if (producer.task) task = increment(task);
        if (producer.lifetime) lifetime = increment(lifetime);
        sequence = increment(sequence);
    }

    private long increment(long value) {
        if (value == Long.MAX_VALUE) {
            invalidate(Invalidity.COUNTER_EXHAUSTED);
            return value; // Saturate, never wrap or recycle an old certificate.
        }
        return value + 1;
    }

    private boolean onOwnerThread() {
        if (Thread.currentThread() == ownerThread) return true;
        invalidate(Invalidity.UNKNOWN_PRODUCER);
        return false;
    }

    private void invalidate(Invalidity reason) {
        if (invalidity == Invalidity.NONE) invalidity = reason;
    }
}
