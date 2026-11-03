package com.pp.shaderlod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Fixed, explicitly opted-in geometry cap. Optional DH inspection never runs in the Options getter. */
public final class GeometryLodController {
    public record Status(boolean active, int requestedChunks, int effectiveChunks, int cap,
                         String reason, String detail, int stableTicks, int warmupTicks) {}
    private record State(GeometryCoverageGuard.Scene scene, GeometryLodPolicy.Decision decision, long flagsGeneration, long lifecycleGeneration) {}

    private static volatile State state = new State(null, new GeometryLodPolicy.Decision(false, 16, "not initialized"), -1, -1);
    private static volatile Status status = new Status(false, 0, 0, 16, "not initialized", "", 0, 600);
    private static GeometryCoverageGuard.Scene observedScene;
    private static int stableTicks;
    private static final GeometryCoverageGuard coverage = new GeometryCoverageGuard();
    private static int lastOriginalEffectiveChunks;
    private static DhAdapter adapter;
    private static boolean adapterChecked;
    private static String adapterFailure = "";

    private GeometryLodController() {}

    private static long observedFlagsGeneration=-1, observedLifecycleGeneration=-1;
    /** Generation changes require fresh coverage, even when the scene itself did not change. */
    private static void checkGenerations() {
        long flags=com.pp.shaderlod.optimization.OptimizationConfig.generation();
        long lifecycle=com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.epoch();
        if (observedFlagsGeneration<0 || flags!=observedFlagsGeneration || lifecycle!=observedLifecycleGeneration) {
            coverage.invalidate(); stableTicks=0; observedScene=null;
            State current=state;
            state=new State(null,new GeometryLodPolicy.Decision(false,current.decision().cap(),
                    "terrain inputs changed; reconfirm coverage"),flags,lifecycle);
            Status previous=status;
            int original=lastOriginalEffectiveChunks>0?lastOriginalEffectiveChunks:previous.requestedChunks();
            status=new Status(false,previous.requestedChunks(),original,current.decision().cap(),
                    "terrain inputs changed; reconfirm coverage",previous.detail(),0,previous.warmupTicks());
        }
        observedFlagsGeneration=flags; observedLifecycleGeneration=lifecycle;
    }


    /** Register once on NeoForge's client game event bus from the main mod constructor. */
    public static void onClientTick(ClientTickEvent.Post event) {
        checkGenerations();
        Minecraft client = Minecraft.getInstance();
        Object world = client.level;
        int requested = client.options.renderDistance().get();
        boolean optedIn = ShaderLodConfig.SPEC.isLoaded() && ShaderLodConfig.ENABLED.get() && ShaderLodConfig.PRESET.get()==LodPreset.PERFORMANCE && com.pp.shaderlod.optimization.OptimizationConfig.requested(com.pp.shaderlod.optimization.OptimizationId.TERRAIN_RADIUS) && !com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended();
        Object pipeline = null;
        boolean irisReady = false;
        boolean dhReady = false;
        int dhChunks = 0;
        int cap = ShaderLodConfig.TERRAIN_VANILLA_CHUNK_CAP.get();
        Entity camera = client.getCameraEntity();
        String detail = "";
        // Avoid even inspecting optional dependencies when no policy can apply.
        if (optedIn && world != null) {
            try {
                pipeline = Iris.getPipelineManager().getPipelineNullable();
                irisReady = IrisApi.getInstance().isShaderPackInUse()
                        && pipeline instanceof IrisRenderingPipeline iris
                        && iris.getDHTerrainShader().isPresent()
                        && iris.getDHCompat() != null
                        && iris.getDHCompat().getInstance() != null;
                DhAdapter dh = adapter();
                DhReadiness readiness = dh == null ? new DhReadiness(false, 0, "DH unavailable")
                        : dh.readiness(requested);
                dhReady = readiness.ready();
                dhChunks = readiness.chunks();
                detail = readiness.detail();
                if (!adapterFailure.isEmpty()) detail = adapterFailure;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                detail = "DH eligibility check failed: " + failure.getClass().getSimpleName();
            }
        }
        int warmupTicks = ShaderLodConfig.TERRAIN_WARMUP_SECONDS.get() * 20;
        // Persisted acknowledgement does not qualify an unknown startup generation.
        // Once bound, reload/readiness/travel/config changes require false then true.
        GeometryCoverageGuard.Scene scene = world != null && camera != null && irisReady && dhReady && optedIn
                ? new GeometryCoverageGuard.Scene(world, pipeline, camera,
                        GeometryCoverageGuard.cell(camera.getX()), GeometryCoverageGuard.cell(camera.getY()),
                        GeometryCoverageGuard.cell(camera.getZ()), requested, cap, dhChunks) : null;
        if (scene == null || !scene.matches(observedScene)) {
            stableTicks = 0;
            observedScene = scene;
        } else if (stableTicks < Integer.MAX_VALUE) {
            stableTicks++;
        }
        boolean coverageConfirmed = coverage.confirmed(stableTicks >= warmupTicks ? scene : null,
                ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.get());
        var decision = GeometryLodPolicy.decide(new GeometryLodPolicy.Inputs(
                ShaderLodConfig.ENABLED.get(), optedIn, world != null,
                irisReady, dhReady, coverageConfirmed,
                stableTicks, warmupTicks, cap));
        state = new State(scene, decision, observedFlagsGeneration, observedLifecycleGeneration);
        int original = lastOriginalEffectiveChunks > 0 ? lastOriginalEffectiveChunks : requested;
        status = new Status(decision.active(), requested, decision.apply(original), decision.cap(),
                decision.reason(), detail, stableTicks, warmupTicks);
    }

