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
    private final boolean rendererSpeed = Boolean.getBoolean(PortRendererFrames.PROPERTY);
    private long measuredSince;
    private long[] measuredProof;
    private JsonObject initialPose;
    private boolean rendererProfileValidated;
    private boolean glfwStartupRequested,glfwStartupAccepted;
    private long glfwStartupAt;

    private final String nativeFamilyMode=System.getProperty("shaderlod.dev.portNativeFamily","");
    private final boolean makeupVolume="makeup-volume".equals(nativeFamilyMode);
    private final boolean bslFallback="bsl-fallback".equals(nativeFamilyMode);
    private final boolean bslSource = Boolean.getBoolean("shaderlod.dev.portBslOriginalSource");
    private final long sourceProbeStartedMillis=System.currentTimeMillis();
    private final boolean qualityRoundtrip = Boolean.getBoolean("shaderlod.dev.portQualityRoundtrip");
    private final String[] stages = bslFallback || makeupVolume ? new String[]{"original","on","quality","off"} : bslSource ? new String[]{"original"} : qualityRoundtrip
            ? new String[]{"original", "quality", "native-info-edit", "native-info-restored", "native-upscaled",
                    "native-aa-restored", "performance", "quality-return", "off"}
            : new String[]{"original", "on", "off"};
    private final String run = Long.toString(System.currentTimeMillis());
    private final String original = System.getProperty("shaderlod.dev.portRoundtripOriginal", "");
    private final JsonObject report = new JsonObject();
    private final long started = System.nanoTime();
    private final BackupPrompt backupPrompt = new BackupPrompt("fabric", run, report);
    private boolean privateInputsValidated;
    private boolean startupObserved;
    private boolean startupWorldAccepted;
    private final long startupDeadlineEpochMillis = Long.parseLong(System.getProperty("shaderlod.dev.startupDeadlineEpochMillis", "0"));
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

    static boolean requested() { return Boolean.getBoolean("shaderlod.dev.portRoundtrip") || Boolean.getBoolean("shaderlod.dev.portBslOriginalSource"); }

    static void register() {
        // Disable rather than compete with any ordinary capture/measurement session.
        for (String flag : new String[]{"fabricSmoke", "smoke", "benchmark", "benchmarkOnly",
                "measureWorkload", "measureOnly", "cameraQuality", "cameraQualityOnly",
                "pixelHistoryCapture", "pixelHistoryCaptureOnly", "physicalResolution",
                "workTrace", "spatialLayoutProbe", "pixelCoverage", "gpuTiming", "cpuLayerTiming",
                "settledConsumerUniforms"}) {
            String value = System.getProperty("shaderlod.dev." + flag, "");
            if (!value.isBlank() && !value.equalsIgnoreCase("false")) {
                LOGGER.error("Fabric roundtrip disabled: conflicting dev property {}", flag);
                return;
            }
        }
        var probe = new FabricRoundtrip();
        if (probe.rendererSpeed && (probe.qualityRoundtrip || Boolean.getBoolean("shaderlod.dev.portRoundtripDh") ||
                Boolean.getBoolean("shaderlod.dev.portRoundtripUi") || Boolean.getBoolean("shaderlod.dev.portOriginalOnly") ||
                !Boolean.getBoolean(com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.RESEARCH_OPT_IN))) {
            LOGGER.error("Renderer speed probe requires explicit candidate opt-in and plain no-DH/no-UI three-stage roundtrip");return;
        }

        if (probe.original.isBlank() || !Path.of(probe.original).getFileName().toString().equals(probe.original)
                || !probe.original.endsWith(".zip")) {
            LOGGER.error("Fabric roundtrip disabled: explicit original ZIP basename required");
            return;
        }
        if(!probe.nativeFamilyMode.isBlank()&&!probe.bslFallback&&!probe.makeupVolume){LOGGER.error("Unknown native family control selector for Fabric26.2");return;}
        if(probe.makeupVolume){
            try{
                if(probe.bslSource||probe.rendererSpeed||probe.qualityRoundtrip||Boolean.getBoolean("shaderlod.dev.portOriginalOnly")||Boolean.getBoolean("shaderlod.dev.portRoundtripDh"))throw new IllegalStateException("MakeUp controls conflict with source/speed/Quality/originalOnly/DH modes");
                for(String flag:new String[]{"shaderlod.experimentalRendererPort092",com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES})if(!"false".equals(System.getProperty(flag,"false")))throw new IllegalStateException("MakeUp controls conflict with "+flag);
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var archive=dir.resolve(probe.original).normalize();
                if(!archive.startsWith(dir)||archive.equals(dir)||Files.isSymbolicLink(archive)||!com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.validate(archive,com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("Certified original MakeUp9.5g/native100/resources required");
            }catch(Exception failure){LOGGER.error("MakeUp26.2 volume controls refused before registration",failure);return;}
            probe.report.add("stageNames",GSON.toJsonTree(probe.stages));probe.report.addProperty("makeupVolumeControl",true);probe.report.addProperty("makeupVolumeMode","makeup-volume");probe.report.addProperty("productionApplyRequested",true);probe.report.addProperty("rendererWorkMeasurementRequested",false);
        }
        if(probe.bslFallback){
            try{
                if(probe.bslSource||probe.rendererSpeed||probe.qualityRoundtrip||Boolean.getBoolean("shaderlod.dev.portOriginalOnly")||Boolean.getBoolean("shaderlod.dev.portRoundtripDh"))throw new IllegalStateException("BSL controls conflict with source/speed/Quality/originalOnly/DH diagnostic modes");
                for(String flag:new String[]{"shaderlod.experimentalRendererPort092",com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES})if(!"false".equals(System.getProperty(flag,"false")))throw new IllegalStateException("BSL controls conflict with "+flag);
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var archive=dir.resolve(probe.original).normalize();
                if(!archive.startsWith(dir)||archive.equals(dir)||Files.isSymbolicLink(archive)||!com.pp.shaderlod.integration.bslquad.BslQuadEligibility.validate(archive,com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("Certified original BSL/native384 resources required");
            }catch(Exception failure){LOGGER.error("BSL26.2 fallback controls refused before registration",failure);return;}
            probe.report.add("stageNames",GSON.toJsonTree(probe.stages));probe.report.addProperty("bslFallbackControl",true);probe.report.addProperty("bslFallbackMode","bsl-fallback");probe.report.addProperty("productionApplyRequested",true);probe.report.addProperty("rendererWorkMeasurementRequested",false);
        }
        if(probe.bslSource) {
            try {
                if(!PortBslOriginalSourceChecks.propertiesAllow(System.getProperties())||probe.startupDeadlineEpochMillis<=System.currentTimeMillis())throw new IllegalStateException("BSL source-only explicit flags/deadline required");
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var archive=dir.resolve(probe.original).normalize();
                if(!archive.startsWith(dir)||archive.equals(dir)||Files.isSymbolicLink(archive)||!PortBslOriginalSourceChecks.sourceMatches(archive,com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions()))throw new IllegalStateException("Pinned BSL original/native384/proof resources required");
                PortBslOriginalSourceChecks.requireFreshExportDirectory(Minecraft.getInstance().gameDirectory.toPath());
            }catch(Exception failure){LOGGER.error("BSL26.2 original source-only registration refused",failure);return;}
            probe.report.add("stageNames",GSON.toJsonTree(probe.stages));probe.report.addProperty("originalSourceProbe",true);probe.report.addProperty("originalSourceFamily","bsl");probe.report.addProperty("productionApplyRequested",false);
            probe.report.addProperty("sourceBoundary","Fresh actual compiler exports and ShaderMap role/program observations; no draw/attached-source identity inference");
        }
        probe.report.addProperty("qualityRoundtrip", probe.qualityRoundtrip);
        probe.report.addProperty("rendererSpeedProbe", probe.rendererSpeed);
        probe.report.addProperty("completedWorldRenderSeam", "renderLevel(Lnet/minecraft/client/DeltaTracker;)V");
        probe.report.addProperty("driverEvidence", "Actual GL function entry observed separately; not GPU completion or clean timing");
        probe.report.addProperty("run", probe.run);
        probe.report.addProperty("originalPack", probe.original);
        probe.report.addProperty("minecraftTarget", "26.2");
        probe.report.addProperty("startupDeadlineEpochMillis", probe.startupDeadlineEpochMillis);
        probe.report.addProperty("backendPolicy", "observed OpenGL required before hashing or GL facts");
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
            if(rendererSpeed && !rendererProfileValidated) { validateRendererProfile(client);rendererProfileValidated=true; }
            if(rendererSpeed && PortRendererFrames.firstRejectedRate()!=null) {
                report.add("rendererRejectedRate",GSON.toJsonTree(PortRendererFrames.firstRejectedRate()));
                throw new IllegalStateException("At least one completed measured frame was rate-throttled, changed cap, unavailable or lost native focus");
            }
            if (!privateInputsValidated) { validatePrivateInputs(client); privateInputsValidated = true; }
            if (System.nanoTime() - started > 300_000_000_000L) throw new IllegalStateException("300 s probe deadline exceeded");
            if(rendererSpeed && !glfwStartupRequested && System.nanoTime()-started>=7_000_000_000L) {
                glfwStartupRequested=true;glfwStartupAt=System.nanoTime();
                GlfwStartup.configure(client,run,report,()->write(client));return;
            }
            if(rendererSpeed && !glfwStartupAccepted) {
                if(!glfwStartupRequested)return;
                if(System.nanoTime()-glfwStartupAt<3_000_000_000L)return;
                if(!GlfwStartup.eligible(client)) {
                    if(System.nanoTime()-glfwStartupAt>=20_000_000_000L)throw new IllegalStateException("Private pre-world native1920 viewport/focus request did not settle");
                    return;
                }
                glfwStartupAccepted=true;report.addProperty("preworldNativeViewportAndFocusAccepted",true);write(client);
            }
            if (!startupObserved && System.nanoTime() - started >= 10_000_000_000L) {
                startupObserved = true;
                observeWaiting(client, "startupObservation");
            }
            if (!startupWorldAccepted && startupDeadlineEpochMillis > 0 && System.currentTimeMillis() >= startupDeadlineEpochMillis)
                throw new IllegalStateException("Absolute startup deadline exceeded before eligible shader world entry");
            if (client.level == null || client.player == null) {
                resetWindow();
                if (startupObserved) backupPrompt.beforeWorld(client, () -> write(client), this::callbackAllowed, failure -> fail(client, failure));
                return;
            }
            backupPrompt.afterWorld(client, () -> write(client));
            com.pp.shaderlod.integration.RendererBackend.requireApplySupported();
            if (world == null) {
                world = client.level;
                firstWorldSince = System.nanoTime();
                initial = FabricLodConfig.snapshot();
                if (initial.enabled()) throw new IllegalStateException("Original baseline requires LOD Off before launch");
                if((bslFallback||makeupVolume)&&(initial.debug()||initial.preset()!=LodPreset.BALANCED))throw new IllegalStateException("BSL controls require Off/Balanced/debugfalse baseline");
                if(rendererSpeed && initial.preset()!=LodPreset.PERFORMANCE) throw new IllegalStateException("Renderer baseline requires Off/Performance");
                originalHash = hash(Iris.getShaderpacksDirectory().resolve(original));
                if(rendererSpeed)report.add("rendererAdmissionAtStartup",RendererAdmissionDiagnostics.observe(client));
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
                        client.gui.setScreen(ui);
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
                long before = System.nanoTime();
                boolean applied = makeupVolume ? applyMakeupVolumeStage() : bslFallback ? applyBslFallbackStage() : qualityRoundtrip ? applyQualityStage(client) : FabricLodSettings.apply(enabled, initial.preset());
                report.addProperty(stages[stage] + "ApplyReturned", applied);
                report.addProperty(stages[stage] + "ApplyElapsedSeconds", (System.nanoTime() - before) / 1_000_000_000.0);
                resetWindow();
                stableSignature = null;
                write(client);
                if (!applied) throw new IllegalStateException("Production Apply failed: " + FabricLodSettings.failureMessage());
                return; // Settle begins on a later tick after production reload returned.
            }
            if (client.gui.screen() != null || client.isPaused() || !client.isWindowActive()
                    || !client.getWindow().isFullscreen() || Iris.isFallback()
                    || !IrisApi.getInstance().isShaderPackInUse()) { resetWindow(); return; }
            var window = client.getWindow();
            var target = client.gameRenderer.mainRenderTarget();
            if (GLFW.glfwGetWindowMonitor(window.handle()) == 0 || window.getWidth() != 1920
                    || window.getHeight() != 1200 || target.width != window.getWidth()
                    || target.height != window.getHeight()) { resetWindow(); return; }
            String selected = Iris.getIrisConfig().getShaderPackName().orElse("");
            if (!selected.equals(Iris.getCurrentPackName())) { resetWindow(); return; }
            if (rendererSpeed ? !selected.equals(original) : stageGenerated() ? selected.equals(original) : !selected.equals(original))
                throw new IllegalStateException("Unexpected selected pack at " + stages[stage] + ": " + selected);
            if (FabricLodConfig.snapshot().enabled() != stageEnabled())
                throw new IllegalStateException("Unexpected LOD enabled state at " + stages[stage]);
            startupWorldAccepted = true;
            report.addProperty("startupEligibleShaderWorldEntered", true);
            if(rendererSpeed)requireRendererRate(client);
            Map<String, String> options = loadedOptions();
            if(bslSource)validateBslOriginalSource(client,options);
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
            if(makeupVolume)validateMakeupVolumeState(client,options,null);
            if(bslFallback)validateBslFallbackState(client,options,null);
            if (System.nanoTime() - settledSince < (rendererSpeed ? 40_000_000_000L : qualityRoundtrip && stage > 0 ? 3_000_000_000L : SETTLE)) return;
            if(rendererSpeed) {
                if(com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled() != stageEnabled()) {
                    report.add("rendererAdmissionAtFailure",RendererAdmissionDiagnostics.observe(client));
                    throw new IllegalStateException("Renderer admission does not match " + stages[stage]);
                }
                if(measuredSince==0) {
                    PortRendererFrames.beginMeasuredWindow(client.options.framerateLimit().get());
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
            if(makeupVolume)validateMakeupVolumeState(client,options,state);
            if(bslFallback)validateBslFallbackState(client,options,state);
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

    private void validatePrivateInputs(Minecraft client) throws Exception {
        if(rendererSpeed)return; // The same exact private profile has already passed validateRendererProfile.
        Path profile = client.gameDirectory.toPath().toAbsolutePath().normalize();
        String loader = "fabric";
        if (!profile.endsWith(Path.of("run-workloads", "port-262-runtime-r1", loader))
                || !profile.equals(profile.toRealPath()))
            throw new IllegalStateException("Probe requires the exact private 26.2 profile without symlinks");
        Path worldDirectory = profile.resolve("saves/ShaderLOD-Landscape");
        try (var paths = Files.list(profile.resolve("saves"))) {
            var worlds = paths.filter(Files::isDirectory).toList();
            if (worlds.size() != 1 || !worlds.get(0).equals(worldDirectory))
                throw new IllegalStateException("Private profile must contain only ShaderLOD-Landscape");
        }
        if (!worldDirectory.equals(worldDirectory.toRealPath())
                || !worldDirectory.resolve("level.dat").equals(worldDirectory.resolve("level.dat").toRealPath()))
            throw new IllegalStateException("Private world/level.dat must not alias another save");
    }

    private static String observedBackend() throws com.pp.shaderlod.integration.RendererBackend.Refusal {
        com.pp.shaderlod.integration.RendererBackend.requireApplySupported();
        return ((net.irisshaders.iris.mixin.GpuDeviceAccessor) com.mojang.blaze3d.systems.RenderSystem.getDevice())
                .getBackend().getDeviceInfo().backendName();
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
        Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1, message -> client.execute(() -> {
                if (done) return;
            screenshotPending = false;
            state.addProperty("screenshotMessage", message.getString());
            state.addProperty("screenshotStatus", Files.isRegularFile(screenshot) ? "saved" : "failed");
            try {
                if (!Files.isRegularFile(screenshot)) throw new IllegalStateException("Settings screenshot failed");
                if (client.gui.screen() != ui) throw new IllegalStateException("Settings screen changed before Cancel");
                button("Cancel").onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                boolean unchanged = initial.equals(FabricLodConfig.snapshot())
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
        String backend = observedBackend();
        var state = new JsonObject();
        state.addProperty("capturedAtUtc", Instant.now().toString());
        if(bslFallback||makeupVolume)state.add("mainArtifactOrigins",mainArtifactOrigins());
        state.addProperty("stage", stages[stage]);
        if(bslSource){state.add("nativeSourceAdmission",validateBslOriginalSource(client,options));
            state.add("terrainActualDrawSourceReadback",PortBslDrawSourceReadback.requireCurrentJoined());state.addProperty("actualTerrainGlDrawEntrySourceJoined",true);
            state.add("terrainCompilerExports",GSON.toJsonTree(PortBslOriginalSourceChecks.terrainExports(client.gameDirectory.toPath(),sourceProbeStartedMillis)));
            var shaderMap=((net.irisshaders.iris.pipeline.ShaderRenderingPipeline)Iris.getPipelineManager().getPipelineNullable()).getShaderMap();
            var observed=new JsonObject();for(var key:java.util.List.of(net.irisshaders.iris.pipeline.programs.ShaderKey.SODIUM_TERRAIN_SOLID,net.irisshaders.iris.pipeline.programs.ShaderKey.SODIUM_TERRAIN_CUTOUT)){
                var program=shaderMap.getShader(key);if(program==null||program.getProgramId()<=0)throw new IllegalStateException("Native terrain ShaderMap program unavailable");
                if(org.lwjgl.opengl.GL20.glGetProgrami(program.getProgramId(),org.lwjgl.opengl.GL20.GL_LINK_STATUS)==0)throw new IllegalStateException("Native terrain ShaderMap program not linked");
                var item=new JsonObject();item.addProperty("shaderKey",key.name());item.addProperty("compilerPrintName",key.getName());item.addProperty("programId",program.getProgramId());item.addProperty("programClass",program.getClass().getName());observed.add(key.name(),item);
            }state.add("terrainShaderMapObservations",observed);state.addProperty("programIdsAreDrawOrAttachedSourceEvidence",false);
        }
        state.addProperty("stableWorldSeconds", (System.nanoTime() - settledSince) / 1_000_000_000.0);
        if(rendererSpeed) {
            if(measuredSince==0 || measuredProof==null)throw new IllegalStateException("No completed-world render measurement window");
            long[] proof=PortRendererFrames.snapshot();long frames=proof[0]-measuredProof[0];
            double seconds=(System.nanoTime()-measuredSince)/1_000_000_000.0;
            if(frames<=0)throw new IllegalStateException("Completed-world render counter absent");
            PortRendererFrames.stopMeasuredWindow();
            state.addProperty("rejectedMeasuredFrames",proof[7]-measuredProof[7]);
            state.addProperty("expectedConfiguredFramerateLimit",PortRendererFrames.expectedLimit());
            state.addProperty("rateObservedFrames",PortRendererFrames.rateObservedFrames());
            state.addProperty("rateUnavailableFrames",PortRendererFrames.rateUnavailableFrames());
            state.addProperty("minimumConfiguredFramerateLimit",PortRendererFrames.minConfigured());
            state.addProperty("maximumConfiguredFramerateLimit",PortRendererFrames.maxConfigured());
            state.addProperty("minimumEffectiveFramerateLimit",PortRendererFrames.minEffective());
            state.addProperty("maximumEffectiveFramerateLimit",PortRendererFrames.maxEffective());
            if(PortRendererFrames.rateObservedFrames()!=frames || PortRendererFrames.rateUnavailableFrames()!=0)
                throw new IllegalStateException("Missing required rate observations in whole completed-render window");
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
        state.add("requestedCoreConfig", GSON.toJsonTree(FabricLodConfig.snapshot()));
        state.addProperty("originalArchiveSha256", hash(Iris.getShaderpacksDirectory().resolve(original)));
        var target = client.gameRenderer.mainRenderTarget();
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
        for (String id : new String[]{"fabricloader", "iris", "sodium", "distanthorizons"})
            state.addProperty(id + "Version", FabricLoader.getInstance().getModContainer(id)
                    .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("absent"));
        return state;
    }


    private JsonObject mainArtifactOrigins(){
        var origins=new JsonObject();
        for(Class<?> type:new Class<?>[]{com.pp.shaderlod.packs.PackSelectionService.class,com.pp.shaderlod.integration.PackSelectionTransaction.class,com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.class,com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.class,com.pp.shaderlod.fabric.FabricLodSettings.class}){
            var location=type.getProtectionDomain().getCodeSource();origins.addProperty(type.getName(),location==null?"unavailable":location.getLocation().toExternalForm());
        }return origins;
    }

    private boolean applyBslFallbackStage(){
        boolean applied=FabricLodSettings.apply(stageEnabled(),stage==2?LodPreset.QUALITY:LodPreset.BALANCED);
        if(!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("BSL public Apply left pending native options");
        return applied;
    }

    private void validateBslFallbackState(Minecraft client,Map<String,String> options,JsonObject state)throws Exception{
        var cfg=FabricLodConfig.snapshot();var expectedPreset=stage==2?LodPreset.QUALITY:LodPreset.BALANCED;
        if(initial==null||initial.enabled()||initial.debug()||initial.preset()!=LodPreset.BALANCED||!FabricLodConfig.loaded()||cfg.enabled()!=stageEnabled()||cfg.preset()!=expectedPreset||cfg.debug()||!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("BSL fallback Core/preset/nativequeue mismatch");
        var expected=new TreeMap<>(com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions());
        if(stageGenerated()){
            expected.put("SHADERLOD_REFINEMENT_ADAPTER","1");
            // Exact hash-pinned helper #ifndef/#define guards also appear as Iris BooleanOptions.
            expected.put("SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true");
            expected.put("SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true");
            expected.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");
        }
        if(!expected.equals(options))throw new IllegalStateException("BSL fallback requires native384 unchanged plus owned flag and exact three parsed helper guards on generated stage");
        if(state==null)return;
        var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var originalPath=dir.resolve(original).normalize();
        if(!com.pp.shaderlod.integration.bslquad.BslQuadEligibility.validate(originalPath,com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("BSL original source certificate changed");
        if(stageGenerated()){
            String selected=Iris.getCurrentPackName();var generated=dir.resolve(selected).normalize();
            if(!generated.startsWith(dir)||generated.equals(dir)||Files.isSymbolicLink(generated))throw new IllegalStateException("Unsafe BSL generated source path");
            try(var zip=new java.util.zip.ZipFile(generated.toFile())){
                var entry=zip.getEntry("shaderlod-selection.json");if(entry==null)throw new IllegalStateException("Generated BSL selection marker absent");
                JsonObject marker;try(var in=zip.getInputStream(entry)){marker=com.google.gson.JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
                String recipeSha;try(var in=com.pp.shaderlod.packs.PackSelectionService.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/bsl-shaders.json")){
                    if(in==null)throw new IllegalStateException("Actual packaged BSL recipe absent");recipeSha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
                }
                if(marker.get("schema").getAsInt()!=1||!"bsl-shaders".equals(marker.get("adapterId").getAsString())||!original.equals(marker.get("originalFile").getAsString())||!originalHash.equals(marker.get("originalSha256").getAsString())||!recipeSha.equals(marker.get("recipeSha256").getAsString()))throw new IllegalStateException("Generated BSL source/recipe marker mismatch");
                String stateFile=marker.get("stateFile").getAsString();var statePath=dir.resolve(stateFile).normalize();
                if(!Path.of(stateFile).getFileName().toString().equals(stateFile)||!statePath.startsWith(dir)||statePath.equals(dir)||Files.isSymbolicLink(statePath))throw new IllegalStateException("Unsafe BSL selection state reference");
                var selection=com.google.gson.JsonParser.parseString(Files.readString(statePath)).getAsJsonObject();String generatedSha=hash(generated);
                if(!selection.get("active").getAsBoolean()||!selected.equals(selection.get("generatedFile").getAsString())||!generatedSha.equals(selection.get("generatedSha256").getAsString())||!original.equals(selection.get("originalFile").getAsString())||!originalHash.equals(selection.get("originalSha256").getAsString())||!recipeSha.equals(selection.get("recipeSha256").getAsString()))throw new IllegalStateException("Generated BSL archive differs from real public Apply selection state");
                state.add("generatedSelectionMarker",marker);state.addProperty("generatedArchiveSha256",generatedSha);state.addProperty("generatedSelectionStateMatched",true);
            }
        }
        state.addProperty("loadedOptionCount",options.size());
        state.add("generatedDeclaredOptions",GSON.toJsonTree(stageGenerated()?Map.of("SHADERLOD_REFINEMENT_ADAPTER","1","SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true","SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true","SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true"):Map.of()));
        state.addProperty("bslFallbackControl",true);state.addProperty("expectedPreset",expectedPreset.name());state.addProperty("underlyingNative384Unchanged",true);state.addProperty("generatedBslRecipeSelected",stageGenerated());state.addProperty("ownedRefinementFlag",stageGenerated()?"1":"absent");state.addProperty("rendererEligible",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());state.addProperty("linkedUniformReadbackPerformed",false);state.addProperty("avoidedWorkMeasured",false);
    }

    private boolean applyMakeupVolumeStage(){
        boolean applied=FabricLodSettings.apply(stageEnabled(),stage==2?LodPreset.QUALITY:LodPreset.BALANCED);
        if(!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("MakeUp public Apply left pending native options");
        return applied;
    }

    private void validateMakeupVolumeState(Minecraft client,Map<String,String> options,JsonObject state)throws Exception{
        var cfg=FabricLodConfig.snapshot();var expectedPreset=stage==2?LodPreset.QUALITY:LodPreset.BALANCED;
        if(initial==null||initial.enabled()||initial.debug()||initial.preset()!=LodPreset.BALANCED||!FabricLodConfig.loaded()||cfg.enabled()!=stageEnabled()||cfg.preset()!=expectedPreset||cfg.debug()||!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("MakeUp fallback Core/preset/nativequeue mismatch");
        var expected=new TreeMap<>(com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.nativeOptions());
        if(stageGenerated()){
            expected.put("SHADERLOD_REFINEMENT_ADAPTER","1");
            // Exact hash-pinned helper #ifndef/#define guards also appear as Iris BooleanOptions.
            expected.put("SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true");
            expected.put("SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true");
            expected.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");
        }
        if(!expected.equals(options))throw new IllegalStateException("MakeUp fallback requires native100 unchanged plus owned flag and exact three parsed helper guards on generated stage");
        if(state==null)return;
        var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var originalPath=dir.resolve(original).normalize();
        if(!com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.validate(originalPath,com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("MakeUp original source certificate changed");
        if(stageGenerated()){
            String selected=Iris.getCurrentPackName();var generated=dir.resolve(selected).normalize();
            if(!generated.startsWith(dir)||generated.equals(dir)||Files.isSymbolicLink(generated))throw new IllegalStateException("Unsafe MakeUp generated source path");
            try(var zip=new java.util.zip.ZipFile(generated.toFile())){
                var entry=zip.getEntry("shaderlod-selection.json");if(entry==null)throw new IllegalStateException("Generated MakeUp selection marker absent");
                JsonObject marker;try(var in=zip.getInputStream(entry)){marker=com.google.gson.JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
                String recipeSha;try(var in=com.pp.shaderlod.packs.PackSelectionService.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/makeup-ultra-fast-shaders.json")){
                    if(in==null)throw new IllegalStateException("Actual packaged MakeUp recipe absent");recipeSha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
                }
                if(marker.get("schema").getAsInt()!=1||!"makeup-ultra-fast-shaders".equals(marker.get("adapterId").getAsString())||!original.equals(marker.get("originalFile").getAsString())||!originalHash.equals(marker.get("originalSha256").getAsString())||!recipeSha.equals(marker.get("recipeSha256").getAsString()))throw new IllegalStateException("Generated MakeUp source/recipe marker mismatch");
                String stateFile=marker.get("stateFile").getAsString();var statePath=dir.resolve(stateFile).normalize();
                if(!Path.of(stateFile).getFileName().toString().equals(stateFile)||!statePath.startsWith(dir)||statePath.equals(dir)||Files.isSymbolicLink(statePath))throw new IllegalStateException("Unsafe MakeUp selection state reference");
                var selection=com.google.gson.JsonParser.parseString(Files.readString(statePath)).getAsJsonObject();String generatedSha=hash(generated);
                if(!selection.get("active").getAsBoolean()||!selected.equals(selection.get("generatedFile").getAsString())||!generatedSha.equals(selection.get("generatedSha256").getAsString())||!original.equals(selection.get("originalFile").getAsString())||!originalHash.equals(selection.get("originalSha256").getAsString())||!recipeSha.equals(selection.get("recipeSha256").getAsString()))throw new IllegalStateException("Generated MakeUp archive differs from real public Apply selection state");
                state.add("generatedSelectionMarker",marker);state.addProperty("generatedArchiveSha256",generatedSha);state.addProperty("generatedSelectionStateMatched",true);
            }
        }
        state.addProperty("loadedOptionCount",options.size());
        state.add("generatedDeclaredOptions",GSON.toJsonTree(stageGenerated()?Map.of("SHADERLOD_REFINEMENT_ADAPTER","1","SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true","SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true","SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true"):Map.of()));
        state.addProperty("makeupVolumeControl",true);state.addProperty("expectedPreset",expectedPreset.name());state.addProperty("underlyingNative100Unchanged",true);state.addProperty("generatedMakeupRecipeSelected",stageGenerated());state.addProperty("ownedRefinementFlag",stageGenerated()?"1":"absent");state.addProperty("rendererEligible",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());state.addProperty("linkedUniformReadbackPerformed",false);state.addProperty("avoidedWorkMeasured",false);
    }

    private boolean stageEnabled() { return bslFallback||makeupVolume ? stage==1||stage==2 : bslSource ? false : qualityRoundtrip ? stage > 0 && stage < stages.length - 1 : stage == 1; }
    private boolean stageGenerated() { return bslFallback||makeupVolume ? stage==1 : !bslSource && (qualityRoundtrip ? stage == 6 : stage == 1); }

    private JsonObject validateBslOriginalSource(Minecraft client,Map<String,String> options) throws Exception {
        if(!PortBslOriginalSourceChecks.propertiesAllow(System.getProperties()))throw new IllegalStateException("BSL source-only flags changed/conflict");
        var config=FabricLodConfig.snapshot();if(!FabricLodConfig.loaded()||initial==null||!initial.equals(config)||config.enabled()||config.debug()||config.preset()!=LodPreset.PERFORMANCE)throw new IllegalStateException("Unchanged loaded native Off/Performance/debugfalse required");
        if(!"OpenGL".equals(observedBackend())||!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())throw new IllegalStateException("Actual supported render-thread OpenGL required");
        var window=client.getWindow();var target=client.gameRenderer.mainRenderTarget();
        if(client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||window.getWidth()!=1920||window.getHeight()!=1200||target.width!=1920||target.height!=1200||!window.isFullscreen()||!client.isWindowActive()||client.options.renderDistance().get()!=24||client.options.enableVsync().get()||client.options.framerateLimit().get()!=260)throw new IllegalStateException("Native Overworld1920/RD24/vsyncOff/configured260 required");
        if(!Iris.getIrisConfig().areDebugOptionsEnabled()||!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Fresh debug exports and empty native option queue required");
        if(!net.irisshaders.iris.platform.IrisPlatformHelpers.getInstance().getGameDir().toAbsolutePath().normalize().equals(client.gameDirectory.toPath().toAbsolutePath().normalize()))throw new IllegalStateException("Iris export/Minecraft profile mismatch");
        var mods=new TreeMap<String,String>();for(var mod:FabricLoader.getInstance().getAllMods())mods.put(mod.getMetadata().getId(),mod.getMetadata().getVersion().getFriendlyString());
        boolean api=com.pp.shaderlod.integration.makeupquad.MakeUpQuadApiPins.exactApi();boolean stack=com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.stackMatches(mods);
        boolean resources=PortBslOriginalSourceChecks.sourceResourcesMatch(client.getResourcePackRepository().getSelectedIds());
        Object pipeline=Iris.getPipelineManager().getPipelineNullable();boolean exactPipeline=pipeline!=null&&pipeline.getClass()==net.irisshaders.iris.pipeline.IrisRenderingPipeline.class;
        boolean exactMap=exactPipeline&&((net.irisshaders.iris.pipeline.ShaderRenderingPipeline)pipeline).getShaderMap().getClass()==net.irisshaders.iris.pipeline.programs.ShaderMap.class;
        boolean values=Iris.getCurrentPack().orElseThrow().getShaderPackOptions().getOptionValues().getClass()==net.irisshaders.iris.shaderpack.option.values.MutableOptionValues.class;
        var caps=org.lwjgl.opengl.GL.getCapabilities();boolean capabilities=com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.capabilitiesAllow(org.lwjgl.system.Pointer.POINTER_SIZE,caps.OpenGL41,caps.glMultiDrawElementsBaseVertex);
        var gateObservation=new JsonObject();gateObservation.addProperty("exactApi",api);gateObservation.addProperty("stackMatches",stack);gateObservation.addProperty("sourceResourceSetMatches",resources);gateObservation.addProperty("exactPipeline",exactPipeline);gateObservation.addProperty("exactShaderMap",exactMap);gateObservation.addProperty("exactMutableValues",values);gateObservation.addProperty("capabilitiesMatch",capabilities);gateObservation.add("actualResourceIds",GSON.toJsonTree(client.getResourcePackRepository().getSelectedIds()));report.add("bslSourceGateObservation",gateObservation);
        if(!api||!stack||!resources||!exactPipeline||!exactMap||!values||!capabilities||Iris.isFallback()||!IrisApi.getInstance().isShaderPackInUse()||com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled()||!PortBslOriginalSourceChecks.sourceMatches(Iris.getShaderpacksDirectory().resolve(original),options))throw new IllegalStateException("BSL native source/options/exact26.2 tuple refused");
        int contextFlags=GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_CONTEXT_FLAGS);if((contextFlags&0x00000008)!=0)throw new IllegalStateException("No-error GL context cannot establish native GLerror evidence");
        int error=GL11.glGetError();if(error!=GL11.GL_NO_ERROR)throw new IllegalStateException("Native source observation GLerror="+error);
        var state=new JsonObject();state.addProperty("native384Matches",true);state.addProperty("exactApi",api);state.addProperty("stackMatches",stack);state.addProperty("resourcesMatch",resources);state.addProperty("exactPipeline",exactPipeline);state.addProperty("exactShaderMap",exactMap);state.addProperty("exactMutableValues",values);state.addProperty("capabilitiesMatch",capabilities);state.addProperty("glError",error);state.addProperty("glContextFlags",contextFlags);state.addProperty("rendererEligible",false);state.addProperty("timingObservation",false);state.add("actualMods",GSON.toJsonTree(mods));state.add("actualResourceIds",GSON.toJsonTree(client.getResourcePackRepository().getSelectedIds()));return state;
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
        observation.addProperty("screenClass", client.gui.screen() == null ? "none" : client.gui.screen().getClass().getName());
        observation.addProperty("screenAbsent", client.gui.screen() == null);
        observation.addProperty("paused", client.isPaused());
        observation.addProperty("focused", client.isWindowActive());
        observation.addProperty("fullscreen", client.getWindow().isFullscreen());
        var window = client.getWindow();
        var target = client.gameRenderer.mainRenderTarget();
        long monitor = GLFW.glfwGetWindowMonitor(window.handle());
        observation.addProperty("nativeMonitor", monitor);
        observation.addProperty("windowFramebufferWidth", window.getWidth());
        observation.addProperty("windowFramebufferHeight", window.getHeight());
        observation.addProperty("framebufferWidth", target == null ? 0 : target.width);
        observation.addProperty("framebufferHeight", target == null ? 0 : target.height);
        observation.addProperty("viewportGate", monitor != 0 && window.getWidth() == 1920 && window.getHeight() == 1200
                && target != null && target.width == window.getWidth() && target.height == window.getHeight());
        try {
            observation.addProperty("renderBackend", observedBackend());
            String selected = Iris.getIrisConfig().getShaderPackName().orElse("");
            String loaded = Iris.getCurrentPackName();
            observation.addProperty("irisSelectedPack", selected);
            observation.addProperty("irisLoadedPack", loaded);
            observation.addProperty("selectedEqualsLoaded", selected.equals(loaded));
            observation.addProperty("stagePackGate", rendererSpeed ? selected.equals(original) : stageGenerated() ? !selected.equals(original) : selected.equals(original));
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
        if(!profile.endsWith(Path.of("run-workloads","port-262-runtime-r1","fabric")) || !profile.equals(profile.toRealPath()))
            throw new IllegalStateException("Renderer probe requires exact nonsymlink private port profile");
        var world=profile.resolve("saves/ShaderLOD-Landscape");
        try(var paths=Files.list(profile.resolve("saves"))) {
            var worlds=paths.filter(Files::isDirectory).toList();
            if(worlds.size()!=1 || !worlds.get(0).equals(world))throw new IllegalStateException("Renderer private profile must contain only ShaderLOD-Landscape");
        }
        if(!world.equals(world.toRealPath()) || !world.resolve("level.dat").equals(world.resolve("level.dat").toRealPath()))
            throw new IllegalStateException("Renderer world must not alias another save");
        if(FabricLoader.getInstance().isModLoaded("distanthorizons"))throw new IllegalStateException("Renderer speed bracket requires no-DH native stack");
    }

    private void fail(Minecraft client, Exception failure) {
        PortRendererFrames.stopMeasuredWindow();

        if(rendererSpeed && measuredSince!=0 && measuredProof!=null) {
            var rejectedWindow=new JsonObject();var proof=PortRendererFrames.snapshot();
            rejectedWindow.addProperty("stage",stages[stage]);
            rejectedWindow.addProperty("renderFrames",proof[0]-measuredProof[0]);
            rejectedWindow.addProperty("expectedConfiguredFramerateLimit",PortRendererFrames.expectedLimit());
            rejectedWindow.addProperty("rateObservedFrames",PortRendererFrames.rateObservedFrames());
            rejectedWindow.addProperty("rateUnavailableFrames",PortRendererFrames.rateUnavailableFrames());
            rejectedWindow.addProperty("rejectedMeasuredFrames",proof[7]-measuredProof[7]);
            if(PortRendererFrames.rateObservedFrames()>0) {
                rejectedWindow.addProperty("minimumConfiguredFramerateLimit",PortRendererFrames.minConfigured());
                rejectedWindow.addProperty("maximumConfiguredFramerateLimit",PortRendererFrames.maxConfigured());
                rejectedWindow.addProperty("minimumEffectiveFramerateLimit",PortRendererFrames.minEffective());
                rejectedWindow.addProperty("maximumEffectiveFramerateLimit",PortRendererFrames.maxEffective());
            }
            rejectedWindow.addProperty("elapsedSeconds",(System.nanoTime()-measuredSince)/1_000_000_000.0);
            rejectedWindow.addProperty("wholeMeasurementInvalid",true);
            report.add("rendererRejectedWindow",rejectedWindow);
            if(PortRendererFrames.firstRejectedRate()!=null)
                report.add("rendererRejectedRate",GSON.toJsonTree(PortRendererFrames.firstRejectedRate()));
        }
        // Preserve the original failed outcome before any recovery attempt or diagnostic.
        done=true;report.addProperty("status","failed");report.addProperty("failure",failure.toString());report.addProperty("failedStage",stages[stage]);
        if(rendererSpeed && initial!=null) {
            report.add("rendererAdmissionBeforeRecovery",RendererAdmissionDiagnostics.observe(client));
            var recovery=new JsonObject();report.add("rendererFailureRecoveryObservation",recovery);
            try {
                boolean knownNative=world==client.level && originalHash!=null && original.equals(Iris.getIrisConfig().getShaderPackName().orElse("")) &&
                    original.equals(Iris.getCurrentPackName()) && !Iris.isFallback() && IrisApi.getInstance().isShaderPackInUse() &&
                    originalHash.equals(hash(Iris.getShaderpacksDirectory().resolve(original)));
                recovery.addProperty("knownCurrentNativeOriginal",knownNative);
                if(knownNative) {
                    boolean returned=FabricLodSettings.apply(initial.enabled(),initial.preset());
                    report.addProperty("rendererFailureRestoreReturned",returned);recovery.addProperty("applyInitialReturned",returned);
                    recovery.add("observedCoreAfter",GSON.toJsonTree(FabricLodConfig.snapshot()));
                    recovery.addProperty("initialCoreRestored",initial.equals(FabricLodConfig.snapshot()));
                    recovery.addProperty("loadedOptionsRestored",originalOptions!=null && originalOptions.equals(loadedOptions()));
                    recovery.addProperty("selectedOriginal",original.equals(Iris.getIrisConfig().getShaderPackName().orElse("")));
                    recovery.addProperty("loadedOriginal",original.equals(Iris.getCurrentPackName()));
                    recovery.addProperty("shaderPackInUse",IrisApi.getInstance().isShaderPackInUse());recovery.addProperty("irisFallback",Iris.isFallback());
                    recovery.addProperty("rendererInactiveAfterRecovery",!com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());
                    recovery.addProperty("statusRemainsFailed",true);
                } else recovery.addProperty("recoveryNotAttempted","Current native original could not be authenticated; original failure retained");
            } catch(Exception | LinkageError restoreFailure) { report.addProperty("rendererFailureRestoreError",restoreFailure.toString()); }
        }
        if(bslSource)report.add("bslDrawSourceFailureObservations",PortBslDrawSourceReadback.observations());
        observeWaiting(client,"failureObservation");write(client);
        LOGGER.error("Fabric roundtrip failed; client remains open; receipt={}",evidence(client),failure);
    }
}
