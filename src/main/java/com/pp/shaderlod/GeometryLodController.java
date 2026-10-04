package com.pp.shaderlod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Fixed, explicitly opted-in geometry cap. Optional DH inspection never runs in the Options getter. */
public final class GeometryLodController {
    public record Status(boolean active, int requestedChunks, int effectiveChunks, int cap,
                         String reason, String detail, int stableTicks, int warmupTicks) {}
    private record State(Object world, GeometryLodPolicy.Decision decision) {}

    private static volatile State state = new State(null, new GeometryLodPolicy.Decision(false, 16, "not initialized"));
    private static volatile Status status = new Status(false, 0, 0, 16, "not initialized", "", 0, 600);
    private static Object observedWorld;
    private static Object observedPipeline;
    private static int stableTicks;
    private static final GeometryCoverageGuard coverage = new GeometryCoverageGuard();
    private static int lastOriginalEffectiveChunks;
    private static DhAdapter adapter;
    private static boolean adapterChecked;
    private static String adapterFailure = "";

    private GeometryLodController() {}

    /** Register once on NeoForge's client game event bus from the main mod constructor. */
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        Object world = client.level;
        int requested = client.options.renderDistance().get();
        // Bind an explicit acknowledgement to one loaded world/dimension instance.
        // A true value carried into a new world must not silently certify its cache.
        boolean coverageConfirmed = coverage.confirmed(world,
                ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.get());
        boolean optedIn = ShaderLodConfig.ENABLED.get() && ShaderLodConfig.TERRAIN_ENABLED.get();
        Object pipeline = null;
        boolean irisReady = false;
        boolean dhReady = false;
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
                dhReady = dh != null && dh.ready();
                if (!adapterFailure.isEmpty()) detail = adapterFailure;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                detail = "DH eligibility check failed: " + failure.getClass().getSimpleName();
            }
        }
        if (world != observedWorld || pipeline != observedPipeline || !irisReady || !dhReady || !optedIn) {
            stableTicks = 0;
            observedWorld = world;
            observedPipeline = pipeline;
        } else if (stableTicks < Integer.MAX_VALUE) {
            stableTicks++;
        }
        int warmupTicks = ShaderLodConfig.TERRAIN_WARMUP_SECONDS.get() * 20;
        var decision = GeometryLodPolicy.decide(new GeometryLodPolicy.Inputs(
                ShaderLodConfig.ENABLED.get(), ShaderLodConfig.TERRAIN_ENABLED.get(), world != null,
                irisReady, dhReady, coverageConfirmed,
                stableTicks, warmupTicks, ShaderLodConfig.TERRAIN_VANILLA_CHUNK_CAP.get()));
        state = new State(world, decision);
        int original = lastOriginalEffectiveChunks > 0 ? lastOriginalEffectiveChunks : requested;
        status = new Status(decision.active(), requested, decision.apply(original), decision.cap(),
                decision.reason(), detail, stableTicks, warmupTicks);
    }

    /** Constant work: no reflection, allocations, GL calls, config reads or renderer mutations. */
    public static int effectiveChunks(int original) {
        lastOriginalEffectiveChunks = original;
        State current = state;
        // Dimension/unload changes recover original distance before the next tick.
        if (current.world() != Minecraft.getInstance().level) return original;
        return current.decision().apply(original);
    }

    public static Status status() { return status; }

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

    /** Public DH 3.3.3 API through reflection keeps absent-DH class loading optional. */
    private static final class DhAdapter {
        private final Field configs;
        private final Field worldProxy;
        private final Method graphics;
        private final Method renderingEnabled;
        private final Method getValue;
        private final Method worldLoaded;

        DhAdapter() throws ReflectiveOperationException {
            Class<?> delayed = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
            configs = delayed.getField("configs");
            worldProxy = delayed.getField("worldProxy");
            graphics = Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig").getMethod("graphics");
            renderingEnabled = Class.forName("com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig")
                    .getMethod("renderingEnabled");
            getValue = Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue").getMethod("getValue");
            worldLoaded = Class.forName("com.seibel.distanthorizons.api.interfaces.world.IDhApiWorldProxy").getMethod("worldLoaded");
        }

        boolean ready() throws ReflectiveOperationException {
            Object config = configs.get(null);
            Object world = worldProxy.get(null);
            if (config == null || world == null || !Boolean.TRUE.equals(worldLoaded.invoke(world))) return false;
            Object graphicsConfig = graphics.invoke(config);
            return Boolean.TRUE.equals(getValue.invoke(renderingEnabled.invoke(graphicsConfig)));
        }
    }
}
