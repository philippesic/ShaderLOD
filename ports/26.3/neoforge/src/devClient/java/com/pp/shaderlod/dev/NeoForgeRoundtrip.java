package com.pp.shaderlod.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pp.shaderlod.ShaderLodConfig;
import com.pp.shaderlod.CoreUniformFrame;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.LodSettings;
import com.pp.shaderlod.ShaderLodSettings;
import com.pp.shaderlod.ShaderLodSettingsScreen;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.gui.components.CycleButton;
import net.neoforged.fml.ModList;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mojang.blaze3d.platform.InputConstants;

/** Dev-only explicit production Apply round trip. No input, focus or exit control. */
final class NeoForgeRoundtrip {
    private static final Logger LOGGER = LoggerFactory.getLogger("shaderlod-dev");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long SETTLE = 30_000_000_000L;
    private final boolean rendererSpeed = Boolean.getBoolean(PortRendererFrames.PROPERTY);
    private long measuredSince;
    private long[] measuredProof;
    private JsonObject initialPose;
    private boolean rendererProfileValidated;

    private final boolean qualityRoundtrip = Boolean.getBoolean("shaderlod.dev.portQualityRoundtrip");
    private final boolean optimizationRoundtrip = Boolean.getBoolean("shaderlod.dev.portOptimizationRoundtrip");
    private final String[] stages = optimizationRoundtrip ? new String[]{"original", "on", "quality", "off"} : qualityRoundtrip
            ? new String[]{"original", "quality", "native-info-edit", "native-info-restored", "native-upscaled",
                    "native-aa-restored", "performance", "quality-return", "off"}
            : new String[]{"original", "on", "off"};
    private final String run = Long.toString(System.currentTimeMillis());
    private final String original = System.getProperty("shaderlod.dev.portRoundtripOriginal", "");
    private final JsonObject report = new JsonObject();
    private final long started = System.nanoTime();
    private final BackupPrompt backupPrompt = new BackupPrompt("neoforge", run, report);
    private final LoadingWarningsPrompt loadingWarnings = new LoadingWarningsPrompt(run, report);
    private boolean startupObserved;
    private boolean startupConfigured;
    private long startupConfiguredAt;
    private boolean startupWorldAccepted;
    private final long startupDeadlineEpochMillis = Long.parseLong(System.getProperty("shaderlod.dev.startupDeadlineEpochMillis", "0"));
    private Object world;
    private long firstWorldSince;
    private boolean waitingObserved;
    private CoreState initial;
    private String originalHash;
    private Map<String, String> originalOptions;
    private JsonObject optimizationInput;
    private String stableSignature;
    private long settledSince;
    private int stage;
    private boolean screenshotPending;
    private boolean advance;
    private boolean done;
    private ShaderLodSettingsScreen ui;
    private long uiOpened;
    private boolean uiFinished;

    static boolean requested() { return Boolean.getBoolean("shaderlod.dev.portRoundtrip"); }

