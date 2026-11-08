package com.pp.shaderlod.dev.timing.costmap;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Stage-1 canonical, immutable contract. No clocks are read and no renderer APIs are loaded.
 * All durations are integer nanoseconds; nullable values are unavailable, never implicit zero.
 * Serialization and catalog interpretation belong to COST_MAP.md stages 3–4.
 */
public final class CostMapRecords {
    public static final int SCHEMA_VERSION = 1;
    private CostMapRecords() {}

    public enum Status {
        MEASURED, UNSAMPLED, NOT_EXECUTED, DISABLED_BY_VERIFIED_CONFIGURATION,
        NOT_APPLICABLE, UNSUPPORTED, CONFLICT, DROPPED, PENDING, INVALID, UNKNOWN
    }
    public enum DhState { ABSENT, INSTALLED_RENDERING_DISABLED, ENABLED_READY,
        ENABLED_NOT_READY, UNSUPPORTED, FAILED, UNKNOWN }
    public enum Backend { TIMESTAMP, ELAPSED }
    public enum Operation { WRAPPER, DRAW_GROUP, DISPATCH, COPY, BLIT, CLEAR, MIP,
        UPLOAD, READBACK, ALLOCATION, BARRIER, CPU_WORK, WAIT, UNKNOWN }

    /** Reasons are required even for measured zero; structural absence has no duration. */
    public record Outcome(Status status, String reason) {
        public Outcome { Objects.requireNonNull(status); text(reason); }
    }
    /** A metadata value can be unknown, but must explain why. No assumed defaults. */
    public record Fact(String value, String evidence) {
        public Fact { text(value); text(evidence); }
    }
    public enum DhField {
        ARTIFACT, VERSION, LOADER, COMPATIBILITY, REQUESTED_VANILLA_DISTANCE,
        EFFECTIVE_VANILLA_DISTANCE, REQUESTED_DH_DISTANCE, EFFECTIVE_DH_DISTANCE,
        DISTANCE_UNITS, OVERRIDE_PROVENANCE, DATABASE_CACHE_IDENTITY, RESIDENCY, BACKLOG
    }
    public record DhIdentity(DhState state, String reason, long evidenceNs,
                             Map<DhField, Fact> identity, Map<String, Fact> exactSettings) {
        public DhIdentity {
            Objects.requireNonNull(state); text(reason);
            identity = Map.copyOf(identity); exactSettings = Map.copyOf(exactSettings);
            require(identity.keySet().containsAll(Set.of(DhField.values())), "incomplete DH identity");
            exactSettings.keySet().forEach(CostMapRecords::text);
        }
        public Outcome renderingApplicability() {
            return switch (state) {
                case ABSENT -> new Outcome(Status.NOT_APPLICABLE, reason);
                case INSTALLED_RENDERING_DISABLED -> new Outcome(Status.DISABLED_BY_VERIFIED_CONFIGURATION, reason);
                case UNSUPPORTED -> new Outcome(Status.UNSUPPORTED, reason);
                case FAILED -> new Outcome(Status.INVALID, reason);
                default -> new Outcome(Status.UNKNOWN, "Rendering execution requires later observation: " + reason);
            };
        }
    }
    /** Typed keys make minimum manifest fields explicit; unknown facts still occupy their keys. */
    public enum CaptureField {
        TOOL_VERSION, SOURCE_VERSION, OPERATION_RECEIPT, MOD_IDENTITY, ARCHIVE_IDENTITY,
        OPTIONS_IDENTITY, RESOURCE_PACK_IDENTITY, PRESET, DIMENSION, SCENE, WORLD, CAMERA,
        NATIVE_RESOLUTION, AA, IRIS_VERSION, SODIUM_VERSION, DH_VERSION, MOD_VERSION,
        JDK_VERSION, GPU, DRIVER, OS, MESA, KERNEL, GL_CONTEXT, VSYNC, FRAME_CAP,
        DISPLAY, COMPOSITOR, GRAPH_REVISION, CATALOG_REVISION, TIER, RESTORATION_STATUS
    }
    public record Capture(int schemaVersion, String captureId, String cohortId, String runId,
                          String processId, Map<CaptureField, Fact> identity, DhIdentity dh,
                          long samplingSeed, int samplingCadence) {
        public Capture {
            require(schemaVersion == SCHEMA_VERSION, "unsupported schema");
            text(captureId); text(cohortId); text(runId); text(processId);
            identity = Map.copyOf(identity); Objects.requireNonNull(dh);
            require(identity.keySet().containsAll(Set.of(CaptureField.values())), "incomplete capture identity");
            require(samplingCadence > 0, "cadence");
        }
    }
    public record FrameId(String captureId, String cohortId, long windowId, long epoch,
                          long frameId, long graphGeneration) {
        public FrameId {
            text(captureId); text(cohortId); positive(windowId); positive(epoch);
            positive(frameId); positive(graphGeneration);
        }
    }
    public record Frame(FrameId id, long startNs, Long nextStartNs, Long rootStartNs, Long rootEndNs,
                        boolean warmup, boolean partialStart, boolean partialEnd, boolean transition,
                        Set<Integer> sampledScopeSetIds, Outcome eligibility, String endReason) {
        public Frame {
            Objects.requireNonNull(id); Objects.requireNonNull(eligibility); text(endReason);
            sampledScopeSetIds = Set.copyOf(sampledScopeSetIds);
            sampledScopeSetIds.forEach(CostMapRecords::positive);
            if (nextStartNs != null) {
                require(nextStartNs >= startNs, "frame clock reset"); Math.subtractExact(nextStartNs, startNs);
            }
            require((rootStartNs == null) == (rootEndNs == null), "partial root endpoints");
            if (rootStartNs != null) require(rootEndNs >= rootStartNs, "root clock reset");
        }
    }
    /** Key is graph/program identity, never a display-name key. IDs stay stable until registry disposal. */
    public record ScopeKey(long graphGeneration, String stageId, String programId,
                           Operation operation, String discriminator) {
        public ScopeKey {
            positive(graphGeneration); text(stageId); text(programId);
            Objects.requireNonNull(operation); text(discriminator);
        }
    }
    public record Scope(int id, ScopeKey key, String name, Set<String> featureLinks,
                        String confidence, Outcome expected) {
        public Scope {
            positive(id); Objects.requireNonNull(key); text(name); text(confidence);
            featureLinks = Set.copyOf(featureLinks); featureLinks.forEach(CostMapRecords::text);
            Objects.requireNonNull(expected);
        }
    }
    /** DH context is carried even when no parent query was scheduled. Use explicit unknown/N/A values. */
    public record Invocation(long eventId, Long parentEventId, FrameId frame, int scopeId,
                             long executionOrdinal, long threadId, String contextId,
                             String dhStage, String worldShadow, String opacity, Outcome observed) {
        public Invocation {
            positive(eventId); Objects.requireNonNull(frame); positive(scopeId); positive(executionOrdinal);
            positive(threadId); text(contextId); text(dhStage); text(worldShadow); text(opacity);
            Objects.requireNonNull(observed);
            if (parentEventId != null) require(parentEventId > 0 && parentEventId < eventId, "parent event identity");
        }
    }
    public record Clock(String domain, int counterBits, long resolutionNs, long generation) {
        public Clock { text(domain); require(counterBits >= 1 && counterBits <= 64, "counter width");
            positive(resolutionNs); require(generation >= 0, "clock generation"); }
    }
    public record CpuEvent(Invocation invocation, Long wallStartNs, Long wallEndNs,
                           Long threadCpuNs, Clock clock, Outcome sampling,
                           String waitSite, String evidenceLevel) {
        public CpuEvent {
            Objects.requireNonNull(invocation); Objects.requireNonNull(clock); Objects.requireNonNull(sampling);
            text(waitSite); text(evidenceLevel);
            if (sampling.status == Status.MEASURED) {
                require(wallStartNs != null && wallEndNs != null && wallEndNs >= wallStartNs, "CPU endpoints");
                long wallNs = Math.subtractExact(wallEndNs, wallStartNs);
                if (threadCpuNs != null) {
                    require(threadCpuNs >= 0, "thread CPU duration");
                    require(threadCpuNs <= wallNs || threadCpuNs - wallNs <= clock.resolutionNs,
                            "thread CPU exceeds wall beyond clock resolution");
                }
            } else require(wallStartNs == null && wallEndNs == null && threadCpuNs == null, "missing CPU has values");
        }
    }
    /** Retrieval identity does not replace issue ownership. No CPU/GPU clock alignment is implied. */
    public record GpuEvent(Invocation invocation, FrameId retrievalFrame, Long beginValue, Long endValue,
                           Long elapsedNs, Clock clock, Backend backend, Outcome availability) {
        public GpuEvent {
            Objects.requireNonNull(invocation); Objects.requireNonNull(clock);
            Objects.requireNonNull(backend); Objects.requireNonNull(availability);
            duration(availability, elapsedNs);
            if (availability.status == Status.MEASURED) require(retrievalFrame != null, "missing retrieval identity");
            if (retrievalFrame != null) {
                require(retrievalFrame.captureId.equals(invocation.frame.captureId), "foreign capture retrieval");
                require(retrievalFrame.epoch > invocation.frame.epoch || (retrievalFrame.epoch == invocation.frame.epoch
                        && retrievalFrame.frameId >= invocation.frame.frameId), "retrieval precedes issue");
            }
            if (backend == Backend.ELAPSED) require(beginValue == null && endValue == null, "elapsed has no GPU positions");
            else if (availability.status == Status.MEASURED) require(beginValue != null && endValue != null, "timestamp endpoints");
            if (availability.status != Status.MEASURED) require(beginValue == null && endValue == null, "missing GPU has endpoints");
        }
    }
    public record Counts(Long eligible, Long executed, Long scheduled, Long issued, Long completed) {
        public Counts {
            for (Long count : new Long[] {eligible, executed, scheduled, issued, completed})
                if (count != null) require(count >= 0, "negative counts");
        }
    }
    public record Coverage(FrameId frame, int scopeId, Counts counts, Outcome outcome,
                           String physicalCoverage, String semanticCoverage, String workloadCoverage) {
        public Coverage {
            Objects.requireNonNull(frame); positive(scopeId); Objects.requireNonNull(counts); Objects.requireNonNull(outcome);
            text(physicalCoverage); text(semanticCoverage); text(workloadCoverage);
        }
    }
    /** Health is available out of band, even when the event buffer is full. Null diagnostics are unobserved. */
    public record Health(int scopeLimit, int eventLimit, int activeLimit, int pendingLimit,
                         Integer queryPoolLimit, long conflicts, long queueEvictions,
                         long droppedEvents, Long droppedBytes, long unresolvedTail, long imbalancedScopes,
                         Long timeouts, Long resets, Long disjoints, Long timerOverheadNs, Outcome outcome) {
        public Health {
            positive(scopeLimit); positive(eventLimit); positive(activeLimit); positive(pendingLimit);
            if (queryPoolLimit != null) positive(queryPoolLimit);
            for (long value : new long[] {conflicts, queueEvictions, droppedEvents, unresolvedTail, imbalancedScopes})
                require(value >= 0, "negative health count");
            for (Long value : new Long[] {droppedBytes, timeouts, resets, disjoints, timerOverheadNs})
                if (value != null) require(value >= 0, "negative diagnostic");
            Objects.requireNonNull(outcome);
        }
    }
    /** Schema only. Stage 4 computes distributions; no implicit filling of missing frames. */
    public record Aggregate(String cohortId, int scopeId, String metric, String denominator,
                            long frames, Set<Long> windows, Set<String> processes, Long meanNs,
                            Long medianNs, Long p95Ns, Long sdNs, Double varianceNsSquared,
                            Map<Status, Long> missingness, String inclusionPolicy,
                            String attributionGrade, String uncertainty, Outcome outcome) {
        public Aggregate {
            text(cohortId); positive(scopeId); text(metric); text(denominator); require(frames >= 0, "frames");
            windows = Set.copyOf(windows); windows.forEach(CostMapRecords::positive);
            processes = Set.copyOf(processes); processes.forEach(CostMapRecords::text);
            missingness = Map.copyOf(missingness); missingness.values().forEach(v -> require(v >= 0, "missing count"));
            text(inclusionPolicy); text(attributionGrade); text(uncertainty); Objects.requireNonNull(outcome);
            duration(outcome, meanNs); duration(outcome, medianNs); duration(outcome, p95Ns); duration(outcome, sdNs);
            if (outcome.status == Status.MEASURED)
                require(varianceNsSquared != null && Double.isFinite(varianceNsSquared) && varianceNsSquared >= 0, "variance");
            else require(varianceNsSquared == null, "missing variance");
        }
    }
    static void duration(Outcome outcome, Long ns) {
        if (outcome.status == Status.MEASURED) require(ns != null && ns >= 0, "measured duration");
        else require(ns == null, "missing duration must be null");
    }
    static void positive(long value) { require(value > 0, "positive ID/limit required"); }
    static void text(String value) { require(value != null && !value.isBlank(), "nonblank text required"); }
    static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}