    /** Constant work: no reflection, allocations, GL calls, config reads or renderer mutations. */
    public static int effectiveChunks(int original) {
        checkGenerations();
        lastOriginalEffectiveChunks = original;
        State current = state;
        // Travel, dimension and camera changes restore before the next tick.
        if (!com.pp.shaderlod.optimization.OptimizationConfig.requested(com.pp.shaderlod.optimization.OptimizationId.TERRAIN_RADIUS) ||
                com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended() ||
                current.flagsGeneration()!=com.pp.shaderlod.optimization.OptimizationConfig.generation() ||
                current.lifecycleGeneration()!=com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.epoch() ||
                !current.decision().active() || !coverage.isConfirmed(current.scene())) return original;
        Minecraft client = Minecraft.getInstance();
        Entity camera = client.getCameraEntity();
        if (current.scene() == null || camera == null
                || current.scene().pipeline()!=Iris.getPipelineManager().getPipelineNullable()
                || current.scene().requestedChunks()!=client.options.renderDistance().get()
                || current.scene().cap()!=ShaderLodConfig.TERRAIN_VANILLA_CHUNK_CAP.get()
                || !current.scene().contains(client.level, camera,
                camera.getX(), camera.getY(), camera.getZ())) {
            coverage.invalidate();
            return original;
        }
        return current.decision().apply(original);
    }

    public static Status status() { return status; }

    /**
     * Client-thread explicit inspected-scene action, using cached eligibility only.
     * On true, the caller must set/save warmSourceConfirmed=true. This does not
     * bypass warmup, inspect terrain coverage, or enable the terrain policy.
     */
    public static boolean confirmCurrentScene() {
        checkGenerations();
        Minecraft client = Minecraft.getInstance();
        Entity camera = client.getCameraEntity();
        State current = state;
        GeometryCoverageGuard.Scene scene = current.scene();
        if (client.level == null || client.player == null || camera == null || scene == null
                || !scene.contains(client.level, camera, camera.getX(), camera.getY(), camera.getZ())
                || scene.pipeline()!=Iris.getPipelineManager().getPipelineNullable()
                || scene.requestedChunks()!=client.options.renderDistance().get()
                || scene.cap()!=ShaderLodConfig.TERRAIN_VANILLA_CHUNK_CAP.get()
                || stableTicks < ShaderLodConfig.TERRAIN_WARMUP_SECONDS.get()*20) {
            return false;
        }
        coverage.rearm();
        coverage.confirmed(scene, true);
        return true;
    }

