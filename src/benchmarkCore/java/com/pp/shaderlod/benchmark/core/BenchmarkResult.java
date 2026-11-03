package com.pp.shaderlod.benchmark.core;

import com.pp.shaderlod.measurement.FrameMeasurement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Dependency-free, versioned capture/result DTOs shared by installed and absent observers. */
public final class BenchmarkResult {
    public static final int SCHEMA_VERSION = 1;
    public enum Arm { ABSENT, MASTER_OFF, MASTER_ON_NO_REQUESTS, FEATURE, RETURN }
    public enum Temperature { COLD_BOOT, COLD_PIPELINE, WARM, UNKNOWN }
    public enum Stage { CONTROL, SEARCH, CONFIRMATION, HOLDOUT, DIAGNOSTIC }
    public enum Motion { STILL, MOTION, STREAMING }
    public enum EvidenceVerdict { PASS, FAIL, UNTESTED, UNAVAILABLE }
    public enum RecoveryStatus { RESTORED, FAILED, PENDING, NOT_REQUIRED, UNKNOWN }

    public record Environment(String tupleId, Map<String, String> loadedArtifacts,
            String device, String os, String driver, String jvm) {
        public Environment { loadedArtifacts = Map.copyOf(loadedArtifacts); }
    }
    public record Scenario(String id, String revision, String routeRevision, String caseId,
            String dimension, Map<String, String> temporalInputs) {
        public Scenario { temporalInputs = Map.copyOf(temporalInputs); }
    }
    public record Configuration(String selectedSource, String sourceIdentity,
            Map<String, String> effectiveOptions, Map<String, String> resources, String aa,
            Integer viewportWidth, Integer viewportHeight, Integer renderDistance,
            Integer simulationDistance, String dhState, String cacheState) {
        public Configuration { effectiveOptions = Map.copyOf(effectiveOptions); resources = Map.copyOf(resources); }
    }
    /** null numeric values mean unavailable, never zero-fill an absent clock. */
    public record Clock(String id, String definition, String scope, String backend,
            BenchmarkAnalysis.Availability availability, Long resolutionNs, String queryCadence,
            String asynchronousTailHandling, long requested, long valid, long skipped, long invalid,
            List<String> coverage, List<String> reasons) {
        public Clock {
            Objects.requireNonNull(availability); coverage = List.copyOf(coverage); reasons = List.copyOf(reasons);
            if (requested < 0 || valid < 0 || skipped < 0 || invalid < 0
                    || valid > requested || skipped > requested - valid || invalid > requested - valid - skipped)
                throw new IllegalArgumentException("inconsistent clock sample counts");
            if (resolutionNs != null && resolutionNs <= 0) throw new IllegalArgumentException("positive timer resolution required");
        }
    }
    public record Work(String signal, String unit, Double requested, Double eligible, Double executed,
            BenchmarkAnalysis.Availability availability, String scope, List<String> evidenceLinks,
            List<String> reasons) {
        public Work { Objects.requireNonNull(availability); evidenceLinks = List.copyOf(evidenceLinks); reasons = List.copyOf(reasons); }
    }
    public record Warmup(Double excludedDurationMs, Boolean ready, List<String> observedSignals,
            List<String> missingSignals, List<String> timeoutReasons, List<String> captureStabilityEvidence) {
        public Warmup {
            observedSignals = List.copyOf(observedSignals); missingSignals = List.copyOf(missingSignals);
            timeoutReasons = List.copyOf(timeoutReasons); captureStabilityEvidence = List.copyOf(captureStabilityEvidence);
        }
    }
    /** Construct after capture/drain; does not alter the frame-clock hot path. */
    public record Sample(int schemaVersion, long frameId, long epoch, Long totalFrameNs,
            Long renderWallNs, Long renderThreadCpuNs, Long processCpuNs, Long gpuElapsedNs,
            String gpuStatus) {
        public Sample { checkVersion(schemaVersion); }
        public static Sample from(FrameMeasurement.Sample s) {
            return new Sample(SCHEMA_VERSION, s.frame(), s.epoch(), s.frameWallNs(), s.renderWallNs(),
                    s.renderCpuNs(), s.processCpuNs(), s.gpuNs(), s.gpuStatus());
        }
    }
    public record Window(int schemaVersion, String id, String comparisonId, String repeatId,
            int order, Arm arm, Map<String, String> featureProfile, Long sourceGeneration,
            Long pipelineGeneration, Integer reloadCount, Temperature temperature, Stage stage,
            Motion motion, Scenario scenario, Configuration configuration, String observerMode,
            Map<String, String> observerCost, List<Clock> clocks, Warmup warmup,
            Map<String, String> hostObservations, List<Work> work, Long firstRawFrameId,
            Long lastRawFrameId, List<Sample> samples, BenchmarkStatistics.Summary statistics,
            BenchmarkAnalysis.Availability availability, BenchmarkAnalysis.Activation activation,
            BenchmarkAnalysis.Validity validity, List<String> reasons) {
        public Window {
            checkVersion(schemaVersion); text(id, "window id"); text(comparisonId, "comparison id"); text(repeatId, "repeat id");
            featureProfile = Map.copyOf(featureProfile); observerCost = Map.copyOf(observerCost);
            clocks = List.copyOf(clocks); hostObservations = Map.copyOf(hostObservations);
            work = List.copyOf(work); samples = List.copyOf(samples); reasons = List.copyOf(reasons);
            Objects.requireNonNull(arm); Objects.requireNonNull(temperature); Objects.requireNonNull(stage);
            Objects.requireNonNull(motion); Objects.requireNonNull(availability); Objects.requireNonNull(activation);
            Objects.requireNonNull(validity);
        }
    }
    public record Evidence(EvidenceVerdict quality, EvidenceVerdict compatibility,
            List<String> stillLinks, List<String> motionLinks, List<String> compatibilityLinks,
            List<String> reasons) {
        public Evidence {
            Objects.requireNonNull(quality); Objects.requireNonNull(compatibility);
            stillLinks = List.copyOf(stillLinks); motionLinks = List.copyOf(motionLinks);
            compatibilityLinks = List.copyOf(compatibilityLinks); reasons = List.copyOf(reasons);
        }
    }
    public record Recovery(RecoveryStatus status, String journalLink, boolean ownershipReleased,
            List<String> reasons) {
        public Recovery { Objects.requireNonNull(status); reasons = List.copyOf(reasons); }
    }
    public record SearchCoverage(int attemptedCandidates, int retries, List<String> searchedCases,
            List<String> heldOutCases, boolean profileFrozenBeforeHoldout, List<String> reasons) {
        public SearchCoverage {
            if (attemptedCandidates < 0 || retries < 0) throw new IllegalArgumentException("negative search counts");
            searchedCases = List.copyOf(searchedCases); heldOutCases = List.copyOf(heldOutCases); reasons = List.copyOf(reasons);
        }
    }
    public record Manifest(int schemaVersion, String analysisVersion, String runId, String createdAtUtc,
            Environment environment, List<Window> windows, List<BenchmarkAnalysis.Decision> decisions,
            Evidence evidence, Recovery recovery, SearchCoverage searchCoverage,
            List<String> evidenceLinks, List<String> limitations) {
        public Manifest {
            checkVersion(schemaVersion); text(analysisVersion, "analysis version"); text(runId, "run id");
            text(createdAtUtc, "creation UTC time"); Objects.requireNonNull(environment);
            windows = List.copyOf(windows); decisions = List.copyOf(decisions);
            Objects.requireNonNull(evidence); Objects.requireNonNull(recovery);
            Objects.requireNonNull(searchCoverage); evidenceLinks = List.copyOf(evidenceLinks); limitations = List.copyOf(limitations);
        }
        /** Missing evidence remains exportable; these reasons prevent treating it as a qualified result. */
        public List<String> qualificationBlockers() {
            var reasons = new ArrayList<String>();
            if (!BenchmarkAnalysis.ANALYSIS_VERSION.equals(analysisVersion)) reasons.add("unknown analysis version");
            if (blank(environment.tupleId()) || environment.loadedArtifacts().isEmpty()
                    || blank(environment.device()) || blank(environment.os()) || blank(environment.driver()) || blank(environment.jvm()))
                reasons.add("missing tuple/artifact/device/OS/driver/JVM identity");
            if (windows.isEmpty()) reasons.add("no captured windows");
            for (var w : windows) {
                if (w.scenario() == null || blank(w.scenario().id()) || blank(w.scenario().revision())
                        || blank(w.scenario().routeRevision()) || blank(w.scenario().caseId()) || blank(w.scenario().dimension())
                        || w.scenario().temporalInputs().isEmpty()) reasons.add("missing scenario/temporal identity: " + w.id());
                var c = w.configuration();
                if (c == null || blank(c.selectedSource()) || blank(c.sourceIdentity()) || blank(c.aa())
                        || c.viewportWidth() == null || c.viewportWidth() <= 0 || c.viewportHeight() == null || c.viewportHeight() <= 0
                        || c.renderDistance() == null || c.simulationDistance() == null || blank(c.dhState()) || blank(c.cacheState()))
                    reasons.add("missing configuration identity: " + w.id());
                if (w.sourceGeneration() == null || w.pipelineGeneration() == null || w.reloadCount() == null
                        || w.temperature() == Temperature.UNKNOWN || blank(w.observerMode()) || w.clocks().isEmpty())
                    reasons.add("missing pipeline/clock/observer identity: " + w.id());
                if (w.warmup() == null || !Boolean.TRUE.equals(w.warmup().ready())
                        || !w.warmup().missingSignals().isEmpty() || !w.warmup().timeoutReasons().isEmpty()
                        || w.warmup().captureStabilityEvidence().isEmpty()) reasons.add("readiness/capture stability unproved: " + w.id());
                if (w.validity() != BenchmarkAnalysis.Validity.VALID || w.activation() != BenchmarkAnalysis.Activation.ACTIVE
                        || w.availability() != BenchmarkAnalysis.Availability.AVAILABLE || w.statistics() == null)
                    reasons.add("window unavailable/inactive/invalid/untested: " + w.id());
            }
            if (decisions.isEmpty()) reasons.add("no analyzed comparisons");
            if (decisions.stream().anyMatch(d -> !d.positive() || d.controlKind() != BenchmarkAnalysis.ControlKind.OPTIMIZATION))
                reasons.add("comparison is not a positive optimization timing result");
            if (evidence.quality() != EvidenceVerdict.PASS || evidence.compatibility() != EvidenceVerdict.PASS
                    || evidence.stillLinks().isEmpty() || evidence.motionLinks().isEmpty() || evidence.compatibilityLinks().isEmpty())
                reasons.add("quality/compatibility evidence incomplete");
            if ((recovery.status() != RecoveryStatus.RESTORED && recovery.status() != RecoveryStatus.NOT_REQUIRED)
                    || !recovery.ownershipReleased()) reasons.add("restoration/ownership release incomplete");
            if (searchCoverage.attemptedCandidates() > 0 && (!searchCoverage.profileFrozenBeforeHoldout()
                    || searchCoverage.heldOutCases().isEmpty() || windows.stream().noneMatch(w -> w.stage() == Stage.HOLDOUT)))
                reasons.add("fresh frozen-profile holdout incomplete");
            return List.copyOf(reasons);
        }
    }
    private BenchmarkResult() {}
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static void text(String value, String field) { if (blank(value)) throw new IllegalArgumentException(field + " required"); }
    private static void checkVersion(int version) { if (version != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported schema version"); }
}
