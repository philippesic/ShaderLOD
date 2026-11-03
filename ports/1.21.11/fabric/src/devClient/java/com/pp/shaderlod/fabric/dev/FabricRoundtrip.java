package com.pp.shaderlod.fabric.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.fabric.FabricLodConfig;
import com.pp.shaderlod.fabric.FabricLodSettings;
import com.pp.shaderlod.fabric.FabricLodScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.opengl.GL11;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

/** Dev-only explicit production Apply round trip. No input, focus or exit control. */
final class FabricRoundtrip {
    private static final Logger LOGGER = LoggerFactory.getLogger("shaderlod-dev");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long SETTLE = 30_000_000_000L;
    private final boolean rendererSpeed = Boolean.getBoolean("shaderlod.dev.portRendererSpeedProbe");
    private final boolean sildurSource = Boolean.getBoolean("shaderlod.dev.portSildurOriginalSource");
    private final boolean originalSource = Boolean.getBoolean("shaderlod.dev.portBslOriginalSource") || sildurSource;
    private String sourceFamily() { return sildurSource ? "sildur" : "bsl"; }
    private final long sourceProbeStartedMillis = System.currentTimeMillis();
    private long measuredSince, measuredFrames;
    private long[] measuredCommands, stageCommands = new long[4];
    private JsonObject speedPose;
    private String speedSettings;
    private final boolean qualityRoundtrip = Boolean.getBoolean("shaderlod.dev.portQualityRoundtrip");
    private final String[] stages = originalSource ? new String[]{"original"} : qualityRoundtrip
            ? new String[]{"original", "quality", "native-info-edit", "native-info-restored", "native-upscaled",
                    "native-aa-restored", "performance", "quality-return", "off"}
            : new String[]{"original", "on", "off"};
    private final String run = Long.toString(System.currentTimeMillis());
    private final String original = System.getProperty("shaderlod.dev.portRoundtripOriginal", "");
    private final JsonObject report = new JsonObject();
    private final long started = System.nanoTime();
    private Object world;
    private long firstWorldSince;
    private boolean waitingObserved;
    private FabricLodConfig.Values initial;
    private String originalHash;
    private Map<String, String> originalOptions;
    private String stableSignature;
    private long settledSince;
    private int stage;
    private boolean screenshotPending;
    private boolean advance;
    private boolean done;
    private FabricLodScreen ui;
    private long uiOpened;
    private boolean uiFinished;

    static boolean requested() { return Boolean.getBoolean("shaderlod.dev.portRoundtrip") || Boolean.getBoolean("shaderlod.dev.portBslOriginalSource") || Boolean.getBoolean("shaderlod.dev.portSildurOriginalSource"); }

    static void register() {
        // Disable rather than compete with any ordinary capture/measurement session.
        for (String flag : new String[]{"fabricSmoke", "smoke", "benchmark", "benchmarkOnly",
                "measureWorkload", "measureOnly", "cameraQuality", "cameraQualityOnly",
                "pixelHistoryCapture", "pixelHistoryCaptureOnly", "physicalResolution",
                "workTrace", "spatialLayoutProbe", "pixelCoverage", "gpuTiming", "cpuLayerTiming"}) {
            String value = System.getProperty("shaderlod.dev." + flag, "");
            if (!value.isBlank() && !value.equalsIgnoreCase("false")) {
                LOGGER.error("Fabric roundtrip disabled: conflicting dev property {}", flag);
                return;
            }
        }
        var probe = new FabricRoundtrip();
        if (probe.original.isBlank() || !Path.of(probe.original).getFileName().toString().equals(probe.original)
                || !probe.original.endsWith(".zip")) {
            LOGGER.error("Fabric roundtrip disabled: explicit original ZIP basename required");
            return;
        }
        if (probe.sildurSource && !PortSildurOriginalSourceChecks.propertiesAllow(System.getProperties())) {
            LOGGER.error("Sildur source refused: unknown/mixed/conflicting launch flags"); return;
        }
        if (probe.originalSource && !PortBslOriginalSourceChecks.flagsAllow(
                probe.rendererSpeed, Boolean.getBoolean("shaderlod.dev.portRoundtrip"), probe.qualityRoundtrip,
                Boolean.getBoolean("shaderlod.dev.portRoundtripUi"), Boolean.getBoolean("shaderlod.rendererSpeedPortR1"),
                Boolean.getBoolean(com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES))) {
            LOGGER.error("{} original-only source capture conflicts with roundtrip/Quality/UI/renderer/research flags", probe.sourceFamily());
            return;
        }
        if (probe.originalSource) {
            try {
                var directory=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();
                var archive=directory.resolve(probe.original).normalize();
                if (!archive.startsWith(directory) || archive.equals(directory) ||
                        !(probe.sildurSource ? PortSildurOriginalSourceChecks.sourceMatches(archive,
                            com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.nativeOptions()) :
                            PortBslOriginalSourceChecks.sourceMatches(archive,
                            com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions())))
                    throw new IllegalStateException("Explicit original must match named "+probe.sourceFamily()+" archive/native resources");
                if (probe.sildurSource) PortSildurOriginalSourceChecks.requireNativeSidecar(archive);
                PortBslOriginalSourceChecks.requireFreshExportDirectory(Minecraft.getInstance().gameDirectory.toPath());
            } catch (Exception failure) {
                LOGGER.error(probe.sourceFamily()+" original source capture refused before registration", failure); return;
            }
        }
        if (probe.rendererSpeed && (probe.qualityRoundtrip || Boolean.getBoolean("shaderlod.dev.portRoundtripUi")
                || !Boolean.getBoolean("shaderlod.rendererSpeedPortR1"))) {
            LOGGER.error("Renderer speed probe requires candidate opt-in and no Quality/UI roundtrip");
            return;
        }
        probe.report.addProperty("rendererSpeedProbe", probe.rendererSpeed);
        probe.report.addProperty("qualityRoundtrip", probe.qualityRoundtrip);
        probe.report.addProperty("originalSourceProbe", probe.originalSource);
        if (probe.originalSource) {
            probe.report.addProperty("originalSourceFamily", probe.sourceFamily());
            probe.report.addProperty("productionApplyRequested", false);
            probe.report.addProperty("sourceBoundary", "Actual prelink compiler-path exports, not attached-source or driver readback");
        }
        probe.report.addProperty("run", probe.run);
        probe.report.addProperty("originalPack", probe.original);
        probe.report.addProperty("linkedUniformReadbackPerformed", false);
        probe.report.addProperty("gpuTimingPerformed", false);
        probe.report.addProperty("physicalF8OrInputTestPerformed", false);
        probe.report.addProperty("status", "waiting-for-original");
        ClientTickEvents.END_CLIENT_TICK.register(probe::tick);
        LOGGER.info("Fabric roundtrip enabled: original/On/Off, 30 s settle each, client remains open");
    }