    public static String confirmationReason() {
        return state.scene()==null ? "The current DH scene is not eligible for terrain replacement."
                : stableTicks < status.warmupTicks() ? "Warm this DH scene for " + ((status.warmupTicks()-stableTicks+19)/20) + " more seconds, then inspect coverage and confirm."
                : "Inspect complete DH replacement coverage before confirming this current scene.";
    }

    /** Immediately fail open; caller can clear/save the config flag for durable revocation. */
    public static void revokeCurrentScene() {
        coverage.invalidate();
        State current = state;
        state = new State(current.scene(), new GeometryLodPolicy.Decision(false,
                current.decision().cap(), "DH replacement coverage revoked"), -1, -1);
        Status previous = status;
        int original = lastOriginalEffectiveChunks > 0 ? lastOriginalEffectiveChunks : previous.requestedChunks();
        status = new Status(false, previous.requestedChunks(), original, previous.cap(),
                "DH replacement coverage revoked", previous.detail(), previous.stableTicks(), previous.warmupTicks());
    }

    private static DhAdapter adapter() {
        if (!adapterChecked) {
            adapterChecked = true;
            if (!ModList.get().isLoaded("distanthorizons")) {
                adapterFailure = "Distant Horizons absent";
                return null;
            }
            try {
                adapter = new DhAdapter();
            } catch (ReflectiveOperationException | LinkageError failure) {
                adapterFailure = "Unsupported DH API: " + failure.getClass().getSimpleName();
                ShaderLod.LOGGER.warn("ShaderLOD terrain policy unavailable: {}", adapterFailure);
            }
        }
        return adapter;
    }

    private record DhReadiness(boolean ready, int chunks, String detail) {}

    /** Public DH 3.3.3 API through reflection keeps absent-DH class loading optional. */
    private static final class DhAdapter {
        private final Field configs;
        private final Field worldProxy;
        private final Method graphics;
        private final Method renderingEnabled;
        private final Method renderingMode;
        private final Method renderDistance;
        private final Method getValue;
        private final Method worldLoaded;

        DhAdapter() throws ReflectiveOperationException {
            Class<?> delayed = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
            configs = delayed.getField("configs");
            worldProxy = delayed.getField("worldProxy");
            graphics = Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig").getMethod("graphics");
            renderingEnabled = Class.forName("com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig")
                    .getMethod("renderingEnabled");
            Class<?> graphicsApi = Class.forName("com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig");
            renderingMode = graphicsApi.getMethod("renderingMode");
            renderDistance = graphicsApi.getMethod("chunkRenderDistance");
            getValue = Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue").getMethod("getValue");
            worldLoaded = Class.forName("com.seibel.distanthorizons.api.interfaces.world.IDhApiWorldProxy").getMethod("worldLoaded");
        }

        DhReadiness readiness(int requestedChunks) throws ReflectiveOperationException {
            Object config = configs.get(null);
            Object world = worldProxy.get(null);
            if (config == null || world == null || !Boolean.TRUE.equals(worldLoaded.invoke(world))) {
                return new DhReadiness(false, 0, "DH world/config unavailable");
            }
            Object graphicsConfig = graphics.invoke(config);
            Object distance = getValue.invoke(renderDistance.invoke(graphicsConfig));
            int chunks = distance instanceof Integer value ? value : 0;
            Object mode = getValue.invoke(renderingMode.invoke(graphicsConfig));
            boolean normalMode = mode instanceof Enum<?> value && value.name().equals("DEFAULT");
            if (!Boolean.TRUE.equals(getValue.invoke(renderingEnabled.invoke(graphicsConfig))) || !normalMode) {
                return new DhReadiness(false, chunks, "DH normal terrain rendering disabled");
            }
            if (!GeometryLodPolicy.reachesRequestedRadius(chunks, requestedChunks)) {
                return new DhReadiness(false, chunks, "DH distance below requested detailed radius");
            }
            return new DhReadiness(true, chunks, "");
        }
    }
}
