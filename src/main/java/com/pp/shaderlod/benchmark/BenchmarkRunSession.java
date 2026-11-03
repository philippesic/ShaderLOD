package com.pp.shaderlod.benchmark;

import com.pp.shaderlod.ShaderLodConfig;
import com.pp.shaderlod.ShaderLodEffectConfig;
import com.pp.shaderlod.benchmark.core.BenchmarkResult;
import com.pp.shaderlod.measurement.MeasurementPins;
import com.pp.shaderlod.measurement.RuntimeMeasurement;
import com.pp.shaderlod.optimization.OptimizationConfig;
import com.pp.shaderlod.tuning.AutoTuner;
import com.pp.shaderlod.tuning.SavedTunings;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Explicit, baseline-only user session. No tuning search, feature selection, replay or promotion. */
public final class BenchmarkRunSession {
    /** Platform adapter must report actual observations, never the requested values as acknowledgements. */
    public interface ObservationSource {
        BenchmarkScenario.DhState dhState();
        BenchmarkReadiness.Observation observe(BenchmarkScenario.Case scenario, long nowNanos);
        default Map<String, String> details() { return Map.of(); }
    }
    private static final BenchmarkRunOwnership OWNERSHIP = new BenchmarkRunOwnership();
    private static ObservationSource observations = new BenchmarkLiveObservations();
    private static BooleanSupplier profilerOwner = BenchmarkRunSession::developmentProfilers;
    private static Job job;
    private static String last = "Idle; FAST measures the selected configuration without tuning";
    private static Path lastResult;
    private static BenchmarkResult.Manifest lastManifest;
    private static BenchmarkScenarioRun.Result lastProgress;
    private BenchmarkRunSession() {}