    static void register() {
        // Disable rather than compete with any ordinary capture/measurement session.
        for (String flag : new String[]{"fabricSmoke", "smoke", "benchmark", "benchmarkOnly",
                "measureWorkload", "measureOnly", "cameraQuality", "cameraQualityOnly",
                "pixelHistoryCapture", "pixelHistoryCaptureOnly", "physicalResolution",
                "workTrace", "spatialLayoutProbe", "pixelCoverage", "gpuTiming", "cpuLayerTiming"}) {
            String value = System.getProperty("shaderlod.dev." + flag, "");
            if (!value.isBlank() && !value.equalsIgnoreCase("false")) {
                LOGGER.error("NeoForge roundtrip disabled: conflicting dev property {}", flag);
                return;
            }
        }
        var probe = new NeoForgeRoundtrip();
        if (probe.optimizationRoundtrip && (probe.qualityRoundtrip || probe.rendererSpeed
                || Boolean.getBoolean("shaderlod.dev.portOriginalOnly") || Boolean.getBoolean("shaderlod.dev.portRoundtripDh"))) {
            LOGGER.error("Optimization controls require plain original/On(Balanced)/Quality/Off");
            return;
        }
        if (probe.rendererSpeed && (probe.qualityRoundtrip || Boolean.getBoolean("shaderlod.dev.portRoundtripDh") ||
                Boolean.getBoolean("shaderlod.dev.portRoundtripUi") || Boolean.getBoolean("shaderlod.dev.portOriginalOnly") ||
                !Boolean.getBoolean(com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.RESEARCH_OPT_IN))) {
            LOGGER.error("Renderer speed probe requires explicit candidate opt-in and plain no-DH/no-UI three-stage roundtrip");return;
        }

        if (probe.original.isBlank() || !Path.of(probe.original).getFileName().toString().equals(probe.original)
                || !probe.original.endsWith(".zip")) {
            LOGGER.error("NeoForge roundtrip disabled: explicit original ZIP basename required");
            return;
        }
        probe.report.addProperty("qualityRoundtrip", probe.qualityRoundtrip);
        probe.report.addProperty("optimizationRoundtrip", probe.optimizationRoundtrip);
        probe.report.add("stageNames", GSON.toJsonTree(probe.stages));
        probe.report.addProperty("rendererSpeedProbe", probe.rendererSpeed);
        probe.report.addProperty("completedWorldRenderSeam", "renderLevel()V");
        probe.report.addProperty("driverEvidence", "Actual GL function entry observed separately; not GPU completion or clean timing");
        probe.report.addProperty("run", probe.run);
        probe.report.addProperty("originalPack", probe.original);
        probe.report.addProperty("minecraftTarget", "26.3");
        probe.report.addProperty("startupDeadlineEpochMillis", probe.startupDeadlineEpochMillis);
        probe.report.addProperty("backendPolicy", "observed OpenGL required before hashing or GL facts");
        probe.report.addProperty("viewportProof", "SDL3 flags, display, positive logical/pixel sizes, focused native fullscreen and matching 1920x1200 Renderpearl target");
        probe.report.addProperty("linkedUniformReadbackPerformed", false);
        probe.report.addProperty("gpuTimingPerformed", false);
        probe.report.addProperty("physicalF8OrInputTestPerformed", false);
        probe.report.addProperty("status", "waiting-for-original");
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> probe.tick(Minecraft.getInstance()));
        LOGGER.info("NeoForge roundtrip enabled: original/On/Off, 30 s settle each, client remains open");
    }

    private void tick(Minecraft client) {
        if (done) return;
        try {
            if(rendererSpeed && !rendererProfileValidated) { validateRendererProfile(client);rendererProfileValidated=true; }
            if(rendererSpeed && PortRendererFrames.firstRejectedRate()!=null) {
                report.add("rendererRejectedRate",GSON.toJsonTree(PortRendererFrames.firstRejectedRate()));
                throw new IllegalStateException("At least one completed measured frame was rate-throttled or lost native focus");
            }
            if (System.nanoTime() - started > 300_000_000_000L) throw new IllegalStateException("300 s probe deadline exceeded");
            if (!startupConfigured && System.nanoTime() - started >= 7_000_000_000L) {
                if (!callbackAllowed()) throw new IllegalStateException("Startup correction deadline expired");
                startupConfigured = true; // No repeated mode/focus requests after failure.
                SdlViewport.configureStartup(client, "neoforge", run, report, () -> write(client));
                startupConfiguredAt = System.nanoTime();
            }
            if (!startupConfigured || System.nanoTime() - startupConfiguredAt < 3_000_000_000L) return;
            if (!startupObserved && System.nanoTime() - started >= 10_000_000_000L) {
                startupObserved = true;
                observeWaiting(client, "startupObservation");
            }
            if (!startupWorldAccepted && startupDeadlineEpochMillis > 0 && System.currentTimeMillis() >= startupDeadlineEpochMillis)
                throw new IllegalStateException("Absolute startup deadline exceeded before eligible shader world entry");
            if (client.level == null || client.player == null) {
                resetWindow();
                if (startupObserved) {
                    loadingWarnings.beforeWorld(client, () -> write(client), this::callbackAllowed, failure -> fail(client, failure));
                    backupPrompt.beforeWorld(client, () -> write(client), this::callbackAllowed, failure -> fail(client, failure));
                }
                return;
            }
            backupPrompt.afterWorld(client, () -> write(client));
            com.pp.shaderlod.integration.RendererBackend.requireApplySupported();
            if (world == null) {
                world = client.level;
                firstWorldSince = System.nanoTime();
                initial = coreState();
                if (initial.enabled()) throw new IllegalStateException("Original baseline requires LOD Off before launch");
                if(rendererSpeed && initial.preset()!=LodPreset.PERFORMANCE) throw new IllegalStateException("Renderer baseline requires Off/Performance");
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
                        ui = new ShaderLodSettingsScreen(ModList.get().getModContainerById("shaderlod").orElseThrow(), null);
                        client.gui.setScreen(ui);
                        var controls = cycles();
                        if (controls.size() != 2) throw new IllegalStateException("Expected two real settings cycle controls");
                        controls.get(0).onPress(new KeyEvent(InputConstants.KEY_RETURN, InputConstants.KEYCODE_RETURN, 0));
                        for (int i = 0; i < 4 && controls.get(1).getValue() != LodPreset.QUALITY; i++)
                            controls.get(1).onPress(new KeyEvent(InputConstants.KEY_RETURN, InputConstants.KEYCODE_RETURN, 0));
                        if (!Boolean.TRUE.equals(controls.get(0).getValue()) || controls.get(1).getValue() != LodPreset.QUALITY)
                            throw new IllegalStateException("Real settings callbacks did not stage On/Quality");
                        uiOpened = System.nanoTime();
                        report.addProperty("status", "waiting-for-settings-screen");
                        write(client);
                        return;
                    }
                    done = true;
                    report.addProperty("status", "complete-observations");
                    write(client);
                    LOGGER.info("NeoForge roundtrip complete; client remains open; receipt={}", evidence(client));
                    return;
                }
                stage++;
                boolean enabled = stageEnabled();
                long before = System.nanoTime();
                boolean applied = optimizationRoundtrip ? ShaderLodSettings.apply(enabled, stage == 2 ? LodPreset.QUALITY : LodPreset.BALANCED)
                        : qualityRoundtrip ? applyQualityStage(client) : ShaderLodSettings.apply(enabled, initial.preset());
                report.addProperty(stages[stage] + "ApplyReturned", applied);
                report.addProperty(stages[stage] + "ApplyElapsedSeconds", (System.nanoTime() - before) / 1_000_000_000.0);
                resetWindow();
                stableSignature = null;
                write(client);
                if (!applied) throw new IllegalStateException("Production Apply failed: " + ShaderLodSettings.failureMessage());
                return; // Settle begins on a later tick after production reload returned.
            }
            if (client.gui.screen() != null || client.isPaused() || !client.isWindowActive()
                    || !SdlViewport.observe(client).nativeFullscreen() || Iris.isFallback()
                    || !IrisApi.getInstance().isShaderPackInUse()) { resetWindow(); return; }
            var window = client.getWindow();
            var target = client.gameRenderer.mainRenderTarget();
            if (!SdlViewport.observe(client).eligible()) { resetWindow(); return; }
            String selected = Iris.getIrisConfig().getShaderPackName().orElse("");
            if (!selected.equals(Iris.getCurrentPackName())) { resetWindow(); return; }
            if (rendererSpeed ? !selected.equals(original) : stageGenerated() ? selected.equals(original) : !selected.equals(original))
                throw new IllegalStateException("Unexpected selected pack at " + stages[stage] + ": " + selected);
            if (coreState().enabled() != stageEnabled())
                throw new IllegalStateException("Unexpected LOD enabled state at " + stages[stage]);
            startupWorldAccepted = true;
            report.addProperty("startupEligibleShaderWorldEntered", true);
            if(rendererSpeed)requireRendererRate(client);
            Map<String, String> options = loadedOptions();
            if (optimizationRoundtrip) validateOptimizationObservation(client, options, null);
            if(rendererSpeed && originalOptions!=null && (!originalOptions.equals(options) || !initialPose.equals(rendererPose(client))))
                throw new IllegalStateException("Renderer native options/pose/weather changed before window");
            String signature = selected + options.toString() + ":" + target.width + "x" + target.height
                    + ":" + client.options.enableVsync().get() + ":" + client.options.framerateLimit().get()
                    + (rendererSpeed ? rendererPose(client).toString() : "");
            if (settledSince == 0 || !signature.equals(stableSignature)) {
                resetWindow();
                stableSignature = signature;
                settledSince = System.nanoTime();
                return;
            }
            if (System.nanoTime() - settledSince < (rendererSpeed ? 40_000_000_000L : qualityRoundtrip && stage > 0 ? 3_000_000_000L : SETTLE)) return;
            if(rendererSpeed) {
                if(com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled() != stageEnabled())
                    throw new IllegalStateException("Renderer admission does not match " + stages[stage]);
                if(measuredSince==0) {
                    PortRendererFrames.beginMeasuredWindow();
                    measuredSince=System.nanoTime();measuredProof=PortRendererFrames.snapshot();return;
                }
                if(System.nanoTime()-measuredSince<30_000_000_000L)return;
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
                if(rendererSpeed)initialPose=rendererPose(client);
                if (qualityRoundtrip) validateQualityBaseline(options);
            }
            if(rendererSpeed) {
                if(!originalOptions.equals(options) || !initialPose.equals(rendererPose(client)))
                    throw new IllegalStateException("Native options/pose/weather changed across renderer stages");
                if(stage>0) for(String key:new String[]{"vsync","framerateLimit","vanillaRenderDistance","inactivityFpsLimit","configuredFramerateLimit","effectiveFramerateLimit","framerateThrottleReason"})
                    if(!state.get(key).equals(report.getAsJsonObject("original").get(key)))
                        throw new IllegalStateException("Renderer settings changed: "+key);
            }
            if (qualityRoundtrip) validateQualityObservation(client, options, state);
            if (optimizationRoundtrip) validateOptimizationObservation(client, options, state);
            if (stage == stages.length - 1) {
                state.addProperty("originalLoadedOptionsRestored", originalOptions.equals(options));
                state.addProperty("originalCoreConfigRestored", initial.equals(coreState()));
            }
            report.add(stages[stage], state);
            report.addProperty("status", "capturing-" + stages[stage]);
            String name = "shaderlod-neoforge-roundtrip-" + run + "-" + stages[stage] + ".png";
            Path screenshot = client.gameDirectory.toPath().resolve("screenshots").resolve(name);
            state.addProperty("screenshot", screenshot.toAbsolutePath().toString());
            state.addProperty("screenshotStatus", "requested");
            screenshotPending = true;
            write(client);
            Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1, message -> client.execute(() -> {
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

    private boolean callbackAllowed() {
        return !done && System.nanoTime() - started <= 300_000_000_000L
                && (startupWorldAccepted || startupDeadlineEpochMillis <= 0 || System.currentTimeMillis() < startupDeadlineEpochMillis);
    }

    private Button button(String label) {
        return ui.children().stream().filter(child -> child instanceof Button)
                .map(child -> (Button) child).filter(button -> button.getMessage().getString().equals(label))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing real settings button: " + label));
    }

    private void captureUi(Minecraft client) throws Exception {
        if (client.gui.screen() != ui) throw new IllegalStateException("Settings screen changed unexpectedly");
        var window = client.getWindow();
        var target = client.gameRenderer.mainRenderTarget();
        if (!SdlViewport.observe(client).eligible()) return;
        if (System.nanoTime() - uiOpened < 2_000_000_000L) return;
        var state = snapshot(client, loadedOptions());
        state.addProperty("screenClass", ui.getClass().getName());
        state.addProperty("stagedEnabledLabel", cycles().get(0).getMessage().getString());
        state.addProperty("stagedPresetLabel", cycles().get(1).getMessage().getString());
        state.addProperty("physicalF8OrInputTestPerformed", false);
        report.add("settingsScreen", state);
        String name = "shaderlod-neoforge-roundtrip-" + run + "-settings.png";
        Path screenshot = client.gameDirectory.toPath().resolve("screenshots").resolve(name);
        state.addProperty("screenshot", screenshot.toAbsolutePath().toString());
        state.addProperty("screenshotStatus", "requested");
        screenshotPending = true;
        write(client);
        Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1, message -> client.execute(() -> {
                if (done) return;
            screenshotPending = false;
            state.addProperty("screenshotMessage", message.getString());
            state.addProperty("screenshotStatus", Files.isRegularFile(screenshot) ? "saved" : "failed");
            try {
                if (!Files.isRegularFile(screenshot)) throw new IllegalStateException("Settings screenshot failed");
                if (client.gui.screen() != ui) throw new IllegalStateException("Settings screen changed before Cancel");
                button("Cancel").onPress(new KeyEvent(InputConstants.KEY_RETURN, InputConstants.KEYCODE_RETURN, 0));
                boolean unchanged = initial.equals(coreState())
                        && originalOptions.equals(loadedOptions())
                        && original.equals(Iris.getIrisConfig().getShaderPackName().orElse(""))
                        && original.equals(Iris.getCurrentPackName())
                        && IrisApi.getInstance().isShaderPackInUse() && !Iris.isFallback();
                state.addProperty("cancelReturnedToWorld", client.gui.screen() == null);
                state.addProperty("cancelKeptConfigOptionsSelection", unchanged);
                if (!unchanged || client.gui.screen() != null) throw new IllegalStateException("Cancel did not preserve state/return to world");
                uiFinished = true;
                advance = true;
                write(client);
            } catch (Exception failure) { fail(client, failure); }
        }));
    }

    private JsonObject snapshot(Minecraft client, Map<String, String> options) throws Exception {
        com.pp.shaderlod.integration.RendererBackend.requireApplySupported();
        String backend = com.pp.shaderlod.integration.RendererBackend.observedName();
        var state = new JsonObject();
        state.addProperty("capturedAtUtc", Instant.now().toString());
        state.addProperty("stage", stages[stage]);
        state.addProperty("stableWorldSeconds", (System.nanoTime() - settledSince) / 1_000_000_000.0);
        if(rendererSpeed) {
            if(measuredSince==0 || measuredProof==null)throw new IllegalStateException("No completed-world render measurement window");
            long[] proof=PortRendererFrames.snapshot();long frames=proof[0]-measuredProof[0];
            double seconds=(System.nanoTime()-measuredSince)/1_000_000_000.0;
            if(frames<=0)throw new IllegalStateException("Completed-world render counter absent");
            PortRendererFrames.stopMeasuredWindow();
            state.addProperty("rejectedMeasuredFrames",proof[7]-measuredProof[7]);
            if(!PortRendererFrames.windowAccepted(frames,measuredProof[7],proof[7])) {
                report.add("rendererRejectedRate",GSON.toJsonTree(PortRendererFrames.firstRejectedRate()));
                throw new IllegalStateException("Completed measured window contains rejected effective rate-control frames");
            }
            state.addProperty("renderFrames",frames);state.addProperty("elapsedSeconds",seconds);
            state.addProperty("meanElapsedFrameMs",1000.0*seconds/frames);
            state.addProperty("helperCalls",proof[1]-measuredProof[1]);state.addProperty("inputCommands",proof[2]-measuredProof[2]);
            state.addProperty("outputCommands",proof[3]-measuredProof[3]);state.addProperty("reducedBatches",proof[4]-measuredProof[4]);
            state.addProperty("glFunctionEntries",proof[5]-measuredProof[5]);state.addProperty("copiedGlFunctionEntries",proof[6]-measuredProof[6]);
            state.addProperty("observerEnabled",true);state.addProperty("gpuCompletionProven",false);
            if(proof[5]<=measuredProof[5] || (stageEnabled() ? proof[4]<=measuredProof[4] || proof[6]<=measuredProof[6]
                || proof[2]-measuredProof[2]<=proof[3]-measuredProof[3] : proof[1]!=measuredProof[1] || proof[6]!=measuredProof[6]))
                throw new IllegalStateException("Actual copied GL consumption/native Off return not observed");
        }
        if(rendererSpeed) {
            var rate=rendererRate(client);
            state.add("rateControl",GSON.toJsonTree(rate));
            state.addProperty("configuredFramerateLimit",rate.configuredLimit());
            state.addProperty("effectiveFramerateLimit",rate.effectiveLimit());
            state.addProperty("framerateThrottleReason",rate.throttleReason());
            state.addProperty("inactivityFpsLimit",rate.inactivityPolicy());
            state.addProperty("nativeWindowFocused",rate.focused());state.addProperty("windowIconified",rate.iconified());
            requireRendererRate(client);
        }
        state.addProperty("vsync",client.options.enableVsync().get());
        state.addProperty("framerateLimit",client.options.framerateLimit().get());
        state.addProperty("vanillaRenderDistance",client.options.renderDistance().get());
        state.addProperty("irisSelectedPack", Iris.getIrisConfig().getShaderPackName().orElse(""));
        state.addProperty("irisLoadedPack", Iris.getCurrentPackName());
        state.addProperty("shaderPackInUse", IrisApi.getInstance().isShaderPackInUse());
        state.addProperty("irisFallback", Iris.isFallback());
        state.addProperty("rendererPortOptIn", Boolean.getBoolean(com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.RESEARCH_OPT_IN));
        state.addProperty("rendererPortEligible", com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());
        state.addProperty("rendererCopyBatches", com.pp.shaderlod.integration.makeupquad.SubmissionCopyEvidence.batches());
        state.addProperty("rendererNativeCommands", com.pp.shaderlod.integration.makeupquad.SubmissionCopyEvidence.nativeCommands());
        state.addProperty("rendererSubmittedCommands", com.pp.shaderlod.integration.makeupquad.SubmissionCopyEvidence.submittedCommands());
        state.add("loadedIrisOptions", GSON.toJsonTree(options));
        state.add("requestedCoreConfig", GSON.toJsonTree(coreState()));
        state.addProperty("originalArchiveSha256", hash(Iris.getShaderpacksDirectory().resolve(original)));
        var target = client.gameRenderer.mainRenderTarget();
        var window = client.getWindow();
        state.addProperty("framebufferWidth", target.width);
        state.addProperty("framebufferHeight", target.height);
        state.addProperty("windowFramebufferWidth", window.getWidth());
        state.addProperty("windowFramebufferHeight", window.getHeight());
        state.addProperty("fullscreen", (org.lwjgl.sdl.SDLVideo.SDL_GetWindowFlags(window.handle()) & org.lwjgl.sdl.SDLVideo.SDL_WINDOW_FULLSCREEN) != 0);
        SdlViewport.observe(client).appendTo(state);
        state.addProperty("focused", client.isWindowActive());
        String renderer = GL11.glGetString(GL11.GL_RENDERER);
        String version = GL11.glGetString(GL11.GL_VERSION);
        if (renderer == null || renderer.isBlank() || version == null || version.isBlank())
            throw new IllegalStateException("Current OpenGL renderer/version unavailable");
        state.addProperty("renderBackend", backend);
        state.addProperty("glRenderer", renderer);
        state.addProperty("glVersion", version);
        state.addProperty("screenshotScale", 1);
        state.addProperty("dimension", client.level.dimension().identifier().toString());
        state.addProperty("dayTime", client.level.getOverworldClockTime());
        state.addProperty("dayTimeSource", "Level.getOverworldClockTime");
        state.addProperty("defaultClockTime", client.level.getDefaultClockTime());
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
        for (String id : new String[]{"neoforge", "iris", "sodium", "distanthorizons"})
            state.addProperty(id + "Version", ModList.get().getModContainerById(id)
                    .map(mod -> mod.getModInfo().getVersion().toString()).orElse("absent"));
        return state;
    }

    private record CoreState(boolean enabled, LodPreset preset, LodSettings settings) {}
    private static CoreState coreState() {
        return new CoreState(ShaderLodConfig.ENABLED.get(), ShaderLodConfig.PRESET.get(), ShaderLodConfig.snapshot());
    }
    private java.util.List<CycleButton<?>> cycles() {
        var result = new java.util.ArrayList<CycleButton<?>>();
        for (var child : ui.children()) if (child instanceof CycleButton<?> cycle) result.add(cycle);
        return result;
    }


    private boolean stageEnabled() { return optimizationRoundtrip ? stage == 1 || stage == 2 : qualityRoundtrip ? stage > 0 && stage < stages.length - 1 : stage == 1; }
    private boolean stageGenerated() { return optimizationRoundtrip ? stage == 1 : qualityRoundtrip ? stage == 6 : stage == 1; }

    /** Private installed caller binds the current graph; this observes public Apply, never renderer policy. */
    private void validateOptimizationObservation(Minecraft client, Map<String, String> options, JsonObject state) throws Exception {
        if (optimizationInput == null) {
            Path input = Path.of(System.getProperty("shaderlod.dev.portOptimizationInput", ""));
            if (!input.isAbsolute() || Files.isSymbolicLink(input)) throw new IllegalStateException("Explicit private optimization input required");
            optimizationInput = com.google.gson.JsonParser.parseString(Files.readString(input)).getAsJsonObject();
            if (!original.equals("photon_v1.3b.zip") || !original.equals(optimizationInput.get("originalFile").getAsString())
                    || !originalHash.equals("120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead")
                    || !originalHash.equals(optimizationInput.get("originalSha256").getAsString()))
                throw new IllegalStateException("Pinned Photon original required");
            report.add("optimizationInput", optimizationInput);
            report.addProperty("mainConsumerOrigin", ShaderLodSettings.class.getProtectionDomain().getCodeSource().getLocation().toString());
            report.addProperty("observerOrigin", NeoForgeRoundtrip.class.getProtectionDomain().getCodeSource().getLocation().toString());
        }
        var expected = new TreeMap<String, String>();
        optimizationInput.getAsJsonObject("nativeOptions").entrySet().forEach(entry -> expected.put(entry.getKey(), entry.getValue().getAsString()));
        if (expected.size() != 366 || !"true".equals(expected.get("TAA")) || !"false".equals(expected.get("TAAU"))
                || !"1.00".equals(expected.get("TAAU_RENDER_SCALE")) || !"false".equals(expected.get("SH_SKYLIGHT")))
            throw new IllegalStateException("Configured366 Photon native tuple required");
        if (stageGenerated()) expected.putAll(Map.of("SHADERLOD_LIVE_PIXEL_BUDGET", "true",
                "SHADERLOD_LIVE_PIXEL_BUDGET_INCLUDED", "true", "SHADERLOD_RENDER_BUDGET_INCLUDED", "true"));
        var cfg = coreState();
        if (!expected.equals(options) || initial.enabled() || initial.preset() != LodPreset.BALANCED || initial.settings().debug()
                || cfg.enabled() != stageEnabled() || cfg.preset() != (stage == 2 ? LodPreset.QUALITY : LodPreset.BALANCED)
                || !initial.settings().equals(cfg.settings()) || !Iris.getShaderPackOptionQueue().isEmpty()
                || client.level.dimension() != net.minecraft.world.level.Level.OVERWORLD)
            throw new IllegalStateException("Photon native map/core/queue/Overworld mismatch");
        if (state == null) return;
        var dir = Iris.getShaderpacksDirectory().toAbsolutePath().normalize();
        if (stageGenerated()) {
            String selected = Iris.getCurrentPackName();
            Path archive = dir.resolve(selected).normalize();
            if (!selected.equals(optimizationInput.get("generatedFile").getAsString()) || !archive.startsWith(dir)
                    || Files.isSymbolicLink(archive) || !hash(archive).equals(optimizationInput.get("generatedSha256").getAsString()))
                throw new IllegalStateException("Current generated Photon identity/archive mismatch");
            try (var zip = new java.util.zip.ZipFile(archive.toFile())) {
                if (zip.size() != 535) throw new IllegalStateException("Current Photon535 graph required");
                JsonObject marker;
                try (var in = zip.getInputStream(java.util.Objects.requireNonNull(zip.getEntry("shaderlod-selection.json")))) {
                    marker = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                }
                String recipeSha;
                try (var in = com.pp.shaderlod.packs.PackSelectionService.class.getResourceAsStream("/assets/shaderlod/adapters/patches/photon-experiments/live-pixel-budget.json")) {
                    recipeSha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(java.util.Objects.requireNonNull(in).readAllBytes()));
                }
                if (marker.get("schema").getAsInt() != 1 || !"photon".equals(marker.get("adapterId").getAsString())
                        || !original.equals(marker.get("originalFile").getAsString()) || !originalHash.equals(marker.get("originalSha256").getAsString())
                        || !recipeSha.equals(marker.get("recipeSha256").getAsString()) || !recipeSha.equals(optimizationInput.get("recipeSha256").getAsString()))
                    throw new IllegalStateException("Photon current source/recipe marker mismatch");
                String stateFile = marker.get("stateFile").getAsString();
                if (!Path.of(stateFile).getFileName().toString().equals(stateFile) || Files.isSymbolicLink(dir.resolve(stateFile)))
                    throw new IllegalStateException("Unsafe Photon state reference");
                var selection = com.google.gson.JsonParser.parseString(Files.readString(dir.resolve(stateFile))).getAsJsonObject();
                if (!selection.get("active").getAsBoolean() || !selected.equals(selection.get("generatedFile").getAsString())
                        || !hash(archive).equals(selection.get("generatedSha256").getAsString()) || !original.equals(selection.get("originalFile").getAsString())
                        || !originalHash.equals(selection.get("originalSha256").getAsString()) || !recipeSha.equals(selection.get("recipeSha256").getAsString()))
                    throw new IllegalStateException("Photon public selection state mismatch");
                state.add("generatedSelectionMarker", marker);
                state.addProperty("generatedArchiveSha256", hash(archive));
                state.addProperty("generatedSelectionStateMatched", true);
            }
        }
        Path canonical = dir.resolve(Iris.getCurrentPackName() + ".txt");
        var properties = new java.util.Properties();
        try (var reader = Files.newBufferedReader(canonical)) { properties.load(reader); }
        var canonicalOptions = new TreeMap<String, String>();
        properties.stringPropertyNames().forEach(key -> canonicalOptions.put(key, properties.getProperty(key)));
        var expectedCanonical = optimizationInput.getAsJsonObject("canonicalOptions").deepCopy();
        if (stageGenerated()) expectedCanonical.addProperty("SHADERLOD_LIVE_PIXEL_BUDGET", "true");
        if (!GSON.toJsonTree(canonicalOptions).equals(expectedCanonical)) throw new IllegalStateException("Photon canonical native options changed");
        state.add("canonicalOptionProperties", GSON.toJsonTree(canonicalOptions));
        state.addProperty("canonicalOptionsSha256", hash(canonical));
        state.addProperty("loadedOptionCount", options.size());
        state.addProperty("configured366Preserved", true);
        state.addProperty("generatedPhotonRecipeSelected", stageGenerated());
        state.addProperty("avoidedWorkMeasured", false);
    }

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
        boolean applied = ShaderLodSettings.apply(stageEnabled(), stage == stages.length - 1 ? initial.preset()
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
        var preset = coreState().preset();
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
        return client.gameDirectory.toPath().resolve("shaderlod-dev/neoforge-roundtrip-" + run + ".json");
    }
    private void write(Minecraft client) {
        try {
            Path path = evidence(client);
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(report) + "\n");
        } catch (Exception failure) { done = true; LOGGER.error("Could not write NeoForge roundtrip receipt", failure); }
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
        observation.addProperty("screenClass", client.gui.screen() == null ? "none" : client.gui.screen().getClass().getName());
        observation.addProperty("screenAbsent", client.gui.screen() == null);
        observation.addProperty("paused", client.isPaused());
        observation.addProperty("focused", client.isWindowActive());
        observation.addProperty("fullscreen", SdlViewport.observe(client).nativeFullscreen());
        var window = client.getWindow();
        var target = client.gameRenderer.mainRenderTarget();
        SdlViewport.observe(client).appendTo(observation);
        try {
            observation.addProperty("renderBackend", com.pp.shaderlod.integration.RendererBackend.observedName());
            String selected = Iris.getIrisConfig().getShaderPackName().orElse("");
            String loaded = Iris.getCurrentPackName();
            observation.addProperty("irisSelectedPack", selected);
            observation.addProperty("irisLoadedPack", loaded);
            observation.addProperty("selectedEqualsLoaded", selected.equals(loaded));
            observation.addProperty("stagePackGate", rendererSpeed ? selected.equals(original) : stageGenerated() ? !selected.equals(original) : selected.equals(original));
            observation.addProperty("shaderPackInUse", IrisApi.getInstance().isShaderPackInUse());
            observation.addProperty("irisFallback", Iris.isFallback());
            observation.addProperty("coreEnabled", coreState().enabled());
            observation.addProperty("coreEnabledGate", coreState().enabled() == stageEnabled());
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


    private void resetWindow() { PortRendererFrames.stopMeasuredWindow();settledSince=0;measuredSince=0;measuredProof=null; }
    private static PortRendererFrames.RateFacts rendererRate(Minecraft client) {
        var tracker=client.getFramerateLimitTracker();var window=client.getWindow();
        return new PortRendererFrames.RateFacts(client.options.inactivityFpsLimit().get().name(),client.options.framerateLimit().get(),
            tracker.getFramerateLimit(),tracker.getThrottleReason().name(),client.isWindowActive() && window.isFocused(),window.isIconified());
    }
    private void requireRendererRate(Minecraft client) {
        var rate=rendererRate(client);
        if(!PortRendererFrames.rateAdmitted(rate.inactivityPolicy(),rate.configuredLimit(),rate.effectiveLimit(),rate.throttleReason(),rate.focused(),rate.iconified())) {
            report.add("rendererRejectedRate",GSON.toJsonTree(rate));
            throw new IllegalStateException("Renderer bracket requires MINIMIZED inactivity policy, configured/effective cap equality, NONE throttle reason and focused noniconified window");
        }
    }
    private static JsonObject rendererPose(Minecraft client) {
        var pose=new JsonObject();pose.addProperty("x",client.player.getX());pose.addProperty("y",client.player.getY());
        pose.addProperty("z",client.player.getZ());pose.addProperty("yaw",client.player.getYRot());pose.addProperty("pitch",client.player.getXRot());
        pose.addProperty("time",client.level.getOverworldClockTime());pose.addProperty("rain",client.level.getRainLevel(1));pose.addProperty("thunder",client.level.getThunderLevel(1));return pose;
    }
    private void validateRendererProfile(Minecraft client) throws Exception {
        var profile=client.gameDirectory.toPath().toAbsolutePath().normalize();
        if(!profile.endsWith(Path.of("run-workloads","port-263-runtime-r1","neoforge")) || !profile.equals(profile.toRealPath()))
            throw new IllegalStateException("Renderer probe requires exact nonsymlink private port profile");
        var world=profile.resolve("saves/ShaderLOD-Landscape");
        try(var paths=Files.list(profile.resolve("saves"))) {
            var worlds=paths.filter(Files::isDirectory).toList();
            if(worlds.size()!=1 || !worlds.get(0).equals(world))throw new IllegalStateException("Renderer private profile must contain only ShaderLOD-Landscape");
        }
        if(!world.equals(world.toRealPath()) || !world.resolve("level.dat").equals(world.resolve("level.dat").toRealPath()))
            throw new IllegalStateException("Renderer world must not alias another save");
        if(ModList.get().isLoaded("distanthorizons"))throw new IllegalStateException("Renderer speed bracket requires no-DH native stack");
    }

    private void fail(Minecraft client, Exception failure) {
        PortRendererFrames.stopMeasuredWindow();
        if((rendererSpeed || optimizationRoundtrip) && initial!=null) {
            String recoveryKey = rendererSpeed ? "rendererFailureRestore" : "optimizationFailureRestore";
            try { report.addProperty(recoveryKey + "Returned", ShaderLodSettings.apply(initial.enabled(),initial.preset())); }
            catch(Exception recovery) { report.addProperty(recoveryKey + "Error",recovery.toString()); }
        }
        done = true;
        report.addProperty("status", "failed");
        report.addProperty("failure", failure.toString());
        report.addProperty("failedStage", stages[stage]);
        observeWaiting(client, "failureObservation");
        write(client);
        LOGGER.error("NeoForge roundtrip failed; client remains open; receipt={}", evidence(client), failure);
    }
}
