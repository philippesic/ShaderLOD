package com.pp.shaderlod.fabric.dev.budget;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.fabric.FabricLodConfig;
import com.pp.shaderlod.integration.bslquad.BslQuadEligibility;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadApiPins;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import java.nio.file.*;
import java.util.*;

/** Native-only diagnostic, driven on the render thread. No Apply/reload or client control. */
public final class BslGpuBudget {
    private static final boolean ALPHA=Boolean.getBoolean("shaderlod.dev.portBslAlphaAdmission");
    private static final boolean GPU=Boolean.getBoolean("shaderlod.dev.portBslGpuBudget");
    private static final boolean ENABLED=GPU || ALPHA;
    private static final com.google.gson.Gson GSON=new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    private static final JsonObject REPORT=new JsonObject();
    private static final List<GpuTimers.Sample> RAW=new ArrayList<>();
    private static final long START=System.nanoTime();
    private static long stable, measuring, draining, measuredFrames;
    private static long nativeRenderOrdinal;
    private static final String NATIVE_EPOCH=java.util.UUID.randomUUID().toString();
    private static boolean done, frameActive, capturePending, captureAfterRender;
    private static boolean alphaArmed;
    private static Object world, pipeline, pack;
    private static boolean sceneRequested, firstWorldObserved;
    private static volatile boolean sceneOperationCompleted;
    private static volatile String sceneFailure;
    private static long lastReadinessWrite;
    private static String phase="waiting-for-world";
    private static FabricLodConfig.Values core;
    private static Path archive;
    private static String archiveStamp;
    private BslGpuBudget() {}
    public static boolean requested() { return ENABLED; }
    public static boolean sampling() { return frameActive; }
    public static void before(boolean renderLevel) {
        frameActive=false;
        if (!ENABLED || done) return;
        nativeRenderOrdinal++;
        var client=Minecraft.getInstance();
        try {
            long now=System.nanoTime();
            long deadline=Long.parseLong(System.getProperty("shaderlod.dev.startupDeadlineEpochMillis","0"));
            if(deadline<=System.currentTimeMillis() || now-START>180_000_000_000L) throw new IllegalStateException("finite deadline elapsed/absent");
            if(!renderLevel || client.level==null || client.player==null || !client.isGameLoadFinished()) { stable=0; return; }
            if(!firstWorldObserved) { firstWorldObserved=true; REPORT.add("firstWorldObservation",observation(client)); stage(client,"waiting-for-native-scene"); }
            if(Iris.getCurrentPack().isEmpty() || !IrisApi.getInstance().isShaderPackInUse() || Iris.getPipelineManager().getPipelineNullable()==null) {
                if(measuring!=0) throw new IllegalStateException("native shader/pipeline disappeared during measurement");
                stable=0;
                if(now-lastReadinessWrite>=1_000_000_000L) { lastReadinessWrite=now; stage(client,"waiting-for-native-shader-pipeline"); }
                return;
            }
            if(!sceneRequested) {
                guard(client);
                if(sceneMatches(client)) { sceneRequested=true; sceneOperationCompleted=true; REPORT.addProperty("sceneOperationRequested",false); REPORT.addProperty("canonicalSceneAlreadyMatched",true); }
                else prepareScene(client);
            }
            if(sceneFailure!=null) throw new IllegalStateException("fixed scene operation failed: "+sceneFailure);
            if(!sceneOperationCompleted) return;
            if(measuring==0 && now-lastReadinessWrite>=1_000_000_000L) { lastReadinessWrite=now; REPORT.add("currentObservation",observation(client)); write(client); }
            if(!ready(client)) { if(measuring!=0) throw new IllegalStateException("native UI/scene readiness changed during measurement"); stable=0; return; }
            guard(client);
            if(stable==0) { stable=now; stage(client,"warming-native-scene"); return; }
            if(now-stable<30_000_000_000L) return;
            if(measuring==0) { measuring=now; REPORT.addProperty("measurementStartedUtc",java.time.Instant.now().toString()); stage(client,ALPHA?"waiting-for-native-alpha-draw-capture":"measuring-native-gpu"); }
            if(ALPHA) {
                alphaArmed=true;
                if(BslAlphaCapture.complete()) {
                    REPORT.add("nativeDrawObservations",BslAlphaCapture.observations());
                    REPORT.addProperty("status","complete-native-alpha-raw-observations");
                    REPORT.addProperty("qualifiedEarlyRejection",false);
                    if(!BslQuadEligibility.validate(archive,options()).eligible()) throw new IllegalStateException("terminal original source changed");
                    done=true;captureAfterRender=true;capturePending=true;write(client);
                }
                return;
            }
            if(now-measuring<20_000_000_000L) {
                GpuTimers.beginTick(true); GpuTimers.beginFrame(true); frameActive=true; measuredFrames++;
            } else {
                if(draining==0) { draining=now; stage(client,"draining-native-gpu-queries"); }
                GpuTimers.pollDrain(); RAW.addAll(GpuTimers.drainSamples());
                if(GpuTimers.snapshot().pendingQueries()==0 || now-draining>=5_000_000_000L) finish(client);
            }
        } catch(Exception | LinkageError failure) { fail(client,failure); }
    }
    public static void after(boolean originalCompleted) {
        if(!originalCompleted && captureAfterRender) { captureAfterRender=false;capturePending=false;fail(Minecraft.getInstance(),new IllegalStateException("native original render did not complete")); }
        if(captureAfterRender) {
            captureAfterRender=false;
            captureStill(Minecraft.getInstance());
        }
        if(!frameActive) return;
        frameActive=false;
        try { GpuTimers.endFrame(); GpuTimers.endTick(); RAW.addAll(GpuTimers.drainSamples()); }
        catch(Exception | LinkageError failure) { fail(Minecraft.getInstance(),failure); }
    }
    public static boolean alphaArmed() { return ALPHA && alphaArmed && !done; }
    public static long nativeRenderOrdinal() {return nativeRenderOrdinal;}
    public static String nativeEpoch() {return NATIVE_EPOCH;}
    public static void alphaFailure(Throwable t) { REPORT.add("nativeDrawObservations",BslAlphaCapture.observations());fail(Minecraft.getInstance(),t); }
    public static void terrainBegin(String name) { if(frameActive) GpuTimers.beginCategory(name,GpuSamplingPolicy.Category.TERRAIN); }
    public static void terrainEnd() { if(frameActive) GpuTimers.endCategory(); }
    private static boolean sceneMatches(Minecraft c) {
        var p=c.player;
        return c.level.dimension()==net.minecraft.world.level.Level.OVERWORLD && c.level.getDayTime()==7000
            && c.level.getRainLevel(1)==0 && c.level.getThunderLevel(1)==0 && p.isSpectator()
            && Math.abs(p.getX()-256.5)<.0001 && Math.abs(p.getY()-95)<.0001 && Math.abs(p.getZ()-768.5)<.0001
            && Math.abs(p.getYRot()-135)<.0001 && Math.abs(p.getXRot()-9)<.0001;
    }
    private static void prepareScene(Minecraft c) {
        var server=c.getSingleplayerServer();
        if(server==null) throw new IllegalStateException("fixed original scene requires integrated server");
        sceneRequested=true; REPORT.addProperty("sceneOperationRequested",true); write(c);
        server.execute(()->{try {
            var rules=server.overworld().getGameRules();
            rules.set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME,false,server);
            rules.set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_WEATHER,false,server);
            rules.set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS,false,server);
            var source=server.createCommandSourceStack();
            for(String command:List.of("time set 7000","weather clear","gamemode spectator @a","tp @a 256.500 95.000 768.500 135.000 9.000"))
                server.getCommands().performPrefixedCommand(source,command);
            sceneOperationCompleted=true;
        } catch(Exception | LinkageError e) {sceneFailure=e.toString();}});
    }
    private static JsonObject observation(Minecraft c) {
        var o=new JsonObject();o.addProperty("capturedAtUtc",java.time.Instant.now().toString());o.addProperty("stage",phase);
        o.addProperty("acceptedStageObservation",false);o.addProperty("worldPresent",c.level!=null);o.addProperty("playerPresent",c.player!=null);
        o.addProperty("screenClass",c.screen==null?"none":c.screen.getClass().getName());o.addProperty("screenAbsent",c.screen==null);o.addProperty("paused",c.isPaused());o.addProperty("focused",c.isWindowActive());
        var w=c.getWindow();var t=c.getMainRenderTarget();o.addProperty("fullscreen",w.isFullscreen());o.addProperty("nativeMonitor",GLFW.glfwGetWindowMonitor(w.handle()));
        o.addProperty("windowFramebufferWidth",w.getWidth());o.addProperty("windowFramebufferHeight",w.getHeight());o.addProperty("framebufferWidth",t==null?0:t.width);o.addProperty("framebufferHeight",t==null?0:t.height);
        o.addProperty("irisSelectedPack",Iris.getIrisConfig().getShaderPackName().orElse(""));o.addProperty("irisLoadedPack",Iris.getCurrentPackName());o.addProperty("shaderPackInUse",IrisApi.getInstance().isShaderPackInUse());o.addProperty("irisFallback",Iris.isFallback());
        o.addProperty("sceneOperationCompleted",sceneOperationCompleted);
        if(c.level!=null) {o.addProperty("dimension",c.level.dimension().identifier().toString());o.addProperty("dayTime",c.level.getDayTime());o.addProperty("rainLevel",c.level.getRainLevel(1));o.addProperty("thunderLevel",c.level.getThunderLevel(1));}
        if(c.player!=null) {o.addProperty("cameraX",c.player.getX());o.addProperty("cameraY",c.player.getY());o.addProperty("cameraZ",c.player.getZ());o.addProperty("yaw",c.player.getYRot());o.addProperty("pitch",c.player.getXRot());o.addProperty("spectator",c.player.isSpectator());}
        return o;
    }
    private static void stage(Minecraft c,String name) {phase=name;REPORT.addProperty("status",name);REPORT.add("currentObservation",observation(c));write(c);}
    private static boolean ready(Minecraft c) {
        var w=c.getWindow();var t=c.getMainRenderTarget();var p=c.player;
        return sceneOperationCompleted && p.isSpectator() && c.screen==null && !c.isPaused() && !c.noRender && c.isWindowActive() && w.isFullscreen()
            && GLFW.glfwGetWindowMonitor(w.handle())!=0 && w.getWidth()==1920 && w.getHeight()==1200 && t.width==1920 && t.height==1200
            && c.level.dimension()==net.minecraft.world.level.Level.OVERWORLD && c.level.getDayTime()==7000
            && c.level.getRainLevel(1)==0 && c.level.getThunderLevel(1)==0
            && Math.abs(p.getX()-256.5)<.0001 && Math.abs(p.getY()-95)<.0001 && Math.abs(p.getZ()-768.5)<.0001
            && Math.abs(p.getYRot()-135)<.0001 && Math.abs(p.getXRot()-9)<.0001;
    }
    private static Map<String,String> options() {
        var o=Iris.getCurrentPack().orElseThrow().getShaderPackOptions();var v=o.getOptionValues();var r=new TreeMap<String,String>();
        o.getOptionSet().getBooleanOptions().keySet().forEach(k->r.put(k,Boolean.toString(v.getBooleanValueOrDefault(k))));
        o.getOptionSet().getStringOptions().keySet().forEach(k->r.put(k,v.getStringValueOrDefault(k)));return r;
    }
    private static String stamp(Path p) throws Exception { var a=Files.readAttributes(p,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);return a.size()+":"+a.lastModifiedTime()+":"+a.fileKey(); }
    private static void guard(Minecraft c) throws Exception {
        if(GPU && ALPHA) throw new IllegalStateException("mixed GPU/alpha modes");
        for(String k:new String[]{"portRoundtrip","portBslOriginalSource","portSildurOriginalSource","portRendererSpeedProbe","portQualityRoundtrip","portRoundtripUi","gpuTiming","compare","benchmark","measureOnly"})
            if(!System.getProperty("shaderlod.dev."+k,"false").equals("false")) throw new IllegalStateException("conflicting flag "+k);
        var cfg=FabricLodConfig.snapshot();var opts=options();
        if(!FabricLodConfig.loaded() || cfg.enabled() || cfg.debug() || cfg.preset()!=LodPreset.PERFORMANCE
            || Boolean.getBoolean("shaderlod.rendererSpeedPortR1") || MakeUpQuadRuntime.enabled()
            || Boolean.getBoolean(com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES)
            || Iris.getIrisConfig().areDebugOptionsEnabled() || !Iris.getShaderPackOptionQueue().isEmpty()
            || !BslQuadEligibility.optionsMatch(opts) || Iris.isFallback() || !IrisApi.getInstance().isShaderPackInUse()
            || !Iris.getCurrentPackName().equals(System.getProperty("shaderlod.dev.portRoundtripOriginal",""))
            || !Iris.getIrisConfig().getShaderPackName().orElse("").equals(Iris.getCurrentPackName())
            || c.options.renderDistance().get()!=24 || c.options.enableVsync().get() || c.options.framerateLimit().get()!=260)
            throw new IllegalStateException("native384/CoreOff/Performance/exportsOff/rendererInactive gate");
        if(world==null) {
            if(!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread() || !"OpenGL".equals(com.mojang.blaze3d.systems.RenderSystem.getDevice().getBackendName())) throw new IllegalStateException("OpenGL render thread required");
            archive=Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName());
            if(Files.isSymbolicLink(archive) || !BslQuadEligibility.validate(archive,opts).eligible()) throw new IllegalStateException("original36b0 archive/source certificate");
            var resources=c.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList();var mods=new TreeMap<String,String>();
            FabricLoader.getInstance().getAllMods().forEach(m->{if(!m.getMetadata().getId().equals("java")) mods.put(m.getMetadata().getId(),m.getMetadata().getVersion().getFriendlyString());});
            if(!MakeUpQuadApiPins.exactApi() || !MakeUpQuadRuntime.resourcesMatch(resources) || !MakeUpQuadRuntime.stackMatches(mods)
                || Iris.getPipelineManager().getPipelineNullable().getClass()!=net.irisshaders.iris.pipeline.IrisRenderingPipeline.class) throw new IllegalStateException("exact pinned tuple/stack/resources");
            var actualPipeline=(net.irisshaders.iris.pipeline.IrisRenderingPipeline)Iris.getPipelineManager().getPipelineNullable();
            if(actualPipeline.getSodiumPrograms().getClass()!=net.irisshaders.iris.pipeline.programs.SodiumPrograms.class ||
                Iris.getCurrentPack().orElseThrow().getShaderPackOptions().getOptionValues().getClass()!=net.irisshaders.iris.shaderpack.option.values.MutableOptionValues.class)
                throw new IllegalStateException("exact program/options classes required");
            var programs=actualPipeline.getSodiumPrograms();var observed=new JsonObject();
            for(var pass:List.of(net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.SOLID,net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.CUTOUT)) {
                int id=programs.getProgram(pass).handle();
                if(org.lwjgl.opengl.GL20.glGetProgrami(id,org.lwjgl.opengl.GL20.GL_LINK_STATUS)==0) throw new IllegalStateException("native terrain program unlinked");
                var entry=new JsonObject();entry.addProperty("programId",id);entry.addProperty("attachedShaderCount",org.lwjgl.opengl.GL20.glGetProgrami(id,org.lwjgl.opengl.GL20.GL_ATTACHED_SHADERS));observed.add(pass.toString(),entry);
            }
            REPORT.add("nativeLinkedTerrainPrograms",observed);
            REPORT.addProperty("sourceBoundary","Pinned original archive/native384 and linked program observations; compiler export/attached source ancestry is external, no execution identity inference");
            world=c.level;pipeline=Iris.getPipelineManager().getPipelineNullable();pack=Iris.getCurrentPack().orElseThrow();core=cfg;archiveStamp=stamp(archive);
            REPORT.add("nativeOptions",GSON.toJsonTree(opts));REPORT.add("core",GSON.toJsonTree(cfg));REPORT.add("mods",GSON.toJsonTree(mods));REPORT.add("resources",GSON.toJsonTree(resources));
            REPORT.addProperty("archiveSha256",BslQuadEligibility.ARCHIVE_SHA);REPORT.addProperty("glRenderer",GL11.glGetString(GL11.GL_RENDERER));REPORT.addProperty("glVersion",GL11.glGetString(GL11.GL_VERSION));
            REPORT.addProperty("scene","Overworld 256.5/95/768.5 yaw135 pitch9 day7000 dry; native1920x1200 RD24");
        } else if(!MakeUpQuadRuntime.resourcesMatch(c.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList()) || world!=c.level || pipeline!=Iris.getPipelineManager().getPipelineNullable() || pack!=Iris.getCurrentPack().orElseThrow() || !core.equals(cfg) || !archiveStamp.equals(stamp(archive))) throw new IllegalStateException("native identity/state changed");
    }
    private static void finish(Minecraft c) throws Exception {
        guard(c);if(!BslQuadEligibility.validate(archive,options()).eligible()) throw new IllegalStateException("terminal original archive mismatch");
        var snap=GpuTimers.snapshot();REPORT.add("gpu",GSON.toJsonTree(snap));REPORT.add("rawCompletedSamples",GSON.toJsonTree(RAW));REPORT.addProperty("measuredRenderFrames",measuredFrames);
        REPORT.addProperty("queueDrops",GpuTimers.queueDrops);REPORT.addProperty("availabilityPollMisses",GpuTimers.unavailablePolls);REPORT.addProperty("availableResults",GpuTimers.availableResults);REPORT.addProperty("zeroResults",GpuTimers.zeroResults);
        boolean useful=!snap.status().startsWith("unavailable") && snap.pendingQueries()==0 && RAW.stream().anyMatch(s->s.scope().equals(GpuTimers.FRAME)&&s.milliseconds()>0) && RAW.stream().anyMatch(s->s.scope().startsWith("terrain/")&&s.milliseconds()>0);
        REPORT.addProperty("status",useful?"complete-native-gpu-observations":"incomplete-native-gpu-observations");
        REPORT.addProperty("interpretation","Rotating elapsed scopes sample different frames; no additive/removable fraction. Query and guard overhead included. No speed or quality acceptance.");
        done=true;GpuTimers.close();capturePending=true;captureAfterRender=true;write(c);
    }
    private static void captureStill(Minecraft c) {
        String name="shaderlod-bsl-"+(ALPHA?"alpha-native-":"gpu-native-")+System.currentTimeMillis()+".png";REPORT.addProperty("still",c.gameDirectory.toPath().resolve("screenshots").resolve(name).toString());
        Screenshot.grab(c.gameDirectory,name,c.getMainRenderTarget(),1,m->c.execute(()->{capturePending=false;REPORT.addProperty("stillMessage",m.getString());REPORT.addProperty("stillSaved",Files.isRegularFile(c.gameDirectory.toPath().resolve("screenshots").resolve(name)));write(c);}));write(c);
    }
    private static void fail(Minecraft c,Throwable f) { done=true;frameActive=false;REPORT.addProperty("status",ALPHA?"failed-native-alpha-admission":"failed-native-gpu-diagnostic");REPORT.addProperty("failure",f.toString());try { REPORT.add("gpuBeforeClose",GSON.toJsonTree(GpuTimers.snapshot()));GpuTimers.close(); } catch(Exception ignored){}write(c); }
    private static void write(Minecraft c) { try {REPORT.addProperty("capturePending",capturePending);var p=c.gameDirectory.toPath().resolve(ALPHA?"shaderlod-dev/bsl-alpha-admission.json":"shaderlod-dev/bsl-gpu-budget.json");Files.createDirectories(p.getParent());Files.writeString(p,GSON.toJson(REPORT)+"\n");} catch(Exception e){org.slf4j.LoggerFactory.getLogger("shaderlod-dev").error("GPU diagnostic receipt failed",e);} }
}
