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
import org.lwjgl.glfw.GLFW;

/** Dev-only explicit production Apply round trip. No input, focus or exit control. */
final class NeoForgeRoundtrip {
    private static final Logger LOGGER = LoggerFactory.getLogger("shaderlod-dev");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long SETTLE = 30_000_000_000L;
    private final boolean rendererSpeed = Boolean.getBoolean(PortRendererFrames.PROPERTY);
    private long measuredSince;
    private long[] measuredProof;
    private long[] bindingCertificateProof;
    private JsonObject initialPose;
    private boolean rendererProfileValidated;
    private final boolean originalOnly=Boolean.getBoolean("shaderlod.dev.portOriginalOnly");
    private boolean glfwStartupRequested,glfwStartupAccepted;
    private long glfwStartupAt;

    private final String nativeFamilyMode=System.getProperty("shaderlod.dev.portNativeFamily","");
    private final boolean makeupVolume="makeup-volume".equals(nativeFamilyMode);
    private final boolean bslVolume="bsl-volume".equals(nativeFamilyMode);
    private final boolean reimaginedVolume="reimagined-volume".equals(nativeFamilyMode);
    private final boolean unboundVolume="unbound-volume".equals(nativeFamilyMode);
    private final boolean sildurVolume="sildur-volume".equals(nativeFamilyMode);
    private final boolean blissVolume="bliss-volume".equals(nativeFamilyMode);
    private final boolean photonVolume="photon-volume".equals(nativeFamilyMode);
    private final boolean volumeControl=makeupVolume||bslVolume||reimaginedVolume||unboundVolume||sildurVolume||blissVolume||photonVolume;
    private final boolean mainBindingCompat=Boolean.getBoolean("shaderlod.dev.portMainBindingCompat");
    private final Map<String,JsonObject> mainBindingStageProofs=new java.util.HashMap<>();
    private final boolean qualityRoundtrip = Boolean.getBoolean("shaderlod.dev.portQualityRoundtrip");
    private final String[] stages = volumeControl ? new String[]{"original","on","quality","off"} : originalOnly ? new String[]{"original"} : qualityRoundtrip
            ? new String[]{"original", "quality", "native-info-edit", "native-info-restored", "native-upscaled",
                    "native-aa-restored", "performance", "quality-return", "off"}
            : new String[]{"original", "on", "off"};
    private final String run = Long.toString(System.currentTimeMillis());
    private final String original = System.getProperty("shaderlod.dev.portRoundtripOriginal", "");
    private final JsonObject report = new JsonObject();
    private final long started = System.nanoTime();
    private final BackupPrompt backupPrompt = new BackupPrompt("neoforge", run, report);
    private final LoadingWarningsPrompt loadingWarnings = new LoadingWarningsPrompt(run, report);
    private boolean privateInputsValidated;
    private boolean startupObserved;
    private boolean startupWorldAccepted;
    private final long startupDeadlineEpochMillis = Long.parseLong(System.getProperty("shaderlod.dev.startupDeadlineEpochMillis", "0"));
    private Object world;
    private long firstWorldSince;
    private boolean waitingObserved;
    private CoreState initial;
    private String originalHash;
    private Map<String, String> originalOptions;
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
                "workTrace", "spatialLayoutProbe", "pixelCoverage", "gpuTiming", "cpuLayerTiming",
                "settledConsumerUniforms"}) {
            String value = System.getProperty("shaderlod.dev." + flag, "");
            if (!value.isBlank() && !value.equalsIgnoreCase("false")) {
                LOGGER.error("NeoForge roundtrip disabled: conflicting dev property {}", flag);
                return;
            }
        }
        var probe = new NeoForgeRoundtrip();
        if(probe.originalOnly && (probe.rendererSpeed || probe.qualityRoundtrip || Boolean.getBoolean("shaderlod.dev.portRoundtripUi") ||
            Boolean.getBoolean("shaderlod.dev.portRoundtripDh") || Boolean.getBoolean(com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.RESEARCH_OPT_IN) ||
            Boolean.getBoolean(com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES))) {
            LOGGER.error("Original-only NeoForge observation refuses timing/Apply/UI/DH/renderer-research flags");return;
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
        if(!probe.nativeFamilyMode.isBlank()&&!probe.volumeControl){LOGGER.error("Unknown native family selector for NeoForge26.2");return;}
        String mainFlag=System.getProperty("shaderlod.dev.portMainBindingCompat","false");
        String mainProbeFlag=System.getProperty("shaderlod.bindingCompatibilityProbe","false");
        if(!java.util.Set.of("false","true").contains(mainFlag)||!java.util.Set.of("false","true").contains(mainProbeFlag)
            ||(!probe.volumeControl&&(probe.mainBindingCompat||Boolean.parseBoolean(mainProbeFlag)))){
            LOGGER.error("MAIN binding compatibility observer requires the explicit MakeUp/BSL controls mode and literal flags");return;
        }
        if(probe.volumeControl){
            try{
                String devFlag=System.getProperty("shaderlod.dev.portBindingInputFix","false");
                boolean devBinding=Boolean.parseBoolean(devFlag);
                if(!java.util.Set.of("false","true").contains(devFlag)||probe.mainBindingCompat==devBinding
                    ||Boolean.parseBoolean(mainProbeFlag)!=probe.mainBindingCompat)
                    throw new IllegalStateException("MakeUp controls require exactly one MAIN+probe or DEV binding correction; mixed correction paths refused");
                if(probe.bslVolume&&!probe.mainBindingCompat)throw new IllegalStateException("BSL controls require MAIN compatibility; DEV MakeUp correction is not a BSL certificate");
                if(probe.photonVolume&&!probe.mainBindingCompat)throw new IllegalStateException("Photon controls require MAIN compatibility");
                if(probe.blissVolume&&!probe.mainBindingCompat)throw new IllegalStateException("Bliss controls require MAIN compatibility");
                if(probe.sildurVolume&&!probe.mainBindingCompat)throw new IllegalStateException("Sildur controls require MAIN compatibility");
                if(probe.unboundVolume&&!probe.mainBindingCompat)throw new IllegalStateException("Unbound controls require MAIN compatibility; DEV MakeUp correction is not an Unbound certificate");
                if(probe.reimaginedVolume&&!probe.mainBindingCompat)throw new IllegalStateException("Reimagined controls require MAIN compatibility; DEV MakeUp correction is not a Reimagined certificate");
                if(probe.originalOnly||probe.rendererSpeed||probe.qualityRoundtrip||Boolean.getBoolean("shaderlod.dev.portRoundtripDh")||!Boolean.getBoolean("shaderlod.dev.portRoundtripUi"))throw new IllegalStateException("MakeUp volume controls require explicit binding compatibility and UI, no original/speed/Photon/DH modes");
                for(String flag:new String[]{com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.RESEARCH_OPT_IN,com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES})if(!"false".equals(System.getProperty(flag,"false")))throw new IllegalStateException("MakeUp volume controls conflict with "+flag);
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var archive=dir.resolve(probe.original).normalize();
                if(!archive.startsWith(dir)||archive.equals(dir)||Files.isSymbolicLink(archive)||(probe.photonVolume?!com.pp.shaderlod.integration.bindingcompat.PhotonSourceCertificate.originalSource(archive):probe.blissVolume?!com.pp.shaderlod.integration.blissquad.BlissQuadEligibility.validate(archive,com.pp.shaderlod.integration.blissquad.BlissQuadEligibility.nativeOptions()).eligible():probe.sildurVolume?!com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.validate(archive,com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.nativeOptions()).eligible():probe.unboundVolume?!com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.validate(archive,com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.nativeOptions()).eligible():probe.reimaginedVolume?!com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.validate(archive,com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.nativeOptions()).eligible():(probe.bslVolume?!com.pp.shaderlod.integration.bslquad.BslQuadEligibility.validate(archive,com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions()).eligible():!com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.validate(archive,com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.nativeOptions()).eligible())))throw new IllegalStateException("Certified original family archive/native options/resources required");
            }catch(Exception failure){LOGGER.error("MakeUp NeoForge26.2 volume controls refused before registration",failure);return;}
            probe.report.add("stageNames",GSON.toJsonTree(probe.stages));probe.report.addProperty("makeupVolumeControl",probe.makeupVolume);probe.report.addProperty("makeupVolumeMode",probe.makeupVolume?"makeup-volume":"");probe.report.addProperty("bslVolumeControl",probe.bslVolume);probe.report.addProperty("bslVolumeMode",probe.bslVolume?"bsl-volume":"");probe.report.addProperty("photonVolumeControl",probe.photonVolume);probe.report.addProperty("photonVolumeMode",probe.photonVolume?"photon-volume":"");probe.report.addProperty("blissVolumeControl",probe.blissVolume);probe.report.addProperty("blissVolumeMode",probe.blissVolume?"bliss-volume":"");probe.report.addProperty("sildurVolumeControl",probe.sildurVolume);probe.report.addProperty("sildurVolumeMode",probe.sildurVolume?"sildur-volume":"");probe.report.addProperty("unboundVolumeControl",probe.unboundVolume);probe.report.addProperty("unboundVolumeMode",probe.unboundVolume?"unbound-volume":"");probe.report.addProperty("reimaginedVolumeControl",probe.reimaginedVolume);probe.report.addProperty("reimaginedVolumeMode",probe.reimaginedVolume?"reimagined-volume":"");probe.report.addProperty("productionApplyRequested",true);probe.report.addProperty("rendererWorkMeasurementRequested",false);probe.report.addProperty("devBindingCompatibilityRequired",!probe.mainBindingCompat);probe.report.addProperty("mainBindingCompatibilityRequired",probe.mainBindingCompat);probe.report.addProperty("bindingCompatibilityBackend",probe.mainBindingCompat?"MAIN":"DEV");
        }
        probe.report.addProperty("qualityRoundtrip", probe.qualityRoundtrip);
        probe.report.addProperty("originalOnly",probe.originalOnly);
        probe.report.addProperty("timedWindowRequested",probe.rendererSpeed);
        probe.report.addProperty("rendererSpeedProbe", probe.rendererSpeed);
        probe.report.addProperty("completedWorldRenderSeam", "renderLevel(Lnet/minecraft/client/DeltaTracker;)V");
        probe.report.addProperty("driverEvidence", "Actual GL function entry observed separately; not GPU completion or clean timing");
        probe.report.addProperty("run", probe.run);
        probe.report.addProperty("originalPack", probe.original);
        probe.report.addProperty("minecraftTarget", "26.2");
        probe.report.addProperty("loaderTarget", "neoforge");
        probe.report.addProperty("startupDeadlineEpochMillis", probe.startupDeadlineEpochMillis);
        probe.report.addProperty("backendPolicy", "observed OpenGL required before hashing or GL facts");
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
            if (!privateInputsValidated) { validatePrivateInputs(client); privateInputsValidated = true; }
            if (System.nanoTime() - started > 300_000_000_000L) throw new IllegalStateException("300 s probe deadline exceeded");
            if(originalOnly && !glfwStartupRequested && System.nanoTime()-started>=7_000_000_000L) {
                glfwStartupRequested=true;glfwStartupAt=System.nanoTime();GlfwStartup.configure(client,run,report,()->write(client));return;
            }
            if(originalOnly && !glfwStartupAccepted) {
                if(!glfwStartupRequested || System.nanoTime()-glfwStartupAt<3_000_000_000L)return;
                if(!GlfwStartup.eligible(client)) {
                    if(System.nanoTime()-glfwStartupAt>=20_000_000_000L)throw new IllegalStateException("NeoForge preworld native1920/focus did not settle");return;
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
                if(volumeControl&&(initial.preset()!=LodPreset.BALANCED||initial.settings().debug()))throw new IllegalStateException("MakeUp volume baseline requires Off/Balanced/debugfalse");
                if((rendererSpeed || originalOnly) && initial.preset()!=LodPreset.PERFORMANCE) throw new IllegalStateException("Renderer baseline requires Off/Performance");
                originalHash = hash(Iris.getShaderpacksDirectory().resolve(original));
                if(originalOnly)report.add("rendererAdmissionAtStartup",RendererAdmissionDiagnostics.observe(client));
            } else if (world != client.level) throw new IllegalStateException("World changed during roundtrip");
            if (!waitingObserved && System.nanoTime() - firstWorldSince >= 10_000_000_000L) {
                waitingObserved = true;
                observeWaiting(client, "firstWorldObservation");
            }
            if (screenshotPending) return;
            if (ui != null && !uiFinished) { captureUi(client); return; }
            if (advance) {
                advance = false;
                if(originalOnly && stage==0) {
                    done=true;report.addProperty("status","complete-original-observation");report.addProperty("productionApplyPerformed",false);
                    report.addProperty("timedWindowPerformed",false);write(client);LOGGER.info("NeoForge original-only observation complete; no production Apply/timing; client remains open");return;
                }
                if (stage == stages.length - 1) {
                    if (Boolean.getBoolean("shaderlod.dev.portRoundtripUi") && !uiFinished) {
                        ui = new ShaderLodSettingsScreen(ModList.get().getModContainerById("shaderlod").orElseThrow(), null);
                        client.gui.setScreen(ui);
                        var controls = cycles();
                        if (controls.size() != 2) throw new IllegalStateException("Expected two real settings cycle controls");
                        controls.get(0).onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                        for (int i = 0; i < 4 && controls.get(1).getValue() != LodPreset.QUALITY; i++)
                            controls.get(1).onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
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
                boolean applied = volumeControl ? applyMakeupVolumeStage() : qualityRoundtrip ? applyQualityStage(client) : ShaderLodSettings.apply(enabled, initial.preset());
                report.addProperty(stages[stage] + "ApplyReturned", applied);
                report.addProperty(stages[stage] + "ApplyElapsedSeconds", (System.nanoTime() - before) / 1_000_000_000.0);
                resetWindow();
                stableSignature = null;
                write(client);
                if (!applied) throw new IllegalStateException("Production Apply failed: " + ShaderLodSettings.failureMessage());
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
            if (coreState().enabled() != stageEnabled())
                throw new IllegalStateException("Unexpected LOD enabled state at " + stages[stage]);
            startupWorldAccepted = true;
            report.addProperty("startupEligibleShaderWorldEntered", true);
            if(rendererSpeed)requireRendererRate(client);
            Map<String, String> options = loadedOptions();
            if(makeupVolume)validateMakeupVolumeState(client,options,null);
            if(bslVolume)validateBslVolumeState(client,options,null);
            if(photonVolume)validatePhotonVolumeState(client,options,null);
            if(blissVolume)validateBlissVolumeState(client,options,null);
            if(sildurVolume)validateSildurVolumeState(client,options,null);
            if(unboundVolume)validateUnboundVolumeState(client,options,null);
            if(reimaginedVolume)validateReimaginedVolumeState(client,options,null);
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
            if (System.nanoTime() - settledSince < (originalOnly ? 2_000_000_000L : rendererSpeed ? 40_000_000_000L : qualityRoundtrip && stage > 0 ? 3_000_000_000L : SETTLE)) return;
            if(rendererSpeed) {
                if(com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled() != stageEnabled())
                    throw new IllegalStateException("Renderer admission does not match " + stages[stage]);
                if(measuredSince==0) {
                    PortRendererFrames.beginMeasuredWindow(client.options.framerateLimit().get());
                    if(Boolean.getBoolean("shaderlod.dev.portBindingInputFix")) bindingCertificateProof=new long[]{
                        com.pp.shaderlod.dev.bindingfix.BindingInputContext.certificateRefreshes,
                        com.pp.shaderlod.dev.bindingfix.BindingInputContext.certificateReuses,
                        com.pp.shaderlod.dev.bindingfix.BindingInputContext.sourceStampReads};
                    measuredSince=System.nanoTime();measuredProof=PortRendererFrames.snapshot();return;
                }
                if(System.nanoTime()-measuredSince<30_000_000_000L)return;
            }
            if(originalOnly) {
                var certificate=com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.validate(Iris.getShaderpacksDirectory().resolve(original),options);
                if(!certificate.eligible() || options.size()!=100 || !"2".equals(options.get("AA_TYPE")))
                    throw new IllegalStateException("Original-only observation requires authenticated original MakeUp/native100/AA2: "+certificate.reason());
                if(!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Original-only observation retains pending shader option changes");
                if(!GlfwStartup.eligible(client) || !initial.equals(coreState()))throw new IllegalStateException("Original-only viewport/core changed");
                report.add("rendererAdmissionAtObservation",RendererAdmissionDiagnostics.observe(client));
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
            if(bslVolume)validateBslVolumeState(client,options,state);
            if(photonVolume)validatePhotonVolumeState(client,options,state);
            if(blissVolume)validateBlissVolumeState(client,options,state);
            if(sildurVolume)validateSildurVolumeState(client,options,state);
            if(unboundVolume)validateUnboundVolumeState(client,options,state);
            if(reimaginedVolume)validateReimaginedVolumeState(client,options,state);
            if (qualityRoundtrip) validateQualityObservation(client, options, state);
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

    private void validatePrivateInputs(Minecraft client) throws Exception {
        if(rendererSpeed)return; // The same exact private profile has already passed validateRendererProfile.
        Path profile = client.gameDirectory.toPath().toAbsolutePath().normalize();
        String loader = "neoforge";
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
                button("Cancel").onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
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
        String backend = observedBackend();
        var state = new JsonObject();
        state.addProperty("capturedAtUtc", Instant.now().toString());
        state.addProperty("stage", stages[stage]);
        if(volumeControl)state.add("mainArtifactOrigins",mainArtifactOrigins());
        if(mainBindingCompat)observeMainBindingCompatibility(state);
        if(Boolean.getBoolean("shaderlod.dev.portBindingInputFix")) {
            var proof=new JsonObject();
            proof.addProperty("correctedDescriptorQueries",com.pp.shaderlod.dev.bindingfix.BindingInputContext.correctedQueries);
            proof.addProperty("shadowCorrectedDescriptorQueries",com.pp.shaderlod.dev.bindingfix.BindingInputContext.shadowCorrectedQueries);
            proof.addProperty("ordinaryCorrectedDescriptorQueries",com.pp.shaderlod.dev.bindingfix.BindingInputContext.ordinaryCorrectedQueries);
            proof.addProperty("glFunctionEntries",com.pp.shaderlod.dev.bindingfix.BindingInputContext.glFunctionEntries);
            proof.addProperty("shadowGlFunctionEntries",com.pp.shaderlod.dev.bindingfix.BindingInputContext.shadowGlFunctionEntries);
            proof.addProperty("ordinaryGlFunctionEntries",com.pp.shaderlod.dev.bindingfix.BindingInputContext.ordinaryGlFunctionEntries);
            proof.addProperty("completedDrawBodies",com.pp.shaderlod.dev.bindingfix.BindingInputContext.completedDrawBodies);
            proof.addProperty("shadowCompletedDrawBodies",com.pp.shaderlod.dev.bindingfix.BindingInputContext.shadowCompletedDrawBodies);
            proof.addProperty("ordinaryCompletedDrawBodies",com.pp.shaderlod.dev.bindingfix.BindingInputContext.ordinaryCompletedDrawBodies);
            proof.addProperty("lastIrisDescriptorSlots",com.pp.shaderlod.dev.bindingfix.BindingInputContext.lastIrisDescriptorSlots);
            proof.addProperty("lastCorrectedDescriptorSlots",com.pp.shaderlod.dev.bindingfix.BindingInputContext.lastCorrectedDescriptorSlots);
            proof.addProperty("irisSlotZeroIdentity",com.pp.shaderlod.dev.bindingfix.BindingInputContext.lastIrisSlotZeroIdentity);
            proof.addProperty("correctedSlotZeroIdentity",com.pp.shaderlod.dev.bindingfix.BindingInputContext.lastCorrectedSlotZeroIdentity);
            proof.addProperty("certificateRefreshes",com.pp.shaderlod.dev.bindingfix.BindingInputContext.certificateRefreshes);
            proof.addProperty("certificateReuses",com.pp.shaderlod.dev.bindingfix.BindingInputContext.certificateReuses);
            proof.addProperty("sourceStampReads",com.pp.shaderlod.dev.bindingfix.BindingInputContext.sourceStampReads);
            if(rendererSpeed) {
                if(bindingCertificateProof==null)throw new IllegalStateException("Missing binding certificate window start");
                long refreshes=com.pp.shaderlod.dev.bindingfix.BindingInputContext.certificateRefreshes-bindingCertificateProof[0];
                proof.addProperty("measuredCertificateRefreshes",refreshes);
                proof.addProperty("measuredCertificateReuses",com.pp.shaderlod.dev.bindingfix.BindingInputContext.certificateReuses-bindingCertificateProof[1]);
                proof.addProperty("measuredSourceStampReads",com.pp.shaderlod.dev.bindingfix.BindingInputContext.sourceStampReads-bindingCertificateProof[2]);
                if(refreshes!=0) {
                    report.add("bindingRejectedCertificateWindow",proof.deepCopy());
                    throw new IllegalStateException("Binding certificate changed inside measured window");
                }
            }
            proof.addProperty("gpuCompletionProven",false);
            proof.addProperty("validationPolicyMutated",false);
            state.add("bindingInputFix",proof);
            if(com.pp.shaderlod.dev.bindingfix.BindingInputContext.shadowCorrectedQueries<=0 ||
                com.pp.shaderlod.dev.bindingfix.BindingInputContext.ordinaryCorrectedQueries<=0 ||
                com.pp.shaderlod.dev.bindingfix.BindingInputContext.shadowGlFunctionEntries<=0 ||
                com.pp.shaderlod.dev.bindingfix.BindingInputContext.ordinaryGlFunctionEntries<=0 ||
                com.pp.shaderlod.dev.bindingfix.BindingInputContext.shadowCompletedDrawBodies<=0 ||
                com.pp.shaderlod.dev.bindingfix.BindingInputContext.ordinaryCompletedDrawBodies<=0 ||
                com.pp.shaderlod.dev.bindingfix.BindingInputContext.lastIrisDescriptorSlots!=1 ||
                com.pp.shaderlod.dev.bindingfix.BindingInputContext.lastCorrectedDescriptorSlots!=16 ||
                com.pp.shaderlod.dev.bindingfix.BindingInputContext.lastIrisSlotZeroIdentity!=
                    com.pp.shaderlod.dev.bindingfix.BindingInputContext.lastCorrectedSlotZeroIdentity)
                throw new IllegalStateException("Binding adapter shadow/ordinary/slot0/driver consumption unproven");
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
        state.add("requestedCoreConfig", GSON.toJsonTree(coreState()));
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


    private JsonObject mainArtifactOrigins(){
        var origins=new JsonObject();
        for(Class<?> type:new Class<?>[]{com.pp.shaderlod.packs.PackSelectionService.class,com.pp.shaderlod.integration.PackSelectionTransaction.class,com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.class,com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.class,com.pp.shaderlod.ShaderLodSettings.class}){
            var location=type.getProtectionDomain().getCodeSource();origins.addProperty(type.getName(),location==null?"unavailable":location.getLocation().toExternalForm());
        }return origins;
    }
    private void observeMainBindingCompatibility(JsonObject state){
        var proof=com.pp.shaderlod.integration.bindingcompat.MakeUpBindingCompatibility.observe();
        state.addProperty("bindingCompatibilityBackend","MAIN");
        state.add("mainBindingCompatibility",proof);
        state.addProperty("mainBindingCompatibilityGlEntryScope","nglMultiDrawElementsBaseVertex only");
        state.addProperty("mainBindingCompatibilityGpuCompletionProven",false);
        long devQueries=com.pp.shaderlod.dev.bindingfix.BindingInputContext.correctedQueries;
        state.addProperty("devCorrectedDescriptorQueries",devQueries);
        var source=com.pp.shaderlod.integration.bindingcompat.MakeUpBindingCompatibility.class.getProtectionDomain().getCodeSource();
        String origin=source==null?"unavailable":source.getLocation().toExternalForm();
        state.addProperty("mainBindingCompatibilityOrigin",origin);
        String[] counterNames={"correctedQueries","shadowCorrectedQueries","ordinaryCorrectedQueries",
            "glFunctionEntries","shadowGlFunctionEntries","ordinaryGlFunctionEntries",
            "completedDrawBodies","shadowCompletedDrawBodies","ordinaryCompletedDrawBodies"};
        JsonObject previous=stage==0?null:mainBindingStageProofs.get(stages[stage-1]);
        var deltas=new JsonObject();boolean progressed=stage==0||previous!=null;
        for(String name:counterNames){
            long delta=proof.get(name).getAsLong()-(previous==null?0:previous.get(name).getAsLong());
            deltas.addProperty(name,delta);progressed&=delta>0;
        }
        state.add("mainBindingStageCounterDeltas",deltas);
        state.addProperty("mainBindingCounterBaseline",stage==0?"zero before observation":stages[stage-1]);
        if(previous!=null)state.add("mainBindingPreviousStageCounters",previous.deepCopy());
        boolean admitted=(!photonVolume||(proof.has("sourceFamily")&&"photon".equals(proof.get("sourceFamily").getAsString())))&&(!blissVolume||(proof.has("sourceFamily")&&"bliss".equals(proof.get("sourceFamily").getAsString())))&&(!sildurVolume||(proof.has("sourceFamily")&&"sildur".equals(proof.get("sourceFamily").getAsString())))&&(!unboundVolume||(proof.has("sourceFamily")&&"unbound".equals(proof.get("sourceFamily").getAsString())))&&(!reimaginedVolume||(proof.has("sourceFamily")&&"reimagined".equals(proof.get("sourceFamily").getAsString())))&&(!bslVolume||(proof.has("sourceFamily")&&"bsl".equals(proof.get("sourceFamily").getAsString())))&&progressed&&devQueries==0&&!"unavailable".equals(origin)
            &&mainArtifactOrigins().entrySet().stream().allMatch(entry->origin.equals(entry.getValue().getAsString()))
            &&proof.get("probeEnabled").getAsBoolean()&&!proof.get("validationPolicyMutated").getAsBoolean()
            &&proof.get("correctedQueries").getAsLong()>0
            &&proof.get("shadowCorrectedQueries").getAsLong()>0&&proof.get("ordinaryCorrectedQueries").getAsLong()>0
            &&proof.get("glFunctionEntries").getAsLong()>0
            &&proof.get("shadowGlFunctionEntries").getAsLong()>0&&proof.get("ordinaryGlFunctionEntries").getAsLong()>0
            &&proof.get("completedDrawBodies").getAsLong()>0
            &&proof.get("shadowCompletedDrawBodies").getAsLong()>0&&proof.get("ordinaryCompletedDrawBodies").getAsLong()>0
            &&proof.get("lastIrisDescriptorSlots").getAsInt()==1&&proof.get("lastCorrectedDescriptorSlots").getAsInt()==16
            &&proof.get("lastIrisSlotZeroIdentity").getAsInt()==proof.get("lastCorrectedSlotZeroIdentity").getAsInt();
        if(!admitted){
            report.add("mainBindingCompatibilityFailureObservation",state.deepCopy());
            throw new IllegalStateException("MAIN binding origin/probe/DEV inactivity/per-stage shadow+ordinary/native-body deltas/slot0 admission unproven");
        }
        mainBindingStageProofs.put(stages[stage],proof.deepCopy());
    }

    private boolean applyMakeupVolumeStage(){
        boolean applied=ShaderLodSettings.apply(stageEnabled(),stage==2?LodPreset.QUALITY:LodPreset.BALANCED);
        if(!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("MakeUp public Apply left pending native options");
        return applied;
    }

    private void validateMakeupVolumeState(Minecraft client,Map<String,String> options,JsonObject state)throws Exception{
        var cfg=coreState();var expectedPreset=stage==2?LodPreset.QUALITY:LodPreset.BALANCED;
        if(initial==null||initial.enabled()||initial.settings().debug()||initial.preset()!=LodPreset.BALANCED||cfg.enabled()!=stageEnabled()||cfg.preset()!=expectedPreset||cfg.settings().debug()||!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("MakeUp fallback Core/preset/nativequeue mismatch");
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

    private void validateBslVolumeState(Minecraft client,Map<String,String> options,JsonObject state)throws Exception{
        var cfg=coreState();var expectedPreset=stage==2?LodPreset.QUALITY:LodPreset.BALANCED;
        if(initial==null||initial.enabled()||initial.settings().debug()||initial.preset()!=LodPreset.BALANCED||cfg.enabled()!=stageEnabled()||cfg.preset()!=expectedPreset||cfg.settings().debug()||!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("BSL fallback Core/preset/nativequeue mismatch");
        var expected=new TreeMap<>(com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions());
        if(stageGenerated()){
            expected.put("SHADERLOD_REFINEMENT_ADAPTER","1");
            // Exact hash-pinned helper #ifndef/#define guards also appear as Iris BooleanOptions.
            expected.put("SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true");
            expected.put("SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true");
            expected.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");
        }
        if(!expected.equals(options))throw new IllegalStateException("BSL fallback requires native384 unchanged plus owned flag and exact three parsed helper guards on generated stage");
        if(!"0".equals(options.get("AURORA")))throw new IllegalStateException("BSL native Aurora input must stay inactive0");
        if(state==null)return;
        state.addProperty("nativeAuroraSetting",options.get("AURORA"));
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
        state.addProperty("bslVolumeControl",true);state.addProperty("expectedPreset",expectedPreset.name());state.addProperty("underlyingNative384Unchanged",true);state.addProperty("generatedBslRecipeSelected",stageGenerated());state.addProperty("ownedRefinementFlag",stageGenerated()?"1":"absent");state.addProperty("rendererEligible",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());state.addProperty("linkedUniformReadbackPerformed",false);state.addProperty("avoidedWorkMeasured",false);
    }

    private void validateReimaginedVolumeState(Minecraft client,Map<String,String> options,JsonObject state)throws Exception{
        var cfg=coreState();var expectedPreset=stage==2?LodPreset.QUALITY:LodPreset.BALANCED;
        if(initial==null||initial.enabled()||initial.settings().debug()||initial.preset()!=LodPreset.BALANCED||cfg.enabled()!=stageEnabled()||cfg.preset()!=expectedPreset||cfg.settings().debug()||!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Reimagined fallback Core/preset/nativequeue mismatch");
        var expected=new TreeMap<>(com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.nativeOptions());
        if(stageGenerated()){
            expected.put("SHADERLOD_REFINEMENT_ADAPTER","1");
            // Exact hash-pinned helper #ifndef/#define guards also appear as Iris BooleanOptions.
            expected.put("SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true");
            expected.put("SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true");
            expected.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");
        }
        if(!expected.equals(options))throw new IllegalStateException("Reimagined fallback requires native398 unchanged plus owned flag and exact three parsed helper guards on generated stage");
        if(!"true".equals(options.get("TAA"))||!"1".equals(options.get("SHADER_STYLE"))||!"1".equals(options.get("RP_MODE")))throw new IllegalStateException("Reimagined native TAA/style1/RP1 changed");
        if(state==null)return;
        state.addProperty("nativeTaa",options.get("TAA"));state.addProperty("nativeShaderStyle",options.get("SHADER_STYLE"));state.addProperty("nativeRpMode",options.get("RP_MODE"));
        var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var originalPath=dir.resolve(original).normalize();
        if(!com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.validate(originalPath,com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("Reimagined original source certificate changed");
        if(stageGenerated()){
            String selected=Iris.getCurrentPackName();var generated=dir.resolve(selected).normalize();
            if(!generated.startsWith(dir)||generated.equals(dir)||Files.isSymbolicLink(generated))throw new IllegalStateException("Unsafe Reimagined generated source path");
            try(var zip=new java.util.zip.ZipFile(generated.toFile())){
                var entry=zip.getEntry("shaderlod-selection.json");if(entry==null)throw new IllegalStateException("Generated Reimagined selection marker absent");
                JsonObject marker;try(var in=zip.getInputStream(entry)){marker=com.google.gson.JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
                String recipeSha;try(var in=com.pp.shaderlod.packs.PackSelectionService.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/complementary-reimagined.json")){
                    if(in==null)throw new IllegalStateException("Actual packaged Reimagined recipe absent");recipeSha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
                }
                if(marker.get("schema").getAsInt()!=1||!"complementary-reimagined".equals(marker.get("adapterId").getAsString())||!original.equals(marker.get("originalFile").getAsString())||!originalHash.equals(marker.get("originalSha256").getAsString())||!recipeSha.equals(marker.get("recipeSha256").getAsString()))throw new IllegalStateException("Generated Reimagined source/recipe marker mismatch");
                String stateFile=marker.get("stateFile").getAsString();var statePath=dir.resolve(stateFile).normalize();
                if(!Path.of(stateFile).getFileName().toString().equals(stateFile)||!statePath.startsWith(dir)||statePath.equals(dir)||Files.isSymbolicLink(statePath))throw new IllegalStateException("Unsafe Reimagined selection state reference");
                var selection=com.google.gson.JsonParser.parseString(Files.readString(statePath)).getAsJsonObject();String generatedSha=hash(generated);
                if(!selection.get("active").getAsBoolean()||!selected.equals(selection.get("generatedFile").getAsString())||!generatedSha.equals(selection.get("generatedSha256").getAsString())||!original.equals(selection.get("originalFile").getAsString())||!originalHash.equals(selection.get("originalSha256").getAsString())||!recipeSha.equals(selection.get("recipeSha256").getAsString()))throw new IllegalStateException("Generated Reimagined archive differs from real public Apply selection state");
                state.add("generatedSelectionMarker",marker);state.addProperty("generatedArchiveSha256",generatedSha);state.addProperty("generatedSelectionStateMatched",true);
            }
        }
        state.addProperty("loadedOptionCount",options.size());
        state.add("generatedDeclaredOptions",GSON.toJsonTree(stageGenerated()?Map.of("SHADERLOD_REFINEMENT_ADAPTER","1","SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true","SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true","SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true"):Map.of()));
        state.addProperty("reimaginedVolumeControl",true);state.addProperty("expectedPreset",expectedPreset.name());state.addProperty("underlyingNative398Unchanged",true);state.addProperty("generatedReimaginedRecipeSelected",stageGenerated());state.addProperty("ownedRefinementFlag",stageGenerated()?"1":"absent");state.addProperty("rendererEligible",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());state.addProperty("linkedUniformReadbackPerformed",false);state.addProperty("avoidedWorkMeasured",false);
    }

    private void validateUnboundVolumeState(Minecraft client,Map<String,String> options,JsonObject state)throws Exception{
        var cfg=coreState();var expectedPreset=stage==2?LodPreset.QUALITY:LodPreset.BALANCED;
        if(initial==null||initial.enabled()||initial.settings().debug()||initial.preset()!=LodPreset.BALANCED||cfg.enabled()!=stageEnabled()||cfg.preset()!=expectedPreset||cfg.settings().debug()||!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Unbound fallback Core/preset/nativequeue mismatch");
        var expected=new TreeMap<>(com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.nativeOptions());
        if(stageGenerated()){
            expected.put("SHADERLOD_REFINEMENT_ADAPTER","1");
            // Exact hash-pinned helper #ifndef/#define guards also appear as Iris BooleanOptions.
            expected.put("SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true");
            expected.put("SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true");
            expected.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");
        }
        if(!expected.equals(options))throw new IllegalStateException("Unbound fallback requires native398 unchanged plus owned flag and exact three parsed helper guards on generated stage");
        if(!"true".equals(options.get("TAA"))||!"4".equals(options.get("SHADER_STYLE"))||!"1".equals(options.get("RP_MODE")))throw new IllegalStateException("Unbound native TAA/style4/RP1 changed");
        if(state==null)return;
        state.addProperty("nativeTaa",options.get("TAA"));state.addProperty("nativeShaderStyle",options.get("SHADER_STYLE"));state.addProperty("nativeRpMode",options.get("RP_MODE"));
        var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var originalPath=dir.resolve(original).normalize();
        if(!com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.validate(originalPath,com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("Unbound original source certificate changed");
        if(stageGenerated()){
            String selected=Iris.getCurrentPackName();var generated=dir.resolve(selected).normalize();
            if(!generated.startsWith(dir)||generated.equals(dir)||Files.isSymbolicLink(generated))throw new IllegalStateException("Unsafe Unbound generated source path");
            try(var zip=new java.util.zip.ZipFile(generated.toFile())){
                var entry=zip.getEntry("shaderlod-selection.json");if(entry==null)throw new IllegalStateException("Generated Unbound selection marker absent");
                JsonObject marker;try(var in=zip.getInputStream(entry)){marker=com.google.gson.JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
                String recipeSha;try(var in=com.pp.shaderlod.packs.PackSelectionService.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/complementary-unbound.json")){
                    if(in==null)throw new IllegalStateException("Actual packaged Unbound recipe absent");recipeSha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
                }
                if(marker.get("schema").getAsInt()!=1||!"complementary-unbound".equals(marker.get("adapterId").getAsString())||!original.equals(marker.get("originalFile").getAsString())||!originalHash.equals(marker.get("originalSha256").getAsString())||!recipeSha.equals(marker.get("recipeSha256").getAsString()))throw new IllegalStateException("Generated Unbound source/recipe marker mismatch");
                String stateFile=marker.get("stateFile").getAsString();var statePath=dir.resolve(stateFile).normalize();
                if(!Path.of(stateFile).getFileName().toString().equals(stateFile)||!statePath.startsWith(dir)||statePath.equals(dir)||Files.isSymbolicLink(statePath))throw new IllegalStateException("Unsafe Unbound selection state reference");
                var selection=com.google.gson.JsonParser.parseString(Files.readString(statePath)).getAsJsonObject();String generatedSha=hash(generated);
                if(!selection.get("active").getAsBoolean()||!selected.equals(selection.get("generatedFile").getAsString())||!generatedSha.equals(selection.get("generatedSha256").getAsString())||!original.equals(selection.get("originalFile").getAsString())||!originalHash.equals(selection.get("originalSha256").getAsString())||!recipeSha.equals(selection.get("recipeSha256").getAsString()))throw new IllegalStateException("Generated Unbound archive differs from real public Apply selection state");
                state.add("generatedSelectionMarker",marker);state.addProperty("generatedArchiveSha256",generatedSha);state.addProperty("generatedSelectionStateMatched",true);
            }
        }
        state.addProperty("loadedOptionCount",options.size());
        state.add("generatedDeclaredOptions",GSON.toJsonTree(stageGenerated()?Map.of("SHADERLOD_REFINEMENT_ADAPTER","1","SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true","SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true","SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true"):Map.of()));
        state.addProperty("unboundVolumeControl",true);state.addProperty("expectedPreset",expectedPreset.name());state.addProperty("underlyingNative398Unchanged",true);state.addProperty("generatedUnboundRecipeSelected",stageGenerated());state.addProperty("ownedRefinementFlag",stageGenerated()?"1":"absent");state.addProperty("rendererEligible",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());state.addProperty("linkedUniformReadbackPerformed",false);state.addProperty("avoidedWorkMeasured",false);
    }

    private void validateSildurVolumeState(Minecraft client,Map<String,String> options,JsonObject state)throws Exception{
        var cfg=coreState();var expectedPreset=stage==2?LodPreset.QUALITY:LodPreset.BALANCED;
        if(initial==null||initial.enabled()||initial.settings().debug()||initial.preset()!=LodPreset.BALANCED||cfg.enabled()!=stageEnabled()||cfg.preset()!=expectedPreset||cfg.settings().debug()||!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Sildur fallback Core/preset/nativequeue mismatch");
        var expected=new TreeMap<>(com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.nativeOptions());
        if(stageGenerated()){
            expected.put("SHADERLOD_EFFECT_ADAPTER","1");
            // Exact hash-pinned helper #ifndef/#define guards also appear as Iris BooleanOptions.
            expected.put("SHADERLOD_EFFECT_ADAPTER_INCLUDED","true");
            expected.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");
        }
        if(!expected.equals(options))throw new IllegalStateException("Sildur fallback requires native115 unchanged plus owned flag and exact two parsed helper guards on generated stage");
        if(!"true".equals(options.get("TAA"))||!"3".equals(options.get("Clouds"))||!"8".equals(options.get("cloudsIT")))throw new IllegalStateException("Sildur native TAA/Clouds3/cloudsIT8 changed");
        if(state==null)return;
        state.addProperty("nativeTaa",options.get("TAA"));state.addProperty("nativeClouds",options.get("Clouds"));state.addProperty("nativeCloudsIT",options.get("cloudsIT"));
        var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var originalPath=dir.resolve(original).normalize();
        if(!com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.validate(originalPath,com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("Sildur original source certificate changed");
        if(stageGenerated()){
            String selected=Iris.getCurrentPackName();var generated=dir.resolve(selected).normalize();
            if(!generated.startsWith(dir)||generated.equals(dir)||Files.isSymbolicLink(generated))throw new IllegalStateException("Unsafe Sildur generated source path");
            try(var zip=new java.util.zip.ZipFile(generated.toFile())){
                var entry=zip.getEntry("shaderlod-selection.json");if(entry==null)throw new IllegalStateException("Generated Sildur selection marker absent");
                JsonObject marker;try(var in=zip.getInputStream(entry)){marker=com.google.gson.JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
                String recipeSha;try(var in=com.pp.shaderlod.packs.PackSelectionService.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/sildurs-vibrant-shaders.json")){
                    if(in==null)throw new IllegalStateException("Actual packaged Sildur recipe absent");recipeSha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
                }
                if(marker.get("schema").getAsInt()!=1||!"sildurs-vibrant-shaders".equals(marker.get("adapterId").getAsString())||!original.equals(marker.get("originalFile").getAsString())||!originalHash.equals(marker.get("originalSha256").getAsString())||!recipeSha.equals(marker.get("recipeSha256").getAsString()))throw new IllegalStateException("Generated Sildur source/recipe marker mismatch");
                String stateFile=marker.get("stateFile").getAsString();var statePath=dir.resolve(stateFile).normalize();
                if(!Path.of(stateFile).getFileName().toString().equals(stateFile)||!statePath.startsWith(dir)||statePath.equals(dir)||Files.isSymbolicLink(statePath))throw new IllegalStateException("Unsafe Sildur selection state reference");
                var selection=com.google.gson.JsonParser.parseString(Files.readString(statePath)).getAsJsonObject();String generatedSha=hash(generated);
                if(!selection.get("active").getAsBoolean()||!selected.equals(selection.get("generatedFile").getAsString())||!generatedSha.equals(selection.get("generatedSha256").getAsString())||!original.equals(selection.get("originalFile").getAsString())||!originalHash.equals(selection.get("originalSha256").getAsString())||!recipeSha.equals(selection.get("recipeSha256").getAsString()))throw new IllegalStateException("Generated Sildur archive differs from real public Apply selection state");
                state.add("generatedSelectionMarker",marker);state.addProperty("generatedArchiveSha256",generatedSha);state.addProperty("generatedSelectionStateMatched",true);
            }
        }
        state.addProperty("loadedOptionCount",options.size());
        state.add("generatedDeclaredOptions",GSON.toJsonTree(stageGenerated()?Map.of("SHADERLOD_EFFECT_ADAPTER","1","SHADERLOD_EFFECT_ADAPTER_INCLUDED","true","SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true"):Map.of()));
        state.addProperty("sildurVolumeControl",true);state.addProperty("expectedPreset",expectedPreset.name());state.addProperty("underlyingNative115Unchanged",true);state.addProperty("generatedSildurRecipeSelected",stageGenerated());state.addProperty("ownedEffectFlag",stageGenerated()?"1":"absent");state.addProperty("rendererEligible",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());state.addProperty("linkedUniformReadbackPerformed",false);state.addProperty("avoidedWorkMeasured",false);
    }

    private void validateBlissVolumeState(Minecraft client,Map<String,String> options,JsonObject state)throws Exception{
        var cfg=coreState();var expectedPreset=stage==2?LodPreset.QUALITY:LodPreset.BALANCED;
        if(initial==null||initial.enabled()||initial.settings().debug()||initial.preset()!=LodPreset.BALANCED||cfg.enabled()!=stageEnabled()||cfg.preset()!=expectedPreset||cfg.settings().debug()||!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Bliss fallback Core/preset/nativequeue mismatch");
        var expected=new TreeMap<>(com.pp.shaderlod.integration.blissquad.BlissQuadEligibility.nativeOptions());
        if(stageGenerated()){
            expected.put("SHADERLOD_EFFECT_ADAPTER","1");
            // Exact hash-pinned helper #ifndef/#define guards also appear as Iris BooleanOptions.
            expected.put("SHADERLOD_EFFECT_ADAPTER_INCLUDED","true");
            expected.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");
        }
        if(!expected.equals(options))throw new IllegalStateException("Bliss fallback requires native463 unchanged plus owned flag and exact two parsed helper guards on generated stage");
        if(!"true".equals(options.get("TAA"))||!"true".equals(options.get("HQ_CLOUDS"))||!"1".equals(options.get("cloud_LevelOfDetail"))||!"false".equals(options.get("TAA_UPSCALING")))throw new IllegalStateException("Bliss native TAA/HQ_CLOUDS/LoD1/native upscale changed");
        if(state==null)return;
        state.addProperty("nativeTaa",options.get("TAA"));state.addProperty("nativeHqClouds",options.get("HQ_CLOUDS"));state.addProperty("nativeCloudLevelOfDetail",options.get("cloud_LevelOfDetail"));state.addProperty("nativeTaaUpscaling",options.get("TAA_UPSCALING"));
        var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var originalPath=dir.resolve(original).normalize();
        if(!com.pp.shaderlod.integration.blissquad.BlissQuadEligibility.validate(originalPath,com.pp.shaderlod.integration.blissquad.BlissQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("Bliss original source certificate changed");
        if(stageGenerated()){
            String selected=Iris.getCurrentPackName();var generated=dir.resolve(selected).normalize();
            if(!generated.startsWith(dir)||generated.equals(dir)||Files.isSymbolicLink(generated))throw new IllegalStateException("Unsafe Bliss generated source path");
            try(var zip=new java.util.zip.ZipFile(generated.toFile())){
                var entry=zip.getEntry("shaderlod-selection.json");if(entry==null)throw new IllegalStateException("Generated Bliss selection marker absent");
                JsonObject marker;try(var in=zip.getInputStream(entry)){marker=com.google.gson.JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
                String recipeSha;try(var in=com.pp.shaderlod.packs.PackSelectionService.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/bliss-shader.json")){
                    if(in==null)throw new IllegalStateException("Actual packaged Bliss recipe absent");recipeSha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
                }
                if(marker.get("schema").getAsInt()!=1||!"bliss-shader".equals(marker.get("adapterId").getAsString())||!original.equals(marker.get("originalFile").getAsString())||!originalHash.equals(marker.get("originalSha256").getAsString())||!recipeSha.equals(marker.get("recipeSha256").getAsString()))throw new IllegalStateException("Generated Bliss source/recipe marker mismatch");
                String stateFile=marker.get("stateFile").getAsString();var statePath=dir.resolve(stateFile).normalize();
                if(!Path.of(stateFile).getFileName().toString().equals(stateFile)||!statePath.startsWith(dir)||statePath.equals(dir)||Files.isSymbolicLink(statePath))throw new IllegalStateException("Unsafe Bliss selection state reference");
                var selection=com.google.gson.JsonParser.parseString(Files.readString(statePath)).getAsJsonObject();String generatedSha=hash(generated);
                if(!selection.get("active").getAsBoolean()||!selected.equals(selection.get("generatedFile").getAsString())||!generatedSha.equals(selection.get("generatedSha256").getAsString())||!original.equals(selection.get("originalFile").getAsString())||!originalHash.equals(selection.get("originalSha256").getAsString())||!recipeSha.equals(selection.get("recipeSha256").getAsString()))throw new IllegalStateException("Generated Bliss archive differs from real public Apply selection state");
                state.add("generatedSelectionMarker",marker);state.addProperty("generatedArchiveSha256",generatedSha);state.addProperty("generatedSelectionStateMatched",true);
            }
        }
        state.addProperty("loadedOptionCount",options.size());
        state.add("generatedDeclaredOptions",GSON.toJsonTree(stageGenerated()?Map.of("SHADERLOD_EFFECT_ADAPTER","1","SHADERLOD_EFFECT_ADAPTER_INCLUDED","true","SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true"):Map.of()));
        state.addProperty("blissVolumeControl",true);state.addProperty("expectedPreset",expectedPreset.name());state.addProperty("underlyingNative463Unchanged",true);state.addProperty("generatedBlissRecipeSelected",stageGenerated());state.addProperty("ownedEffectFlag",stageGenerated()?"1":"absent");state.addProperty("rendererEligible",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());state.addProperty("linkedUniformReadbackPerformed",false);state.addProperty("avoidedWorkMeasured",false);
    }

    private void validatePhotonVolumeState(Minecraft client,Map<String,String> options,JsonObject state)throws Exception{
        var cfg=coreState();var expectedPreset=stage==2?LodPreset.QUALITY:LodPreset.BALANCED;
        if(initial==null||initial.enabled()||initial.settings().debug()||initial.preset()!=LodPreset.BALANCED||cfg.enabled()!=stageEnabled()||cfg.preset()!=expectedPreset||cfg.settings().debug()||!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Photon fallback Core/preset/nativequeue mismatch");
        var expected=new TreeMap<>(com.pp.shaderlod.integration.bindingcompat.PhotonSourceCertificate.configuredOptions());
        if(stageGenerated()){
            expected.put("SHADERLOD_LIVE_PIXEL_BUDGET","true");
            // Exact hash-pinned helper #ifndef/#define guards also appear as Iris BooleanOptions.
            expected.put("SHADERLOD_LIVE_PIXEL_BUDGET_INCLUDED","true");
            expected.put("SHADERLOD_RENDER_BUDGET_INCLUDED","true");
        }
        if(!expected.equals(options))throw new IllegalStateException("Photon fallback requires configured366 unchanged plus owned flag and exact two parsed helper guards on generated stage");
        if(!"true".equals(options.get("TAA"))||!"false".equals(options.get("TAAU"))||!"1.00".equals(options.get("TAAU_RENDER_SCALE"))||!"false".equals(options.get("SH_SKYLIGHT")))throw new IllegalStateException("Photon configured TAA/TAAU/latent scale/skylight changed");
        if(client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD)throw new IllegalStateException("Photon configured control requires Overworld");
        if(state==null)return;
        state.addProperty("nativeTaa",options.get("TAA"));state.addProperty("nativeTaau",options.get("TAAU"));state.addProperty("nativeTaauRenderScale",options.get("TAAU_RENDER_SCALE"));state.addProperty("configuredShSkylight",options.get("SH_SKYLIGHT"));
        var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var originalPath=dir.resolve(original).normalize();
        if(!com.pp.shaderlod.integration.photonquad.PhotonQuadEligibility.validate(originalPath,com.pp.shaderlod.integration.photonquad.PhotonQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("Photon original source certificate changed");
        if(stageGenerated()){
            String selected=Iris.getCurrentPackName();var generated=dir.resolve(selected).normalize();
            if(!generated.startsWith(dir)||generated.equals(dir)||Files.isSymbolicLink(generated))throw new IllegalStateException("Unsafe Photon generated source path");
            try(var zip=new java.util.zip.ZipFile(generated.toFile())){
                var entry=zip.getEntry("shaderlod-selection.json");if(entry==null)throw new IllegalStateException("Generated Photon selection marker absent");
                JsonObject marker;try(var in=zip.getInputStream(entry)){marker=com.google.gson.JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
                String recipeSha;try(var in=com.pp.shaderlod.packs.PackSelectionService.class.getResourceAsStream("/assets/shaderlod/adapters/patches/photon-experiments/live-pixel-budget.json")){
                    if(in==null)throw new IllegalStateException("Actual packaged Photon recipe absent");recipeSha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
                }
                if(marker.get("schema").getAsInt()!=1||!"photon".equals(marker.get("adapterId").getAsString())||!original.equals(marker.get("originalFile").getAsString())||!originalHash.equals(marker.get("originalSha256").getAsString())||!recipeSha.equals(marker.get("recipeSha256").getAsString()))throw new IllegalStateException("Generated Photon source/recipe marker mismatch");
                String stateFile=marker.get("stateFile").getAsString();var statePath=dir.resolve(stateFile).normalize();
                if(!Path.of(stateFile).getFileName().toString().equals(stateFile)||!statePath.startsWith(dir)||statePath.equals(dir)||Files.isSymbolicLink(statePath))throw new IllegalStateException("Unsafe Photon selection state reference");
                var selection=com.google.gson.JsonParser.parseString(Files.readString(statePath)).getAsJsonObject();String generatedSha=hash(generated);
                if(!selection.get("active").getAsBoolean()||!selected.equals(selection.get("generatedFile").getAsString())||!generatedSha.equals(selection.get("generatedSha256").getAsString())||!original.equals(selection.get("originalFile").getAsString())||!originalHash.equals(selection.get("originalSha256").getAsString())||!recipeSha.equals(selection.get("recipeSha256").getAsString()))throw new IllegalStateException("Generated Photon archive differs from real public Apply selection state");
                state.add("generatedSelectionMarker",marker);state.addProperty("generatedArchiveSha256",generatedSha);state.addProperty("generatedSelectionStateMatched",true);
            }
        }
        state.addProperty("loadedOptionCount",options.size());
        state.add("generatedDeclaredOptions",GSON.toJsonTree(stageGenerated()?Map.of("SHADERLOD_LIVE_PIXEL_BUDGET","true","SHADERLOD_LIVE_PIXEL_BUDGET_INCLUDED","true","SHADERLOD_RENDER_BUDGET_INCLUDED","true"):Map.of()));
        state.addProperty("photonVolumeControl",true);state.addProperty("expectedPreset",expectedPreset.name());state.addProperty("underlyingConfigured366Unchanged",true);state.addProperty("generatedPhotonRecipeSelected",stageGenerated());state.addProperty("ownedEffectFlag",stageGenerated()?"true":"absent");state.addProperty("rendererEligible",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());state.addProperty("linkedUniformReadbackPerformed",false);state.addProperty("avoidedWorkMeasured",false);
    }

    private boolean stageEnabled() { return volumeControl ? stage==1||stage==2 : qualityRoundtrip ? stage > 0 && stage < stages.length - 1 : stage == 1; }
    private boolean stageGenerated() { return volumeControl ? stage==1 : qualityRoundtrip ? stage == 6 : stage == 1; }

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
        if(!profile.endsWith(Path.of("run-workloads","port-262-runtime-r1","neoforge")) || !profile.equals(profile.toRealPath()))
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
        if(originalOnly) { report.add("rendererAdmissionAtFailure",RendererAdmissionDiagnostics.observe(client));report.addProperty("productionApplyPerformed",false);report.addProperty("timedWindowPerformed",false); }
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
            try { report.addProperty("rendererFailureRestoreReturned", ShaderLodSettings.apply(initial.enabled(),initial.preset())); }
            catch(Exception recovery) { report.addProperty("rendererFailureRestoreError",recovery.toString()); }
        }
        observeWaiting(client, "failureObservation");
        write(client);
        LOGGER.error("NeoForge roundtrip failed; client remains open; receipt={}", evidence(client), failure);
    }
}
