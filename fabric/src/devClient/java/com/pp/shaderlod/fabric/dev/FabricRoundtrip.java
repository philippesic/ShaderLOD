package com.pp.shaderlod.fabric.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pp.shaderlod.fabric.FabricLodConfig;
import com.pp.shaderlod.fabric.FabricLodSettings;
import com.pp.shaderlod.fabric.FabricLodScreen;
import net.minecraft.client.gui.components.Button;
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
    private final boolean singleWindow = System.getProperties().containsKey("shaderlod.dev.fabricRendererSinglePhase");
    private final String singlePhase = System.getProperty("shaderlod.dev.fabricRendererSinglePhase", "");
    private final boolean nativeCostControl = Boolean.getBoolean("shaderlod.dev.fabricNativeCostControl");
    private final boolean rendererSpeed = Boolean.getBoolean("shaderlod.dev.fabricRendererSpeedProbe");
    private final boolean rendererSource = Boolean.getBoolean("shaderlod.dev.fabricRendererSourceProbe");
    private final boolean originalSource = Boolean.getBoolean("shaderlod.dev.fabricOriginalSourceProbe");
    private final String originalSourceFamily = System.getProperty("shaderlod.dev.fabricOriginalSourceFamily", "");
    private final String rendererResearchFamily = System.getProperty("shaderlod.dev.fabricRendererResearchFamily", "");
    private boolean rendererFamilyCertified;
    private boolean originalSourceCertified;
    private final long sourceProbeStartedMillis = System.currentTimeMillis();
    private long measuredSince, measuredFrames;
    private long[] measuredCommands;
    private final boolean dhRequested = Boolean.getBoolean("shaderlod.dev.fabricRoundtripDh");
    private final String[] STAGES = singleWindow ? new String[]{singlePhase} : nativeCostControl ? new String[]{"original", "native-off-2", "native-off-3"} : originalSource ? new String[]{"original"} : dhRequested
            ? new String[]{"original", "renderer-disabled", "renderer-return", "on", "off"}
            : new String[]{"original", "on", "off"};
    private final int onStage = singleWindow ? (FabricNativeCostWindow.phaseEnabled(singlePhase) ? 0 : -1) : nativeCostControl || originalSource ? -1 : dhRequested ? 3 : 1;
    private final int offStage = singleWindow || originalSource ? 0 : dhRequested ? 4 : 2;
    private Object ownedDhMode;
    private JsonObject initialPose;
    private final String run = Long.toString(System.currentTimeMillis());
    private final String original = System.getProperty("shaderlod.dev.fabricRoundtripOriginal", "");
    private final JsonObject report = new JsonObject();
    private final long started = System.nanoTime();
    private Object world;
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

    static boolean requested() { return Boolean.getBoolean("shaderlod.dev.fabricRoundtrip")
            || Boolean.getBoolean("shaderlod.dev.fabricOriginalSourceProbe"); }

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
        if (probe.singleWindow && !FabricNativeCostWindow.singleFlagsAllow(probe.singlePhase,
                probe.rendererResearchFamily, probe.rendererSpeed, probe.rendererSource, probe.originalSource,
                probe.dhRequested, Boolean.getBoolean("shaderlod.dev.fabricRoundtripUi"),
                Boolean.getBoolean("shaderlod.dev.fabricRoundtrip"), probe.nativeCostControl)) {
            LOGGER.error("Single BSL window requires explicit phase/speed and no mixed controls/source/UI/DH"); return;
        }
        if (probe.nativeCostControl && !FabricNativeCostWindow.flagsAllow(probe.rendererResearchFamily,
                probe.rendererSpeed, probe.rendererSource, probe.originalSource, probe.dhRequested,
                Boolean.getBoolean("shaderlod.dev.fabricRoundtripUi"),
                Boolean.getBoolean("shaderlod.dev.fabricRoundtrip"))) {
            LOGGER.error("Native cost control requires explicit BSL speed roundtrip without source/UI/DH"); return;
        }
        boolean namedRenderer = !probe.rendererResearchFamily.isEmpty();
        if ((probe.rendererSpeed || probe.rendererSource) && !namedRenderer
                && Boolean.getBoolean(com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES)) {
            LOGGER.error("Renderer research requires an explicit known family");
            return;
        }
        if (namedRenderer && !FabricOriginalSourceChecks.rendererFlagsAllow(probe.rendererResearchFamily,
                probe.rendererSpeed, probe.rendererSource, probe.originalSource, probe.dhRequested,
                Boolean.getBoolean("shaderlod.dev.fabricRoundtripUi"),
                Boolean.getBoolean(com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.FABRIC_CANDIDATE),
                Boolean.getBoolean(com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES))) {
            LOGGER.error("Named BSL renderer requires exactly one source/speed probe and explicit research/candidate opt-ins");
            return;
        }
        if (probe.originalSource && !FabricOriginalSourceChecks.flagsAllow(probe.originalSourceFamily,
                probe.rendererSpeed, probe.rendererSource, probe.dhRequested,
                Boolean.getBoolean("shaderlod.dev.fabricRoundtripUi"),
                Boolean.getBoolean(com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.FABRIC_CANDIDATE),
                Boolean.getBoolean(com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES))) {
            LOGGER.error("Original source capture requires named BSL, no renderer/research, speed, roundtrip-source, DH or UI flags");
            return;
        }
        if ((probe.rendererSpeed || probe.rendererSource) && (probe.dhRequested || Boolean.getBoolean("shaderlod.dev.fabricRoundtripUi"))
                || probe.rendererSpeed && probe.rendererSource) {
            LOGGER.error("Renderer speed probe requires the no-DH, no-UI three-stage round trip");
            return;
        }
        if (probe.original.isBlank() || !Path.of(probe.original).getFileName().toString().equals(probe.original)
                || !probe.original.endsWith(".zip")) {
            LOGGER.error("Fabric roundtrip disabled: explicit original ZIP basename required");
            return;
        }
        if (namedRenderer) {
            Path archive = Iris.getShaderpacksDirectory().resolve(probe.original);
            if (!FabricOriginalSourceChecks.sourceMatches(probe.rendererResearchFamily, archive,
                    com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions())) {
                LOGGER.error("Named BSL renderer original/certificate mismatch");
                return;
            }
        }
        probe.report.addProperty("singleWindow", probe.singleWindow);
        if (probe.singleWindow) {
            probe.report.addProperty("singleWindowPhase", probe.singlePhase);
            probe.report.addProperty("preloadedEnabled", FabricNativeCostWindow.phaseEnabled(probe.singlePhase));
            probe.report.addProperty("productionApplyOrReloadPerformed", false);
        }
        probe.report.addProperty("nativeCostControl", probe.nativeCostControl);
        if (probe.nativeCostControl) {
            probe.report.addProperty("nativeControlReloads", false);
            probe.report.addProperty("candidateSpeedClaim", false);
        }
        probe.report.addProperty("run", probe.run);
        probe.report.addProperty("originalPack", probe.original);
        probe.report.addProperty("dhCounterfactualRequested", probe.dhRequested);
        probe.report.addProperty("linkedUniformReadbackPerformed", false);
        probe.report.addProperty("gpuTimingPerformed", false);
        probe.report.addProperty("rendererSpeedProbe", probe.rendererSpeed);
        probe.report.addProperty("rendererSourceProbe", probe.rendererSource);
        probe.report.addProperty("originalSourceProbe", probe.originalSource);
        probe.report.addProperty("rendererResearchFamily", probe.rendererResearchFamily);
        if (probe.originalSource) {
            probe.report.addProperty("originalSourceFamily", probe.originalSourceFamily);
            try {
                Path directory = Iris.getShaderpacksDirectory().toAbsolutePath().normalize();
                Path archive = directory.resolve(probe.original).normalize();
                if (!archive.startsWith(directory) || archive.equals(directory)
                        || !FabricOriginalSourceChecks.sourceMatches(probe.originalSourceFamily, archive,
                                com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions()))
                    throw new IllegalStateException("Original source input is not the certified BSL archive/resources");
                FabricOriginalSourceChecks.requireFreshExportDirectory(Minecraft.getInstance().gameDirectory.toPath());
            }
            catch (Exception failure) { probe.fail(Minecraft.getInstance(), failure); return; }
        }
        probe.report.addProperty("status", "waiting-for-original");
        ClientTickEvents.END_CLIENT_TICK.register(probe::tick);
        LOGGER.info("Fabric roundtrip enabled: original/On/Off, 30 s settle each, client remains open");
    }

    private void tick(Minecraft client) {
        if (done) return;
        try {
            if ((nativeCostControl || singleWindow) && FabricNativeCostFrames.WINDOW.invalidFrames() != 0)
                throw new IllegalStateException("Native cost control measured frame violated pacing/focus/native contract");
            if (System.nanoTime() - started > 300_000_000_000L) throw new IllegalStateException("300 s probe deadline exceeded");
            if (client.level == null || client.player == null) { resetWindow(); return; }
            if (world == null) {
                world = client.level;
                initial = FabricLodConfig.snapshot();
                if (initial.enabled() != (singleWindow && FabricNativeCostWindow.phaseEnabled(singlePhase)))
                    throw new IllegalStateException("Preloaded core does not match explicit window phase/baseline");
                if (singleWindow && (initial.preset() != com.pp.shaderlod.LodPreset.PERFORMANCE || initial.debug()))
                    throw new IllegalStateException("Single BSL window requires preloaded Performance/debugfalse");
                originalHash = hash(Iris.getShaderpacksDirectory().resolve(original));
                if (dhRequested) {
                    if (!FabricLoader.getInstance().getModContainer("distanthorizons").orElseThrow()
                            .getMetadata().getVersion().getFriendlyString().equals("3.3.3"))
                        throw new IllegalStateException("Exact DH 3.3.3 required");
                    Object mode = dhValue("renderingMode");
                    if (dhConfigMethod("getApiValue").invoke(mode) != null)
                        throw new IllegalStateException("Preexisting DH renderingMode API override; preserve it");

                }
            } else if (world != client.level) throw new IllegalStateException("World changed during roundtrip");
            if ((rendererSpeed || rendererSource || originalSource) && !report.has("startup") && IrisApi.getInstance().isShaderPackInUse()
                    && !Iris.isFallback() && Iris.getCurrentPackName().equals(original)) {
                var startup = new JsonObject();
                startup.addProperty("capturedAtUtc", Instant.now().toString());
                startup.addProperty("shaderPack", Iris.getCurrentPackName());
                startup.addProperty("shaderPackInUse", true);
                startup.addProperty("irisFallback", false);
                startup.addProperty("originalArchiveSha256", originalHash);
                startup.addProperty("framebufferWidth", client.getMainRenderTarget().width);
                startup.addProperty("framebufferHeight", client.getMainRenderTarget().height);
                startup.addProperty("windowFramebufferWidth", client.getWindow().getWidth());
                startup.addProperty("windowFramebufferHeight", client.getWindow().getHeight());
                startup.addProperty("fullscreen", client.getWindow().isFullscreen());
                startup.addProperty("nativeMonitor", GLFW.glfwGetWindowMonitor(client.getWindow().getWindow()));
                startup.addProperty("focused", client.isWindowActive());
                startup.add("selectedResourcePacks", GSON.toJsonTree(client.getResourcePackRepository().getSelectedIds()));
                var mods = new TreeMap<String, String>();
                FabricLoader.getInstance().getAllMods().forEach(mod -> mods.put(mod.getMetadata().getId(),
                        mod.getMetadata().getVersion().getFriendlyString()));
                startup.add("loadedMods", GSON.toJsonTree(mods));
                startup.add("rendererAdmission", rendererAdmission(client));
                report.add("startup", startup);
                write(client);
            }
            if (screenshotPending) return;
            if (ui != null && !uiFinished) { captureUi(client); return; }
            if (advance) {
                advance = false;
                if (stage == offStage) {
                    if (Boolean.getBoolean("shaderlod.dev.fabricRoundtripUi") && !uiFinished) {
                        ui = new FabricLodScreen(null);
                        client.setScreen(ui);
                        button("LOD: Off").onPress();
                        button("Quality").onPress();
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
                if (nativeCostControl) {
                    if (!initial.equals(FabricLodConfig.snapshot()))
                        throw new IllegalStateException("Native control core changed between Off windows");
                    resetWindow(); stableSignature = null;
                    write(client); return; // Native-only phase change: no Apply, On or reload.
                }
                if (dhRequested && stage == 1) {
                    setDhDisabled();
                    resetWindow(); stableSignature = null;
                    write(client); return;
                }
                if (dhRequested && stage == 2) {
                    clearOwnedDhOverride();
                    resetWindow(); stableSignature = null;
                    write(client); return;
                }
                boolean enabled = stage == onStage;
                long before = System.nanoTime();
                boolean applied = FabricLodSettings.apply(enabled, initial.preset());
                report.addProperty(STAGES[stage] + "ApplyReturned", applied);
                report.addProperty(STAGES[stage] + "ApplyElapsedSeconds", (System.nanoTime() - before) / 1_000_000_000.0);
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
            if (GLFW.glfwGetWindowMonitor(window.getWindow()) == 0 || window.getWidth() <= 0
                    || window.getHeight() <= 0 || target.width != window.getWidth()
                    || target.height != window.getHeight()) { resetWindow(); return; }
            String selected = Iris.getIrisConfig().getShaderPackName().orElse("");
            if (!selected.equals(Iris.getCurrentPackName())) { resetWindow(); return; }
            if (rendererSpeed || rendererSource || originalSource ? !selected.equals(original)
                    : stage == onStage ? selected.equals(original) : !selected.equals(original))
                throw new IllegalStateException("Unexpected selected pack at " + STAGES[stage] + ": " + selected);
            if (FabricLodConfig.snapshot().enabled() != (stage == onStage))
                throw new IllegalStateException("Unexpected LOD enabled state at " + STAGES[stage]);
            if (dhRequested) {
                if (!dhState().get("worldLoaded").getAsBoolean()) { settledSince = 0; return; }
                requireDh(client, stage == 1 ? "DISABLED" : "DEFAULT");
            }
            Map<String, String> options = loadedOptions();
            String signature = selected + options.toString() + ":" + target.width + "x" + target.height
                    + ":" + client.options.enableVsync().get() + ":" + client.options.framerateLimit().get();
            if (settledSince == 0 || !signature.equals(stableSignature)) {
                stableSignature = signature;
                resetWindow();
                settledSince = System.nanoTime();
                return;
            }
            if (System.nanoTime() - settledSince < (rendererSpeed ? 40_000_000_000L : SETTLE)) return;
            if (!rendererResearchFamily.isEmpty()) {
                if (!FabricOriginalSourceChecks.optionsMatch(rendererResearchFamily, options))
                    throw new IllegalStateException("Named BSL renderer requires exact native384 at every stage");
                if (!rendererFamilyCertified) {
                    if (!FabricOriginalSourceChecks.sourceMatches(rendererResearchFamily,
                            Iris.getShaderpacksDirectory().resolve(original), options))
                        throw new IllegalStateException("Named BSL renderer source/certificate mismatch");
                    rendererFamilyCertified = true;
                }
            }
            if (originalSource) validateOriginalSource(client, options);
            if (rendererSpeed) {
                boolean active = com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled();
                if (active != (stage == onStage)) {
                    report.add("rendererAdmissionAtFailure", rendererAdmission(client));
                    throw new IllegalStateException("Unexpected renderer gate at " + STAGES[stage]);
                }
                if (measuredSince == 0) {
                    measuredSince = System.nanoTime();
                    measuredFrames = FabricRendererFrames.completed();
                    measuredCommands = FabricRendererFrames.commands();
                    if (nativeCostControl || singleWindow) FabricNativeCostFrames.WINDOW.begin(stage == onStage);
                    if (singleWindow) { originalOptions = Map.copyOf(options); initialPose = pose(client); }
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
            if (stage == 0 && !singleWindow) { originalOptions = Map.copyOf(options); initialPose = pose(client); }
            if ((rendererSpeed || rendererSource) && (!originalOptions.equals(options) || !initialPose.equals(pose(client))))
                throw new IllegalStateException("Renderer speed probe changed native options/pose/weather");
            if (rendererSpeed && stage > 0 && (state.get("vsync").getAsBoolean()
                    != report.getAsJsonObject("original").get("vsync").getAsBoolean()
                    || state.get("framerateLimit").getAsInt()
                    != report.getAsJsonObject("original").get("framerateLimit").getAsInt()
                    || state.get("vanillaRenderDistance").getAsInt()
                    != report.getAsJsonObject("original").get("vanillaRenderDistance").getAsInt()))
                throw new IllegalStateException("Renderer speed probe changed framerate/render-distance settings");
            if (dhRequested) {
                state.addProperty("originalPoseWeatherUnchanged", initialPose.equals(pose(client)));
                if (!initialPose.equals(pose(client))) {
                    report.add("expectedPoseAtFailure",initialPose);
                    report.add("actualPoseAtFailure",pose(client));
                    throw new IllegalStateException("DH pose/weather changed");
                }
                if (stage < onStage && (!originalOptions.equals(options) || !initial.equals(FabricLodConfig.snapshot())))
                    throw new IllegalStateException("DH counterfactual changed native shader options/core");
                state.addProperty("counterfactualNativeOptionsCoreUnchanged", stage < onStage);
            }
            if (singleWindow) {
                state.addProperty("preloadedLoadedOptionsUnchanged", originalOptions.equals(options));
                state.addProperty("preloadedCoreConfigUnchanged", initial.equals(FabricLodConfig.snapshot()));
                if (!initial.equals(FabricLodConfig.snapshot())) throw new IllegalStateException("Single window changed preloaded core");
            }
            if (stage == offStage && !singleWindow) {
                state.addProperty("originalLoadedOptionsRestored", originalOptions.equals(options));
                state.addProperty("originalCoreConfigRestored", initial.equals(FabricLodConfig.snapshot()));
            }
            report.add(STAGES[stage], state);
            report.addProperty("status", "capturing-" + STAGES[stage]);
            String name = "shaderlod-fabric-roundtrip-" + run + "-" + STAGES[stage] + ".png";
            Path screenshot = client.gameDirectory.toPath().resolve("screenshots").resolve(name);
            state.addProperty("screenshot", screenshot.toAbsolutePath().toString());
            state.addProperty("screenshotStatus", "requested");
            screenshotPending = true;
            write(client);
            Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> client.execute(() -> {
                screenshotPending = false;
                state.addProperty("screenshotMessage", message.getString());
                state.addProperty("screenshotStatus", Files.isRegularFile(screenshot) ? "saved" : "failed");
                write(client);
                if (!Files.isRegularFile(screenshot)) fail(client, new IllegalStateException("Screenshot failed"));
                else if (!singleWindow && stage == offStage && (!state.get("originalLoadedOptionsRestored").getAsBoolean()
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
        if (!client.isWindowActive() || !client.getWindow().isFullscreen()) return;
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
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> client.execute(() -> {
            screenshotPending = false;
            state.addProperty("screenshotMessage", message.getString());
            state.addProperty("screenshotStatus", Files.isRegularFile(screenshot) ? "saved" : "failed");
            try {
                if (!Files.isRegularFile(screenshot)) throw new IllegalStateException("Settings screenshot failed");
                if (client.screen != ui) throw new IllegalStateException("Settings screen changed before Cancel");
                button("Cancel").onPress();
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
        state.addProperty("stage", STAGES[stage]);
        state.addProperty("stableWorldSeconds", (System.nanoTime() - settledSince) / 1_000_000_000.0);
        if (rendererSpeed) {
            long frames = FabricRendererFrames.completed() - measuredFrames;
            double seconds = (System.nanoTime() - measuredSince) / 1_000_000_000.0;
            if (measuredSince == 0 || frames <= 0) throw new IllegalStateException("Missing completed world render window");
            state.addProperty("renderFrames", frames);
            state.addProperty("elapsedSeconds", seconds);
            state.addProperty("meanElapsedFrameMs", 1000.0 * seconds / frames);
            state.addProperty("rendererEligible", com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());
            long[] commands = FabricRendererFrames.commands();
            state.addProperty("compactorCalls", commands[0] - measuredCommands[0]);
            state.addProperty("inputCommands", commands[1] - measuredCommands[1]);
            state.addProperty("outputCommands", commands[2] - measuredCommands[2]);
            state.addProperty("reducedBatches", commands[3] - measuredCommands[3]);
            state.addProperty("commandObserverEnabled", true);
            if (nativeCostControl || singleWindow) {
                var observed = FabricNativeCostFrames.WINDOW;
                state.addProperty("nativeCostControl", nativeCostControl);
                state.addProperty("expectedEnabledForWindow", stage == onStage);
                state.addProperty("nativeControlReloads", false);
                state.addProperty("rateObservedFrames", observed.frames());
                state.addProperty("invalidMeasuredFrames", observed.invalidFrames());
                state.addProperty("windowMinEffectiveFramerateLimit", observed.minEffective());
                state.addProperty("windowMaxEffectiveFramerateLimit", observed.maxEffective());
                state.addProperty("windowMinStoredFramerateLimit", observed.minStored());
                state.addProperty("windowMaxStoredFramerateLimit", observed.maxStored());
                state.addProperty("frameRateFocusViewportNativeContractValid", observed.invalidFrames() == 0);
                if (observed.frames() != frames || observed.invalidFrames() != 0
                        || !initial.equals(FabricLodConfig.snapshot()))
                    throw new IllegalStateException("Native control frame coverage/rate/core mismatch");
                observed.end();
            }
            if (stage == onStage ? commands[3] <= measuredCommands[3]
                    || commands[1] - measuredCommands[1] <= commands[2] - measuredCommands[2]
                    : commands[0] != measuredCommands[0])
                throw new IllegalStateException("Renderer command reduction/native restoration not observed");
        }
        if (rendererSource) {
            boolean active = com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled();
            state.addProperty("rendererEligible", active);
            state.addProperty("sourceExportOnly", true);
            if (active != (stage == onStage)) throw new IllegalStateException("Unexpected renderer source admission at " + STAGES[stage]);
        }
        if (originalSource) {
            validateOriginalSource(client, options);
            state.addProperty("sourceExportOnly", true);
            state.addProperty("originalSourceFamily", originalSourceFamily);
            state.addProperty("rendererEligible", false);
            state.addProperty("settingsApplyPerformed", false);
            state.addProperty("compilerExportEvidenceOnly", true);
            state.add("terrainCompilerExports", GSON.toJsonTree(FabricOriginalSourceChecks.terrainExports(
                    client.gameDirectory.toPath(), sourceProbeStartedMillis)));
            var pipeline = (net.irisshaders.iris.pipeline.IrisRenderingPipeline) Iris.getPipelineManager().getPipelineNullable();
            var programs = pipeline.getSodiumPrograms();
            state.addProperty("solidProgramId", programs.getProgram(net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.SOLID).handle());
            state.addProperty("cutoutProgramId", programs.getProgram(net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.CUTOUT).handle());
            state.addProperty("programIdsAreDrawOrAttachedSourceEvidence", false);
        }
        state.addProperty("irisSelectedPack", Iris.getIrisConfig().getShaderPackName().orElse(""));
        state.addProperty("irisLoadedPack", Iris.getCurrentPackName());
        state.addProperty("shaderPackInUse", IrisApi.getInstance().isShaderPackInUse());
        state.addProperty("irisFallback", Iris.isFallback());
        // Exact 1.21.1 pinned Iris path is OpenGL; queried only in its current render context.
        state.addProperty("observedBackend", "OpenGL");
        state.addProperty("glVendor", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR));
        state.addProperty("glRenderer", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
        state.addProperty("glVersion", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION));
        state.addProperty("vanillaRenderDistance", client.options.renderDistance().get());
        state.addProperty("vsync", client.options.enableVsync().get());
        state.addProperty("framerateLimit", client.options.framerateLimit().get());
        if (dhRequested) state.add("actualDhApi", dhState());
        state.add("loadedIrisOptions", GSON.toJsonTree(options));
        state.add("selectedResourcePacks", GSON.toJsonTree(client.getResourcePackRepository().getSelectedIds()));
        state.add("requestedCoreConfig", GSON.toJsonTree(FabricLodConfig.snapshot()));
        state.addProperty("originalArchiveSha256", hash(Iris.getShaderpacksDirectory().resolve(original)));
        var target = client.getMainRenderTarget();
        var window = client.getWindow();
        state.addProperty("framebufferWidth", target.width);
        state.addProperty("framebufferHeight", target.height);
        state.addProperty("windowFramebufferWidth", window.getWidth());
        state.addProperty("windowFramebufferHeight", window.getHeight());
        state.addProperty("fullscreen", window.isFullscreen());
        state.addProperty("nativeMonitor", GLFW.glfwGetWindowMonitor(window.getWindow()));
        state.addProperty("focused", client.isWindowActive());
        state.addProperty("dimension", client.level.dimension().location().toString());
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

    private void resetWindow() { settledSince = 0; measuredSince = 0; measuredFrames = 0; if (nativeCostControl || singleWindow) FabricNativeCostFrames.WINDOW.end(); }

    private void validateOriginalSource(Minecraft client, Map<String, String> options) throws Exception {
        var config = FabricLodConfig.snapshot();
        if (!FabricLodConfig.loaded() || !config.equals(initial) || config.enabled() || config.debug()
                || config.preset() != com.pp.shaderlod.LodPreset.PERFORMANCE)
            throw new IllegalStateException("Original source capture requires unchanged loaded Off/Performance core config");
        if (!net.irisshaders.iris.platform.IrisPlatformHelpers.getInstance().getGameDir().toAbsolutePath().normalize()
                .equals(client.gameDirectory.toPath().toAbsolutePath().normalize()))
            throw new IllegalStateException("Iris compiler export game directory differs from actual client profile");
        if (!Iris.getIrisConfig().areDebugOptionsEnabled() || !Iris.getShaderPackOptionQueue().isEmpty())
            throw new IllegalStateException("Prepare debug compiler exports and empty native option queue before launch");
        if (client.level.dimension() != net.minecraft.world.level.Level.OVERWORLD
                || client.getMainRenderTarget().width != 1920 || client.getMainRenderTarget().height != 1200
                || client.options.renderDistance().get() != 24 || client.options.enableVsync().get()
                || client.options.framerateLimit().get() != 260)
            throw new IllegalStateException("Original source capture requires Overworld/native1920x1200/RD24/vsyncOff/260");
        var admission = rendererAdmission(client);
        for (String key : java.util.List.of("resourcesMatch", "stackMatches", "exactApi", "exactPipeline",
                "exactSodiumPrograms", "exactMutableValues", "capabilitiesMatch"))
            if (!admission.has(key) || !admission.get(key).getAsBoolean())
                throw new IllegalStateException("Original source tuple admission unavailable: " + key);
        if (com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled())
            throw new IllegalStateException("Original source capture must leave renderer native");
        if (!FabricOriginalSourceChecks.optionsMatch(originalSourceFamily, options))
            throw new IllegalStateException("Original source capture requires exact certified BSL native384 map");
        if (!originalSourceCertified) {
            var archive = Iris.getShaderpacksDirectory().resolve(original);
            if (!FabricOriginalSourceChecks.sourceMatches(originalSourceFamily, archive, options))
                throw new IllegalStateException("Original source capture archive/certificate mismatch");
            originalSourceCertified = true;
        }
    }

    /** Untimed actual gate observations, before windows or only after admission failure. */
    private static JsonObject rendererAdmission(Minecraft client) {
        var state = new JsonObject();
        try {
            var config = FabricLodConfig.snapshot();
            state.addProperty("candidateOptIn", Boolean.getBoolean(com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.FABRIC_CANDIDATE));
            state.addProperty("configLoaded", FabricLodConfig.loaded());
            state.add("config", GSON.toJsonTree(config));
            state.addProperty("lifecycleSuspended", com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
            state.addProperty("overworldIdentity", client.level != null && client.level.dimension() == net.minecraft.world.level.Level.OVERWORLD);
            state.addProperty("resourcesMatch", com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.resourcesMatch(client.getResourcePackRepository().getSelectedIds()));
            var mods = new TreeMap<String, String>();
            FabricLoader.getInstance().getAllMods().forEach(mod -> mods.put(mod.getMetadata().getId(), mod.getMetadata().getVersion().getFriendlyString()));
            state.addProperty("stackMatches", com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.stackMatches(mods));
            state.addProperty("exactApi", com.pp.shaderlod.integration.makeupquad.MakeUpQuadApiPins.exactApi());
            var pipeline = Iris.getPipelineManager().getPipelineNullable();
            state.addProperty("pipelineClass", pipeline == null ? "absent" : pipeline.getClass().getName());
            boolean exactPipeline = pipeline != null && pipeline.getClass() == net.irisshaders.iris.pipeline.IrisRenderingPipeline.class;
            state.addProperty("exactPipeline", exactPipeline);
            if (exactPipeline) state.addProperty("exactSodiumPrograms", ((net.irisshaders.iris.pipeline.IrisRenderingPipeline)pipeline).getSodiumPrograms().getClass() == net.irisshaders.iris.pipeline.programs.SodiumPrograms.class);
            var values = Iris.getCurrentPack().orElseThrow().getShaderPackOptions().getOptionValues();
            state.addProperty("optionValuesClass", values.getClass().getName());
            state.addProperty("exactMutableValues", values.getClass() == net.irisshaders.iris.shaderpack.option.values.MutableOptionValues.class);
            var caps = org.lwjgl.opengl.GL.getCapabilities();
            state.addProperty("capabilitiesMatch", com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.capabilitiesAllow(org.lwjgl.system.Pointer.POINTER_SIZE, caps.OpenGL41, caps.glMultiDrawElementsBaseVertex));
            var archive = Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName());
            var options = loadedOptions();
            state.addProperty("makeUpSourceOptionsEligible", com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.validate(archive, options).eligible());
            state.addProperty("sildurSourceOptionsEligible", com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.validate(archive, options).eligible());
            state.addProperty("bslSourceOptionsEligible", com.pp.shaderlod.integration.bslquad.BslQuadEligibility.validate(archive, options).eligible());
            state.addProperty("rendererResearchOptIn", Boolean.getBoolean(com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES));
            state.addProperty("namedRendererResearchFamily", System.getProperty("shaderlod.dev.fabricRendererResearchFamily", ""));
        } catch (Exception | LinkageError failure) { state.addProperty("unavailable", failure.toString()); }
        return state;
    }

    private static java.lang.reflect.Method dhConfigMethod(String name) throws Exception {
        return Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue").getMethod(name);
    }
    private static Object dhValue(String method) throws Exception {
        Object configs = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed").getField("configs").get(null);
        if (configs == null) throw new IllegalStateException("DH API configs unavailable");
        Object graphics = Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig")
                .getMethod("graphics").invoke(configs);
        return Class.forName("com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig")
                .getMethod(method).invoke(graphics);
    }
    private static JsonObject dhState() throws Exception {
        JsonObject state = new JsonObject();
        Object world = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed").getField("worldProxy").get(null);
        if (world == null) throw new IllegalStateException("DH world API unavailable");
        state.addProperty("worldLoaded", (Boolean) Class.forName("com.seibel.distanthorizons.api.interfaces.world.IDhApiWorldProxy")
                .getMethod("worldLoaded").invoke(world));
        state.addProperty("rendererMode", ((Enum<?>) dhConfigMethod("getValue").invoke(dhValue("renderingMode"))).name());
        state.addProperty("baseRendererMode", ((Enum<?>) dhConfigMethod("getTrueValue").invoke(dhValue("renderingMode"))).name());
        state.addProperty("renderingEnabled", (Boolean) dhConfigMethod("getValue").invoke(dhValue("renderingEnabled")));
        state.addProperty("radiusChunks", (Integer) dhConfigMethod("getValue").invoke(dhValue("chunkRenderDistance")));
        return state;
    }
    private static void requireDh(Minecraft client, String mode) throws Exception {
        JsonObject state = dhState();
        if (!state.get("worldLoaded").getAsBoolean() || state.get("renderingEnabled").getAsBoolean() != !mode.equals("DISABLED")
                || !state.get("rendererMode").getAsString().equals(mode)
                || !state.get("baseRendererMode").getAsString().equals(mode)
                || state.get("radiusChunks").getAsInt() != 128 || client.options.renderDistance().get() != 12)
            throw new IllegalStateException("DH counterfactual invariant: " + state);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void setDhDisabled() throws Exception {
        Object value = dhValue("renderingMode");
        if (dhConfigMethod("getApiValue").invoke(value) != null)
            throw new IllegalStateException("DH API override appeared before owned toggle");
        Object disabled = Enum.valueOf((Class) Class.forName("com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode"), "DISABLED");
        boolean changed = (Boolean) Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue")
                .getMethod("setValue", Object.class, String.class).invoke(value, disabled, "ShaderLOD explicit dev DH counterfactual");
        if (!changed) throw new IllegalStateException("DH rejected renderer DISABLED API override");
        ownedDhMode = value;
    }
    private void clearOwnedDhOverride() throws Exception {
        if (ownedDhMode == null) return;
        Object actual = dhConfigMethod("getApiValue").invoke(ownedDhMode);
        if (!(actual instanceof Enum<?> mode) || !mode.name().equals("DISABLED"))
            throw new IllegalStateException("Owned DH API override changed; refusing to clear another value");
        // Pinned DH QuickRenderToggleConfigEventHandler mirrors the effective mode into its
        // underlying UI config via reciprocal listeners. Restore our known DEFAULT baseline
        // before clearing only our API override; clear alone would leave the base DISABLED.
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object restored = Enum.valueOf((Class) Class.forName("com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode"), "DEFAULT");
        if (!(Boolean) Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue")
                .getMethod("setValue", Object.class, String.class)
                .invoke(ownedDhMode, restored, "ShaderLOD explicit dev DH counterfactual restore"))
            throw new IllegalStateException("Could not restore owned DH mode DEFAULT");
        if (!(Boolean) dhConfigMethod("clearValue").invoke(ownedDhMode))
            throw new IllegalStateException("Could not clear owned DH API override");
        ownedDhMode = null;
    }
    private static JsonObject pose(Minecraft client) {
        JsonObject pose = new JsonObject();
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
    private void fail(Minecraft client, Exception failure) {
        done = true;
        if (nativeCostControl || singleWindow) {
            report.addProperty("invalidMeasuredFrames", FabricNativeCostFrames.WINDOW.invalidFrames());
            report.addProperty("rateObservedFramesAtFailure", FabricNativeCostFrames.WINDOW.frames());
            report.addProperty("firstInvalidNativeFrame", FabricNativeCostFrames.WINDOW.firstInvalid());
            FabricNativeCostFrames.WINDOW.end();
        }
        try { clearOwnedDhOverride(); report.addProperty("ownedDhOverrideClearedOnFailure", true); }
        catch (Exception clearFailure) { report.addProperty("dhOverrideCleanupFailure", clearFailure.toString()); }
        report.addProperty("status", "failed");
        report.addProperty("failure", failure.toString());
        report.addProperty("failedStage", STAGES[stage]);
        write(client);
        LOGGER.error("Fabric roundtrip failed; client remains open; receipt={}", evidence(client), failure);
    }
}