    public static boolean active() { return OWNERSHIP.active(); }
    public static boolean restorationPending() { return OWNERSHIP.restorationPending(); }
    public static boolean owned() { return active() || restorationPending(); }
    public static String status() { return job == null ? last : job.status(); }
    public static Path lastResult() { return lastResult; }
    public static BenchmarkResult.Manifest lastManifest() { return lastManifest; }
    public static BenchmarkScenarioRun.Result progress() { return job == null ? lastProgress : job.run == null ? null : job.run.result(); }
    public static Path resultsDirectory() { return Minecraft.getInstance().gameDirectory.toPath().resolve("shaderlod-benchmarks").toAbsolutePath().normalize(); }
    public static String results() {
        if (lastResult != null) return status() + "; latest completed report: " + lastResult;
        return (lastManifest == null ? "No benchmark report yet; " : "Latest report retained in memory; ") + status();
    }
    public static void configureObservations(ObservationSource source) {
        requireIdle(); observations = java.util.Objects.requireNonNull(source);
    }
    /** Parent can join live dev/driver profiler ownership without a dependency on dev classes. */
    public static void setProfilerOwnerCheck(BooleanSupplier check) {
        requireIdle(); profilerOwner = java.util.Objects.requireNonNull(check);
    }
    public static void requireIdle() {
        if (owned()) throw new IllegalStateException("Benchmark owns this session; cancel and finish restoration first");
    }
    public static BenchmarkScenario.DhState suggestedDhState() {
        var state = observations.dhState();
        // An expectation only; UNKNOWN still fails the live acknowledgement/readiness check.
        return state == BenchmarkScenario.DhState.UNKNOWN ? BenchmarkScenario.DhState.ENABLED : state;
    }
    public static void start() { start(suggestedDhState()); }
    public static void start(BenchmarkScenario.DhState expectedDh) {
        var client = Minecraft.getInstance();
        var target = client.getMainRenderTarget();
        start(new BenchmarkScenario.Settings(client.options.renderDistance().get(), target.width, target.height, expectedDh));
    }
    public static void startDiagnostic() { startDiagnostic(suggestedDhState()); }
    public static void startDiagnostic(BenchmarkScenario.DhState expectedDh) {
        var client = Minecraft.getInstance(); var target = client.getMainRenderTarget();
        startDiagnostic(new BenchmarkScenario.Settings(client.options.renderDistance().get(), target.width, target.height, expectedDh));
    }
    public static void startDiagnostic(BenchmarkScenario.Settings settings) { start(settings, true); }
    public static void start(BenchmarkScenario.Settings settings) { start(settings, false); }
    private static void start(BenchmarkScenario.Settings settings, boolean diagnostic) {
        var client = Minecraft.getInstance();
        requireIdle();
        AutoTuner.requireIdleForBenchmark();
        if (SavedTunings.pending()) throw new IllegalStateException("Restore the pending saved tuning before benchmarking");
        if (RuntimeMeasurement.needed() || RuntimeMeasurement.requested() || RuntimeMeasurement.pendingQueries() != 0
                || RuntimeMeasurement.pair() != null && RuntimeMeasurement.pair().restorationPending())
            throw new IllegalStateException("Stop manual/paired timing and finish its restoration before benchmarking");
        if (profilerOwner.getAsBoolean()) throw new IllegalStateException("Stop detailed profiling before a performance benchmark");
        if (client.level != null && !BenchmarkWorld.ready())
            throw new IllegalStateException("Save & Quit, then start benchmark from the main menu; personal worlds are never switched");
        if (BenchmarkWorld.active() && !BenchmarkWorld.ready()) throw new IllegalStateException(BenchmarkWorld.status());
        var next = new Job(diagnostic ? BenchmarkScenario.diagnostic(settings) : BenchmarkScenario.fast(settings), observations);
        OWNERSHIP.acquire(next);
        job = next;
        last = "Preparing " + next.schedule.id() + " benchmark";
        try { next.open(); }
        catch (RuntimeException failure) { next.fail("Preparation failed: " + message(failure)); }
    }
    public static void tick(ClientTickEvent.Post event) {
        if (job == null) return;
        try { job.tick(System.nanoTime()); }
        catch (RuntimeException failure) { job.fail(message(failure)); }
    }
    public static void cancel() {
        if (job == null) return;
        job.cancel();
    }
    public static void shutdown() { cancel(); }
    private static String message(Throwable failure) { return failure.getMessage() == null ? failure.toString() : failure.getMessage(); }
    private static boolean developmentProfilers() {
        String fallback = System.getProperty("shaderlod.dev.compare", "").isEmpty() ? "false" : "true";
        return Boolean.parseBoolean(System.getProperty("shaderlod.dev.gpuTiming", fallback))
                || Boolean.parseBoolean(System.getProperty("shaderlod.dev.cpuTiming", fallback));
    }

    private static final class Job {
        final long openedAt = System.nanoTime();
        final String id;
        final BenchmarkScenario.Schedule schedule;
        final ObservationSource source;
        BenchmarkScenarioWorld world;
        final BenchmarkRunCapture capture = new BenchmarkRunCapture(new BenchmarkRunCapture.Clock() {
            public void start() { RuntimeMeasurement.request(true); }
            public void pause() { RuntimeMeasurement.pauseWindow(); }
            public com.pp.shaderlod.measurement.CpuWindowMeasurement.Result cpuWindow() { return RuntimeMeasurement.pauseCpuWindow(); }
            public void poll() { RuntimeMeasurement.pollSamples(); }
            public int pending() { return RuntimeMeasurement.pendingQueries(); }
            public List<com.pp.shaderlod.measurement.FrameMeasurement.Sample> drain() { return RuntimeMeasurement.drain(); }
            public void stop() { RuntimeMeasurement.request(false); RuntimeMeasurement.close(); }
        }, 65_536);
        final Map<String, String> host = new TreeMap<>();
        final Map<String, Long> caseStarted = new TreeMap<>(), captureStarted = new TreeMap<>();
        RuntimeMeasurement.ObserverMode priorObserver;
        BenchmarkScenarioRun run;
        BenchmarkRunReport.Context context;
        boolean opening = true, recovering, observerAcquired, cancelled, recoveryExpired, worldReleased, openedWorld;
        long recoveringAt, lastHostAt;
        String failure = "", captureInterference = "";
        Object expectedPack, expectedPipeline;
        String selected, coreSettings, effectSettings, videoSettings;
        com.pp.shaderlod.optimization.OptimizationSnapshot flags;
        Map<String, String> effective;
        List<String> resources;
        long reloads;

