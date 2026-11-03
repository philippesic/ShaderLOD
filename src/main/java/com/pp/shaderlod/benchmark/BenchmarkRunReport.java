package com.pp.shaderlod.benchmark;

import com.pp.shaderlod.benchmark.core.AnalysisPolicy;
import com.pp.shaderlod.benchmark.core.BenchmarkAnalysis;
import com.pp.shaderlod.benchmark.core.BenchmarkResult;
import com.pp.shaderlod.benchmark.core.BenchmarkStatistics;
import com.pp.shaderlod.measurement.FrameMeasurement;
import com.pp.shaderlod.optimization.OptimizationSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/** Post-capture projection into the shared versioned contract. No optimization verdict is produced. */
public final class BenchmarkRunReport {
    private static final AnalysisPolicy POLICY = new AnalysisPolicy(30, 100, 2, 1, 1,
            "Descriptive baseline only; independent repeats and control calibration unavailable", 50);
    public record Context(BenchmarkResult.Environment environment, BenchmarkResult.Configuration configuration,
                          Map<String, String> profile, BenchmarkResult.Arm arm, Map<String, String> pins) {
        public Context { profile = Map.copyOf(profile); pins = Map.copyOf(pins); }
    }
    private BenchmarkRunReport() {}
    public static Context context(Map<String, String> pins,
            Map<String, String> options, List<String> resources, OptimizationSnapshot flags, boolean enabled,
            int simulationDistance, int renderDistance, int framebufferWidth, int framebufferHeight, String actualDhState) {
        var artifacts = new TreeMap<String, String>();
        pins.forEach((key, value) -> { if (key.contains("artifact") || key.contains("mod_")) artifacts.put(key, value); });
        var profile = new TreeMap<String, String>();
        flags.flags().forEach((key, value) -> profile.put(key.key(), value.toString()));
        profile.put("selected_core_settings", pins.getOrDefault("settings", "unavailable"));
        var selectedResources = new TreeMap<String, String>();
        for (int i = 0; i < resources.size(); i++) selectedResources.put(Integer.toString(i), resources.get(i));
        var config = new BenchmarkResult.Configuration(pins.getOrDefault("source_path", "unavailable"),
                pins.getOrDefault("source_sha256", "unavailable"), options, selectedResources,
                "Native selected AA preserved; see effectiveOptions; active AA mode not independently verified",
                framebufferWidth, framebufferHeight, renderDistance, simulationDistance,
                actualDhState, "Warm owned scene; OS/driver cache state uncontrolled");
        // Core presets can still alter work when independent feature requests are empty.
        // This observer never establishes an original/masterOn-no-requests control by implication.
        var arm = !enabled ? BenchmarkResult.Arm.MASTER_OFF : BenchmarkResult.Arm.FEATURE;
        return new Context(new BenchmarkResult.Environment("root NeoForge MC1.21.1/Iris; see loadedArtifacts", artifacts,
                pins.getOrDefault("gpu_renderer", "unavailable"), pins.getOrDefault("os", "unavailable"),
                pins.getOrDefault("gl_version", "unavailable"), pins.getOrDefault("java", "unavailable")), config, profile, arm, pins);
    }
    public static BenchmarkResult.Manifest manifest(Context context, String id, BenchmarkScenario.Schedule schedule,
            BenchmarkScenarioRun.Result result, Map<String, List<FrameMeasurement.Sample>> samples,
            Map<String, String> host, Map<String, Long> caseStarted, Map<String, Long> captureStarted,
            String failure, long elapsedNanos) {
        return manifest(context,id,schedule,result,samples,host,caseStarted,captureStarted,failure,elapsedNanos,Map.of());
    }
    public static BenchmarkResult.Manifest manifest(Context context, String id, BenchmarkScenario.Schedule schedule,
            BenchmarkScenarioRun.Result result, Map<String, List<FrameMeasurement.Sample>> samples,
            Map<String, String> host, Map<String, Long> caseStarted, Map<String, Long> captureStarted,
            String failure, long elapsedNanos, Map<String, com.pp.shaderlod.measurement.CpuWindowMeasurement.Result> cpuWindows) {
        if (result != null && result.stage() != BenchmarkScenarioRun.Stage.FINISHED)
            throw new IllegalStateException("Stop capture and finish restoration before constructing an export");
        var windows = new ArrayList<BenchmarkResult.Window>();
        for (int i = 0; i < schedule.cases().size(); i++) {
            var c = schedule.cases().get(i);
            var r = result == null ? new BenchmarkScenarioRun.CaseResult(c, BenchmarkScenarioRun.Verdict.NOT_RUN, null, "Preparation did not reach this case") : result.cases().get(i);
            var rows = samples.getOrDefault(c.id(), List.of());
            boolean initialUnavailable = !rows.isEmpty() && rows.getFirst().frameWallNs() == null;
            var measuredRows = initialUnavailable ? rows.subList(1, rows.size()) : rows;
            var stats = BenchmarkStatistics.summarize(measuredRows, POLICY);
            var availability = r.verdict() == BenchmarkScenarioRun.Verdict.UNAVAILABLE ? BenchmarkAnalysis.Availability.UNAVAILABLE
                    : rows.isEmpty() ? BenchmarkAnalysis.Availability.UNTESTED : BenchmarkAnalysis.Availability.AVAILABLE;
            var reasons = new ArrayList<String>();
            reasons.add(r.reason());
            reasons.add("Selected-configuration baseline only; no matched comparison, search, holdout or promotion");
            if (!failure.isEmpty()) reasons.add(failure);
            boolean complete = r.verdict() == BenchmarkScenarioRun.Verdict.COMPLETE && failure.isEmpty();
            boolean clockValid = stats.count() > 1 && BenchmarkStatistics.completeCoverage(stats);
            var validity = rows.isEmpty() ? BenchmarkAnalysis.Validity.UNTESTED
                    : complete && clockValid ? BenchmarkAnalysis.Validity.VALID : BenchmarkAnalysis.Validity.INVALID;
            var work = new ArrayList<BenchmarkResult.Work>();
            var ready = r.readiness();
            if (ready != null) for (var coverage : ready.coverage()) work.add(new BenchmarkResult.Work(
                    coverage.requested().name(), "provider-defined executed work; see reason", null, null,
                    coverage.executed().isPresent() ? (double) coverage.executed().getAsLong() : null,
                    coverage.eligible() == BenchmarkReadiness.Eligibility.ELIGIBLE && coverage.executed().isPresent()
                            ? BenchmarkAnalysis.Availability.AVAILABLE : BenchmarkAnalysis.Availability.UNAVAILABLE,
                    "Readiness observation/capture coverage; not cumulative command count", List.of(), List.of("eligibility=" + coverage.eligible() + "; " + coverage.reason())));
            if (ready == null) for (var requested : BenchmarkScenario.Work.values()) if (c.requestedWork().contains(requested))
                work.add(new BenchmarkResult.Work(requested.name(), "unavailable", null, null, null,
                        BenchmarkAnalysis.Availability.UNTESTED, "Case not observed", List.of(), List.of("No live work observations")));
            var observed = ready == null || ready.latest() == null ? List.<String>of() : List.of(ready.latest().toString());
            var missing = ready == null ? List.of("Readiness not observed")
                    : ready.state() == BenchmarkReadiness.State.UNAVAILABLE ? ready.reasons() : List.<String>of();
            var timeout = r.verdict() == BenchmarkScenarioRun.Verdict.TIMED_OUT ? List.of(r.reason()) : List.<String>of();
            Double excludedMs = caseStarted.containsKey(c.id()) && captureStarted.containsKey(c.id())
                    ? (captureStarted.get(c.id()) - caseStarted.get(c.id())) / 1e6 : null;
            var warmup = new BenchmarkResult.Warmup(excludedMs, complete, observed, missing, timeout,
                    ready == null ? List.of() : List.of("readiness=" + ready.state() + ";stableObservations=" + ready.stableObservations()
                            + ";stableNanos=" + ready.stableNanos() + ";" + String.join(";", ready.reasons())));
            var details = new TreeMap<>(host);
            details.putAll(com.pp.shaderlod.measurement.CpuMeasurementReport.observations(cpuWindows.get(c.id()),rows));
            if (context != null) for (String key : List.of("frame_limit", "vsync", "window_frame_limit", "mac_swap_interval",
                    "clock", "clock_resolution", "pipeline_identity", "reload_count", "gpu_vendor", "simulation", "shader_clock"))
                details.put(key, context.pins().getOrDefault(key, "unavailable"));
            details.put("excluded_initial_unavailable_interval", Boolean.toString(initialUnavailable));
            details.put("case_verdict", r.verdict().name());
            details.put("scenario_outcome", result == null ? "PREPARATION_INCOMPLETE" : result.outcome().name());
            details.put("scenario_recovery", result == null ? "No scenario started; outer world/observer recovery verified"
                    : "scenarioVerified=" + result.restorationVerified() + ";outer world/observer recovery verified;" + result.reason());
            details.put("case_expected_settings", c.settings().toString());
            details.put("case_expected_environment", c.environment().toString());
            details.put("memory_scope", "1Hz JVM heap occupancy and per-collector cumulative count/collection time; individual GC pauses/allocation/native/RSS/GPU memory unavailable; no forced GC");
            details.put("clock_resolution", "ns storage; effective clock resolution uncalibrated");
            details.put("end_to_end_ms", Double.toString(elapsedNanos / 1e6));
            details.put("work_coverage", "Only requested/observed work applies; authored materials do not certify PBR; no universal scene coverage");
            var temporal = Map.of("policy", c.temporalPolicy().name(), "time", Long.toString(c.environment().dayTime()),
                    "weather", c.environment().weather().name(), "animations", "Native shader/frame/jitter/AA clocks preserved; replay unavailable");
            var clocks = List.of(
                    clock("totalFrame", "Render-start to next render-start; includes presentation/limiter gaps; not whole-loop instrumentation", "System.nanoTime", rows, FrameMeasurement.Sample::frameWallNs, false),
                    clock("renderWall", "GameRenderer HEAD/RETURN wall duration; omitted simulation/presentation outside render", "System.nanoTime", rows, s -> s.renderWallNs(), false),
                    clock("renderThreadCpu", "Current render-thread CPU inside GameRenderer; overlaps render wall/process CPU", "ThreadMXBean", rows, FrameMeasurement.Sample::renderCpuNs, false),
                    clock("processCpu", "All JVM threads CPU consumed during GameRenderer scope; not whole-loop process CPU", "OperatingSystemMXBean", rows, FrameMeasurement.Sample::processCpuNs, false),
                    clock("gpuElapsed", "Unavailable in low-overhead PERFORMANCE mode; no GPU busy or DH_DRAW claim", "GPU queries disabled", rows, FrameMeasurement.Sample::gpuNs, true));
            windows.add(new BenchmarkResult.Window(BenchmarkResult.SCHEMA_VERSION, id + "/" + c.id(), id, "single-jvm", i,
                    context == null ? BenchmarkResult.Arm.FEATURE : context.arm(), context == null ? Map.of() : context.profile(),
                    pinLong(context, "source_generation"), pinLong(context, "pipeline_identity"),
                    context == null ? null : Math.toIntExact(pinLong(context, "reload_count") == null ? 0 : pinLong(context, "reload_count")), BenchmarkResult.Temperature.WARM, BenchmarkResult.Stage.DIAGNOSTIC,
                    c.workload() == BenchmarkScenario.Workload.STREAMING ? BenchmarkResult.Motion.STREAMING : BenchmarkResult.Motion.STILL,
                    new BenchmarkResult.Scenario(schedule.id(), c.sceneVersion(), BenchmarkScenario.ROUTE_VERSION, c.id(), "minecraft:overworld", temporal),
                    context == null ? null : context.configuration(), "PERFORMANCE", Map.of("calibration", "Recording-off/coarse/GPU/detailed observer-cost brackets untested"),
                    clocks, warmup, details, work, rows.isEmpty() ? null : rows.getFirst().frame(), rows.isEmpty() ? null : rows.getLast().frame(),
                    rows.stream().map(BenchmarkResult.Sample::from).toList(), stats, availability,
                    complete ? BenchmarkAnalysis.Activation.ACTIVE : BenchmarkAnalysis.Activation.UNKNOWN, validity, reasons));
        }
        var limitations = new ArrayList<>(List.of("Baseline-only selected configuration; no quality/compatibility certification or positive winning profile",
                "Short-window p95/p99 descriptive only; no independent repeats or matched effect inference",
                "Native animations/AA/options unchanged; replay and temporal-history convergence require platform evidence",
                "Missing queue/work/clock/memory coverage remains unavailable; heap is not process or GPU memory",
                "No ShaderLOD-absent, master transition, no-reload null control, known-effect control, observer-cost calibration or holdout comparison",
                "No independent whole-loop throughput cross-check; no workload pooling or headline aggregate"));
        if (!failure.isEmpty()) limitations.add(failure);
        return new BenchmarkResult.Manifest(BenchmarkResult.SCHEMA_VERSION, BenchmarkAnalysis.ANALYSIS_VERSION, id, Instant.now().toString(),
                context == null ? new BenchmarkResult.Environment("unavailable during preparation", Map.of(), "unavailable", "unavailable", "unavailable", "unavailable") : context.environment(),
                windows, List.of(), new BenchmarkResult.Evidence(BenchmarkResult.EvidenceVerdict.UNTESTED, BenchmarkResult.EvidenceVerdict.UNTESTED,
                        List.of(), List.of(), List.of(), List.of("Performance baseline does not inspect image quality or qualify compatibility")),
                new BenchmarkResult.Recovery(BenchmarkResult.RecoveryStatus.RESTORED, null, true,
                        List.of("Owned world closed or never acquired; clock stopped; previous observer mode restored; video/shader/native animation settings were not modified")),
                new BenchmarkResult.SearchCoverage(0, 0, List.of(), List.of(), false,
                        List.of("No tuning search or held-out confirmation; Benchmark cases are named observation coverage only")), List.of(), limitations);
    }
    private static Long pinLong(Context context, String key) {
        if (context == null) return null;
        try { return Long.valueOf(context.pins().get(key)); }
        catch (NumberFormatException | NullPointerException absent) { return null; }
    }
    private static BenchmarkResult.Clock clock(String id, String definition, String backend, List<FrameMeasurement.Sample> rows,
            Function<FrameMeasurement.Sample, Long> value, boolean unavailable) {
        boolean positive = id.equals("totalFrame") || id.equals("gpuElapsed");
        long valid = rows.stream().map(value).filter(v -> v != null && (positive ? v > 0 : v >= 0)).count();
        long invalid = rows.stream().map(value).filter(v -> v != null && (positive ? v <= 0 : v < 0)).count();
        boolean cpuDiagnostic=id.equals("renderThreadCpu")||id.equals("processCpu");
        boolean noAdvance=cpuDiagnostic&&rows.stream().map(value).noneMatch(v->v!=null&&v>0);
        String diagnostic=cpuDiagnostic?com.pp.shaderlod.measurement.CpuMeasurementReport.observations(null,rows)
                .get(id.equals("renderThreadCpu")?"render_cpu.status":"render_scope_process_cpu.status"):"Effective timer resolution and missing render routes explicitly uncalibrated";
        return new BenchmarkResult.Clock(id, definition, "GameRenderer.render; clocks overlap and must not be summed", backend,
                unavailable || valid == 0 || noAdvance ? BenchmarkAnalysis.Availability.UNAVAILABLE : BenchmarkAnalysis.Availability.AVAILABLE,
                null, unavailable ? "off" : "each rendered frame while capture active", "Pause, nonblocking poll and drain before release; bounded stage deadline",
                unavailable ? 0 : rows.size(), unavailable ? 0 : valid,
                unavailable ? 0 : rows.size() - valid - invalid, unavailable ? 0 : invalid, List.of(definition),
                List.of(unavailable ? "GPU elapsed, GPU busy, DH pass timers intentionally unavailable" : diagnostic));
    }
}
