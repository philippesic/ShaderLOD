package com.pp.shaderlod.benchmark;

import com.pp.shaderlod.benchmark.mixin.BenchmarkSectionQueuesAccessor;
import com.pp.shaderlod.benchmark.mixin.BenchmarkSodiumQueuesAccessor;
import com.pp.shaderlod.measurement.RuntimeMeasurement;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FogType;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import static com.pp.shaderlod.benchmark.BenchmarkReadiness.*;

/** Main-side opt-in observer. Submission provenance is never a seen-pixel, quality or GPU-busy verdict. */
public final class BenchmarkLiveObservations implements BenchmarkRunSession.ObservationSource {
    private final NativePreRoll temporal = new NativePreRoll(32, 1_000_000_000L, 500_000_000L);
    private record LightReading(long at, boolean ready) {}
    private CompletableFuture<LightReading> lighting;
    private final BenchmarkLightingHistory lightingHistory = new BenchmarkLightingHistory();
    private Object lightingOwner;
    private DhSnapshot dh = DhSnapshot.unknown("Not polled");
    private long lastDhAt = Long.MIN_VALUE;

    /** Parent: call at the outer main frame HEAD, outside nested shadow/DH passes. */
    public static void beginFrame() {
        BenchmarkDhMaintenance.owner(BenchmarkRunSession.active());
        BenchmarkLiveWork.beginFrame(BenchmarkRunSession.active() && BenchmarkWorld.ready());
    }
    /** Parent: call on successful outer main frame RETURN, after all world/shadow submissions. */
    public static void endFrame() {
        if (!BenchmarkLiveWork.enabled()) return;
        BenchmarkLiveWork.endFrame(System.nanoTime(), SystemTimeUniforms.COUNTER.getAsInt(),
                Iris.getPipelineManager().getPipelineNullable(), Iris.getCurrentPack().orElse(null), RuntimeMeasurement.reloadCount());
    }
    /** Parent: call on frame failure and benchmark cleanup/cancel, so no later unrelated draws are observed. */
    public static void reset() { BenchmarkLiveWork.disable(); BenchmarkDhMaintenance.owner(false); }

    @Override public BenchmarkScenario.DhState dhState() {
        // Explicit start/menu query is allowed; there is no ordinary-gameplay tick registration.
        return readDh(false).state();
    }
    @Override public Observation observe(BenchmarkScenario.Case scenario, long now) {
        if (!BenchmarkRunSession.active() || !BenchmarkWorld.ready())
            throw new IllegalStateException("Live observations require active benchmark world ownership");
        var client = Minecraft.getInstance();
        if (lastDhAt == Long.MIN_VALUE || now - lastDhAt >= 250_000_000L) {
            dh = readDh(true); lastDhAt = now;
        }
        var frame = BenchmarkLiveWork.latest();
        boolean fresh = frame != null && frame.valid() && now >= frame.completedAtNanos()
                && now - frame.completedAtNanos() <= 500_000_000L;
        boolean camera = BenchmarkWorld.poseReady() || BenchmarkWorld.flythroughStarted();
        var pipeline = Iris.getPipelineManager().getPipelineNullable();
        var source = Iris.getCurrentPack().orElse(null);
        boolean pipelineReady = !Iris.isFallback() && pipeline != null && source != null
                && Iris.getShaderPackOptionQueue().isEmpty();
        boolean expectedDhWork = dh.state() != BenchmarkScenario.DhState.ENABLED
                || fresh && frame.dhHook() && frame.dhRendererEntered() && frame.dhDraws() > 0 && frame.dhIndices() > 0;
        var history = temporal.observe(scenario.id(), frame, now, camera && pipelineReady && expectedDhWork,
                pipeline, source, RuntimeMeasurement.reloadCount());
        var target = client.getMainRenderTarget();
        var settings = dh.state() == BenchmarkScenario.DhState.UNKNOWN ? null : new BenchmarkScenario.Settings(
                client.options.renderDistance().get(), target.width, target.height, dh.state());
        Geometry geometry = fresh && frame.dhHook() && frame.dhRendererEntered()
                ? new Geometry(frame.dhDraws(), frame.dhIndices(), frame.dhFingerprint()) : null;
        return new Observation(now, camera, settings, sodiumQueues(), lighting(client, now),
                pipelineReady ? Signal.READY : Signal.BUSY, history, dh.state(), dh.loading(), dh.conversion(), geometry,
                classify(frame, fresh, viewFacts(client)));
    }