        Job(BenchmarkScenario.Schedule schedule, ObservationSource source) { this.schedule = schedule; this.source = source;
            id = (schedule.id().equals("FAST") ? "fast-" : "diagnostic-") + Instant.now().toString().replace(':', '-') + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        void open() {
            priorObserver = RuntimeMeasurement.observerMode();
            RuntimeMeasurement.observerMode(RuntimeMeasurement.ObserverMode.PERFORMANCE);
            observerAcquired = true;
            if (BenchmarkWorld.ready()) prepare();
            else {
                openedWorld = true;
                BenchmarkWorld.open(() -> {
                    // A cancelled opening callback must never revive recording.
                    if (job == this && !recovering && !cancelled) {
                        try { prepare(); } catch (RuntimeException error) { fail(message(error)); }
                    }
                });
            }
        }
        void prepare() {
            var client = Minecraft.getInstance();
            client.setScreen(null);
            if (!BenchmarkWorld.ready() || client.level == null) throw new IllegalStateException("Owned benchmark world is not ready");
            world = new BenchmarkScenarioWorld();
            selected = Iris.getCurrentPackName();
            expectedPack = Iris.getCurrentPack().orElseThrow(() -> new IllegalStateException("Select an active shader first"));
            expectedPipeline = Iris.getPipelineManager().getPipelineNullable();
            if (Iris.isFallback() || expectedPipeline == null) throw new IllegalStateException("A usable Iris shader pipeline is required");
            flags = OptimizationConfig.snapshot();
            coreSettings = ShaderLodConfig.snapshot().toString() + ShaderLodConfig.PRESET.get();
            effectSettings = ShaderLodEffectConfig.snapshot().toString();
            videoSettings = videoSettings();
            effective = effectiveOptions();
            resources = List.copyOf(client.getResourcePackRepository().getSelectedIds());
            reloads = RuntimeMeasurement.reloadCount();
            // Full identity/source work is outside all capture windows.
            var pins = new TreeMap<>(MeasurementPins.capture(coreSettings));
            pins.put("reload_count", Long.toString(reloads));
            pins.put("source_generation", Long.toString(OptimizationConfig.generation()));
            pins.put("pipeline_identity", Integer.toString(System.identityHashCode(expectedPipeline)));
            var target = client.getMainRenderTarget();
            context = BenchmarkRunReport.context(pins, effective, resources, flags,
                    ShaderLodConfig.ENABLED.get(), client.options.simulationDistance().get(), client.options.renderDistance().get(),
                    target.width, target.height, source.dhState().name());
            var capturePort = new BenchmarkScenarioRun.Capture() {
                public void startCapture(BenchmarkScenario.Case c) {
                    if (!effective.equals(effectiveOptions())) throw new IllegalStateException("Effective native shader options changed before capture");
                    captureStarted.put(c.id(), System.nanoTime());
                    capture.startCapture(c);
                }
                public void stopCapture(BenchmarkScenario.Case c) {
                    capture.stopCapture(c);
                    if (!effective.equals(effectiveOptions())) throw new IllegalStateException("Effective native shader options changed during capture");
                }
                public boolean drained() { return capture.drained(); }
            };
            var worldPort = new BenchmarkScenarioRun.World() {
                public void apply(BenchmarkScenario.Case c) { caseStarted.put(c.id(), System.nanoTime()); world.apply(c); }
                public boolean acknowledged() { return world.acknowledged(); }
                public void beginRoute(int ticks) { world.beginRoute(ticks); }
                public boolean routeStarted() { return world.routeStarted(); }
                public boolean routeComplete() { return world.routeComplete(); }
                public void restore() { restoreAll(); }
                public boolean restored() { return verifyWorldRestored(); }
            };
            run = new BenchmarkScenarioRun(schedule, worldPort, capturePort, System.nanoTime());
            opening = false;
            host.put("boot_prepare_ms", Double.toString((System.nanoTime() - openedAt) / 1e6));
            host.put("simulation", "Native simulation; no tick freeze or time replay; only owned world environment/poses change");
            host.put("observer", "PERFORMANCE; GPU queries disabled; detailed profiling forbidden");
        }
        void tick(long now) {
            if (recoveryExpired) return;
            if (recovering) { recoveryTick(now); return; }
            if (opening) {
                if (now - openedAt >= 300_000_000_000L || !BenchmarkWorld.active()) fail("Owned world opening failed or exceeded five-minute preparation budget");
                return;
            }
            if (run.result().stage() != BenchmarkScenarioRun.Stage.RESTORE) {
                validate();
                capture.collect();
            }
            if (now - lastHostAt >= 1_000_000_000L) observeHost(now);
            BenchmarkReadiness.Observation observation = null;
            var state = run.result();
            if (state.stage() == BenchmarkScenarioRun.Stage.WARM || state.stage() == BenchmarkScenarioRun.Stage.CAPTURE) {
                var c = run.currentCase().orElseThrow();
                observation = source.observe(c, now);
            }
            var result = run.tick(now, observation);
            if (result.stage() == BenchmarkScenarioRun.Stage.FINISHED) {
                recovering = true;
                recoveringAt = now;
                OWNERSHIP.recovering(this);
                recoveryTick(now);
            }
        }
        void validate() {
            var client = Minecraft.getInstance();
            boolean chatOpen = client.screen instanceof net.minecraft.client.gui.screens.ChatScreen;
            if (!BenchmarkWorld.ready() || client.level == null || client.screen != null && !chatOpen)
                throw new IllegalStateException("Owned world/foreground capture interrupted");
            if (chatOpen && capture.busy())
                captureInterference = "Chat UI overlapped a timing window; captures remain descriptive and unqualified";
            if (profilerOwner.getAsBoolean()) throw new IllegalStateException("Detailed profiler took ownership");
            if (RuntimeMeasurement.requested() != capture.busy() || RuntimeMeasurement.observerMode() != RuntimeMeasurement.ObserverMode.PERFORMANCE)
                throw new IllegalStateException("Runtime clock/observer ownership changed externally");
            if (Iris.isFallback() || Iris.getCurrentPack().orElse(null) != expectedPack
                    || Iris.getPipelineManager().getPipelineNullable() != expectedPipeline || RuntimeMeasurement.reloadCount() != reloads
                    || !java.util.Objects.equals(selected, Iris.getCurrentPackName()) || !Iris.getShaderPackOptionQueue().isEmpty())
                throw new IllegalStateException("Shader source/pipeline changed; capture invalidated");
            if (!flags.equals(OptimizationConfig.snapshot()) || !coreSettings.equals(ShaderLodConfig.snapshot().toString() + ShaderLodConfig.PRESET.get())
                    || !effectSettings.equals(ShaderLodEffectConfig.snapshot().toString())
                    || !videoSettings.equals(videoSettings())
                    || !resources.equals(List.copyOf(client.getResourcePackRepository().getSelectedIds())))
                throw new IllegalStateException("Configuration changed externally; user edits retained, capture invalidated");
        }
        void observeHost(long now) {
            lastHostAt = now;
            var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            var gcObservations = new StringBuilder();
            for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                long count = gc.getCollectionCount(), millis = gc.getCollectionTime();
                gcObservations.append(gc.getName()).append("[count=").append(count < 0 ? "unavailable" : Long.toString(count))
                        .append(",collectionTimeMs=").append(millis < 0 ? "unavailable" : Long.toString(millis)).append("]; ");
            }
            // <= 180 entries, one per second; heap occupancy is explicitly not resident/GPU memory.
            host.put("host_" + (now - openedAt) / 1_000_000_000L,
                    "heapUsedBytes=" + heap.getUsed() + ",heapCommittedBytes=" + heap.getCommitted() + ",gc=" + gcObservations);
            host.putAll(source.details());
            if (world != null) host.put("route_elapsed_ticks_" + (now - openedAt) / 1_000_000_000L, Integer.toString(world.routeElapsedTicks()));
        }
        void fail(String why) {
            failure = failure.isEmpty() ? why : failure + "; " + why;
            cancelled = true;
            if (run != null && run.result().stage() != BenchmarkScenarioRun.Stage.FINISHED) run.cancel(System.nanoTime());
            recovering = true;
            recoveringAt = System.nanoTime();
            OWNERSHIP.recovering(this);
            try { restoreAll(); } catch (RuntimeException error) { failure += "; recovery request: " + message(error); }
        }
        void cancel() {
            cancelled = true;
            recoveryExpired = false;
            fail("Cancelled by user; remaining coverage untested");
            // A failed recovery is explicitly retryable via Cancel; ownership remains until verified.
            if (OWNERSHIP.restorationPending()) { recoveringAt = System.nanoTime(); tick(recoveringAt); }
        }
        void recoveryTick(long now) {
            if ((world != null || openedWorld) && BenchmarkWorld.active()) {
                if (now - recoveringAt >= schedule.recoveryBudget().toNanos()) {
                    last = "Recovery deadline exhausted; use benchmark cancel to retry; owner retained";
                    recoveryExpired = true;
                }
                return;
            }
            try {
                if (!verifyWorldRestored()) throw new IllegalStateException("Owned world restoration remains pending");
                capture.abort();
                if (observerAcquired) {
                    RuntimeMeasurement.observerMode(priorObserver);
                    observerAcquired = false;
                }
                if (RuntimeMeasurement.needed() || RuntimeMeasurement.requested() || RuntimeMeasurement.pendingQueries() != 0)
                    throw new IllegalStateException("Runtime measurement cleanup remains pending");
                var result = run == null ? null : run.result();
                if (run != null && result.stage() != BenchmarkScenarioRun.Stage.FINISHED) result = run.tick(now, null);
                lastProgress = result;
                capture.discardedSamples().forEach((caseId, count) -> host.put("discarded_samples/" + caseId, count.toString()));
                String validityFailure = failure.isEmpty() ? captureInterference : failure + (captureInterference.isEmpty() ? "" : "; " + captureInterference);
                var report = BenchmarkRunReport.manifest(context, id, schedule, result, capture.snapshot(), host,
                        caseStarted, captureStarted, validityFailure, now - openedAt, capture.cpuWindows());
                lastManifest = report;
                lastResult = null;
                // All clocks stopped and owned world closed before serialization or filesystem IO.
                Path output = resultsDirectory().resolve(id + ".json");
                String exportFailure = "";
                try { com.pp.shaderlod.benchmark.core.BenchmarkResultWriter.write(output, report); lastResult = output; }
                catch (java.io.IOException | RuntimeException error) { exportFailure = "; export failed: " + message(error) + "; in-memory results retained"; }
                OWNERSHIP.release(this, true);
                BenchmarkLiveObservations.reset();
                long completed = result == null ? 0 : result.cases().stream().filter(c -> c.verdict() == BenchmarkScenarioRun.Verdict.COMPLETE).count();
                last = (cancelled ? "Cancelled/incomplete" : result == null ? "Incomplete" : result.outcome().name())
                        + "; " + completed + "/" + schedule.cases().size() + " cases captured; selected-config baseline only; no quality/compatibility verdict"
                        + (validityFailure.isEmpty() ? "" : "; " + validityFailure) + exportFailure;
                job = null;
            } catch (RuntimeException error) {
                last = "Benchmark restoration/export preparation failed; owner retained: " + message(error);
                recoveryExpired = true;
            }
        }
        String status() {
            if (recovering) return last.startsWith("Recovery") || last.startsWith("Benchmark restoration") ? last : "Restoring benchmark world and observer; " + failure;
            if (opening) return "Preparing owned benchmark world; " + BenchmarkWorld.status();
            var r = run.result();
            long done = r.cases().stream().filter(c -> c.verdict() != BenchmarkScenarioRun.Verdict.NOT_RUN && c.verdict() != BenchmarkScenarioRun.Verdict.RUNNING).count();
            String name = run.currentCase().map(BenchmarkScenario.Case::id).orElse("complete");
            return schedule.id() + " " + done + "/" + schedule.cases().size() + " " + name + ": " + r.stage() + "; /shaderlod benchmark cancel";
        }
        void requestWorldRestore() {
            if (world != null && !worldReleased) world.restore();
            else if (!worldReleased && openedWorld) BenchmarkWorld.exit();
        }
        void restoreAll() {
            BenchmarkLiveObservations.reset();
            try { capture.abort(); }
            finally { requestWorldRestore(); }
        }
        boolean verifyWorldRestored() {
            if (world == null && !openedWorld) return true;
            if (BenchmarkWorld.active()) return false;
            if (world != null && !worldReleased) worldReleased = world.restored();
            return world == null || worldReleased;
        }
    }
    private static Map<String, String> effectiveOptions() {
        return Iris.getCurrentPack().map(p -> com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(p.getShaderPackOptions())).orElse(Map.of());
    }
    private static String videoSettings() {
        var client = Minecraft.getInstance(); var target = client.getMainRenderTarget();
        return client.options.renderDistance().get() + "/" + client.options.simulationDistance().get() + "/"
                + target.width + "x" + target.height + "/" + client.options.framerateLimit().get() + "/"
                + client.options.enableVsync().get() + "/" + client.getWindow().getFramerateLimit();
    }