    private void tick(Minecraft client) {
        if (done) return;
        try {
            if (rendererSpeed && com.pp.shaderlod.port.dev.PortRendererFrames.invalidMeasuredFrames() != 0)
                throw new IllegalStateException("Measured frame limiter violation: "
                        + com.pp.shaderlod.port.dev.PortRendererFrames.firstLimiterViolation());
            if (System.nanoTime() - started > 300_000_000_000L) throw new IllegalStateException("300 s probe deadline exceeded");
            if (client.level == null || client.player == null) { resetWindow(); return; }
            if (world == null) {
                world = client.level;
                firstWorldSince = System.nanoTime();
                initial = FabricLodConfig.snapshot();
                if (initial.enabled()) throw new IllegalStateException("Original baseline requires LOD Off before launch");
                originalHash = hash(Iris.getShaderpacksDirectory().resolve(original));
            } else if (world != client.level) throw new IllegalStateException("World changed during roundtrip");
            if (!waitingObserved && System.nanoTime() - firstWorldSince >= 10_000_000_000L) {
                waitingObserved = true;
                observeWaiting(client, "firstWorldObservation");
            }
            if (screenshotPending) return;
            if (ui != null && !uiFinished) { captureUi(client); return; }
            if (advance) {
                advance = false;
                if (stage == stages.length - 1) {
                    if (Boolean.getBoolean("shaderlod.dev.portRoundtripUi") && !uiFinished) {
                        ui = new FabricLodScreen(null);
                        client.setScreen(ui);
                        button("LOD: Off").onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                        if (!qualityRoundtrip || initial.preset() != LodPreset.QUALITY) button("Quality").onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                        uiOpened = System.nanoTime();
                        report.addProperty("status", "waiting-for-settings-screen");
                        write(client);
                        return;
                    }
                    done = true;
                    report.addProperty("status", "complete-observations");
                    write(client);
                    LOGGER.info("Fabric roundtrip complete; client remains open; receipt={}", evidence(client));
                    return;
                }
                stage++;
                boolean enabled = stageEnabled();
                if (rendererSpeed) stageCommands = com.pp.shaderlod.port.dev.PortRendererFrames.commands();
                long before = System.nanoTime();
                boolean applied = qualityRoundtrip ? applyQualityStage(client) : FabricLodSettings.apply(enabled, rendererSpeed ? LodPreset.PERFORMANCE : initial.preset());
                report.addProperty(stages[stage] + "ApplyReturned", applied);
                report.addProperty(stages[stage] + "ApplyElapsedSeconds", (System.nanoTime() - before) / 1_000_000_000.0);
                resetWindow();
                stableSignature = null;
                write(client);
                if (!applied) throw new IllegalStateException("Production Apply failed: " + FabricLodSettings.failureMessage());
                return; // Settle begins on a later tick after production reload returned.
            }
            if (client.screen != null || client.isPaused() || !client.isWindowActive()
                    || !client.getWindow().isFullscreen() || Iris.isFallback()
                    || !IrisApi.getInstance().isShaderPackInUse()) { resetWindow(); return; }
            var window = client.getWindow();
            var target = client.getMainRenderTarget();
            if (GLFW.glfwGetWindowMonitor(window.handle()) == 0 || window.getWidth() != 1920
                    || window.getHeight() != 1200 || target.width != window.getWidth()
                    || target.height != window.getHeight()) { resetWindow(); return; }
            String selected = Iris.getIrisConfig().getShaderPackName().orElse("");
            if (!selected.equals(Iris.getCurrentPackName())) { resetWindow(); return; }
            if (stageGenerated() ? selected.equals(original) : !selected.equals(original))
                throw new IllegalStateException("Unexpected selected pack at " + stages[stage] + ": " + selected);
            if (FabricLodConfig.snapshot().enabled() != stageEnabled())
                throw new IllegalStateException("Unexpected LOD enabled state at " + stages[stage]);
            Map<String, String> options = loadedOptions();
            if (originalSource) validateOriginalSource(client, options);
            String signature = selected + options.toString() + ":" + target.width + "x" + target.height;
            if (settledSince == 0 || !signature.equals(stableSignature)) {
                stableSignature = signature;
                resetWindow();
                settledSince = System.nanoTime();
                return;
            }
            if (rendererSpeed) validateSpeedState(client, options);
            if (System.nanoTime() - settledSince < (rendererSpeed ? 40_000_000_000L
                    : qualityRoundtrip && stage > 0 ? 3_000_000_000L : SETTLE)) return;
            if (rendererSpeed) {
                if (measuredSince == 0) {
                    measuredSince = System.nanoTime();
                    measuredFrames = com.pp.shaderlod.port.dev.PortRendererFrames.frames();
                    measuredCommands = com.pp.shaderlod.port.dev.PortRendererFrames.commands();
                    com.pp.shaderlod.port.dev.PortRendererFrames.beginWindow();
                    return;
                }
                if (System.nanoTime() - measuredSince < 30_000_000_000L) return;
            }
            var state = snapshot(client, options);
            if (stage > 0 && (state.get("framebufferWidth").getAsInt()
                    != report.getAsJsonObject("original").get("framebufferWidth").getAsInt()
                    || state.get("framebufferHeight").getAsInt()
                    != report.getAsJsonObject("original").get("framebufferHeight").getAsInt()))
                throw new IllegalStateException("Framebuffer changed between roundtrip stages");
            boolean unchanged = originalHash.equals(state.get("originalArchiveSha256").getAsString());
            state.addProperty("originalArchiveUnchanged", unchanged);
            if (!unchanged) throw new IllegalStateException("Original archive changed");
            if (stage == 0) {
                originalOptions = Map.copyOf(options);
                if (qualityRoundtrip) validateQualityBaseline(options);
            }
            if (qualityRoundtrip) validateQualityObservation(client, options, state);
            if (stage == stages.length - 1) {
                state.addProperty("originalLoadedOptionsRestored", originalOptions.equals(options));
                state.addProperty("originalCoreConfigRestored", initial.equals(FabricLodConfig.snapshot()));
            }
            report.add(stages[stage], state);
            report.addProperty("status", "capturing-" + stages[stage]);
            String name = "shaderlod-fabric-roundtrip-" + run + "-" + stages[stage] + ".png";
            Path screenshot = client.gameDirectory.toPath().resolve("screenshots").resolve(name);
            state.addProperty("screenshot", screenshot.toAbsolutePath().toString());
            state.addProperty("screenshotStatus", "requested");
            screenshotPending = true;
            write(client);
            Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), 1, message -> client.execute(() -> {
                if (done) return;
                screenshotPending = false;
                state.addProperty("screenshotMessage", message.getString());
                state.addProperty("screenshotStatus", Files.isRegularFile(screenshot) ? "saved" : "failed");
                write(client);
                if (!Files.isRegularFile(screenshot)) fail(client, new IllegalStateException("Screenshot failed"));
                else if (stage == stages.length - 1 && (!state.get("originalLoadedOptionsRestored").getAsBoolean()
                        || !state.get("originalCoreConfigRestored").getAsBoolean()))
                    fail(client, new IllegalStateException("Off did not restore original loaded options/config"));
                else advance = true;
            }));
        } catch (Exception failure) { fail(client, failure); }
    }

    private Button button(String label) {
        return ui.children().stream().filter(child -> child instanceof Button)
                .map(child -> (Button) child).filter(button -> button.getMessage().getString().equals(label))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing real settings button: " + label));
    }

    private void captureUi(Minecraft client) throws Exception {
        if (client.screen != ui) throw new IllegalStateException("Settings screen changed unexpectedly");
        var window = client.getWindow();
        var target = client.getMainRenderTarget();
        if (!client.isWindowActive() || !window.isFullscreen()
                || GLFW.glfwGetWindowMonitor(window.handle()) == 0
                || window.getWidth() != 1920 || window.getHeight() != 1200
                || target.width != window.getWidth() || target.height != window.getHeight()) return;
        if (System.nanoTime() - uiOpened < 2_000_000_000L) return;
        var state = snapshot(client, loadedOptions());
        state.addProperty("screenClass", ui.getClass().getName());
        state.addProperty("stagedEnabledLabel", button("LOD: On").getMessage().getString());
        state.addProperty("stagedPresetLabel", button("Quality (selected)").getMessage().getString());
        state.addProperty("physicalF8OrInputTestPerformed", false);
        report.add("settingsScreen", state);
        String name = "shaderlod-fabric-roundtrip-" + run + "-settings.png";
        Path screenshot = client.gameDirectory.toPath().resolve("screenshots").resolve(name);
        state.addProperty("screenshot", screenshot.toAbsolutePath().toString());
        state.addProperty("screenshotStatus", "requested");
        screenshotPending = true;
        write(client);
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), 1, message -> client.execute(() -> {
                if (done) return;
            screenshotPending = false;
            state.addProperty("screenshotMessage", message.getString());
            state.addProperty("screenshotStatus", Files.isRegularFile(screenshot) ? "saved" : "failed");
            try {
                if (!Files.isRegularFile(screenshot)) throw new IllegalStateException("Settings screenshot failed");
                if (client.screen != ui) throw new IllegalStateException("Settings screen changed before Cancel");
                button("Cancel").onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                boolean unchanged = initial.equals(FabricLodConfig.snapshot())
                        && originalOptions.equals(loadedOptions())
                        && original.equals(Iris.getIrisConfig().getShaderPackName().orElse(""))
                        && original.equals(Iris.getCurrentPackName())
                        && IrisApi.getInstance().isShaderPackInUse() && !Iris.isFallback();
                state.addProperty("cancelReturnedToWorld", client.screen == null);
                state.addProperty("cancelKeptConfigOptionsSelection", unchanged);
                if (!unchanged || client.screen != null) throw new IllegalStateException("Cancel did not preserve state/return to world");
                uiFinished = true;
                advance = true;
                write(client);
            } catch (Exception failure) { fail(client, failure); }
        }));
    }

    private JsonObject snapshot(Minecraft client, Map<String, String> options) throws Exception {
        var state = new JsonObject();
        state.addProperty("capturedAtUtc", Instant.now().toString());
        state.addProperty("stage", stages[stage]);
        state.addProperty("stableWorldSeconds", (System.nanoTime() - settledSince) / 1_000_000_000.0);
        if (rendererSpeed && measuredSince != 0 && !done) {
            if (com.pp.shaderlod.port.dev.PortRendererFrames.invalidMeasuredFrames() != 0)
                throw new IllegalStateException("Throttled or unavailable measured frame; entire window refused");
            long frames = com.pp.shaderlod.port.dev.PortRendererFrames.frames() - measuredFrames;
            double seconds = (System.nanoTime() - measuredSince) / 1_000_000_000.0;
            if (frames <= 0) throw new IllegalStateException("Missing completed world-render window");
            com.pp.shaderlod.port.dev.PortRendererFrames.endWindow();
            state.addProperty("minimumEffectiveFramerateLimit", com.pp.shaderlod.port.dev.PortRendererFrames.minEffective());
            state.addProperty("maximumEffectiveFramerateLimit", com.pp.shaderlod.port.dev.PortRendererFrames.maxEffective());
            state.addProperty("renderFrames", frames); state.addProperty("elapsedSeconds", seconds);
            state.addProperty("meanElapsedFrameMs", 1000.0 * seconds / frames);
            long[] commands = com.pp.shaderlod.port.dev.PortRendererFrames.commands();
            long calls = commands[0] - stageCommands[0], input = commands[1] - stageCommands[1];
            long output = commands[2] - stageCommands[2], reduced = commands[3] - stageCommands[3];
            if (!com.pp.shaderlod.port.dev.PortRendererFrames.stageCommandsAllow(stageEnabled(), calls, input, output, reduced))
                throw new IllegalStateException("Missing On reduction or unexpected Off compactor calls");
            state.addProperty("stageCompactorCalls", calls); state.addProperty("stageInputCommands", input);
            state.addProperty("stageOutputCommands", output); state.addProperty("stageReducedBatches", reduced);
            state.addProperty("windowCompactorCalls", commands[0] - measuredCommands[0]);
            state.addProperty("windowInputCommands", commands[1] - measuredCommands[1]);
            state.addProperty("windowOutputCommands", commands[2] - measuredCommands[2]);
            state.addProperty("windowReducedBatches", commands[3] - measuredCommands[3]);
            state.addProperty("rendererEligible", com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());
            state.addProperty("commandObserverEnabled", true);
        }
        if (originalSource) {
            var admission=validateOriginalSource(client,options);
            state.addProperty("sourceExportOnly",true); state.addProperty("originalSourceFamily",sourceFamily());
            state.add("originalSourceAdmission",admission);
            if (sildurSource) {
                var mapping=PortSildurOriginalSourceChecks.programMapping(client);
                state.add("terrainProgramMapping",GSON.toJsonTree(mapping));
                state.add("terrainCompilerExports",GSON.toJsonTree(PortSildurOriginalSourceChecks.terrainExports(
                    client.gameDirectory.toPath(),sourceProbeStartedMillis,mapping)));
            } else state.add("terrainCompilerExports",GSON.toJsonTree(PortBslOriginalSourceChecks.terrainExports(
                client.gameDirectory.toPath(),sourceProbeStartedMillis)));
            var programs=((net.irisshaders.iris.pipeline.IrisRenderingPipeline)
                Iris.getPipelineManager().getPipelineNullable()).getSodiumPrograms();
            state.addProperty("solidProgramId",programs.getProgram(
                net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.SOLID).handle());
            state.addProperty("cutoutProgramId",programs.getProgram(
                net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.CUTOUT).handle());
            state.addProperty("programIdsAreDrawOrAttachedSourceEvidence",false);
            if (sildurSource) state.addProperty("exportPairToIndividualHandleAssociationPerformed",false);
        }
        state.addProperty("vsync", client.options.enableVsync().get());
        state.addProperty("framerateLimit", client.options.framerateLimit().get());
        if (rendererSpeed) {
            var tracker = client.getFramerateLimitTracker();
            state.addProperty("configuredFramerateLimit", client.options.framerateLimit().get());
            state.addProperty("effectiveFramerateLimit", tracker.getFramerateLimit());
            state.addProperty("framerateThrottleReason", tracker.getThrottleReason().name());
            state.addProperty("inactivityFpsLimit", client.options.inactivityFpsLimit().get().name());
            state.addProperty("iconified", client.getWindow().isIconified());
            state.addProperty("invalidMeasuredFrames", com.pp.shaderlod.port.dev.PortRendererFrames.invalidMeasuredFrames());
            state.addProperty("firstLimiterViolation", com.pp.shaderlod.port.dev.PortRendererFrames.firstLimiterViolation());
        }
        state.addProperty("renderDistance", client.options.renderDistance().get());
        state.add("selectedResourcePacks", GSON.toJsonTree(client.getResourcePackRepository().getSelectedIds()));
        state.addProperty("irisSelectedPack", Iris.getIrisConfig().getShaderPackName().orElse(""));
        state.addProperty("irisLoadedPack", Iris.getCurrentPackName());
        state.addProperty("shaderPackInUse", IrisApi.getInstance().isShaderPackInUse());
        state.addProperty("irisFallback", Iris.isFallback());
        state.add("loadedIrisOptions", GSON.toJsonTree(options));
        state.add("requestedCoreConfig", GSON.toJsonTree(FabricLodConfig.snapshot()));
        state.addProperty("originalArchiveSha256", hash(Iris.getShaderpacksDirectory().resolve(original)));
        var target = client.getMainRenderTarget();
        var window = client.getWindow();
        state.addProperty("framebufferWidth", target.width);
        state.addProperty("framebufferHeight", target.height);
        state.addProperty("windowFramebufferWidth", window.getWidth());
        state.addProperty("windowFramebufferHeight", window.getHeight());
        state.addProperty("fullscreen", window.isFullscreen());
        state.addProperty("nativeMonitor", GLFW.glfwGetWindowMonitor(window.handle()));
        state.addProperty("focused", client.isWindowActive());
        String renderer = GL11.glGetString(GL11.GL_RENDERER);
        String version = GL11.glGetString(GL11.GL_VERSION);
        if (renderer == null || renderer.isBlank() || version == null || version.isBlank())
            throw new IllegalStateException("Current OpenGL renderer/version unavailable");
        state.addProperty("renderBackend", "OpenGL");
        state.addProperty("glRenderer", renderer);
        state.addProperty("glVersion", version);
        state.addProperty("screenshotScale", 1);
        state.addProperty("dimension", client.level.dimension().identifier().toString());
        state.addProperty("dayTime", client.level.getDayTime());
        state.addProperty("gameTime", client.level.getGameTime());
        state.addProperty("rainLevel", client.level.getRainLevel(1.0f));
        state.addProperty("thunderLevel", client.level.getThunderLevel(1.0f));
        state.addProperty("cameraX", client.player.getX());
        state.addProperty("cameraY", client.player.getY());
        state.addProperty("cameraZ", client.player.getZ());
        state.addProperty("yaw", client.player.getYRot());
        state.addProperty("pitch", client.player.getXRot());
        var server = client.getSingleplayerServer();
        if (server != null) state.addProperty("worldName", server.getWorldData().getLevelName());
        for (String id : new String[]{"fabricloader", "iris", "sodium", "distanthorizons"})
            state.addProperty(id + "Version", FabricLoader.getInstance().getModContainer(id)
                    .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("absent"));
        return state;
    }


    /** Explicit named native compiler-source admission; no Apply, source rewrite or renderer activity. */
    private JsonObject validateOriginalSource(Minecraft client,Map<String,String> options) throws Exception {
        var state=new JsonObject();var config=FabricLodConfig.snapshot();
        if (sildurSource) {
            if (!PortSildurOriginalSourceChecks.propertiesAllow(System.getProperties()))
                throw new IllegalStateException("Sildur source flags changed/conflict");
            PortSildurOriginalSourceChecks.requireNativeSidecar(Iris.getShaderpacksDirectory().resolve(original));
        }
        if (!FabricLodConfig.loaded() || initial==null || !config.equals(initial) || config.enabled() || config.debug()
                || config.preset()!=LodPreset.PERFORMANCE)
            throw new IllegalStateException(sourceFamily()+" original-only requires unchanged loaded Off/Performance core");
        if (!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread() ||
                !"OpenGL".equals(com.mojang.blaze3d.systems.RenderSystem.getDevice().getBackendName()))
            throw new IllegalStateException(sourceFamily()+" original source requires observed OpenGL render context");
        if (client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD ||
                client.getMainRenderTarget().width!=1920 || client.getMainRenderTarget().height!=1200 ||
                client.getWindow().getWidth()!=1920 || client.getWindow().getHeight()!=1200 ||
                !client.getWindow().isFullscreen() || !client.isWindowActive() ||
                client.options.renderDistance().get()!=24 || client.options.enableVsync().get() ||
                client.options.framerateLimit().get()!=260)
            throw new IllegalStateException(sourceFamily()+" source requires Overworld native1920/RD24/vsyncOff/configured260");
        if (!Iris.getIrisConfig().areDebugOptionsEnabled() || !Iris.getShaderPackOptionQueue().isEmpty())
            throw new IllegalStateException(sourceFamily()+" source requires debug exports and empty native option queue");
        var profile=client.gameDirectory.toPath().toAbsolutePath().normalize();
        if (!net.irisshaders.iris.platform.IrisPlatformHelpers.getInstance().getGameDir()
                .toAbsolutePath().normalize().equals(profile))
            throw new IllegalStateException("Iris export and actual Minecraft profile directories differ");
        boolean exactApi=com.pp.shaderlod.integration.makeupquad.MakeUpQuadApiPins.exactApi();
        var resources=client.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList();
        boolean resourcesMatch=com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.resourcesMatch(resources);
        var mods=new TreeMap<String,String>();
        for(var mod:FabricLoader.getInstance().getAllMods()) {
            var metadata=mod.getMetadata();if(!metadata.getId().equals("java"))
                mods.put(metadata.getId(),metadata.getVersion().getFriendlyString());
        }
        boolean stackMatch=com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.stackMatches(mods);
        Object pipeline=Iris.getPipelineManager().getPipelineNullable();
        boolean exactPipeline=pipeline!=null && pipeline.getClass()==net.irisshaders.iris.pipeline.IrisRenderingPipeline.class;
        boolean exactPrograms=exactPipeline && ((net.irisshaders.iris.pipeline.IrisRenderingPipeline)pipeline)
            .getSodiumPrograms().getClass()==net.irisshaders.iris.pipeline.programs.SodiumPrograms.class;
        var values=Iris.getCurrentPack().orElseThrow().getShaderPackOptions().getOptionValues();
        boolean exactValues=values.getClass()==net.irisshaders.iris.shaderpack.option.values.MutableOptionValues.class;
        var caps=org.lwjgl.opengl.GL.getCapabilities();
        boolean capabilities=com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.capabilitiesAllow(
            org.lwjgl.system.Pointer.POINTER_SIZE,caps.OpenGL41,caps.glMultiDrawElementsBaseVertex);
        if (!exactApi || !resourcesMatch || !stackMatch || !exactPipeline || !exactPrograms || !exactValues || !capabilities
                || Iris.isFallback() || !IrisApi.getInstance().isShaderPackInUse())
            throw new IllegalStateException(sourceFamily()+" original source exact1.21.11 tuple unavailable");
        if (com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled() ||
                !(sildurSource ? PortSildurOriginalSourceChecks.sourceMatches(Iris.getShaderpacksDirectory().resolve(original),options) :
                    PortBslOriginalSourceChecks.sourceMatches(Iris.getShaderpacksDirectory().resolve(original),options)))
            throw new IllegalStateException(sourceFamily()+" original native source/options/renderer-state certificate refused");
        state.addProperty("exactApi",exactApi);state.addProperty("resourcesMatch",resourcesMatch);
        state.addProperty("stackMatches",stackMatch);state.addProperty("exactPipeline",exactPipeline);
        state.addProperty("exactSodiumPrograms",exactPrograms);state.addProperty("exactMutableValues",exactValues);
        state.addProperty("capabilitiesMatch",capabilities);state.addProperty(sildurSource ? "native115Matches" : "native384Matches",true);
        state.addProperty("rendererEligible",false);state.addProperty("configuredFramerateLimit",260);
        state.addProperty("timingObservation",false);state.add("actualMods",GSON.toJsonTree(mods));
        state.add("actualResourceIds",GSON.toJsonTree(resources));
        state.add("pendingIrisOptionQueue",GSON.toJsonTree(new TreeMap<>(Iris.getShaderPackOptionQueue())));
        return state;
    }

    private boolean stageEnabled() { return originalSource ? false : qualityRoundtrip ? stage > 0 && stage < stages.length - 1 : stage == 1; }
    private boolean stageGenerated() { return !originalSource && !rendererSpeed && (qualityRoundtrip ? stage == 6 : stage == 1); }

    private void validateQualityBaseline(Map<String, String> options) {
        if (!original.equals("photon_v1.3b.zip")
                || !originalHash.equals("120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead"))
            throw new IllegalStateException("Quality probe requires exact pinned original Photon 1.3b");
        if (!"true".equals(options.get("TAA")) || !"false".equals(options.get("TAAU"))
                || !"1.00".equals(options.get("TAAU_RENDER_SCALE")) || !"2".equals(options.get("INFO")))
            throw new IllegalStateException("Quality baseline requires native TAA=true/TAAU=false/scale1.0/INFO2");
        if (!Iris.getShaderPackOptionQueue().isEmpty()) throw new IllegalStateException("Preexisting Iris option queue");
        boolean witness = java.util.Arrays.stream(Iris.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("shaderlod$witnessOwnedOptions"));
        report.addProperty("transformedIrisOptionWriterWitnessPresent", witness);
        if (!witness) throw new IllegalStateException("Transformed Iris option writer witness absent");
    }

    private boolean applyQualityStage(Minecraft client) throws Exception {
        if (!Iris.getShaderPackOptionQueue().isEmpty()) throw new IllegalStateException("Preexisting Iris option queue");
        Map<String, String> queued = switch (stage) {
            case 2 -> Map.of("INFO", "3");
            case 3 -> Map.of("INFO", originalOptions.get("INFO"));
            case 4 -> Map.of("TAAU", "true", "TAAU_RENDER_SCALE", "0.50");
            case 5 -> Map.of("TAAU", originalOptions.get("TAAU"), "TAAU_RENDER_SCALE", originalOptions.get("TAAU_RENDER_SCALE"));
            default -> Map.of();
        };
        var transition = new JsonObject();
        transition.add("beforeQueue", GSON.toJsonTree(new TreeMap<>(Iris.getShaderPackOptionQueue())));
        transition.add("beforeLoadedOptions", GSON.toJsonTree(loadedOptions()));
        transition.add("requestedNativeOptions", GSON.toJsonTree(queued));
        report.add(stages[stage] + "Transition", transition);
        write(client);
        var declared = Iris.getCurrentPack().orElseThrow().getShaderPackOptions().getOptionSet();
        for (var entry : queued.entrySet()) {
            boolean legal = declared.getBooleanOptions().containsKey(entry.getKey())
                    && java.util.List.of("true", "false").contains(entry.getValue());
            var stringOption = declared.getStringOptions().get(entry.getKey());
            legal |= stringOption != null && stringOption.getOption().getAllowedValues().contains(entry.getValue());
            if (!legal) throw new IllegalStateException("Undeclared or illegal pinned native option: " + entry);
        }
        Iris.getShaderPackOptionQueue().putAll(queued);
        transition.add("queuedBeforeApply", GSON.toJsonTree(new TreeMap<>(Iris.getShaderPackOptionQueue())));
        write(client);
        boolean applied = FabricLodSettings.apply(stageEnabled(), stage == stages.length - 1 ? initial.preset()
                : stageGenerated() ? LodPreset.PERFORMANCE : LodPreset.QUALITY);
        transition.addProperty("publicApplyReturned", applied);
        transition.add("afterQueue", GSON.toJsonTree(new TreeMap<>(Iris.getShaderPackOptionQueue())));
        transition.add("afterLoadedOptions", GSON.toJsonTree(loadedOptions()));
        write(client);
        if (!Iris.getShaderPackOptionQueue().isEmpty()) throw new IllegalStateException("Public Apply left pending native options");
        return applied;
    }

    private void validateQualityObservation(Minecraft client, Map<String, String> options, JsonObject state) throws Exception {
        if (!Iris.getShaderPackOptionQueue().isEmpty()) throw new IllegalStateException("Pending native queue at settled capture");
        state.add("pendingIrisOptionQueue", GSON.toJsonTree(new TreeMap<>(Iris.getShaderPackOptionQueue())));
        var preset = FabricLodConfig.snapshot().preset();
        LodPreset expectedPreset = stage == 0 || stage == stages.length - 1 ? initial.preset()
                : stageGenerated() ? LodPreset.PERFORMANCE : LodPreset.QUALITY;
        if (preset != expectedPreset) throw new IllegalStateException("Unexpected core preset at " + stages[stage]);
        if (!stageGenerated()) {
            var expected = new TreeMap<>(originalOptions);
            if (stage == 2) expected.put("INFO", "3");
            if (stage == 4) { expected.put("TAAU", "true"); expected.put("TAAU_RENDER_SCALE", "0.50"); }
            boolean equal = expected.equals(options);
            state.addProperty("nativeLoadedOptionsMatchExpected", equal);
            if (!equal) throw new IllegalStateException("Quality/original graph changed native options at " + stages[stage]);
        } else {
            boolean flags = "true".equals(options.get("SHADERLOD_LIVE_PIXEL_BUDGET"));
            state.addProperty("performancePrivatePixelBudgetLoaded", flags);
            if (!flags) throw new IllegalStateException("Performance private pixel-budget option absent");
        }
        Path canonical = Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName() + ".txt");
        state.addProperty("canonicalOptionsFile", canonical.toAbsolutePath().toString());
        state.addProperty("canonicalOptionsFileExists", Files.isRegularFile(canonical));
        if (Files.isRegularFile(canonical)) {
            state.addProperty("canonicalOptionsSha256", hash(canonical));
            var properties = new java.util.Properties();
            try (var reader = Files.newBufferedReader(canonical)) { properties.load(reader); }
            var values = new TreeMap<String, String>();
            properties.stringPropertyNames().forEach(key -> values.put(key, properties.getProperty(key)));
            state.add("canonicalOptionProperties", GSON.toJsonTree(values));
        }
        state.addProperty("programEnabledTAAURelationEvidence", "Pinned source relation only; not executed draws, linked uniform delivery or FPS");
    }

    private void resetWindow() { if (rendererSpeed) com.pp.shaderlod.port.dev.PortRendererFrames.endWindow(); settledSince = 0; measuredSince = 0; measuredFrames = 0; measuredCommands = null; }
    private void validateSpeedState(Minecraft client, Map<String,String> options) throws Exception {
        var tracker = client.getFramerateLimitTracker();
        if (!com.pp.shaderlod.port.dev.PortRendererFrames.limiterAllows(client.options.framerateLimit().get(),
                tracker.getFramerateLimit(), tracker.getThrottleReason().name(),
                client.options.inactivityFpsLimit().get() == net.minecraft.client.InactivityFpsLimit.MINIMIZED,
                client.isWindowActive(), client.getWindow().isIconified()))
            throw new IllegalStateException("Speed probe requires MINIMIZED, focused noniconified window, configured/effective cap equal and reason NONE");
        var config = FabricLodConfig.snapshot();
        if (config.preset() != LodPreset.PERFORMANCE || !Iris.getShaderPackOptionQueue().isEmpty())
            throw new IllegalStateException("Speed probe requires Performance and empty native option queue");
        if (speedPose == null) {
            var archive = Iris.getShaderpacksDirectory().resolve(original);
            boolean source = com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.validate(archive, options).eligible()
                    || com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.validate(archive, options).eligible();
            if (!source) throw new IllegalStateException("Speed probe requires certified original MakeUp/Sildur native options");
        }
        if (com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled() != stageEnabled())
            throw new IllegalStateException("Unexpected renderer gate at " + stages[stage]);
        JsonObject pose = speedPose(client);
        String settings = client.options.enableVsync().get() + ":" + client.options.framerateLimit().get()
                + ":" + client.options.renderDistance().get() + ":" + client.options.inactivityFpsLimit().get().name();
        if (speedPose == null) { speedPose = pose; speedSettings = settings; originalOptions = Map.copyOf(options); }
        if (!speedPose.equals(pose) || !speedSettings.equals(settings) || !originalOptions.equals(options))
            throw new IllegalStateException("Speed probe changed options/camera/weather/FPS settings");
        var expected = GSON.toJsonTree(initial).getAsJsonObject();
        var actual = GSON.toJsonTree(config).getAsJsonObject();
        expected.remove("enabled"); actual.remove("enabled");
        if (!expected.equals(actual)) throw new IllegalStateException("Speed probe changed core settings");
    }
    private static JsonObject speedPose(Minecraft client) {
        var pose = new JsonObject();
        pose.addProperty("x", client.player.getX()); pose.addProperty("y", client.player.getY());
        pose.addProperty("z", client.player.getZ()); pose.addProperty("yaw", client.player.getYRot());
        pose.addProperty("pitch", client.player.getXRot()); pose.addProperty("dayTime", client.level.getDayTime());
        pose.addProperty("rain", client.level.getRainLevel(1)); pose.addProperty("thunder", client.level.getThunderLevel(1));
        return pose;
    }

    private static Map<String, String> loadedOptions() {
        var options = Iris.getCurrentPack().orElseThrow().getShaderPackOptions();
        var values = options.getOptionValues();
        var result = new TreeMap<String, String>();
        options.getOptionSet().getBooleanOptions().keySet().forEach(key -> result.put(key, Boolean.toString(values.getBooleanValueOrDefault(key))));
        options.getOptionSet().getStringOptions().keySet().forEach(key -> result.put(key, values.getStringValueOrDefault(key)));
        return result;
    }

    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    private Path evidence(Minecraft client) {
        return client.gameDirectory.toPath().resolve("shaderlod-dev/fabric-roundtrip-" + run + ".json");
    }
    private void write(Minecraft client) {
        try {
            Path path = evidence(client);
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(report) + "\n");
        } catch (Exception failure) { done = true; LOGGER.error("Could not write Fabric roundtrip receipt", failure); }
    }
    /** One early observation plus failure state; diagnostic only, never an accepted stage. */
    private void observeWaiting(Minecraft client, String key) {
        var observation = new JsonObject();
        observation.addProperty("capturedAtUtc", Instant.now().toString());
        observation.addProperty("acceptedStageObservation", false);
        observation.addProperty("stage", stages[stage]);
        observation.addProperty("secondsSinceProbeStart", (System.nanoTime() - started) / 1_000_000_000.0);
        observation.addProperty("worldPresent", client.level != null);
        observation.addProperty("playerPresent", client.player != null);
        observation.addProperty("screenClass", client.screen == null ? "none" : client.screen.getClass().getName());
        observation.addProperty("screenAbsent", client.screen == null);
        observation.addProperty("paused", client.isPaused());
        observation.addProperty("focused", client.isWindowActive());
        observation.addProperty("fullscreen", client.getWindow().isFullscreen());
        var window = client.getWindow();
        var target = client.getMainRenderTarget();
        long monitor = GLFW.glfwGetWindowMonitor(window.handle());
        observation.addProperty("nativeMonitor", monitor);
        observation.addProperty("windowFramebufferWidth", window.getWidth());
        observation.addProperty("windowFramebufferHeight", window.getHeight());
        observation.addProperty("framebufferWidth", target == null ? 0 : target.width);
        observation.addProperty("framebufferHeight", target == null ? 0 : target.height);
        observation.addProperty("viewportGate", monitor != 0 && window.getWidth() == 1920 && window.getHeight() == 1200
                && target != null && target.width == window.getWidth() && target.height == window.getHeight());
        try {
            String selected = Iris.getIrisConfig().getShaderPackName().orElse("");
            String loaded = Iris.getCurrentPackName();
            observation.addProperty("irisSelectedPack", selected);
            observation.addProperty("irisLoadedPack", loaded);
            observation.addProperty("selectedEqualsLoaded", selected.equals(loaded));
            observation.addProperty("stagePackGate", stageGenerated() ? !selected.equals(original) : selected.equals(original));
            observation.addProperty("shaderPackInUse", IrisApi.getInstance().isShaderPackInUse());
            observation.addProperty("irisFallback", Iris.isFallback());
            observation.addProperty("coreEnabled", FabricLodConfig.snapshot().enabled());
            observation.addProperty("coreEnabledGate", FabricLodConfig.snapshot().enabled() == stageEnabled());
            observation.addProperty("stableSignaturePresent", stableSignature != null);
            observation.addProperty("stableSeconds", settledSince == 0 ? 0 : (System.nanoTime() - settledSince) / 1_000_000_000.0);
            if (client.level != null && client.player != null && Iris.getCurrentPack().isPresent())
                observation.add("snapshot", snapshot(client, loadedOptions()));
        } catch (Exception unavailable) {
            observation.addProperty("snapshotUnavailable", unavailable.toString());
        }
        report.add(key, observation);
        write(client);
        LOGGER.info("Roundtrip waiting observation {}: {}; receipt={}", key, observation, evidence(client));
    }

    private void fail(Minecraft client, Exception failure) {
        done = true;
        report.addProperty("status", "failed");
        report.addProperty("failure", failure.toString());
        report.addProperty("failedStage", stages[stage]);
        observeWaiting(client, "failureObservation");
        write(client);
        LOGGER.error("Fabric roundtrip failed; client remains open; receipt={}", evidence(client), failure);
    }
}