    private Signal lighting(Minecraft client, long now) {
        var server = client.getSingleplayerServer();
        if (server == null) return Signal.UNAVAILABLE;
        if (lightingOwner != server) { lightingOwner = server; lighting = null; lightingHistory.reset(); }
        if (lighting != null && lighting.isDone()) {
            if (lighting.isCompletedExceptionally()) lightingHistory.failed();
            else {
                var reading = lighting.getNow(null);
                if (reading != null) lightingHistory.completed(reading.at(), reading.ready());
            }
        }
        if (lighting == null || lighting.isDone())
            lighting = server.submit(() -> new LightReading(System.nanoTime(), !server.overworld().getChunkSource().getLightEngine().hasLightWork()));
        return lightingHistory.signal(now);
    }
    private static Signal sodiumQueues() {
        try {
            var renderer = SodiumWorldRenderer.instanceNullable();
            if (!(renderer instanceof BenchmarkSodiumQueuesAccessor accessor)) return Signal.UNAVAILABLE;
            var sections = accessor.shaderlod$benchmarkSections();
            if (sections == null || !(sections instanceof BenchmarkSectionQueuesAccessor queues)) return Signal.UNAVAILABLE;
            var builder = sections.getBuilder();
            boolean busy = !builder.isBuildQueueEmpty() || builder.getBusyThreadCount() != 0
                    || !queues.shaderlod$benchmarkBuildResults().isEmpty() || sections.needsUpdate()
                    || queues.shaderlod$benchmarkTaskLists().values().stream().anyMatch(v -> !v.isEmpty());
            return busy ? Signal.BUSY : Signal.READY;
        } catch (RuntimeException | LinkageError unavailable) { return Signal.UNAVAILABLE; }
    }

