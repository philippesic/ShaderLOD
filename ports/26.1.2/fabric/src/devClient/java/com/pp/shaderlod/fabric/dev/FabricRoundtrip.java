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
    private long measuredSince, measuredFrames;
    private long[] measuredCommands, stageCommands = new long[4];
    private JsonObject speedPose;
    private String speedSettings;
    private final String nativeFamilyMode = System.getProperty("shaderlod.dev.portNativeFamily", "");
    private final String fpsFamily=PortFpsPolicy.family(nativeFamilyMode);
    private final boolean fpsProbe=!fpsFamily.isEmpty();
    private boolean fpsWindowComplete;
    private JsonObject fpsEvidence;
    private final boolean bslFallback = "bsl-fallback".equals(nativeFamilyMode);
    private final boolean bslVolume = "bsl-volume".equals(nativeFamilyMode);
    private final boolean photonCloud = "photon-cloud".equals(nativeFamilyMode);
    private final boolean photonCost = PortFpsPolicy.opticalCostRequested();
    private final boolean frameProbe = fpsProbe || photonCost;
    private final boolean blissCloud = "bliss-cloud".equals(nativeFamilyMode);
    private final boolean sildurCloud = "sildur-cloud".equals(nativeFamilyMode);
    private final boolean makeupVolume = "makeup-volume".equals(nativeFamilyMode);
    private final boolean complementaryVolume = "reimagined-volume".equals(nativeFamilyMode) || "unbound-volume".equals(nativeFamilyMode);
    private final String complementaryVolumeFamily = "unbound-volume".equals(nativeFamilyMode) ? "unbound" : "reimagined";
    private final boolean nativeFamily = !nativeFamilyMode.isBlank() && !bslFallback && !bslVolume && !makeupVolume && !sildurCloud && !blissCloud && !photonCloud && !complementaryVolume && !fpsProbe;
    private Object nativeCertifiedPack, nativeCertifiedPipeline;
    private String nativeArchiveStamp;
    private long nativeCertifiedGeneration = -1;
    private long nativeCertificateValidations;
    private JsonObject nativeOriginalStageSidecar;
    private final boolean qualityRoundtrip = Boolean.getBoolean("shaderlod.dev.portQualityRoundtrip");
    private final String[] stages = photonCost ? new String[]{"original","on","off"} : fpsProbe ? new String[]{"original","performance","balanced","quality","off"} : bslFallback || bslVolume || makeupVolume || sildurCloud || blissCloud || photonCloud || complementaryVolume ? new String[]{"original","on","quality","off"} : nativeFamily ? ReimaginedNativeControlChecks.stages() : qualityRoundtrip
            ? new String[]{"original", "quality", "native-info-edit", "native-info-restored", "native-upscaled",
                    "native-aa-restored", "performance", "quality-return", "off"}
            : new String[]{"original", "on", "off"};
    private final String run = Long.toString(System.currentTimeMillis());
    private final String original = System.getProperty("shaderlod.dev.portRoundtripOriginal", "");
    private final JsonObject report = new JsonObject();
    private final long started = System.nanoTime();
    private final BackupPrompt backupPrompt = new BackupPrompt("fabric", run, report);
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
    private long[] nativeStageCommandBaseline;
    private int stage;
    private boolean screenshotPending;
    private boolean advance;
    private boolean done;
    private FabricLodScreen ui;
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
        if (Boolean.getBoolean("shaderlod.dev.portPhotonOpticalCost") && (!probe.photonCost || Boolean.getBoolean("shaderlod.dev.portRoundtripUi"))) {
            LOGGER.error("Photon optical cost requires photon-cloud roundtrip and no UI roundtrip"); return;
        }
        if(probe.fpsProbe){
            try{
                for(String flag:new String[]{"shaderlod.dev.portRendererSpeedProbe","shaderlod.dev.portQualityRoundtrip","shaderlod.rendererSpeedPortR1",com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES})if(!"false".equals(System.getProperty(flag,"false")))throw new IllegalStateException("FPS mode conflict: "+flag);
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var archive=dir.resolve(probe.original).normalize();
                if(!archive.startsWith(dir)||archive.equals(dir)||Files.isSymbolicLink(archive)||!PortFpsPolicy.sourceMatches(probe.fpsFamily,archive))throw new IllegalStateException("FPS named original certificate refused");
            }catch(Exception failure){LOGGER.error("FPS mode refused before registration",failure);return;}
        }
        if(probe.bslFallback){
            try{
                for(String flag:new String[]{"shaderlod.dev.portRendererSpeedProbe","shaderlod.dev.portQualityRoundtrip","shaderlod.rendererSpeedPortR1",com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES})
                    if(!"false".equals(System.getProperty(flag,"false")))throw new IllegalStateException("BSL fallback conflicts with "+flag);
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var archive=dir.resolve(probe.original).normalize();
                if(!archive.startsWith(dir)||archive.equals(dir)||Files.isSymbolicLink(archive)||!com.pp.shaderlod.integration.bslquad.BslQuadEligibility.validate(archive,com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("Certified original BSL10.1.8/native384/resources required");
            }catch(Exception failure){LOGGER.error("BSL fallback control refused before registration",failure);return;}
        }
        if(probe.bslVolume){
            try{
                for(String flag:new String[]{"shaderlod.dev.portRendererSpeedProbe","shaderlod.dev.portQualityRoundtrip","shaderlod.rendererSpeedPortR1",com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES})
                    if(!"false".equals(System.getProperty(flag,"false")))throw new IllegalStateException("BSL volume conflicts with "+flag);
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var archive=dir.resolve(probe.original).normalize();
                if(!archive.startsWith(dir)||archive.equals(dir)||Files.isSymbolicLink(archive)||!com.pp.shaderlod.integration.bslquad.BslQuadEligibility.validate(archive,com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("Certified original BSL10.1.8/native384/resources required");
            }catch(Exception failure){LOGGER.error("BSL volume control refused before registration",failure);return;}
        }
        if(probe.makeupVolume){
            try{
                for(String flag:new String[]{"shaderlod.dev.portRendererSpeedProbe","shaderlod.dev.portQualityRoundtrip","shaderlod.rendererSpeedPortR1",com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES})
                    if(!"false".equals(System.getProperty(flag,"false")))throw new IllegalStateException("MakeUp volume conflicts with "+flag);
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var archive=dir.resolve(probe.original).normalize();
                if(!archive.startsWith(dir)||archive.equals(dir)||Files.isSymbolicLink(archive)||!com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.validate(archive,com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("Certified original MakeUp9.5g/native100/resources required");
            }catch(Exception failure){LOGGER.error("MakeUp volume control refused before registration",failure);return;}
        }
        if(probe.sildurCloud){
            try{
                for(String flag:new String[]{"shaderlod.dev.portRendererSpeedProbe","shaderlod.dev.portQualityRoundtrip","shaderlod.rendererSpeedPortR1",com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES})
                    if(!"false".equals(System.getProperty(flag,"false")))throw new IllegalStateException("Sildur cloud conflicts with "+flag);
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var archive=dir.resolve(probe.original).normalize();
                if(!archive.startsWith(dir)||archive.equals(dir)||Files.isSymbolicLink(archive)||!com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.validate(archive,com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("Certified original Sildur2.02Extreme/native115/resources required");
            }catch(Exception failure){LOGGER.error("Sildur cloud control refused before registration",failure);return;}
        }
        if(probe.blissCloud){
            try{
                for(String flag:new String[]{"shaderlod.dev.portRendererSpeedProbe","shaderlod.dev.portQualityRoundtrip","shaderlod.rendererSpeedPortR1",com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES})
                    if(!"false".equals(System.getProperty(flag,"false")))throw new IllegalStateException("Bliss cloud conflicts with "+flag);
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var archive=dir.resolve(probe.original).normalize();
                if(!archive.startsWith(dir)||archive.equals(dir)||Files.isSymbolicLink(archive)||!com.pp.shaderlod.integration.blissquad.BlissQuadEligibility.validate(archive,com.pp.shaderlod.integration.blissquad.BlissQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("Certified original Bliss2.1.2/native463/resources required");
            }catch(Exception failure){LOGGER.error("Bliss cloud control refused before registration",failure);return;}
        }
        if(probe.photonCloud){
            try{
                for(String flag:new String[]{"shaderlod.dev.portRendererSpeedProbe","shaderlod.dev.portQualityRoundtrip","shaderlod.rendererSpeedPortR1",com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES})
                    if(!"false".equals(System.getProperty(flag,"false")))throw new IllegalStateException("Photon cloud conflicts with "+flag);
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var archive=dir.resolve(probe.original).normalize();
                if(!archive.startsWith(dir)||archive.equals(dir)||Files.isSymbolicLink(archive)||!com.pp.shaderlod.integration.photonquad.PhotonQuadEligibility.validate(archive,com.pp.shaderlod.integration.photonquad.PhotonQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("Certified original Photon1.3b/source/native366/resources required");
            }catch(Exception failure){LOGGER.error("Photon cloud control refused before registration",failure);return;}
        }
        if(probe.complementaryVolume){
            try{
                for(String flag:new String[]{"shaderlod.dev.portRendererSpeedProbe","shaderlod.dev.portQualityRoundtrip","shaderlod.rendererSpeedPortR1",com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES})
                    if(!"false".equals(System.getProperty(flag,"false")))throw new IllegalStateException("Complementary volume conflicts with "+flag);
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var archive=dir.resolve(probe.original).normalize();
                if(!archive.startsWith(dir)||archive.equals(dir)||Files.isSymbolicLink(archive)||!ReimaginedNativeControlChecks.sourceMatches(probe.complementaryVolumeFamily,archive,ReimaginedNativeControlChecks.nativeOptions(probe.complementaryVolumeFamily)))throw new IllegalStateException("Certified original Named Complementary r5.9.3/native398/resources required");
            }catch(Exception failure){LOGGER.error("Complementary volume control refused before registration",failure);return;}
        }
        if (probe.nativeFamily && !ReimaginedNativeControlChecks.launchAllows(probe.nativeFamilyMode,
                System.getProperty("shaderlod.dev.portRendererSpeedProbe","false"), System.getProperty("shaderlod.dev.portQualityRoundtrip","false"),
                System.getProperty("shaderlod.rendererSpeedPortR1", "false"),
                System.getProperty(com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES, "false"))) {
            LOGGER.error("Native R/U control requires explicit reimagined or unbound mode, no Photon Quality/speed/candidate/research route");
            return;
        }
        if (probe.nativeFamily) {
            try {
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var archive=dir.resolve(probe.original).normalize();
                if(!archive.startsWith(dir)||archive.equals(dir)||Files.isSymbolicLink(archive)||
                        !ReimaginedNativeControlChecks.sourceMatches(probe.nativeFamilyMode,archive,
                            ReimaginedNativeControlChecks.nativeOptions(probe.nativeFamilyMode)))
                    throw new IllegalStateException("Pinned named original R/U r5.9.3/native398 source/resources required");
            } catch(Exception failure) {LOGGER.error("Named native family control refused before registration",failure);return;}
        }
        if (probe.rendererSpeed && (probe.qualityRoundtrip || Boolean.getBoolean("shaderlod.dev.portRoundtripUi")
                || !Boolean.getBoolean("shaderlod.rendererSpeedPortR1"))) {
            LOGGER.error("Renderer speed probe requires candidate opt-in and no Quality/UI roundtrip");
            return;
        }
        probe.report.addProperty("photonOpticalCostProbe",probe.photonCost);
        if(probe.photonCost){probe.report.add("stageNames",GSON.toJsonTree(probe.stages));probe.report.addProperty("settleSeconds",30);probe.report.addProperty("measurementSeconds",15);probe.report.addProperty("fullWorkControlPerformed",false);probe.report.addProperty("costEvidenceBoundary","Completed render frames per wall-clock window; no GPU timing or optical-depth attribution");}
        probe.report.addProperty("fpsMeasurementProbe",probe.fpsProbe);
        if(probe.fpsProbe){probe.report.addProperty("fpsFamily",probe.fpsFamily);probe.report.add("stageNames",GSON.toJsonTree(probe.stages));probe.report.addProperty("settleSeconds",40);probe.report.addProperty("measurementSeconds",30);probe.report.addProperty("internalDeadlineSeconds",600);}
        probe.report.addProperty("rendererSpeedProbe", probe.rendererSpeed);
        probe.report.addProperty("qualityRoundtrip", probe.qualityRoundtrip);
        probe.report.addProperty("nativeFamilyControl",probe.nativeFamily);
        probe.report.addProperty("bslFallbackControl",probe.bslFallback);
        probe.report.addProperty("bslVolumeControl",probe.bslVolume);
        if(probe.bslVolume){probe.report.addProperty("bslVolumeMode","bsl-volume");probe.report.add("stageNames",GSON.toJsonTree(probe.stages));probe.report.addProperty("rendererWorkMeasurementRequested",false);probe.report.addProperty("referenceNativeAuroraEnabled",false);}
        probe.report.addProperty("complementaryVolumeControl",probe.complementaryVolume);
        if(probe.complementaryVolume){probe.report.addProperty("complementaryVolumeFamily",probe.complementaryVolumeFamily);probe.report.add("stageNames",GSON.toJsonTree(probe.stages));probe.report.addProperty("rendererWorkMeasurementRequested",false);}
        probe.report.addProperty("photonCloudControl",probe.photonCloud);
        if(probe.photonCloud){probe.report.addProperty("photonCloudMode","photon-cloud");probe.report.add("stageNames",GSON.toJsonTree(probe.stages));probe.report.addProperty("rendererWorkMeasurementRequested",false);}
        probe.report.addProperty("blissCloudControl",probe.blissCloud);
        if(probe.blissCloud){probe.report.addProperty("blissCloudMode","bliss-cloud");probe.report.add("stageNames",GSON.toJsonTree(probe.stages));probe.report.addProperty("rendererWorkMeasurementRequested",false);}
        probe.report.addProperty("sildurCloudControl",probe.sildurCloud);
        if(probe.sildurCloud){probe.report.addProperty("sildurCloudMode","sildur-cloud");probe.report.add("stageNames",GSON.toJsonTree(probe.stages));probe.report.addProperty("rendererWorkMeasurementRequested",false);}
        probe.report.addProperty("makeupVolumeControl",probe.makeupVolume);
        if(probe.makeupVolume){probe.report.addProperty("makeupVolumeMode","makeup-volume");probe.report.add("stageNames",GSON.toJsonTree(probe.stages));probe.report.addProperty("rendererWorkMeasurementRequested",false);}
        if(probe.bslFallback){probe.report.addProperty("bslFallbackMode","bsl-fallback");probe.report.add("stageNames",GSON.toJsonTree(probe.stages));probe.report.addProperty("rendererWorkMeasurementRequested",false);}
        if(probe.nativeFamily){probe.report.addProperty("nativeFamily",probe.nativeFamilyMode);probe.report.add("stageNames",GSON.toJsonTree(probe.stages));probe.report.addProperty("rendererWorkMeasurementRequested",true);probe.report.addProperty("rendererWorkObservationKind","actual production compact RETURN counts only; no timing or GL submission attribution");}
        probe.report.addProperty("run", probe.run);
        probe.report.addProperty("originalPack", probe.original);
        probe.report.addProperty("minecraftTarget", "26.1.2");
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
            if ((rendererSpeed || frameProbe&&measuredSince!=0) && com.pp.shaderlod.port.dev.PortRendererFrames.invalidMeasuredFrames() != 0)
                throw new IllegalStateException("Measured frame limiter violation: "
                        + com.pp.shaderlod.port.dev.PortRendererFrames.firstLimiterViolation());
            if (System.nanoTime() - started > (fpsProbe?600_000_000_000L:300_000_000_000L)) throw new IllegalStateException("Finite "+(fpsProbe?600:300)+" s probe deadline exceeded");
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
                if(fpsProbe&&(initial.debug()||initial.preset()!=LodPreset.PERFORMANCE))throw new IllegalStateException("FPS original requires Off/Performance/debugfalse");
                if((bslFallback||bslVolume||makeupVolume||sildurCloud||blissCloud||photonCloud||complementaryVolume)&&(initial.debug()||initial.preset()!=LodPreset.BALANCED))throw new IllegalStateException("BSL fallback baseline requires native Off/Balanced/debugfalse");
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
                boolean applied = fpsProbe ? applyFpsStage() : bslVolume ? applyBslVolumeStage() : complementaryVolume ? applyComplementaryVolumeStage() : photonCloud ? applyPhotonCloudStage() : blissCloud ? applyBlissCloudStage() : sildurCloud ? applySildurCloudStage() : makeupVolume ? applyMakeupVolumeStage() : bslFallback ? applyBslFallbackStage() : nativeFamily ? applyNativeFamilyStage(client) : qualityRoundtrip ? applyQualityStage(client) : FabricLodSettings.apply(enabled, rendererSpeed ? LodPreset.PERFORMANCE : initial.preset());
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
            startupWorldAccepted = true;
            report.addProperty("startupEligibleShaderWorldEntered", true);
            Map<String, String> options = loadedOptions();
            String signature = selected + options.toString() + ":" + target.width + "x" + target.height;
            if (settledSince == 0 || !signature.equals(stableSignature)) {
                stableSignature = signature;
                resetWindow();
                settledSince = System.nanoTime();
                if(nativeFamily)nativeStageCommandBaseline=com.pp.shaderlod.port.dev.PortRendererFrames.commands();
                return;
            }
            if(fpsProbe)validateFpsState(client,options,null);
            if(complementaryVolume)validateComplementaryVolumeState(client,options,null);
            if(makeupVolume)validateMakeupVolumeState(client,options,null);
            if(sildurCloud)validateSildurCloudState(client,options,null);
            if(blissCloud)validateBlissCloudState(client,options,null);
            if(photonCloud)validatePhotonCloudState(client,options,null);
            if(photonCost)validatePhotonCostState(client);
            if(bslVolume)validateBslVolumeState(client,options,null);
            if(bslFallback)validateBslFallbackState(client,options,null);
            if (nativeFamily) validateNativeFamilyState(client,options,null);
            if (rendererSpeed) validateSpeedState(client, options);
            if (System.nanoTime() - settledSince < (rendererSpeed || fpsProbe ? 40_000_000_000L
                    : qualityRoundtrip && stage > 0 ? 3_000_000_000L : SETTLE)) return;
            if(frameProbe&&!fpsWindowComplete){
                if(measuredSince==0){
                    var source=new JsonObject();if(photonCost)validatePhotonCloudState(client,options,source);else validateFpsState(client,options,source);report.add(stages[stage]+"SourceBeforeMeasurement",source);
                    measuredFrames=com.pp.shaderlod.port.dev.PortRendererFrames.frames();measuredCommands=com.pp.shaderlod.port.dev.PortRendererFrames.commands();
                    com.pp.shaderlod.port.dev.PortRendererFrames.beginFpsWindow();measuredSince=System.nanoTime();return;
                }
                if(System.nanoTime()-measuredSince<(photonCost?15_000_000_000L:30_000_000_000L))return;
                finishFpsWindow();
            }
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
            if(fpsProbe)validateFpsState(client,options,state);
            if(complementaryVolume)validateComplementaryVolumeState(client,options,state);
            if(makeupVolume)validateMakeupVolumeState(client,options,state);
            if(sildurCloud)validateSildurCloudState(client,options,state);
            if(blissCloud)validateBlissCloudState(client,options,state);
            if(photonCloud)validatePhotonCloudState(client,options,state);
            if(bslVolume)validateBslVolumeState(client,options,state);
            if(bslFallback)validateBslFallbackState(client,options,state);
            if (nativeFamily) validateNativeFamilyState(client,options,state);
            if (qualityRoundtrip) validateQualityObservation(client, options, state);
            if (stage == stages.length - 1) {
                state.addProperty("originalLoadedOptionsRestored", originalOptions.equals(options));
                state.addProperty("originalCoreConfigRestored", initial.equals(FabricLodConfig.snapshot()));
            }
            if(photonCost&&stage==stages.length-1){
                double baseline=report.getAsJsonObject("original").getAsJsonObject("fpsWindow").get("meanElapsedFrameMs").getAsDouble();
                state.addProperty("originalReturnMeanFrameMsDriftPercent",100*(fpsEvidence.get("meanElapsedFrameMs").getAsDouble()/baseline-1));
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
        if(nativeFamily)state.add("nativeSidecarBeforeCancel",nativeSidecarObservation());
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
                if(nativeFamily){var afterSidecar=nativeSidecarObservation();state.add("nativeSidecarAfterCancel",afterSidecar);
                    Boolean byteState=nativeSidecarByteMatch(state.getAsJsonObject("nativeSidecarBeforeCancel"),afterSidecar);
                    state.addProperty("cancelKeptObservedSidecarBytes",byteState);state.addProperty("cancelSidecarRecoveryObservation",byteState==null?"unverified":byteState?"unchanged-observed-bytes":"changed-observed-bytes");}
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
        com.pp.shaderlod.integration.RendererBackend.requireApplySupported();
        String backend = com.pp.shaderlod.integration.RendererBackend.observedName();
        var state = new JsonObject();
        state.addProperty("capturedAtUtc", Instant.now().toString());
        state.add("mainArtifactOrigins",mainArtifactOrigins());
        state.addProperty("stage", stages[stage]);
        if(frameProbe&&fpsEvidence!=null)state.add("fpsWindow",fpsEvidence.deepCopy());
        state.addProperty("stableWorldSeconds", (System.nanoTime() - settledSince) / 1_000_000_000.0);
        if(nativeFamily && nativeStageCommandBaseline!=null && settledSince!=0 && !done){
            long[] commands=com.pp.shaderlod.port.dev.PortRendererFrames.commands();
            long calls=commands[0]-nativeStageCommandBaseline[0],input=commands[1]-nativeStageCommandBaseline[1];
            long output=commands[2]-nativeStageCommandBaseline[2],reduced=commands[3]-nativeStageCommandBaseline[3];
            if(calls<0||input<0||output<0||reduced<0||reduced>calls||output>input)
                throw new IllegalStateException("Invalid native-family compact RETURN counter deltas");
            boolean performance=stage==2;
            boolean avoided=calls>0&&input>output&&reduced>0;
            if(!performance&&(calls!=0||input!=0||output!=0||reduced!=0))
                throw new IllegalStateException("Native Off/Quality unexpectedly invoked production compactor");
            var work=new JsonObject();work.addProperty("boundary","AdjacentQuadCoalescer.compact RETURN");
            work.add("baseline",GSON.toJsonTree(nativeStageCommandBaseline));work.add("current",GSON.toJsonTree(commands));
            work.addProperty("calls",calls);work.addProperty("inputCommands",input);work.addProperty("outputCommands",output);
            work.addProperty("reducedBatches",reduced);work.addProperty("removedCommands",input-output);
            work.addProperty("steadyWindowSeconds",(System.nanoTime()-settledSince)/1_000_000_000.0);
            work.addProperty("performanceStage",performance);work.addProperty("actualAvoidedCommandsObserved",avoided);
            work.addProperty("workStatus",performance?(avoided?"actual-compact-command-reduction":"no-avoided-work-observed"):"native-no-compactor-calls");
            work.addProperty("rendererEligible",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());
            work.addProperty("isGlSubmissionOrFpsEvidence",false);state.add("nativeStageCommandObservation",work);
        }

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


    private LodPreset fpsPreset(){return stage==2?LodPreset.BALANCED:stage==3?LodPreset.QUALITY:LodPreset.PERFORMANCE;}
    private boolean applyFpsStage(){boolean applied=FabricLodSettings.apply(stageEnabled(),fpsPreset());if(!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("FPS Apply queue not empty");return applied;}
    private void finishFpsWindow(){
        long now=System.nanoTime(),count=com.pp.shaderlod.port.dev.PortRendererFrames.frames()-measuredFrames;double seconds=(now-measuredSince)/1_000_000_000.0;
        com.pp.shaderlod.port.dev.PortRendererFrames.endWindow();
        if(count<=0||seconds<(photonCost?15:30)||com.pp.shaderlod.port.dev.PortRendererFrames.invalidMeasuredFrames()!=0)throw new IllegalStateException("Missing/invalid completed FPS window");
        long[] commands=com.pp.shaderlod.port.dev.PortRendererFrames.commands();var rate=new JsonObject();
        rate.addProperty("windowStartNanoTime",measuredSince);rate.addProperty("windowEndNanoTime",now);rate.addProperty("frameCounterStart",measuredFrames);rate.addProperty("frameCounterEnd",measuredFrames+count);rate.addProperty("counterBoundary","GameRenderer.render RETURN with renderLevel/currentworld/loadfinished");rate.addProperty("completedRenderFrames",count);rate.addProperty("elapsedSeconds",seconds);rate.addProperty("fps",count/seconds);rate.addProperty("meanElapsedFrameMs",1000*seconds/count);
        rate.addProperty("configuredFramerateLimit",260);rate.addProperty("minimumEffectiveFramerateLimit",com.pp.shaderlod.port.dev.PortRendererFrames.minEffective());rate.addProperty("maximumEffectiveFramerateLimit",com.pp.shaderlod.port.dev.PortRendererFrames.maxEffective());rate.addProperty("invalidMeasuredFrames",com.pp.shaderlod.port.dev.PortRendererFrames.invalidMeasuredFrames());
        rate.addProperty("compactorCalls",commands[0]-measuredCommands[0]);rate.addProperty("inputCommands",commands[1]-measuredCommands[1]);rate.addProperty("outputCommands",commands[2]-measuredCommands[2]);rate.addProperty("reducedBatches",commands[3]-measuredCommands[3]);rate.addProperty("isGpuTiming",false);
        fpsEvidence=rate;fpsWindowComplete=true;
    }
    private void validateFpsState(Minecraft client,Map<String,String> options,JsonObject state)throws Exception{
        var cfg=FabricLodConfig.snapshot();var tracker=client.getFramerateLimitTracker();
        if(!nativeFamilyMode.equals(System.getProperty("shaderlod.dev.portNativeFamily",""))||!PortFpsPolicy.requested()||cfg.enabled()!=stageEnabled()||cfg.preset()!=fpsPreset()||cfg.debug()||!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("FPS mode/Core/preset/queue mismatch");
        var expectedCore=GSON.toJsonTree(initial).getAsJsonObject();var actualCore=GSON.toJsonTree(cfg).getAsJsonObject();expectedCore.remove("enabled");expectedCore.remove("preset");actualCore.remove("enabled");actualCore.remove("preset");if(!expectedCore.equals(actualCore))throw new IllegalStateException("FPS nonstage Core settings changed");
        for(String flag:new String[]{"shaderlod.dev.portRendererSpeedProbe","shaderlod.dev.portQualityRoundtrip","shaderlod.rendererSpeedPortR1",com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES})if(!"false".equals(System.getProperty(flag,"false")))throw new IllegalStateException("FPS conflicting property changed");
        if(client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD)throw new IllegalStateException("FPS fixed Overworld required");
        if(client.options.renderDistance().get()!=24||client.options.enableVsync().get()||client.options.framerateLimit().get()!=260||!com.pp.shaderlod.port.dev.PortRendererFrames.limiterAllows(260,tracker.getFramerateLimit(),tracker.getThrottleReason().name(),client.options.inactivityFpsLimit().get()==net.minecraft.client.InactivityFpsLimit.MINIMIZED,client.isWindowActive(),client.getWindow().isIconified()))throw new IllegalStateException("FPS RD24/vsyncOff/effective260/focus limiter mismatch");
        var mods=new TreeMap<String,String>();for(var mod:FabricLoader.getInstance().getAllMods()){var metadata=mod.getMetadata();if(!metadata.getId().equals("java"))mods.put(metadata.getId(),metadata.getVersion().getFriendlyString());}
        if(!com.pp.shaderlod.integration.makeupquad.MakeUpQuadApiPins.exactApi()||!com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.stackMatches(mods)||!com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.resourcesMatch(client.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList()))throw new IllegalStateException("FPS actual API/stack/resources mismatch");
        var pipeline=Iris.getPipelineManager().getPipelineNullable();var pack=Iris.getCurrentPack().orElse(null);
        if(pipeline==null||pipeline.getClass()!=net.irisshaders.iris.pipeline.IrisRenderingPipeline.class||((net.irisshaders.iris.pipeline.IrisRenderingPipeline)pipeline).getSodiumPrograms().getClass()!=net.irisshaders.iris.pipeline.programs.SodiumPrograms.class||pack==null||pack.getShaderPackOptions().getOptionValues().getClass()!=net.irisshaders.iris.shaderpack.option.values.MutableOptionValues.class)throw new IllegalStateException("FPS actual pipeline/programs/options class mismatch");
        var expected=stageGenerated()?PortFpsPolicy.generatedOptions(fpsFamily):PortFpsPolicy.nativeOptions(fpsFamily);if(!expected.equals(options))throw new IllegalStateException("FPS declared native/owned option map mismatch");
        var pose=speedPose(client);if(speedPose==null)speedPose=pose.deepCopy();if(!speedPose.equals(pose))throw new IllegalStateException("FPS fixed camera/time/weather changed");
        if(state==null)return;
        if(!PortFpsPolicy.sourceMatches(fpsFamily,Iris.getShaderpacksDirectory().resolve(original)))throw new IllegalStateException("FPS original certificate changed");
        state.addProperty("fpsFamily",fpsFamily);state.addProperty("expectedPreset",fpsPreset().name());state.addProperty("selectedGraph",stageGenerated()?"generated-recipe":"certified-native-original");state.addProperty("rendererEligible",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());state.addProperty("nativeOptionsCount",PortFpsPolicy.nativeOptions(fpsFamily).size());state.addProperty("loadedOptionsCount",options.size());
        var changed=new TreeMap<String,String>();for(var entry:options.entrySet())if(!entry.getValue().equals(PortFpsPolicy.nativeOptions(fpsFamily).get(entry.getKey())))changed.put(entry.getKey(),entry.getValue());state.add("sourceDeclaredOwnedOptionChanges",GSON.toJsonTree(changed));state.addProperty("qualitySensitiveOwnedAaChange",fpsFamily.equals("photon")&&stageGenerated());state.addProperty("linkedUniformReadbackPerformed",false);
        if(stageGenerated())validateFpsGeneratedSource(state);
    }
    private void validateFpsGeneratedSource(JsonObject state)throws Exception{
        var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();String selected=Iris.getCurrentPackName();var generated=dir.resolve(selected).normalize();if(!generated.startsWith(dir)||generated.equals(dir)||Files.isSymbolicLink(generated))throw new IllegalStateException("FPS unsafe generated archive");
        try(var zip=new java.util.zip.ZipFile(generated.toFile())){
            var entry=zip.getEntry("shaderlod-selection.json");if(entry==null)throw new IllegalStateException("FPS generated marker absent");JsonObject marker;try(var in=zip.getInputStream(entry)){marker=com.google.gson.JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
            String id=PortFpsPolicy.adapter(fpsFamily),path=id.equals("photon")?"patches/photon-experiments/live-pixel-budget.json":"patches/adapters/"+id+".json",recipeSha;
            try(var in=com.pp.shaderlod.packs.PackSelectionService.class.getResourceAsStream("/assets/shaderlod/adapters/"+path)){if(in==null)throw new IllegalStateException("FPS packaged recipe absent");recipeSha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));}
            if(marker.get("schema").getAsInt()!=1||!id.equals(marker.get("adapterId").getAsString())||!original.equals(marker.get("originalFile").getAsString())||!originalHash.equals(marker.get("originalSha256").getAsString())||!recipeSha.equals(marker.get("recipeSha256").getAsString()))throw new IllegalStateException("FPS generated recipe/source marker mismatch");
            String name=marker.get("stateFile").getAsString();var pathState=dir.resolve(name).normalize();if(!Path.of(name).getFileName().toString().equals(name)||!pathState.startsWith(dir)||pathState.equals(dir)||Files.isSymbolicLink(pathState))throw new IllegalStateException("FPS unsafe selection state");
            var selection=com.google.gson.JsonParser.parseString(Files.readString(pathState)).getAsJsonObject();String sha=hash(generated);if(!selection.get("active").getAsBoolean()||!sha.equals(selection.get("generatedSha256").getAsString())||!selected.equals(selection.get("generatedFile").getAsString())||!recipeSha.equals(selection.get("recipeSha256").getAsString())||!originalHash.equals(selection.get("originalSha256").getAsString()))throw new IllegalStateException("FPS actual public Apply generated state mismatch");
            state.add("generatedSelectionMarker",marker);state.addProperty("generatedArchiveSha256",sha);state.addProperty("generatedSelectionStateMatched",true);
        }
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
        }
        if(!expected.equals(options))throw new IllegalStateException("BSL fallback requires native384 unchanged plus owned flag and exact two parsed helper guards on generated stage");
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
        state.add("generatedDeclaredOptions",GSON.toJsonTree(stageGenerated()?Map.of("SHADERLOD_REFINEMENT_ADAPTER","1","SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true","SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true"):Map.of()));
        state.addProperty("bslFallbackControl",true);state.addProperty("expectedPreset",expectedPreset.name());state.addProperty("underlyingNative384Unchanged",true);state.addProperty("generatedBslRecipeSelected",stageGenerated());state.addProperty("ownedRefinementFlag",stageGenerated()?"1":"absent");state.addProperty("rendererEligible",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());state.addProperty("linkedUniformReadbackPerformed",false);state.addProperty("avoidedWorkMeasured",false);
    }

    private boolean applyBslVolumeStage(){
        boolean applied=FabricLodSettings.apply(stageEnabled(),stage==2?LodPreset.QUALITY:LodPreset.BALANCED);
        if(!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("BSL public Apply left pending native options");
        return applied;
    }
    private void validateBslVolumeState(Minecraft client,Map<String,String> options,JsonObject state)throws Exception{
        var cfg=FabricLodConfig.snapshot();var expectedPreset=stage==2?LodPreset.QUALITY:LodPreset.BALANCED;
        if(initial==null||initial.enabled()||initial.debug()||initial.preset()!=LodPreset.BALANCED||!FabricLodConfig.loaded()||cfg.enabled()!=stageEnabled()||cfg.preset()!=expectedPreset||cfg.debug()||!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("BSL volume Core/preset/nativequeue mismatch");
        var expected=new TreeMap<>(com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions());
        if(stageGenerated()){
            expected.put("SHADERLOD_REFINEMENT_ADAPTER","1");
            // Exact hash-pinned helper #ifndef/#define guards also appear as Iris BooleanOptions.
            expected.put("SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true");
            expected.put("SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true");
            expected.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");
        }
        if(!expected.equals(options))throw new IllegalStateException("BSL volume requires native384 unchanged plus owned flag and exact three parsed helper guards on generated stage");
        if(!"0".equals(options.get("AURORA")))throw new IllegalStateException("BSL native Aurora setting changed; this control keeps inactive reference Aurora");
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
        state.addProperty("bslVolumeControl",true);state.addProperty("referenceNativeAuroraEnabled",false);state.addProperty("expectedPreset",expectedPreset.name());state.addProperty("underlyingNative384Unchanged",true);state.addProperty("generatedBslRecipeSelected",stageGenerated());state.addProperty("ownedRefinementFlag",stageGenerated()?"1":"absent");state.addProperty("rendererEligible",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());state.addProperty("linkedUniformReadbackPerformed",false);state.addProperty("avoidedWorkMeasured",false);
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

    private boolean applySildurCloudStage(){
        boolean applied=FabricLodSettings.apply(stageEnabled(),stage==2?LodPreset.QUALITY:LodPreset.BALANCED);
        if(!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Sildur public Apply left pending native options");
        return applied;
    }
    private void validateSildurCloudState(Minecraft client,Map<String,String> options,JsonObject state)throws Exception{
        var cfg=FabricLodConfig.snapshot();var expectedPreset=stage==2?LodPreset.QUALITY:LodPreset.BALANCED;
        if(initial==null||initial.enabled()||initial.debug()||initial.preset()!=LodPreset.BALANCED||!FabricLodConfig.loaded()||cfg.enabled()!=stageEnabled()||cfg.preset()!=expectedPreset||cfg.debug()||!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Sildur fallback Core/preset/nativequeue mismatch");
        var expected=new TreeMap<>(com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.nativeOptions());
        if(stageGenerated()){
            expected.put("SHADERLOD_EFFECT_ADAPTER","1");
            // Exact hash-pinned helper #ifndef/#define guards also appear as Iris BooleanOptions.
            expected.put("SHADERLOD_EFFECT_ADAPTER_INCLUDED","true");
            expected.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");
        }
        if(!expected.equals(options))throw new IllegalStateException("Sildur fallback requires native115 unchanged plus owned flag and exact two parsed helper guards on generated stage");
        if(state==null)return;
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
        state.addProperty("sildurCloudControl",true);state.addProperty("expectedPreset",expectedPreset.name());state.addProperty("underlyingNative115Unchanged",true);state.addProperty("generatedSildurRecipeSelected",stageGenerated());state.addProperty("ownedEffectFlag",stageGenerated()?"1":"absent");state.addProperty("rendererEligible",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());state.addProperty("linkedUniformReadbackPerformed",false);state.addProperty("avoidedWorkMeasured",false);
    }

    private boolean applyBlissCloudStage(){
        boolean applied=FabricLodSettings.apply(stageEnabled(),stage==2?LodPreset.QUALITY:LodPreset.BALANCED);
        if(!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Bliss public Apply left pending native options");
        return applied;
    }
    private void validateBlissCloudState(Minecraft client,Map<String,String> options,JsonObject state)throws Exception{
        var cfg=FabricLodConfig.snapshot();var expectedPreset=stage==2?LodPreset.QUALITY:LodPreset.BALANCED;
        if(initial==null||initial.enabled()||initial.debug()||initial.preset()!=LodPreset.BALANCED||!FabricLodConfig.loaded()||cfg.enabled()!=stageEnabled()||cfg.preset()!=expectedPreset||cfg.debug()||!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Bliss fallback Core/preset/nativequeue mismatch");
        var expected=new TreeMap<>(com.pp.shaderlod.integration.blissquad.BlissQuadEligibility.nativeOptions());
        if(stageGenerated()){
            expected.put("SHADERLOD_EFFECT_ADAPTER","1");
            // Exact hash-pinned helper #ifndef/#define guards also appear as Iris BooleanOptions.
            expected.put("SHADERLOD_EFFECT_ADAPTER_INCLUDED","true");
            expected.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");
        }
        if(!expected.equals(options))throw new IllegalStateException("Bliss fallback requires native463 unchanged plus owned flag and exact two parsed helper guards on generated stage");
        if(state==null)return;
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
        state.addProperty("blissCloudControl",true);state.addProperty("expectedPreset",expectedPreset.name());state.addProperty("underlyingNative463Unchanged",true);state.addProperty("generatedBlissRecipeSelected",stageGenerated());state.addProperty("ownedEffectFlag",stageGenerated()?"1":"absent");state.addProperty("rendererEligible",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());state.addProperty("linkedUniformReadbackPerformed",false);state.addProperty("avoidedWorkMeasured",false);
    }

    private static Map<String,String> photonCloudOptions() {
        // Exact previously rendered Mac configured366 fixture; retain latent native scale.
        var configured = new TreeMap<>(com.pp.shaderlod.integration.photonquad.PhotonQuadEligibility.nativeOptions());
        if (configured.size()!=366) throw new IllegalStateException("Photon pinned native366 certificate unavailable");
        configured.put("TAAU_RENDER_SCALE","1.00");
        return Map.copyOf(configured);
    }

    private void validatePhotonCostState(Minecraft client) {
        if(client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD)throw new IllegalStateException("Photon optical cost requires Overworld");
        var tracker=client.getFramerateLimitTracker();
        if(client.options.renderDistance().get()!=24||client.options.enableVsync().get()||client.options.framerateLimit().get()!=260||!com.pp.shaderlod.port.dev.PortRendererFrames.limiterAllows(260,tracker.getFramerateLimit(),tracker.getThrottleReason().name(),client.options.inactivityFpsLimit().get()==net.minecraft.client.InactivityFpsLimit.MINIMIZED,client.isWindowActive(),client.getWindow().isIconified()))throw new IllegalStateException("Photon optical cost RD24/vsyncOff/effective260/focus mismatch");
        var pose=speedPose(client);if(speedPose==null)speedPose=pose.deepCopy();
        if(!speedPose.equals(pose))throw new IllegalStateException("Photon optical cost camera/time/weather changed");
        var cfg=FabricLodConfig.snapshot();
        if(cfg.near()!=initial.near()||cfg.far()!=initial.far()||cfg.transition()!=initial.transition())throw new IllegalStateException("Photon optical cost distance settings changed");
    }

    private boolean applyPhotonCloudStage(){
        boolean applied=FabricLodSettings.apply(stageEnabled(),!photonCost&&stage==2?LodPreset.QUALITY:LodPreset.BALANCED);
        if(!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Photon public Apply left pending native options");
        return applied;
    }
    private void validatePhotonCloudState(Minecraft client,Map<String,String> options,JsonObject state)throws Exception{
        var cfg=FabricLodConfig.snapshot();var expectedPreset=!photonCost&&stage==2?LodPreset.QUALITY:LodPreset.BALANCED;
        if(initial==null||initial.enabled()||initial.debug()||initial.preset()!=LodPreset.BALANCED||!FabricLodConfig.loaded()||cfg.enabled()!=stageEnabled()||cfg.preset()!=expectedPreset||cfg.debug()||!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Photon fallback Core/preset/nativequeue mismatch");
        var expected=new TreeMap<>(photonCloudOptions());
        if(stageGenerated()){
            expected.put("SHADERLOD_LIVE_PIXEL_BUDGET","true");
            // Exact hash-pinned helper #ifndef/#define guards also appear as Iris BooleanOptions.
            expected.put("SHADERLOD_LIVE_PIXEL_BUDGET_INCLUDED","true");
            expected.put("SHADERLOD_RENDER_BUDGET_INCLUDED","true");
        }
        if(!expected.equals(options))throw new IllegalStateException("Photon fallback requires configured366 unchanged plus owned flag and exact two parsed helper guards on generated stage");
        if(state==null)return;
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
        state.addProperty("photonCloudControl",true);state.addProperty("expectedPreset",expectedPreset.name());state.addProperty("underlyingConfigured366Unchanged",true);state.addProperty("generatedPhotonRecipeSelected",stageGenerated());state.addProperty("ownedLivePixelFlag",stageGenerated()?"true":"absent");state.addProperty("rendererEligible",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());state.addProperty("linkedUniformReadbackPerformed",false);state.addProperty("avoidedWorkMeasured",false);
    }

    private boolean applyComplementaryVolumeStage(){
        boolean applied=FabricLodSettings.apply(stageEnabled(),stage==2?LodPreset.QUALITY:LodPreset.BALANCED);
        if(!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Complementary public Apply left pending native options");
        return applied;
    }
    private void validateComplementaryVolumeState(Minecraft client,Map<String,String> options,JsonObject state)throws Exception{
        var cfg=FabricLodConfig.snapshot();var expectedPreset=stage==2?LodPreset.QUALITY:LodPreset.BALANCED;
        if(initial==null||initial.enabled()||initial.debug()||initial.preset()!=LodPreset.BALANCED||!FabricLodConfig.loaded()||cfg.enabled()!=stageEnabled()||cfg.preset()!=expectedPreset||cfg.debug()||!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Complementary fallback Core/preset/nativequeue mismatch");
        var expected=new TreeMap<>(ReimaginedNativeControlChecks.nativeOptions(complementaryVolumeFamily));
        if(stageGenerated()){
            expected.put("SHADERLOD_REFINEMENT_ADAPTER","1");
            // Exact hash-pinned helper #ifndef/#define guards also appear as Iris BooleanOptions.
            expected.put("SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true");
            expected.put("SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true");
            expected.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");
        }
        if(!expected.equals(options))throw new IllegalStateException("Complementary fallback requires native398 unchanged plus owned flag and exact three parsed helper guards on generated stage");
        if(state==null)return;
        var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var originalPath=dir.resolve(original).normalize();
        if(!ReimaginedNativeControlChecks.sourceMatches(complementaryVolumeFamily,originalPath,ReimaginedNativeControlChecks.nativeOptions(complementaryVolumeFamily)))throw new IllegalStateException("Complementary original source certificate changed");
        if(stageGenerated()){
            String selected=Iris.getCurrentPackName();var generated=dir.resolve(selected).normalize();
            if(!generated.startsWith(dir)||generated.equals(dir)||Files.isSymbolicLink(generated))throw new IllegalStateException("Unsafe Complementary generated source path");
            try(var zip=new java.util.zip.ZipFile(generated.toFile())){
                var entry=zip.getEntry("shaderlod-selection.json");if(entry==null)throw new IllegalStateException("Generated Complementary selection marker absent");
                JsonObject marker;try(var in=zip.getInputStream(entry)){marker=com.google.gson.JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
                String recipeSha;try(var in=com.pp.shaderlod.packs.PackSelectionService.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/complementary-"+complementaryVolumeFamily+".json")){
                    if(in==null)throw new IllegalStateException("Actual packaged Complementary recipe absent");recipeSha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
                }
                if(marker.get("schema").getAsInt()!=1||!("complementary-"+complementaryVolumeFamily).equals(marker.get("adapterId").getAsString())||!original.equals(marker.get("originalFile").getAsString())||!originalHash.equals(marker.get("originalSha256").getAsString())||!recipeSha.equals(marker.get("recipeSha256").getAsString()))throw new IllegalStateException("Generated Complementary source/recipe marker mismatch");
                String stateFile=marker.get("stateFile").getAsString();var statePath=dir.resolve(stateFile).normalize();
                if(!Path.of(stateFile).getFileName().toString().equals(stateFile)||!statePath.startsWith(dir)||statePath.equals(dir)||Files.isSymbolicLink(statePath))throw new IllegalStateException("Unsafe Complementary selection state reference");
                var selection=com.google.gson.JsonParser.parseString(Files.readString(statePath)).getAsJsonObject();String generatedSha=hash(generated);
                if(!selection.get("active").getAsBoolean()||!selected.equals(selection.get("generatedFile").getAsString())||!generatedSha.equals(selection.get("generatedSha256").getAsString())||!original.equals(selection.get("originalFile").getAsString())||!originalHash.equals(selection.get("originalSha256").getAsString())||!recipeSha.equals(selection.get("recipeSha256").getAsString()))throw new IllegalStateException("Generated Complementary archive differs from real public Apply selection state");
                state.add("generatedSelectionMarker",marker);state.addProperty("generatedArchiveSha256",generatedSha);state.addProperty("generatedSelectionStateMatched",true);
            }
        }
        state.addProperty("loadedOptionCount",options.size());
        state.add("generatedDeclaredOptions",GSON.toJsonTree(stageGenerated()?Map.of("SHADERLOD_REFINEMENT_ADAPTER","1","SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true","SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true","SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true"):Map.of()));
        state.addProperty("complementaryVolumeControl",true);state.addProperty("complementaryVolumeFamily",complementaryVolumeFamily);state.addProperty("expectedPreset",expectedPreset.name());state.addProperty("underlyingNative398Unchanged",true);state.addProperty("generatedComplementaryRecipeSelected",stageGenerated());state.addProperty("ownedRefinementFlag",stageGenerated()?"1":"absent");state.addProperty("rendererEligible",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());state.addProperty("linkedUniformReadbackPerformed",false);state.addProperty("avoidedWorkMeasured",false);
    }

    private boolean stageEnabled() { return photonCost ? stage==1 : fpsProbe ? stage>0&&stage<4 : bslFallback || bslVolume || makeupVolume || sildurCloud || blissCloud || photonCloud || complementaryVolume ? stage==1||stage==2 : nativeFamily ? ReimaginedNativeControlChecks.stageEnabled(stage) : qualityRoundtrip ? stage > 0 && stage < stages.length - 1 : stage == 1; }
    private boolean stageGenerated() { return photonCost ? stage==1 : fpsProbe ? stage>0&&stage<3&&!original.equals(Iris.getCurrentPackName()) : bslFallback || bslVolume || makeupVolume || sildurCloud || blissCloud || photonCloud || complementaryVolume ? stage==1 : !nativeFamily && !rendererSpeed && (qualityRoundtrip ? stage == 6 : stage == 1); }

    private boolean applyNativeFamilyStage(Minecraft client) throws Exception {
        if(!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Preexisting native option queue");
        validateNativeFamilyInputs(client,loadedOptions());
        var transition=new JsonObject();transition.add("beforeCore",GSON.toJsonTree(FabricLodConfig.snapshot()));
        transition.add("beforeLoadedOptions",GSON.toJsonTree(loadedOptions()));transition.addProperty("beforeSelectedPack",Iris.getCurrentPackName());
        transition.add("nativeSidecarBeforePublicApply",nativeSidecarObservation());
        report.add(stages[stage]+"Transition",transition);write(client);
        boolean applied=FabricLodSettings.apply(stageEnabled(),ReimaginedNativeControlChecks.stagePreset(stage,initial.preset()));
        transition.addProperty("publicApplyReturned",applied);transition.add("afterCore",GSON.toJsonTree(FabricLodConfig.snapshot()));
        transition.add("afterLoadedOptions",GSON.toJsonTree(loadedOptions()));transition.add("afterQueue",GSON.toJsonTree(new TreeMap<>(Iris.getShaderPackOptionQueue())));
        transition.add("nativeSidecarAfterPublicApply",nativeSidecarObservation());write(client);
        if(applied&&!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Native Apply left queued options");
        return applied;
    }
    private void validateNativeFamilyInputs(Minecraft client,Map<String,String> options) throws Exception {
        com.pp.shaderlod.integration.RendererBackend.requireApplySupported();
        if(!ReimaginedNativeControlChecks.launchAllows(nativeFamilyMode,System.getProperty("shaderlod.dev.portRendererSpeedProbe","false"),System.getProperty("shaderlod.dev.portQualityRoundtrip","false"),
                System.getProperty("shaderlod.rendererSpeedPortR1","false"),System.getProperty(com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES,"false")))
            throw new IllegalStateException("Native family mode/flags changed");
        if(!original.equals(Iris.getCurrentPackName())||!original.equals(Iris.getIrisConfig().getShaderPackName().orElse(""))||Iris.isFallback()||!IrisApi.getInstance().isShaderPackInUse()||!Iris.getShaderPackOptionQueue().isEmpty())
            throw new IllegalStateException("Unchanged native selected/loaded graph and empty queue required");
        if(!ReimaginedNativeControlChecks.optionsMatch(nativeFamilyMode,options))
            throw new IllegalStateException("Named family native398/options proof refused");
        if(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended())
            throw new IllegalStateException("Settings transaction still suspended");
        var archive=Iris.getShaderpacksDirectory().resolve(original);
        if(Files.isSymbolicLink(archive))throw new IllegalStateException("Original archive path aliases another source");
        var attributes=Files.readAttributes(archive,java.nio.file.attribute.BasicFileAttributes.class,java.nio.file.LinkOption.NOFOLLOW_LINKS);
        String stamp=attributes.size()+":"+attributes.lastModifiedTime()+":"+attributes.fileKey();
        Object currentPack=Iris.getCurrentPack().orElseThrow(),currentPipeline=Iris.getPipelineManager().getPipelineNullable();
        long generation=com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation();
        if(currentPack!=nativeCertifiedPack||currentPipeline!=nativeCertifiedPipeline||generation!=nativeCertifiedGeneration||!stamp.equals(nativeArchiveStamp)){
            if(!ReimaginedNativeControlChecks.sourceMatches(nativeFamilyMode,archive,options))
                throw new IllegalStateException("Named family native archive/source certificate refused");
            nativeCertificateValidations++;nativeCertifiedPack=currentPack;nativeCertifiedPipeline=currentPipeline;
            nativeCertifiedGeneration=generation;nativeArchiveStamp=stamp;
        }
        var mods=new TreeMap<String,String>();for(var mod:FabricLoader.getInstance().getAllMods()){var metadata=mod.getMetadata();if(!metadata.getId().equals("java"))mods.put(metadata.getId(),metadata.getVersion().getFriendlyString());}
        var resources=client.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList();
        Object pipeline=Iris.getPipelineManager().getPipelineNullable();
        if(!com.pp.shaderlod.integration.makeupquad.MakeUpQuadApiPins.exactApi()||!com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.stackMatches(mods)||!com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.resourcesMatch(resources)||pipeline==null||pipeline.getClass()!=net.irisshaders.iris.pipeline.IrisRenderingPipeline.class||
                ((net.irisshaders.iris.pipeline.IrisRenderingPipeline)pipeline).getSodiumPrograms().getClass()!=net.irisshaders.iris.pipeline.programs.SodiumPrograms.class||
                Iris.getCurrentPack().orElseThrow().getShaderPackOptions().getOptionValues().getClass()!=net.irisshaders.iris.shaderpack.option.values.MutableOptionValues.class)
            throw new IllegalStateException("Exact26.1.2 Fabric Iris/Sodium API/classes/stack/resources required");
        var caps=org.lwjgl.opengl.GL.getCapabilities();
        if(!com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.capabilitiesAllow(org.lwjgl.system.Pointer.POINTER_SIZE,caps.OpenGL41,caps.glMultiDrawElementsBaseVertex))
            throw new IllegalStateException("Pinned64bit/OpenGL41/native draw API unavailable");
        if(client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||client.getMainRenderTarget().width!=1920||client.getMainRenderTarget().height!=1200||client.getWindow().getWidth()!=1920||client.getWindow().getHeight()!=1200||client.options.renderDistance().get()!=24||client.options.enableVsync().get()||client.options.framerateLimit().get()!=260)
            throw new IllegalStateException("Native Overworld1920/RD24/vsyncOff/configured260 required");
    }
    private JsonObject nativeSidecarObservation(){
        var observation=new JsonObject();var path=Iris.getShaderpacksDirectory().resolve(original+".txt");
        observation.addProperty("path",path.toAbsolutePath().toString());observation.addProperty("observedAtUtc",Instant.now().toString());
        try {
            boolean exists=Files.exists(path,java.nio.file.LinkOption.NOFOLLOW_LINKS);observation.addProperty("exists",exists);
            if(exists&&(!Files.isRegularFile(path,java.nio.file.LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path)))throw new IllegalStateException("Nonregular/aliased sidecar");
            if(exists)observation.addProperty("sha256",hash(path));
            observation.addProperty("observationComplete",true);
        }catch(Exception unavailable){observation.addProperty("observationComplete",false);observation.addProperty("unavailable",unavailable.toString());}
        return observation;
    }
    private Boolean nativeSidecarByteMatch(JsonObject before,JsonObject after){
        return ReimaginedNativeControlChecks.sidecarByteStateRestored(before!=null&&before.get("observationComplete").getAsBoolean(),
            before!=null&&before.has("exists")&&before.get("exists").getAsBoolean(),before!=null&&before.has("sha256")?before.get("sha256").getAsString():null,
            after!=null&&after.get("observationComplete").getAsBoolean(),after!=null&&after.has("exists")&&after.get("exists").getAsBoolean(),after!=null&&after.has("sha256")?after.get("sha256").getAsString():null);
    }
    private void validateNativeFamilyState(Minecraft client,Map<String,String> options,JsonObject state) throws Exception {
        validateNativeFamilyInputs(client,options);var cfg=FabricLodConfig.snapshot();
        if(initial==null||!FabricLodConfig.loaded()||initial.enabled()||initial.debug()||initial.preset()!=LodPreset.PERFORMANCE||
                !ReimaginedNativeControlChecks.coreMatches(stage,cfg.enabled(),cfg.preset(),initial.preset())||cfg.debug()||
                cfg.near()!=initial.near()||cfg.far()!=initial.far()||cfg.transition()!=initial.transition()||
                originalOptions!=null&&!originalOptions.equals(options))
            throw new IllegalStateException("Named native family core/options changed outside declared stage");
        if(state!=null){
            // Revalidate content at every accepted stage as well as pack/pipeline/Apply/stamp invalidation.
            if(!ReimaginedNativeControlChecks.sourceMatches(nativeFamilyMode,Iris.getShaderpacksDirectory().resolve(original),options))
                throw new IllegalStateException("Original certificate changed before accepted native stage");
            nativeCertificateValidations++;state.addProperty("nativeSourceCertificateValidations",nativeCertificateValidations);
            var sidecar=nativeSidecarObservation();state.add("nativeSidecar",sidecar);
            if(stage==0){nativeOriginalStageSidecar=sidecar.deepCopy();report.add("nativeSidecarAtOriginalStage",nativeOriginalStageSidecar);report.addProperty("nativeSidecarPrelaunchEvidenceBoundary","Separate parent caller freeze; not observed by this runtime controller");}
            if(stage==stages.length-1){
                Boolean restored=nativeSidecarByteMatch(nativeOriginalStageSidecar,sidecar);
                state.addProperty("nativeSidecarRestoredFromOriginalStage",restored);state.addProperty("nativeSidecarRecoveryObservation",restored==null?"unverified":restored?"restored-original-stage-bytes":"changed-original-stage-bytes");
                state.addProperty("prelaunchSidecarByteRestorationAccepted",false);state.addProperty("prelaunchSidecarRecoveryObservation","unverified-external-caller-freeze-required");state.addProperty("nativeSidecarChangeCauseAssigned",false);
            }
            state.addProperty("nativeFamily",nativeFamilyMode);state.addProperty("native398Matches",true);state.addProperty("nativeOriginalGraphRetained",true);state.addProperty("expectedPreset",ReimaginedNativeControlChecks.stagePreset(stage,initial.preset()).name());
            var origins=new JsonObject();
            for(Class<?> type:new Class<?>[]{com.pp.shaderlod.packs.PackSelectionService.class,com.pp.shaderlod.integration.PackSelectionTransaction.class,com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.class,com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.class,com.pp.shaderlod.fabric.FabricLodSettings.class}){
                var location=type.getProtectionDomain().getCodeSource();
                origins.addProperty(type.getName(),location==null?"unavailable":location.getLocation().toExternalForm());
            }
            state.add("mainArtifactOrigins",origins);
            state.addProperty("rendererEligible",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());state.addProperty("avoidedRendererWorkMeasured",false);state.addProperty("terrainSourceMappingFreshlyPerformed",false);}
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

    private void resetWindow() { if(frameProbe&&measuredSince!=0&&!fpsWindowComplete)throw new IllegalStateException("FPS readiness/signature changed during measured window"); if (rendererSpeed||frameProbe) com.pp.shaderlod.port.dev.PortRendererFrames.endWindow(); fpsWindowComplete=false;fpsEvidence=null; settledSince = 0; measuredSince = 0; measuredFrames = 0; measuredCommands = null; nativeStageCommandBaseline = null; }
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
        pose.addProperty("pitch", client.player.getXRot()); pose.addProperty("dayTime", client.level.getOverworldClockTime());
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
            observation.addProperty("renderBackend", com.pp.shaderlod.integration.RendererBackend.observedName());
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
        if(photonCost){
            com.pp.shaderlod.port.dev.PortRendererFrames.endWindow();
            try {
                boolean restored=initial!=null&&FabricLodSettings.apply(initial.enabled(),initial.preset());
                report.addProperty("failurePublicRestoreReturned",restored);
                report.addProperty("failureOriginalSelectionRestored",restored&&original.equals(Iris.getCurrentPackName())&&original.equals(Iris.getIrisConfig().getShaderPackName().orElse("")));
                report.addProperty("failureCoreRestored",initial!=null&&initial.equals(FabricLodConfig.snapshot()));
                report.addProperty("failureNativeOptionsRestored",originalOptions!=null&&originalOptions.equals(loadedOptions()));
            } catch(Exception restoreFailure){report.addProperty("failurePublicRestoreFailure",restoreFailure.toString());}
        }
        report.addProperty("status", "failed");
        report.addProperty("failure", failure.toString());
        report.addProperty("failedStage", stages[stage]);
        observeWaiting(client, "failureObservation");
        write(client);
        LOGGER.error("Fabric roundtrip failed; client remains open; receipt={}", evidence(client), failure);
    }
}