    /** Safe fallback: absent observation APIs are exported as unavailable, never fabricated READY. */
    private static final class ConservativeObservations implements ObservationSource {
        private CompletableFuture<Boolean> lighting;
        public BenchmarkScenario.DhState dhState() {
            return ModList.get().isLoaded("distanthorizons") ? BenchmarkScenario.DhState.UNKNOWN : BenchmarkScenario.DhState.ABSENT;
        }
        public BenchmarkReadiness.Observation observe(BenchmarkScenario.Case c, long now) {
            var client = Minecraft.getInstance();
            var server = client.getSingleplayerServer();
            var signal = BenchmarkReadiness.Signal.UNAVAILABLE;
            if (lighting != null && lighting.isDone() && !lighting.isCompletedExceptionally())
                signal = lighting.getNow(false) ? BenchmarkReadiness.Signal.READY : BenchmarkReadiness.Signal.BUSY;
            if (server != null && (lighting == null || lighting.isDone()))
                lighting = server.submit(() -> !server.overworld().getChunkSource().getLightEngine().hasLightWork());
            var dh = dhState();
            var target = client.getMainRenderTarget();
            var actual = dh == BenchmarkScenario.DhState.UNKNOWN ? null : new BenchmarkScenario.Settings(
                    client.options.renderDistance().get(), target.width, target.height, dh);
            return new BenchmarkReadiness.Observation(now, BenchmarkWorld.poseReady() || BenchmarkWorld.flythroughStarted(), actual,
                    client.levelRenderer.hasRenderedAllSections() ? BenchmarkReadiness.Signal.READY : BenchmarkReadiness.Signal.BUSY,
                    signal, Iris.isFallback() || Iris.getPipelineManager().getPipelineNullable() == null ? BenchmarkReadiness.Signal.BUSY : BenchmarkReadiness.Signal.READY,
                    BenchmarkReadiness.Signal.UNAVAILABLE, dh, BenchmarkReadiness.Signal.UNAVAILABLE,
                    BenchmarkReadiness.Signal.UNAVAILABLE, null, Map.of());
        }
        public Map<String, String> details() { return Map.of("readiness_provider", "Conservative vanilla observation; native-history convergence, Sodium queue and work counters unavailable"); }
    }
}