    /** Actual world facts. Frustum/section eligibility is still not a proof of shaded effect pixels. */
    public record ViewFacts(boolean authoredTerrain, boolean foliage, boolean water, boolean underwater,
                            boolean materials, boolean emissivesGlass, boolean cold, boolean distant) {}
    private static ViewFacts viewFacts(Minecraft client) {
        var level = client.level;
        if (level == null || client.player == null) return new ViewFacts(false, false, false, false, false, false, false, false);
        var ground = new BlockPos(0, 64, 24);
        boolean authored = level.getBlockState(ground).is(Blocks.GRASS_BLOCK);
        var leaf = new BlockPos(-35, 68, 24);
        var water = new BlockPos(0, 69, 117);
        var material = new BlockPos(-37, 69, 73);
        var emissive = new BlockPos(-47, 71, 73);
        var glass = new BlockPos(-49, 71, 73);
        var westWater = new BlockPos(-24, 69, 97);
        var snow = new BlockPos(43, 65, 126);
        var far = new BlockPos(0, 82, 320);
        return new ViewFacts(authored,
                authored && level.getBlockState(leaf).is(BlockTags.LEAVES) && view(client, leaf),
                authored && (level.getBlockState(water).is(Blocks.WATER) && view(client, water)
                        || level.getBlockState(westWater).is(Blocks.WATER) && view(client, westWater)),
                client.gameRenderer.getMainCamera().getFluidInCamera() == FogType.WATER,
                authored && level.getBlockState(material).is(Blocks.IRON_BLOCK) && view(client, material),
                authored && level.getBlockState(emissive).is(Blocks.GLOWSTONE) && view(client, emissive)
                        && level.getBlockState(glass).is(Blocks.WHITE_STAINED_GLASS) && view(client, glass),
                authored && level.isRaining() && level.getBlockState(snow).is(Blocks.SNOW_BLOCK) && view(client, snow),
                authored && level.getBlockState(far).is(Blocks.STONE_BRICKS) && view(client, far));
    }
    private static boolean view(Minecraft client, BlockPos pos) {
        var camera = client.gameRenderer.getMainCamera();
        var delta = net.minecraft.world.phys.Vec3.atCenterOf(pos).subtract(camera.getPosition());
        double range = client.options.renderDistance().get() * 16.0;
        if (delta.lengthSqr() > range * range || delta.lengthSqr() < 0.01) return false;
        var direction = camera.getLookVector();
        double dot = (delta.x * direction.x() + delta.y * direction.y() + delta.z * direction.z()) / delta.length();
        // Conservative central view cone (inside ordinary FOV), plus Sodium section visibility.
        var renderer = SodiumWorldRenderer.instanceNullable();
        double cone = Math.cos(Math.toRadians(Math.max(1, Math.min(40, client.options.fov().get() / 2.0 - 2))));
        return dot > cone && renderer != null && renderer.isBoxVisible(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    /** Counts are actual world subdraw entries in the last completed frame, with classification limits. */
    public static Map<BenchmarkScenario.Work, WorkEvidence> classify(BenchmarkLiveWork.Frame frame,
                                                                    boolean fresh, ViewFacts facts) {
        var out = new EnumMap<BenchmarkScenario.Work, WorkEvidence>(BenchmarkScenario.Work.class);
        boolean nativeWorld = fresh && frame != null && frame.sodiumHook() && frame.layerHook();
        long entries = nativeWorld ? frame.worldEntries() : 0;
        long translucent = nativeWorld ? frame.translucentEntries() : 0;
        put(out, BenchmarkScenario.Work.TERRAIN, nativeWorld && facts.authoredTerrain(), entries,
                "Authored terrain verified + actual native world subdraws; individual blocks/pixels not attributed");
        put(out, BenchmarkScenario.Work.FOLIAGE_CUTOUTS, nativeWorld && facts.foliage(), entries,
                "Authored leaves in central view/visible section + native world geometry; leaf pixels/cutout cost unproven");
        put(out, BenchmarkScenario.Work.WATER, nativeWorld && facts.water(), translucent,
                "Authored water in central view/visible section + actual translucent dispatch; water pixels/reflection work unproven");
        put(out, BenchmarkScenario.Work.UNDERWATER, nativeWorld && facts.underwater(), entries,
                "Actual camera fluid WATER + native world geometry; underwater effect pixels unproven");
        put(out, BenchmarkScenario.Work.MATERIALS, nativeWorld && facts.materials(), entries,
                "Authored iron panel in central view/visible section + native geometry; PBR unproven");
        put(out, BenchmarkScenario.Work.EMISSIVES_GLASS, nativeWorld && facts.emissivesGlass(), translucent,
                "Authored glowstone/glass in central view/visible sections + translucent dispatch; emissive shader work unproven");
        put(out, BenchmarkScenario.Work.COLD_PRECIPITATION, nativeWorld && facts.cold(), entries,
                "Actual precipitation + authored snow in view + world geometry; precipitation GPU work unproven");
        put(out, BenchmarkScenario.Work.DISTANT_TERRAIN, nativeWorld && facts.distant(), entries,
                "Client-loaded authored far face in central view/visible section + native geometry; specific far-face pixels unproven; DH-only far identity unavailable");
        boolean dhKnown = fresh && frame != null && frame.dhHook() && frame.dhRendererEntered();
        put(out, BenchmarkScenario.Work.DH_TERRAIN, dhKnown, dhKnown ? frame.dhDraws() : 0,
                "Actual positive-index DH world terrain ILWJGLService delegate returns; not shadow, GPU completion or distant pixel proof");
        out.put(BenchmarkScenario.Work.PBR, new WorkEvidence(Eligibility.UNAVAILABLE, OptionalLong.empty(),
                "No resource/native-effect activation proof; panel or generic geometry cannot establish PBR"));
        return out;
    }
    private static void put(Map<BenchmarkScenario.Work, WorkEvidence> out, BenchmarkScenario.Work work,
                            boolean eligible, long count, String provenance) {
        out.put(work, new WorkEvidence(eligible ? Eligibility.ELIGIBLE : Eligibility.UNAVAILABLE,
                eligible ? OptionalLong.of(count) : OptionalLong.empty(), provenance));
    }

    public record DhSnapshot(BenchmarkScenario.DhState state, Signal loading, Signal conversion, String detail) {
        static DhSnapshot unknown(String reason) {
            return new DhSnapshot(BenchmarkScenario.DhState.UNKNOWN, Signal.UNAVAILABLE, Signal.UNAVAILABLE, reason);
        }
    }
    public static Signal queueSignal(long queued, long running) {
        return queued < 0 || running < 0 ? Signal.UNAVAILABLE : queued == 0 && running == 0 ? Signal.READY : Signal.BUSY;
    }
    private static DhSnapshot readDh(boolean queues) {
        if (!ModList.get().isLoaded("distanthorizons"))
            return new DhSnapshot(BenchmarkScenario.DhState.ABSENT, Signal.UNAVAILABLE, Signal.UNAVAILABLE, "DH absent");
        String version = ModList.get().getModContainerById("distanthorizons").orElseThrow().getModInfo().getVersion().toString();
        if (!"3.3.3".equals(version)) return DhSnapshot.unknown("Unsupported DH version " + version);
        try {
            Class<?> delayed = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
            Object configs = delayed.getField("configs").get(null);
            if (configs == null) return DhSnapshot.unknown("DH config not initialized");
            Object graphics = Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig").getMethod("graphics").invoke(configs);
            Class<?> api = Class.forName("com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig");
            Method value = Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue").getMethod("getValue");
            Object mode = value.invoke(api.getMethod("renderingMode").invoke(graphics));
            Object radius = value.invoke(api.getMethod("chunkRenderDistance").invoke(graphics));
            Object enabled = value.invoke(api.getMethod("renderingEnabled").invoke(graphics));
            if (!(mode instanceof Enum<?> rendererMode) || !(enabled instanceof Boolean)) return DhSnapshot.unknown("DH mode/value unknown");
            if (!Boolean.TRUE.equals(enabled) || rendererMode.name().equals("DISABLED"))
                return new DhSnapshot(BenchmarkScenario.DhState.INSTALLED_DISABLED, Signal.UNAVAILABLE, Signal.UNAVAILABLE, "DH installed, rendering disabled; radius=" + radius);
            if (!rendererMode.name().equals("DEFAULT")) return DhSnapshot.unknown("DH non-default renderer mode " + rendererMode.name());
            if (!queues) return new DhSnapshot(BenchmarkScenario.DhState.ENABLED, Signal.UNAVAILABLE, Signal.UNAVAILABLE, "DH DEFAULT enabled; radius=" + radius + "; queues not requested");
            try {
                var loading = pool("getRenderLoadingExecutor");
                var conversion = pool("getChunkToLodBuilderExecutor");
                var propagation = pool("getUpdatePropagatorExecutor");
                int cooldownsBefore = BenchmarkDhMaintenance.cooldowns();
                var files = pool("getFileHandlerExecutor");
                int cooldownsAfter = BenchmarkDhMaintenance.cooldowns();
                var generation = pool("getWorldGenExecutor");
                Signal load = combine(loading.signal(), BenchmarkDhMaintenance.fileSignal(files.queued(), files.running(), cooldownsBefore, cooldownsAfter));
                Signal convert = combine(combine(conversion.signal(), propagation.signal()), generation.signal());
                return new DhSnapshot(BenchmarkScenario.DhState.ENABLED, load, convert,
                        "DH3.3.3 radius=" + radius + "; pools [queued,running]: render=" + loading + ",conversion=" + conversion + ",propagation=" + propagation + ",file=" + files
                                + ",generation=" + generation + ";knownBeaconCooldowns=" + cooldownsBefore + "/" + cooldownsAfter
                                + "; only exactly observed beacon sleep is exempt; other file activity blocks capture"
                                + "; not a GPU queue; other internal work unobserved");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError missingQueues) {
                return new DhSnapshot(BenchmarkScenario.DhState.ENABLED, Signal.UNAVAILABLE, Signal.UNAVAILABLE,
                        "DH DEFAULT enabled; queue APIs unavailable: " + missingQueues.getClass().getSimpleName());
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return DhSnapshot.unknown("DH API unavailable: " + unavailable.getClass().getSimpleName());
        }
    }
    private record Pool(long queued, long running) { Signal signal() { return queueSignal(queued, running); } }
    private static Pool pool(String getter) throws ReflectiveOperationException {
        Class<?> pools = Class.forName("com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil");
        Object executor = pools.getMethod(getter).invoke(null);
        if (executor == null) return new Pool(-1, -1);
        return new Pool(((Number)executor.getClass().getMethod("getQueueSize").invoke(executor)).longValue(),
                ((Number)executor.getClass().getMethod("getRunningTaskCount").invoke(executor)).longValue());
    }
    private static Signal combine(Signal a, Signal b) {
        return a == Signal.UNAVAILABLE || b == Signal.UNAVAILABLE ? Signal.UNAVAILABLE
                : a == Signal.BUSY || b == Signal.BUSY ? Signal.BUSY : Signal.READY;
    }
    @Override public Map<String, String> details() {
        if (!BenchmarkRunSession.owned()) return Map.of();
        var f = BenchmarkLiveWork.latest();
        return Map.of("live_observer", "Native last-completed-frame CPU submissions; seen-pixel/quality verdict UNTESTED",
                "native_temporal_policy", "32 progressed Iris frames and >=1s unchanged pipeline/pack/reload after pose ack; lighting uses actual server readings <=500ms old; DH needs observed world terrain; pre-roll is NOT convergence",
                "dh_observation", dh.detail(), "coverage_limits", "PBR unavailable without native-effect/resource proof; DH-only authored far-face visibility unavailable",
                "native_frame_work", f == null ? "No completed owned frame" : "sequence=" + f.sequence()
                        + ",worldSubdraws=" + f.worldEntries() + ",worldIndices=" + f.worldIndices()
                        + ",shadowSubdraws=" + f.shadowEntries() + ",shadowIndices=" + f.shadowIndices()
                        + ",translucentSubdraws=" + f.translucentEntries()
                        + ",dhWorldDraws=" + f.dhDraws() + ",dhWorldIndices=" + f.dhIndices()
                        + ",dhShadowDraws=" + f.dhShadowDraws() + ",dhShadowIndices=" + f.dhShadowIndices()
                        + ",sodiumHook=" + f.sodiumHook() + ",layerHook=" + f.layerHook() + ",dhHook=" + f.dhHook());
    }

    /** Pure explicit pre-roll policy. Frame counter wraps at 720720 in pinned Iris; reset/change restarts. */
    public static final class NativePreRoll {
        private final int frames;
        private final long duration, maxAge;
        private Object pipeline, source;
        private String caseId;
        private long since, lastSequence = -1, reloads, ownerEpoch = -1;
        private int previous, progressed;
        public NativePreRoll(int frames, long durationNanos, long maximumAgeNanos) {
            if (frames < 2 || durationNanos <= 0 || maximumAgeNanos <= 0) throw new IllegalArgumentException("Invalid pre-roll policy");
            this.frames = frames; duration = durationNanos; maxAge = maximumAgeNanos;
        }
        public Signal observe(String id, BenchmarkLiveWork.Frame frame, long now, boolean acknowledged,
                              Object actualPipeline, Object actualSource, long actualReloads) {
            if (frame == null || !frame.valid() || frame.nativeFrame() < 0 || frame.nativeFrame() >= 720720
                    || now < frame.completedAtNanos() || now - frame.completedAtNanos() > maxAge) {
                caseId = null; progressed = 0;
                return Signal.BUSY;
            }
            boolean identity = actualPipeline != null && actualSource != null && frame.pipeline() == actualPipeline
                    && frame.source() == actualSource && frame.reloads() == actualReloads;
            if (!acknowledged || !identity) { caseId = null; progressed = 0; return Signal.BUSY; }
            if (!id.equals(caseId) || pipeline != actualPipeline || source != actualSource || reloads != actualReloads
                    || ownerEpoch != frame.ownerEpoch()) {
                caseId = id; pipeline = actualPipeline; source = actualSource; reloads = actualReloads;
                ownerEpoch = frame.ownerEpoch();
                since = now; previous = frame.nativeFrame(); lastSequence = frame.sequence(); progressed = 0;
                return Signal.BUSY;
            }
            if (frame.sequence() != lastSequence) {
                int delta = (frame.nativeFrame() - previous + 720720) % 720720;
                if (delta == 0 || delta > 10000) { since = now; progressed = 0; }
                else progressed = Math.min(frames, progressed + delta);
                previous = frame.nativeFrame(); lastSequence = frame.sequence();
            }
            return progressed >= frames && now - since >= duration ? Signal.READY : Signal.BUSY;
        }
    }
}
