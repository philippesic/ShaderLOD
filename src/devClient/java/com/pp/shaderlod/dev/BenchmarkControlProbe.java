package com.pp.shaderlod.dev;

import com.google.gson.*;
import com.pp.shaderlod.benchmark.core.*;
import com.pp.shaderlod.measurement.FrameMeasurement;
import com.pp.shaderlod.dev.timing.CpuTimers;
import com.pp.shaderlod.dev.timing.GpuTimers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.irisshaders.iris.Iris;
import java.lang.management.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Opt-in canonical controller using the same clock/model with the main mod absent or present. */
public final class BenchmarkControlProbe {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().serializeNulls().registerTypeAdapter(Double.class,(JsonSerializer<Double>)(v,t,c)->Double.isFinite(v)?new JsonPrimitive(v):JsonNull.INSTANCE).create();
    private static final String WORLD="ShaderLOD-Landscape";
    private static final String SAVED_AUTHORED="ShaderLOD-Benchmark-overworld-3-flat";
    private static final ThreadMXBean THREAD=ManagementFactory.getThreadMXBean();
    private static BenchmarkControlProbe owner;
    private final Path control,root;
    private long modified=-1,renderFrames,worldFrames;
    private boolean renderingAgeArmed;
    private long firstUsableRenderNs, firstUsableRenderFrame;
    private net.minecraft.world.item.ItemStack glintPrevious;
    private Object glintPlayer;
    private net.minecraft.world.level.GameType glintGameMode;
    private int closing;
    private boolean readyReported,configured;
    private Capture capture;
    private Motion motion;
    private static final boolean QUALITY_RUNTIME=Boolean.getBoolean("shaderlod.dev.qualityMotionRuntime");
    private CameraQualitySession quality;
    private JsonObject lastMotion = new JsonObject();
    private static final class Motion {
        final CameraQualitySession.MotionPath path;
        final Object world, pack, pipeline, camera;
        final long started=System.nanoTime();
        int ticks;
        Motion(Minecraft c,JsonObject request){path=new CameraQualitySession.MotionPath(c,request);world=c.level;pack=Iris.getCurrentPack().orElse(null);pipeline=Iris.getPipelineManager().getPipelineNullable();camera=c.player;}
    }
    private WorldState previous;
    private boolean savedAuthoredOpened;
    private int savedPoseIndex;
    private static final String SAVED_SMOKE="ShaderLOD-Smoke";
    private String savedWorld=SAVED_AUTHORED;
    private SavedPose naturalPose;
    private boolean savedNether;
    private static final SavedPose NETHER_POSE=new SavedPose(.5,200,.5,0,4,6000);
    private final StandardFeatureScene standardFeatureScene=new StandardFeatureScene();
    // Exact clear-weather poses in the existing pinned overworld-3-flat save.
    // No world construction, material edits or unbounded camera coordinates.
    private record SavedPose(double x,double y,double z,float yaw,float pitch,long day) {}
    private static final List<SavedPose> SAVED_POSES=List.of(
        new SavedPose(.5,74,-20.5,0,4,6000),new SavedPose(-40.5,69,6.5,-15,0,6000),
        new SavedPose(.5,71,82.5,0,9,6000),new SavedPose(.5,66,117.5,0,-3,6000),
        new SavedPose(-43.5,70,54.5,0,0,6000),new SavedPose(.5,71,82.5,90,-4,12000),
        new SavedPose(-43.5,70,54.5,0,0,18000),
        new SavedPose(.5,71,82.5,0,-65,6000),new SavedPose(-43.5,70,54.5,0,-80,12000),
        // Existing native grass top (ground y64) and brick gallery (z73).
        // Bounded close material coverage; no blocks or resource data are edited.
        new SavedPose(.5,65,-6.5,0,65,6000),new SavedPose(-50.5,65,71,0,10,6000),
        // Existing stone gallery face has native Replete normal/height data.
        new SavedPose(-60.5,65,71,0,10,6000));
    private final Map<Object,Object> dhPrevious=new LinkedHashMap<>(),dhLast=new LinkedHashMap<>();
    private final Map<String,Object> dhConfigs=new HashMap<>();
    private record WorldState(Object server,double x,double y,double z,float yaw,float pitch,long day,int rain,int thunder,boolean raining,boolean thundering,boolean frozen,int distance,boolean hideGui,net.minecraft.world.level.GameType gameType) {}
    private static final FrameMeasurement.GpuBackend NO_GPU=new FrameMeasurement.GpuBackend(){
        public String status(){return "performance: no GPU queries; elapsed unavailable";} public int counterBits(){return 0;}
        public int create(){throw new IllegalStateException("Queries disabled");} public boolean busy(){return false;}
        public void begin(int q){throw new IllegalStateException("Queries disabled");} public void end(){throw new IllegalStateException("Queries disabled");}
        public boolean available(int q){return false;} public long nanoseconds(int q){throw new IllegalStateException("Queries disabled");} public void delete(int q){}
    };
    private static final class Capture {
        final String label,comparison,repeat,arm; final boolean recording,qualification; final long delayNs,started=System.nanoTime();
        final boolean authored;final String sceneVersion,sceneStatus;
        final Map<String,String> featureProfile;
        final long frameStart,worldStart;final Object world,pack,pipeline,camera;final JsonObject before;
        final FrameMeasurement clock;final List<FrameMeasurement.Sample> samples=new ArrayList<>();
        final List<Long> completedRenderEndsNs=new ArrayList<>();
        final NativeCpuPlacement cpuPlacement=new NativeCpuPlacement();
        final NativeRenderAffinity affinity;
        final double x,y,z;final float yaw,pitch,rain,thunder;final int width,height,rd,limit;final long generation,day;
        final Object server;final boolean vsync,fullscreen;final String dimension;
        long observed,completed,spinIterations;String failure="";boolean rendering;
        Capture(JsonObject r,JsonObject snapshot,long frames,long worlds){
            var c=Minecraft.getInstance();label=text(r,"label","window");comparison=text(r,"comparison","controls");repeat=text(r,"repeat","session");arm=text(r,"arm",ModList.get().isLoaded("shaderlod")?"MASTER_ON_NO_REQUESTS":"ABSENT");
            if(!label.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid window label");
            qualification=r.has("qualification")&&r.get("qualification").getAsBoolean();recording=!r.has("recording")||r.get("recording").getAsBoolean();delayNs=r.has("calibration_delay_ns")?r.get("calibration_delay_ns").getAsLong():0;
            if(delayNs<0||delayNs>25_000_000)throw new IllegalArgumentException("Calibration delay must be0..25ms");
            boolean saved=owner!=null&&owner.savedAuthoredActive();
            boolean nativeTerrain=saved&&SAVED_SMOKE.equals(owner.savedWorld)&&Boolean.getBoolean("shaderlod.dev.nativeTerrainScene");
            authored=saved||main()&&Installed.authoredActive();sceneVersion=nativeTerrain?"saved-native-terrain-v1":saved&&owner.savedNether?"saved-nether-native-v1":saved&&SAVED_SMOKE.equals(owner.savedWorld)?"saved-natural-cave-v1":saved?"saved-authored-v3":authored?Installed.authoredVersion():"coast-v1";sceneStatus=nativeTerrain?"Prepared native seed8675309 spawn; no cave/query census or constructed geometry":saved&&owner.savedNether?"Existing saved Nether view; native non-Overworld mip graph":saved&&SAVED_SMOKE.equals(owner.savedWorld)?"Existing native seed8675309 cave selected by bounded loaded-chunk census; no constructed blocks":saved?"Existing saved authored scene; no construction by this caller":authored?Installed.authoredStatus():"coast fixed pose";
            featureProfile=main()?Installed.featureProfile():Map.of();
            before=snapshot;frameStart=frames;worldStart=worlds;world=c.level;pack=Iris.getCurrentPack().orElse(null);pipeline=Iris.getPipelineManager().getPipelineNullable();camera=c.getCameraEntity();
            x=c.getCameraEntity().getX();y=c.getCameraEntity().getY();z=c.getCameraEntity().getZ();yaw=c.getCameraEntity().getYRot();pitch=c.getCameraEntity().getXRot();
            width=c.getWindow().getWidth();height=c.getWindow().getHeight();rd=c.options.renderDistance().get();generation=main()?Installed.generation():0;
            dimension=c.level.dimension().location().toString();server=c.getSingleplayerServer();day=c.level.getDayTime();rain=c.level.getRainLevel(1);thunder=c.level.getThunderLevel(1);limit=c.options.framerateLimit().get();vsync=c.options.enableVsync().get();fullscreen=c.getWindow().isFullscreen();
            clock=new FrameMeasurement(NO_GPU,System::nanoTime,()->THREAD.isCurrentThreadCpuTimeSupported()&&THREAD.isThreadCpuTimeEnabled()?THREAD.getCurrentThreadCpuTime():-1,()->ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os?os.getProcessCpuTime():-1);clock.enabled(recording);
            affinity=NativeRenderAffinity.open();
        }
    }
    private BenchmarkControlProbe(Path control){this.control=control.toAbsolutePath();root=this.control.getParent();}
    public static boolean register(){String p=System.getProperty("shaderlod.dev.benchmarkControlProbe","");if(p.isBlank())return false;NativeCpuPlacement.warm();owner=new BenchmarkControlProbe(Path.of(p));NeoForge.EVENT_BUS.addListener(owner::tick);return true;}
    private static boolean main(){return ModList.get().isLoaded("shaderlod");}
    public static void beginWorld(){if(owner!=null&&owner.capture!=null)owner.worldFrames++;}
    public static void beginRender(){if(owner==null||owner.capture==null)return;var w=owner.capture;owner.guard(w);w.observed=owner.worldFrames;w.rendering=true;if(w.recording)w.cpuPlacement.begin();w.clock.begin();}
    public static void endRender(){if(owner==null)return;owner.renderFrames++;com.pp.shaderlod.dev.readiness.ReadinessEvents.frame();if(owner.renderingAgeArmed)owner.markRenderingAge();if(QUALITY_RUNTIME&&owner.quality!=null)owner.quality.render(Minecraft.getInstance());var w=owner.capture;if(w==null)return;
        if(w.delayNs>0){long end=System.nanoTime()+w.delayNs;while(System.nanoTime()<end)w.spinIterations++;}
        w.clock.end();if(w.rendering&&w.recording)w.cpuPlacement.end();owner.guard(w);if(!w.rendering||owner.worldFrames!=w.observed+1)w.failure="Expected exactly one world render per renderer frame";if(w.rendering){w.completed++;if(w.recording)w.completedRenderEndsNs.add(System.nanoTime());}w.rendering=false;
    }
    /** One origin per fresh process, at the first completed fixed-scene usable render after arming at title. */
    private void markRenderingAge(){var c=Minecraft.getInstance();if(!savedAuthoredReady()||c.screen!=null||c.isPaused()||!c.isWindowActive()||Iris.isFallback()
            ||c.getWindow().getWidth()!=2560||c.getWindow().getHeight()!=1600||!c.getWindow().isFullscreen()
            ||c.options.renderDistance().get()!=12||c.options.framerateLimit().get()!=260||c.options.enableVsync().get())return;
        firstUsableRenderNs=System.nanoTime();firstUsableRenderFrame=renderFrames;renderingAgeArmed=false;
        com.pp.shaderlod.dev.readiness.ReadinessEvents.firstUsable(firstUsableRenderNs);
    }
    private void guard(Capture w){if(!w.failure.isEmpty())return;var c=Minecraft.getInstance();var camera=c.getCameraEntity();
        if(!c.isWindowActive()||c.screen!=null||c.isPaused())w.failure="Focus/GUI/pause changed";
        else if(c.level!=w.world||camera!=w.camera||Iris.isFallback()||Iris.getCurrentPack().orElse(null)!=w.pack||Iris.getPipelineManager().getPipelineNullable()!=w.pipeline)w.failure="World/pack/pipeline changed";
        else if(c.getWindow().getWidth()!=w.width||c.getWindow().getHeight()!=w.height||c.options.renderDistance().get()!=w.rd)w.failure="Viewport/render distance changed";
        else if(c.getSingleplayerServer()!=w.server||c.level.getDayTime()!=w.day||c.level.getRainLevel(1)!=w.rain||c.level.getThunderLevel(1)!=w.thunder)w.failure="Server/time/weather changed";
        else if(c.options.framerateLimit().get()!=w.limit||c.options.enableVsync().get()!=w.vsync||c.getWindow().isFullscreen()!=w.fullscreen)w.failure="Presentation settings changed";
        else if(camera.getX()!=w.x||camera.getY()!=w.y||camera.getZ()!=w.z||camera.getYRot()!=w.yaw||camera.getXRot()!=w.pitch)w.failure="Fixed camera changed";
        else if(main()&&Installed.generation()!=w.generation)w.failure="Optimization generation changed";
    }
    private void tick(ClientTickEvent.Post event){var c=Minecraft.getInstance();if(closing>0&&--closing==0){c.stop();return;}advanceMotion(c);if(QUALITY_RUNTIME&&quality!=null)quality.tick(c);if(main()&&com.pp.shaderlod.optimization.NativeAdmissionCost.ENABLED)CpuAdmissionCostProbe.tick(renderFrames);
        if(!configured&&c.getWindow()!=null){org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(c.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_AUTO_ICONIFY,0);configured=true;}
        if(capture!=null)capture.samples.addAll(capture.clock.drain());
        if(!readyReported&&c.level!=null&&c.levelRenderer.hasRenderedAllSections()&&BackgroundLaunch.qualityWindowReady(c)){
            readyReported=true;var w=c.getWindow();var t=c.getMainRenderTarget();
            com.mojang.logging.LogUtils.getLogger().info("ShaderLOD benchmark control ready: shaders={}, fallback={}",Iris.getCurrentPack().isPresent(),Iris.isFallback());
            com.mojang.logging.LogUtils.getLogger().info("ShaderLOD inspection native fullscreen: {}, monitor={}, framebuffer={}x{}, target={}x{}",w.isFullscreen(),org.lwjgl.glfw.GLFW.glfwGetWindowMonitor(w.getWindow()),w.getWidth(),w.getHeight(),t.width,t.height);
        }
        if(!Files.isRegularFile(control))return;
        try{long time=Files.getLastModifiedTime(control).toMillis();if(time==modified)return;modified=time;var r=JsonParser.parseString(Files.readString(control)).getAsJsonObject();String id=r.get("request_id").getAsString();if(!id.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Request identity");
            // Reserve separately: published receipts are immutable, avoiding Windows read/replace races.
            Path receipt=root.resolve("receipts").resolve(id+".json");Files.createDirectories(receipt.getParent());if(Files.exists(receipt))throw new IllegalStateException("Receipt already published");Files.writeString(receipt.resolveSibling(id+".pending"),"PENDING",StandardOpenOption.CREATE_NEW);var out=new JsonObject();out.addProperty("request_id",id);
            try{String action=r.get("action").getAsString();if(capture!=null&&!Set.of("window_stop","status","cancel").contains(action))throw new IllegalStateException("Capture owns client; stop it before edits");
                if(main()&&CpuAdmissionCostProbe.active()&&!Set.of("cpu_admission_cost","status","cancel").contains(action))throw new IllegalStateException("Stop untimed CPU admission costs before mutation or timing");
                if(motion!=null&&!Set.of("motion_stop","status","capture","capture_coast","capture_saved_authored","causal_fragment","cancel").contains(action))throw new IllegalStateException("Quality motion owns client; stop it before edits or timing");
                if(quality!=null&&!quality.finalized()&&!Set.of("quality_stop","quality_status","status").contains(action))throw new IllegalStateException("Quality image leases own client; stop and drain before edits, timing or restoration");
                if(com.pp.shaderlod.dev.timing.CloudSamplerStateProbe.quadCapturePresent()&&!Set.of("g06_sampler_observe","status","cancel").contains(action))throw new IllegalStateException("Stop bounded quad observation before mutation or timing");
                if(Set.of("configure","settings_apply","pack","cancel","open_authored","close_authored","video","background_size","frame_copy_observe","resources").contains(action))HistoryRotationProbe.closeBeforeMutation("DEV_CONTROL_"+action);
                switch(action){
                    case "prepare_feature_scene"->{idle();requireMain();requireSavedAuthored();
                        if(!"translucent_fastpath".equals(r.get("feature").getAsString())||!savedAuthoredReady()
                            ||motion!=null||Installed.frameCopyObserving()||Installed.bslMipObserving()
                            ||CpuTimers.recording()||GpuTimers.recording())throw new IllegalStateException("Exclusive untimed G04 scene preparation required");
                        out.add("feature_scene",standardFeatureScene.prepare(root,snapshot()));}
                    case "history_rotation"->{idle();requireMain();out.add("history_rotation",HistoryRotationProbe.request(r.has("operation")?r.get("operation").getAsString():"status"));}

                    case "g04_honey_place"->{idle();requireMain();requireSavedAuthored();if(!savedAuthoredReady()||motion!=null||Installed.frameCopyObserving()||Installed.bslMipObserving()||G01TemporalProbe.active()||G01GraphProbe.active())throw new IllegalStateException("Exclusive untimed settled saved scene required");out.add("g04_honey",G04HoneyFixture.place(snapshot(),r.get("ownership_manifest_sha256").getAsString()));}
                    case "status"->{}
                    case "quality_start"->{idle();requireSavedAuthored();
                        if(!QUALITY_RUNTIME||!savedAuthoredReady()||motion!=null||CpuTimers.recording()||GpuTimers.recording()
                            ||MipFamilyProbe.observing()||G01TemporalProbe.active()||G01GraphProbe.active()
                            ||main()&&(Installed.frameCopyObserving()||Installed.bslMipObserving()))
                            throw new IllegalStateException("Exclusive untimed settled saved scene required");
                        quality=CameraQualitySession.createForBenchmark(c,root,r.get("label").getAsString(),r.getAsJsonObject("request"));
                        out.add("quality",quality.status());}
                    case "quality_stop","quality_status"->{if(!QUALITY_RUNTIME)throw new IllegalStateException("Quality runtime disabled");
                        if(quality==null){var q=new JsonObject();q.addProperty("finalized",true);q.addProperty("status","none");out.add("quality",q);}
                        else{if(action.equals("quality_stop"))quality.cancel(c);out.add("quality",quality.status());}}
                    case "reflection_status"->{idle();requireMain();out.addProperty("bsl_exact_reflection",com.pp.shaderlod.optimization.bslexact.BslExactSsrRuntime.status());}
                    case "ao_depth_status"->{idle();requireMain();var proof=JSON.toJsonTree(com.pp.shaderlod.optimization.bsl.BslAoDepthRuntime.controlProof()).getAsJsonObject();
                        proof.addProperty("status",com.pp.shaderlod.optimization.bsl.BslAoDepthRuntime.status());out.add("ao_depth",proof);}
                    case "readiness_status"->out.add("readiness",com.pp.shaderlod.dev.readiness.ReadinessEvents.status());
                    case "readiness_sources"->out.add("readiness_sources",com.pp.shaderlod.dev.readiness.ReadinessEvents.sourcePins());
                    case "readiness_mark"->com.pp.shaderlod.dev.readiness.ReadinessEvents.event("MILESTONE",null,null,r.get("marker").getAsLong(),0);
                    case "readiness_export"->{idle();out.add("readiness",com.pp.shaderlod.dev.readiness.ReadinessEvents.export());}
                    case "vrs_status"->{idle();requireMain();out.add("vrs",JSON.toJsonTree(com.pp.shaderlod.optimization.vrs.BloomVrsRuntime.status()));}
                    case "inactive_draw_elision"->{idle();requireMain();com.pp.shaderlod.optimization.sourcecache.InactiveDrawElision.configure(r.get("enabled").getAsBoolean());out.addProperty("inactive_draw_elision",com.pp.shaderlod.optimization.sourcecache.InactiveDrawElision.elide(true));}
                    case "early_alpha_status"->{idle();requireMain();if(r.has("observe"))com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpPostSampleAlphaWitness.observe(r.get("observe").getAsBoolean(),r.has("reset")&&r.get("reset").getAsBoolean());out.add("makeup_post_sample_draw_witness",JSON.toJsonTree(com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpPostSampleAlphaWitness.snapshot()));out.addProperty("makeup_early_alpha_status",com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime.status());out.add("makeup_early_alpha_costs",JSON.toJsonTree(com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime.costCounters()));var pack=Iris.getCurrentPack().orElse(null);var diagnostic=new JsonObject();
                        diagnostic.addProperty("exact_api",com.pp.shaderlod.optimization.makeupearlyalpha.mixin.MakeUpEarlyAlphaPlugin.exactApi());
                        if(pack!=null){var options=pack.getShaderPackOptions();var set=options.getOptionSet();var values=com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(set,options.getOptionValues());
                            diagnostic.addProperty("declaration",com.pp.shaderlod.optimization.makeupao.MakeUpAoOptionGuard.declaration(set));
                            diagnostic.addProperty("profile",com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime.profile(values));
                            diagnostic.addProperty("source",com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime.source(pack));
                            diagnostic.addProperty("compile_enabled",com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaCompilation.enabled(pack));
                            diagnostic.addProperty("pack_view",pack instanceof com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.PackView);
                            if(pack instanceof com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.PackView view){var input=view.shaderlod$makeupPackInput();diagnostic.addProperty("input_present",input!=null);if(input!=null){diagnostic.addProperty("input_current",input.options().current(options));diagnostic.addProperty("input_early_alpha",input.options().values().get("SHADERLOD_MAKEUP_EARLY_ALPHA"));}}
                            var defaults=new java.util.TreeMap<String,String>();set.getBooleanOptions().forEach((k,v)->defaults.put(k,Boolean.toString(v.getOption().getDefaultValue())));set.getStringOptions().forEach((k,v)->defaults.put(k,v.getOption().getDefaultValue()));
                            var expected=new java.util.TreeMap<>(com.pp.shaderlod.optimization.makeupao.MakeUpAoContract.generated("0"));expected.put("SHADERLOD_REFINEMENT_ADAPTER","0");var differences=new JsonObject();var keys=new java.util.TreeSet<>(defaults.keySet());keys.addAll(expected.keySet());for(var key:keys)if(!java.util.Objects.equals(defaults.get(key),expected.get(key))){var delta=new JsonObject();delta.addProperty("expected",expected.get(key));delta.addProperty("actual",defaults.get(key));differences.add(key,delta);}diagnostic.add("default_differences",differences);
                        }out.add("makeup_early_alpha_source_diagnostic",diagnostic);}
                    case "c01_observation"->{idle();requireMain();Installed.idle();requireSavedAuthored();
                        if(BenchmarkProfileComparison.active()||!savedAuthoredReady()||c.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD
                            ||c.screen!=null||c.isPaused()||!c.isWindowActive()||Iris.isFallback()
                            ||motion!=null||Installed.frameCopyObserving()||Installed.bslMipObserving()
                            ||CpuTimers.recording()||GpuTimers.recording()
                            ||!com.pp.shaderlod.optimization.makeupprime.C01Observation.privateCostQualified())
                            throw new IllegalStateException("Private settled owned native C01 cost diagnostic required");
                        for(String key:r.keySet())if(!Set.of("request_id","action","frames","visibility_frames").contains(key))
                            throw new IllegalArgumentException("Unknown private C01 observation field");
                        if(r.has("visibility_frames")&&!r.has("frames"))throw new IllegalArgumentException("Timing frame count required");
                        if(r.has("frames")){
                            int frames=r.get("frames").getAsInt();
                            if((frames!=0&&frames!=128)||!r.has("visibility_frames")||r.get("visibility_frames").getAsInt()!=0)
                                throw new IllegalArgumentException("Private cost pass requires 0/128 frames and visibility_frames=0");
                            com.pp.shaderlod.optimization.makeupprime.C01Observation.configure(id,frames,0);
                        }
                        out.add("c01_observation",JSON.toJsonTree(com.pp.shaderlod.optimization.makeupprime.C01Observation.snapshot()));
                    }
                    case "c01_bounds"->{idle();requireMain();
                        if(r.has("mode")){
                            if(c.level==null||c.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD
                                ||com.pp.shaderlod.tuning.AutoTuner.active()||com.pp.shaderlod.measurement.RuntimeMeasurement.requested())
                                throw new IllegalStateException("Untimed owned Overworld C01 configuration required");
                            com.pp.shaderlod.optimization.makeupprime.C01Prefix.stop();
                            com.pp.shaderlod.optimization.makeupprime.C01Bounds.configure(id,r.get("mode").getAsString(),r.has("prefix")?r.get("prefix").getAsInt():4,r.has("group")?r.get("group").getAsInt():16,r.has("frames")?r.get("frames").getAsInt():0,r.has("delay")?r.get("delay").getAsInt():256);
                        }out.add("c01_bounds",JSON.toJsonTree(com.pp.shaderlod.optimization.makeupprime.C01Bounds.snapshot()));
                    }
                    case "generated_control"->{idle();requireMain();
                        var applied=com.pp.shaderlod.optimization.OptimizationEditTransaction.apply(
                            com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested(),Iris::reload,Iris::isFallback,com.pp.shaderlod.optimization.GeneratedControl::open);
                        if(!applied.success())throw new IllegalStateException(applied.message());
                        com.pp.shaderlod.optimization.GeneratedControl.holdCurrentSelection();
                    }
                    case "g07_diagnostic"->{idle();requireMain();out.add("g07_diagnostic",G07Diagnostic.read(r));}
                    case "runtime_classes"->{idle();requireMain();out.add("runtime_classes",RuntimeClassEvidence.read());}
                    case "moreculling_state"->{idle();requireMain();out.add("moreculling",MoreCullingEvidence.read());}
                    case "g06_sampler_observe"->{idle();requireMain();out.add("g06_sampler",g06SamplerObserve(r,id));}
                    case "vrs_request"->{idle();requireMain();out.addProperty("vrs_result",com.pp.shaderlod.optimization.vrs.BloomVrsRuntime.setRequested(r.get("enabled").getAsBoolean()));out.add("vrs",JSON.toJsonTree(com.pp.shaderlod.optimization.vrs.BloomVrsRuntime.status()));}
                    case "motion_start"->{idle();if(motion!=null||!(savedAuthoredReady()||naturalCoastOwned()||main()&&Installed.authoredReady()&&Installed.authoredPoseReady())||c.player==null||!BackgroundLaunch.qualityWindowReady(c)||c.screen!=null||c.isPaused()||Iris.isFallback()||main()&&Installed.frameCopyObserving())throw new IllegalStateException("Settled authored pose in a focused or hidden diagnostic window required");motion=new Motion(c,r.getAsJsonObject("path"));lastMotion=new JsonObject();lastMotion.addProperty("quality_only",true);lastMotion.addProperty("duration_seconds",motion.path.duration());}
                    case "motion_stop"->{finishMotion(c,"cancelled",true);}
                    case "frame_copy_observe"->{idle();requireMain();Installed.frameCopyObserve(r.get("enabled").getAsBoolean(),!r.has("reset")||r.get("reset").getAsBoolean());}
                    case "bsl_material_arm"->{idle();BslMaterialProbe.arm(root.resolve("bsl-material-"+id+".json"));}
                    case "bsl_material_status"->{idle();out.add("bsl_material",BslMaterialProbe.status());}
                    case "makeup_single_alpha_private"->{idle();requireMain();if(r.has("enabled"))com.pp.shaderlod.experiment.sourceonlyalpha.MakeUpSingleAlphaR1.configure(r.get("enabled").getAsBoolean());out.add("makeup_single_alpha_private",JSON.toJsonTree(com.pp.shaderlod.experiment.sourceonlyalpha.MakeUpSingleAlphaR1.status()));}
                    case "bsl_alpha_private"->{idle();requireMain();if(r.has("enabled")){if(r.has("variant"))com.pp.shaderlod.experiment.sourceonlyalpha.BslPostSampleAlphaR1.configure(r.get("enabled").getAsBoolean(),r.get("variant").getAsString());else com.pp.shaderlod.experiment.sourceonlyalpha.BslPostSampleAlphaR1.configure(r.get("enabled").getAsBoolean());}out.add("bsl_alpha_private",JSON.toJsonTree(r.has("diagnostic")&&r.get("diagnostic").getAsBoolean()?com.pp.shaderlod.experiment.sourceonlyalpha.BslPostSampleAlphaR1.diagnose():com.pp.shaderlod.experiment.sourceonlyalpha.BslPostSampleAlphaR1.status()));}
                    case "bsl_public_native_option"->{idle();requireMain();out.add("native_option",JSON.toJsonTree(BslNativeOptionProbe.taa(r.get("taa").getAsBoolean())));}
                    case "bsl_public_option_cutout_arm"->{idle();BslMaterialProbe.armBslOptionMismatch(root.resolve("bsl-option-cutout-"+id+".json"));}
                    case "bsl_public_cutout_arm"->{idle();BslMaterialProbe.armBslPublic(root.resolve("bsl-public-cutout-"+id+".json"));}
                    case "c01_cost"->{idle();requireMain();
                        if(r.has("observe")){
                            if(r.get("observe").getAsBoolean()){
                                if(!savedAuthoredOpened||!savedAuthoredReady()||motion!=null||Installed.frameCopyObserving()||Installed.bslMipObserving()
                                    ||CpuTimers.recording()||GpuTimers.recording()
                                    ||!com.pp.shaderlod.optimization.makeupprime.C01Observation.privateCostQualified())
                                    throw new IllegalStateException("Exclusive settled native C01 cost capture required");
                                int group=r.has("group")?r.get("group").getAsInt():32;if(!Set.of(8,16,32).contains(group))throw new IllegalArgumentException("Finite C01 cost group required");
                                com.pp.shaderlod.optimization.makeupprime.C01Bounds.configure(id,"deferred_cpu",1,group,128,256);
                                com.pp.shaderlod.optimization.makeupprime.C01Observation.configure(id,128,0);
                                com.pp.shaderlod.optimization.makeupprime.C01Cost.observe(true);
                            }else{
                                com.pp.shaderlod.optimization.makeupprime.C01Cost.observe(false);
                                com.pp.shaderlod.optimization.makeupprime.C01Observation.stop();
                                com.pp.shaderlod.optimization.makeupprime.C01Bounds.stop();
                            }
                        }
                        var capture=new JsonObject();capture.add("work",JSON.toJsonTree(com.pp.shaderlod.optimization.makeupprime.C01Bounds.snapshot()));
                        capture.add("observation",JSON.toJsonTree(com.pp.shaderlod.optimization.makeupprime.C01Observation.snapshot()));out.add("c01_cost",capture);
                    }
                    case "g09_prefix_admission"->{idle();requireMain();if(!"1".equals(System.getProperty("shaderlod.dev.g09PrefixAdmissionOnly")))throw new IllegalStateException("Exact opt-in admission-only diagnostic required");out.addProperty("prefix_implementation",com.pp.shaderlod.optimization.OptimizationRuntimeContext.implementation("bsl-shaders",com.pp.shaderlod.optimization.OptimizationId.POM_EMPTY_HEIGHT_PREFIX));out.addProperty("prefix_status",com.pp.shaderlod.bslprefix.BslPrefixRuntime.status());out.add("prefix_link_diagnostics",JSON.toJsonTree(com.pp.shaderlod.bslprefix.BslPrefixRuntime.linkDiagnostics()));}
                    case "cpu_admission_cost"->{idle();requireMain();if(r.has("stop")&&r.get("stop").getAsBoolean())CpuAdmissionCostProbe.stop(renderFrames);if(r.has("frames"))CpuAdmissionCostProbe.start(renderFrames,r.get("frames").getAsInt());out.add("cpu_admission_cost",JSON.toJsonTree(CpuAdmissionCostProbe.status()));if(r.has("transitions")&&r.get("transitions").getAsBoolean())out.add("cpu_mode_transitions",JSON.toJsonTree(CpuAdmissionCostProbe.modeTransitions()));}
                    case "solas_lean_alpha"->{idle();requireMain();
                        if(r.has("arm")){
                            if(r.get("arm").getAsBoolean())SolasLeanAlphaProbe.arm(r.get("pack").getAsString(),r.get("sha256").getAsString(),r.get("vertex_sha256").getAsString(),r.get("fragment_sha256").getAsString());
                            else SolasLeanAlphaProbe.disarm();
                        }
                        out.add("solas_lean_alpha",JSON.toJsonTree(SolasLeanAlphaProbe.status()));}
                    case "sildur_lean_alpha"->{idle();requireMain();
                        if(r.has("arm")){
                            if(r.get("arm").getAsBoolean())SildurLeanAlphaProbe.arm(r.get("pack").getAsString(),r.get("sha256").getAsString(),r.get("vertex_sha256").getAsString(),r.get("fragment_sha256").getAsString());
                            else SildurLeanAlphaProbe.disarm();
                        }
                        out.add("sildur_lean_alpha",JSON.toJsonTree(SildurLeanAlphaProbe.status()));}
                    case "cpu_graph_identity"->{idle();requireMain();out.add("cpu_graph_identity",JSON.toJsonTree(NativeCpuIdentity.graph()));out.add("admission_cache_window",JSON.toJsonTree(Installed.cacheWindow()));}
                    case "bsl_public_state"->{idle();out.addProperty("bsl_public_active",com.pp.shaderlod.optimization.bslearlyalpha.BslPostSampleAlpha.active());out.addProperty("bsl_public_status",com.pp.shaderlod.optimization.bslearlyalpha.BslPostSampleAlpha.status());out.addProperty("bsl_public_implementation",com.pp.shaderlod.optimization.OptimizationRuntimeContext.implementation("bsl-shaders",com.pp.shaderlod.optimization.OptimizationId.EARLY_ALPHA));}
                    case "bsl_private_cutout_arm"->{idle();BslMaterialProbe.armBslPrivate(root.resolve("bsl-private-cutout-"+id+".json"));}
                    case "g03_cutout_arm"->{idle();BslMaterialProbe.armG03(root.resolve("g03-cutout-"+id+".json"),r.get("sha256").getAsString());}
                    case "g03_public_cutout_arm"->{idle();BslMaterialProbe.armG03Public(root.resolve("g03-public-cutout-"+id+".json"),r.get("sha256").getAsString());}
                    case "g03_canopy_install"->{idle();requireSavedAuthored();out.add("g03_canopy",G03CanopyFixture.install());}
                    case "g03_canopy_verify"->{idle();requireSavedAuthored();out.add("g03_canopy",G03CanopyFixture.verify());}
                    case "g03_canopy_restore"->{idle();out.add("g03_canopy",G03CanopyFixture.restore());}
                    case "bsl_mip_observe"->{idle();requireMain();if(motion!=null||Installed.frameCopyObserving())throw new IllegalStateException("Exclusive untimed observer required");boolean enabled=r.get("enabled").getAsBoolean();if(r.has("reload")&&r.get("reload").getAsBoolean()&&!enabled)throw new IllegalArgumentException("Reload requires armed observer");Installed.bslMipObserve(enabled,!r.has("reset")||r.get("reset").getAsBoolean());if(r.has("reload")&&r.get("reload").getAsBoolean())Iris.reload();out.add("bsl_mip_witness",Installed.bslMipSnapshot());}
                    case "capture"->{idle();requireMain();if(!Installed.authoredReady()||!BackgroundLaunch.qualityWindowReady(c)||c.screen!=null||Iris.isFallback())throw new IllegalStateException("Settled authored world in a focused or hidden diagnostic window required");String name="probe-"+id+".png";net.minecraft.client.Screenshot.grab(c.gameDirectory,name,c.getMainRenderTarget(),message->{});out.addProperty("capture_requested",c.gameDirectory.toPath().resolve("screenshots").resolve(name).toString());out.addProperty("capture_completed",false);}
                    case "capture_current"->{idle();if(c.level==null||c.player==null||!c.levelRenderer.hasRenderedAllSections()||!BackgroundLaunch.qualityWindowReady(c)||c.screen!=null||c.isPaused()||Iris.isFallback())throw new IllegalStateException("Settled unpaused shader world in a focused or hidden diagnostic window required");String name="probe-"+id+".png";net.minecraft.client.Screenshot.grab(c.gameDirectory,name,c.getMainRenderTarget(),message->{});out.addProperty("capture_requested",c.gameDirectory.toPath().resolve("screenshots").resolve(name).toString());out.addProperty("capture_completed",false);}
                    case "g01_exposure_probe"->{idle();if(motion!=null||main()&&Installed.frameCopyObserving())throw new IllegalStateException("Exclusive untimed exposure proof required");out.add("g01_exposure",G01ExposureProbe.read(r.get("sha256").getAsString()));}
                    case "g01_temporal"->{idle();if(r.has("frames")&&Boolean.getBoolean("shaderlod.experimentalMakeUpHistoryExchange"))throw new IllegalStateException("Readback observer excluded from canonical exchange qualification");if(main()&&Installed.frameCopyObserving())throw new IllegalStateException("Exclusive untimed temporal proof required");if(r.has("cancel")&&r.get("cancel").getAsBoolean())G01TemporalProbe.cancel();out.add("g01_temporal",G01TemporalProbe.request(r));if(r.has("mutation"))try{switch(r.get("mutation").getAsString()){
                        case "apply"->{requireMain();Installed.settingsApply(r.get("enabled").getAsBoolean(),r.get("preset").getAsString());}
                        case "reload"->Iris.reload();
                        case "pose"->{requireMain();Installed.authoredPose(r.get("index").getAsInt());}
                        case "saved_pose"->{requireSavedAuthored();int index=r.get("index").getAsInt();if(index<0||index>=SAVED_POSES.size())throw new IllegalArgumentException("Pinned pose required");var p=SAVED_POSES.get(index);pose(SAVED_AUTHORED,p.x(),p.y(),p.z(),p.yaw(),p.pitch(),p.day());savedPoseIndex=index;}
                        case "size"->g01Size(r);
                        case "dimension"->g01Dimension(r);
                        case "diagnostic_pack"->diagnosticPack(r);
                        default->throw new IllegalArgumentException("Unknown bounded G01 mutation");
                    }}catch(Exception failure){G01TemporalProbe.cancel();throw failure;}}
                    case "g01_glint"->{idle();glint(r.get("enabled").getAsBoolean());out.addProperty("glint_client_item_override",glintPrevious!=null);}
                    case "g01_graph"->{idle();if(r.has("frames")&&Boolean.getBoolean("shaderlod.experimentalMakeUpHistoryExchange"))throw new IllegalStateException("Graph observer excluded from canonical exchange qualification");if(main()&&Installed.frameCopyObserving())throw new IllegalStateException("Exclusive untimed graph observation required");out.add("g01_graph",G01GraphProbe.request(r));}
                    case "g01_rt3_payload"->{idle();if(r.has("arm")&&r.get("arm").getAsBoolean()&&Boolean.getBoolean("shaderlod.experimentalMakeUpHistoryExchange"))throw new IllegalStateException("Payload/error-consuming observer excluded from canonical exchange qualification");if(main()&&Installed.frameCopyObserving())throw new IllegalStateException("Exclusive untimed proof required");out.add("g01_rt3_payload",G01QuadPayloadProbe.request(r.has("arm")&&r.get("arm").getAsBoolean()));}
                    case "causal_fragment"->{idle();if(main()&&Installed.frameCopyObserving())throw new IllegalStateException("Exclusive untimed causal proof required");out.add("causal",CausalFragmentProbe.read(r.get("sha256").getAsString()));}
                    case "depth_hierarchy_timer_arm"->{idle();com.pp.shaderlod.experiment.hierarchy.DepthHierarchyR50.armTimer();}
                    case "depth_hierarchy_timer"->{idle();out.add("depth_hierarchy_timer",com.pp.shaderlod.experiment.hierarchy.DepthHierarchyR50.timerResult());}
                    case "depth_hierarchy_verify"->{idle();out.add("depth_hierarchy",com.pp.shaderlod.experiment.hierarchy.DepthHierarchyR50.verify());}
                    case "depth_hierarchy_cost"->{idle();if(motion!=null||CpuTimers.recording()||GpuTimers.recording()||main()&&Installed.frameCopyObserving())throw new IllegalStateException("Idle outside motion/recording required");out.add("depth_hierarchy",com.pp.shaderlod.experiment.hierarchy.DepthHierarchyR50.costControl(r.get("enabled").getAsBoolean()));}
                    case "depth_hierarchy_status"->{idle();out.add("depth_hierarchy",com.pp.shaderlod.experiment.hierarchy.DepthHierarchyR50.status());}
                    case "g10_bloom_work"->{idle();if(!Boolean.getBoolean("shaderlod.dev.g10BenchmarkRuntime"))throw new IllegalStateException("Explicit G10 work runtime required");
                        var before=G10BloomReceipt.status();var work=FragmentWorkProbe.read(r.get("sha256").getAsString());var after=G10BloomReceipt.status();
                        if(!Boolean.TRUE.equals(before.get("qualified"))||!before.equals(after))throw new IllegalStateException("G10 producer changed during readback");
                        var record=new JsonObject();record.add("draw_receipt",JSON.toJsonTree(before));record.add("image",work);out.add("g10_work",record);}
                    case "g09_prefix_work"->{idle();out.add("g09_work",JSON.toJsonTree(com.pp.shaderlod.bslprefix.BslPrefixRuntime.privateWork()));}
                    case "g08_bound_status"->{idle();if(!Boolean.getBoolean("shaderlod.dev.g08BoundRuntime"))throw new IllegalStateException("Explicit G08 bound observer runtime required");if(r.has("observe"))G08BoundProbe.observe(r.get("observe").getAsBoolean(),r.has("sha256")?r.get("sha256").getAsString():"");out.add("g08_bound",JSON.toJsonTree(G08BoundProbe.status()));}
                    case "g08_bound_queue"->{idle();out.add("g08_bound_queue",r.has("sha256")?G08BoundQueuedCapture.request(r.get("sha256").getAsString(),r.get("phases").getAsInt()):G08BoundQueuedCapture.status());}
                    case "g08_bound_read"->{idle();out.add("g08_bound",G08BoundReadback.read(r.get("sha256").getAsString()));}
                    case "g08_cloud_status"->{idle();if(!Boolean.getBoolean("shaderlod.dev.g08CloudRuntime"))throw new IllegalStateException("Explicit G08 observer runtime required");if(r.has("observe"))G08CloudProbe.observe(r.get("observe").getAsBoolean());out.add("g08_cloud",JSON.toJsonTree(G08CloudProbe.status()));}
                    case "g08_cloud_queue"->{idle();out.add("g08_queue",r.has("sha256")?G08QueuedCapture.request(r.get("sha256").getAsString(),r.get("phases").getAsInt()):G08QueuedCapture.status());}
                    case "mip_family_observe"->{idle();if(r.has("pack"))observedPack(r);else {if(r.has("observe"))MipFamilyProbe.observe(r.get("observe").getAsBoolean());if(r.has("reload")&&r.get("reload").getAsBoolean())Iris.reload();if(r.has("rearm_settled")&&r.get("rearm_settled").getAsBoolean())MipFamilyProbe.rearmSettled();}out.add("mip_family",JSON.toJsonTree(MipFamilyProbe.status()));}
                    case "g08_cloud_read"->{idle();out.add("g08_cloud",G08CloudReadback.read(r.get("sha256").getAsString()));}
                    case "g10_bloom_status"->{idle();if(!Boolean.getBoolean("shaderlod.dev.g10BenchmarkRuntime"))throw new IllegalStateException("Explicit private G10 runtime required");if(r.has("observe"))G10BloomReceipt.observe(r.get("observe").getAsBoolean());out.add("g10_bloom",JSON.toJsonTree(G10BloomReceipt.status()));}
                    case "bloom_sampler_status"->{idle();out.add("bloom_sampler",BloomCoordinateProbe.samplerStatus());}
                    case "bloom_coordinates"->{idle();if(motion!=null||main()&&Installed.frameCopyObserving())throw new IllegalStateException("Exclusive untimed coordinate read required");out.add("bloom_coordinates",BloomCoordinateProbe.read(r.get("sha256").getAsString(),r.get("group").getAsInt(),r.get("frame_mod").getAsInt(),root.resolve("bloom-coordinates-"+id)));}
                    case "fragment_work_probe"->{idle();if(motion!=null||main()&&Installed.frameCopyObserving())throw new IllegalStateException("Exclusive untimed fragment work proof required");out.add("fragment_work",FragmentWorkProbe.read(r.get("sha256").getAsString()));}
                    case "graph_dump"->{idle();if(motion!=null||main()&&Installed.frameCopyObserving())throw new IllegalStateException("Stop motion and copy diagnostics before graph export");out.addProperty("graph_export",NativeGraphDump.write(root.resolve("native-graph-"+id),snapshot()).toString());}
                    case "mip_nether_pose"->{idle();requireMain();requireSavedAuthored();if(!Boolean.getBoolean("shaderlod.dev.mipFamilyRuntime")||savedNether||!SAVED_AUTHORED.equals(savedWorld)||!savedAuthoredReady())throw new IllegalStateException("Owned settled original authored scene required for Nether mip observation");var server=c.getSingleplayerServer();var playerUuid=c.player.getUUID();server.submit(()->{var player=server.getPlayerList().getPlayer(playerUuid);var level=server.getLevel(net.minecraft.world.level.Level.NETHER);if(player==null||level==null)throw new IllegalStateException("Native Nether unavailable");player.teleportTo(level,NETHER_POSE.x(),NETHER_POSE.y(),NETHER_POSE.z(),Set.of(),NETHER_POSE.yaw(),NETHER_POSE.pitch());level.setDayTime(6000);level.setWeatherParameters(1_000_000,0,false,false);}).get(2,TimeUnit.SECONDS);savedNether=true;}
                    case "mip_nether_settle"->{idle();requireMain();if(!Boolean.getBoolean("shaderlod.dev.mipFamilyRuntime")||!savedNether||!savedAuthoredActive()||c.level.dimension()!=net.minecraft.world.level.Level.NETHER||c.player.getX()!=NETHER_POSE.x()||c.player.getY()!=NETHER_POSE.y()||c.player.getZ()!=NETHER_POSE.z())throw new IllegalStateException("Actual native Nether camera required");c.level.setDayTime(6000);c.level.setRainLevel(0);c.level.setThunderLevel(0);}
                    case "native_terrain_open"->{idle();requireMain();requireSavedAuthored();if(!Boolean.getBoolean("shaderlod.dev.nativeTerrainScene")||c.level!=null||!(c.screen instanceof TitleScreen)||savedAuthoredOpened||!c.getLevelSource().levelExists(SAVED_SMOKE))throw new IllegalStateException("Prepared private CPU natural save required");savedWorld=SAVED_SMOKE;savedAuthoredOpened=true;try{c.createWorldOpenFlows().openWorld(SAVED_SMOKE,()->savedAuthoredOpened=false);}catch(RuntimeException failure){savedAuthoredOpened=false;throw failure;}}
                    case "native_terrain_spawn"->{idle();requireMain();requireCpuNaturalSaved();var server=c.getSingleplayerServer();if(server.overworld().getSeed()!=8675309L)throw new IllegalStateException("Prepared native seed differs");var p=server.overworld().getSharedSpawnPos();naturalPose=new SavedPose(p.getX()+.5,p.getY(),p.getZ()+.5,0,0,6000);var v=naturalPose;pose(SAVED_SMOKE,v.x(),v.y(),v.z(),v.yaw(),v.pitch(),v.day());out.addProperty("native_seed",server.overworld().getSeed());out.add("native_pose",JSON.toJsonTree(v));}
                    case "c01_natural_open"->{idle();requireMain();requireSavedAuthored();if(!Boolean.getBoolean("shaderlod.dev.c01PreparedSmoke")||!Boolean.getBoolean("shaderlod.dev.c01Bounds")||c.level!=null||!(c.screen instanceof TitleScreen)||savedAuthoredOpened||!c.getLevelSource().levelExists(SAVED_SMOKE))throw new IllegalStateException("Prepared private natural Smoke save required");savedWorld=SAVED_SMOKE;savedAuthoredOpened=true;try{c.createWorldOpenFlows().openWorld(SAVED_SMOKE,()->savedAuthoredOpened=false);}catch(RuntimeException failure){savedAuthoredOpened=false;throw failure;}}
                    case "c01_natural_spawn"->{idle();requireNaturalSaved();var server=c.getSingleplayerServer();if(server.overworld().getSeed()!=8675309L)throw new IllegalStateException("Prepared native seed differs");var p=server.overworld().getSharedSpawnPos();naturalPose=new SavedPose(p.getX()+.5,p.getY(),p.getZ()+.5,0,0,6000);var v=naturalPose;pose(SAVED_SMOKE,v.x(),v.y(),v.z(),v.yaw(),v.pitch(),v.day());out.addProperty("native_seed",server.overworld().getSeed());}
                    case "c01_natural_scan"->{idle();requireNaturalSaved();if(!savedAuthoredReady())throw new IllegalStateException("Settled prepared spawn required");out.add("natural_sites",NaturalOcclusionProbe.scan());}
                    case "c01_natural_site"->{idle();requireNaturalSaved();var site=NaturalOcclusionProbe.site(r.get("index").getAsInt());int direction=r.get("direction").getAsInt();if(direction<0||direction>3)throw new IllegalArgumentException("Four cardinal views only");naturalPose=new SavedPose(site.x(),site.y(),site.z(),direction*90,0,6000);var v=naturalPose;pose(SAVED_SMOKE,v.x(),v.y(),v.z(),v.yaw(),v.pitch(),v.day());out.add("natural_site",JSON.toJsonTree(site));}
                    case "open_saved_authored"->{idle();requireSavedAuthored();if(c.level!=null||!(c.screen instanceof TitleScreen)||savedAuthoredOpened)throw new IllegalStateException("Open existing saved scene from title only");if(!c.getLevelSource().levelExists(SAVED_AUTHORED))throw new IllegalStateException("Existing authored save required; this caller never creates it");savedAuthoredOpened=true;try{c.createWorldOpenFlows().openWorld(SAVED_AUTHORED,()->savedAuthoredOpened=false);}catch(RuntimeException failure){savedAuthoredOpened=false;throw failure;}}
                    case "pose_saved_authored"->{idle();requireSavedAuthored();if(!savedAuthoredOpened||!SAVED_AUTHORED.equals(savedWorld)||savedNether)throw new IllegalStateException("Open saved authored scene first");int index=r.has("index")?r.get("index").getAsInt():0;if(index<0||index>=SAVED_POSES.size())throw new IllegalArgumentException("Pinned saved pose0..11 required");var p=SAVED_POSES.get(index);pose(SAVED_AUTHORED,p.x(),p.y(),p.z(),p.yaw(),p.pitch(),p.day());savedPoseIndex=index;}
                    case "close_saved_authored"->{idle();requireSavedAuthored();if(!savedAuthoredActive())throw new IllegalStateException("Owned saved scene required");restore();c.level.disconnect();c.disconnect(new TitleScreen());c.setScreen(new TitleScreen());savedAuthoredOpened=false;savedWorld=SAVED_AUTHORED;naturalPose=null;savedNether=false;}
                    case "capture_saved_authored"->{idle();requireSavedAuthored();if(!(savedAuthoredReady()||motion!=null&&savedAuthoredActive()&&c.levelRenderer.hasRenderedAllSections())||!BackgroundLaunch.qualityWindowReady(c)||c.screen!=null||Iris.isFallback())throw new IllegalStateException("Settled saved scene in focused or hidden diagnostic window required");String name="probe-"+id+".png";net.minecraft.client.Screenshot.grab(c.gameDirectory,name,c.getMainRenderTarget(),message->{});out.addProperty("capture_requested",c.gameDirectory.toPath().resolve("screenshots").resolve(name).toString());out.addProperty("capture_completed",false);}
                    case "capture_coast"->{idle();if(previous==null||c.getSingleplayerServer()!=previous.server()||c.level==null||!c.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toRealPath().equals(c.gameDirectory.toPath().resolve("saves/"+WORLD).toRealPath())||!c.levelRenderer.hasRenderedAllSections()||!BackgroundLaunch.qualityWindowReady(c)||c.screen!=null||Iris.isFallback())throw new IllegalStateException("Settled owned posed landscape in focused or hidden diagnostic window required");String name="probe-"+id+".png";net.minecraft.client.Screenshot.grab(c.gameDirectory,name,c.getMainRenderTarget(),message->{});out.addProperty("capture_requested",c.gameDirectory.toPath().resolve("screenshots").resolve(name).toString());out.addProperty("capture_completed",false);}
                    case "natural_coast_sites"->{idle();if(!naturalCoastOwned())throw new IllegalStateException("Owned posed coast required");out.add("natural_sites",NaturalOcclusionProbe.scan());}
                    case "pose_natural_coast"->{idle();if(!naturalCoastOwned())throw new IllegalStateException("Owned posed coast required");var site=NaturalOcclusionProbe.site(r.get("index").getAsInt());int direction=r.has("direction")?r.get("direction").getAsInt():0;if(direction<0||direction>3)throw new IllegalArgumentException("Four cardinal views only");pose(WORLD,site.x(),site.y(),site.z(),direction*90,0,6000);}
                    case "pose_pbr_coast"->{idle();pose(WORLD,492.5,68,660.5,90,3.7f,6000);}
                    case "open_coast"->{if(c.level!=null||!(c.screen instanceof TitleScreen))throw new IllegalStateException("Open from title only");c.createWorldOpenFlows().openWorld(WORLD,()->{});}
                    case "open_saved"->{idle();String name=r.get("world").getAsString();if(!name.matches("[A-Za-z0-9_-]{1,64}")||c.level!=null||!(c.screen instanceof TitleScreen)||!c.getLevelSource().levelExists(name))throw new IllegalStateException("Existing safe saved world from title required");c.createWorldOpenFlows().openWorld(name,()->{});}
                    case "pose"->pose();
                    case "open_authored"->{idle();requireMain();if(c.level!=null)throw new IllegalStateException("Open authored from title only");Installed.openAuthored();}
                    case "authored_pose"->{idle();requireMain();if(!Installed.authoredActive())throw new IllegalStateException("Authored world required");Installed.authoredPose(r.has("index")?r.get("index").getAsInt():0);}
                    case "close_authored"->{idle();requireMain();Installed.closeAuthored();}
                    case "configure"->{idle();if(r.has("hide_gui"))c.options.hideGui=r.get("hide_gui").getAsBoolean();if(previous==null&&!(main()&&Installed.authoredActive())&&!savedAuthoredActive())pose();int rd=r.has("distance")?r.get("distance").getAsInt():12;if(rd!=12&&rd!=24)throw new IllegalArgumentException("RD12/24");c.options.renderDistance().set(rd);if(main()&&r.has("master"))Installed.master(r.get("master").getAsBoolean());if(main()&&r.has("features"))Installed.features(r.getAsJsonArray("features"));/* DH mode last: Iris checkFrame must update its cached macro before its next automatic reload. A settings reload after setting DEFAULT in this callback races that native cache. */if(r.has("dh_mode"))setDh("renderingMode",r.get("dh_mode").getAsString());}
                    case "g01_exchange"->{idle();requireMain();out.add("g01_exchange",JSON.toJsonTree(com.pp.shaderlod.optimization.framecopy.exchange.CanonicalBankExchange.request(r.has("mode")?r.get("mode").getAsString():"status")));}
                    case "reload"->{idle();Iris.reload();}
                    case "resources"->{
                        idle();if(main())Installed.idle();
                        var ids=new java.util.ArrayList<String>();for(var entry:r.getAsJsonArray("ids"))ids.add(entry.getAsString());
                        c.getResourcePackRepository().reload();
                        if(!c.getResourcePackRepository().getAvailableIds().containsAll(ids))throw new IllegalArgumentException("Resource pack not installed");
                        c.getResourcePackRepository().setSelected(ids);c.options.updateResourcePacks(c.getResourcePackRepository());c.reloadResourcePacks();
                    }
                    case "render_age_arm"->{idle();if(c.level!=null||!(c.screen instanceof TitleScreen)||renderingAgeArmed||firstUsableRenderNs!=0)throw new IllegalStateException("One age origin per fresh title process required");renderingAgeArmed=true;}
                    case "diagnostic_pack"->{idle();diagnosticPack(r);}
                    case "diagnostic_empty_requests"->{idle();requireMain();diagnosticEmptyRequests(r);}
                    case "settings_apply"->{idle();requireMain();com.pp.shaderlod.dev.readiness.ReadinessEvents.event("PUBLIC_APPLY_BEGIN",null,null,r.get("enabled").getAsBoolean()?1:0,0);try{Installed.settingsApply(r.get("enabled").getAsBoolean(),r.get("preset").getAsString());com.pp.shaderlod.dev.readiness.ReadinessEvents.event("PUBLIC_APPLY_RETURN",null,null,0,0);}catch(RuntimeException|Error e){com.pp.shaderlod.dev.readiness.ReadinessEvents.failed("PUBLIC_APPLY",e);throw e;}}
                    case "settings_cancel_check"->{idle();requireMain();out.add("cancel_check",Installed.settingsCancel(r.get("feature").getAsString()));}
                    case "settings_recovery_check"->{idle();requireMain();if(Installed.frameCopyObserving())throw new IllegalStateException("Stop copy diagnostics before recovery check");out.add("recovery_check",Installed.settingsRecovery(r.get("enabled").getAsBoolean(),r.get("preset").getAsString()));}
                    case "focus"->{idle();BackgroundLaunch.requireForeground("focus");org.lwjgl.glfw.GLFW.glfwFocusWindow(c.getWindow().getWindow());c.setScreen(null);}
                    case "video_modes"->{idle();var monitor=c.getWindow().findBestMonitor();if(monitor==null)throw new IllegalStateException("Monitor unavailable");monitor.refreshVideoModes();var modes=new JsonArray();for(int i=0;i<monitor.getModeCount();i++){var m=monitor.getMode(i);var v=new JsonObject();v.addProperty("width",m.getWidth());v.addProperty("height",m.getHeight());v.addProperty("refresh_rate",m.getRefreshRate());modes.add(v);}out.add("video_modes",modes);}
                    case "video"->{idle();video(r);}
                    case "background_size"->{idle();var w=c.getWindow();if(!BackgroundLaunch.enabled()||w.isFullscreen()||org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(w.getWindow(),org.lwjgl.glfw.GLFW.GLFW_VISIBLE)!=0)throw new IllegalStateException("Hidden background window required");int width=r.get("width").getAsInt(),height=r.get("height").getAsInt();if(width<64||height<64||width>4096||height>4096||w.getWidth()<=0||w.getHeight()<=0)throw new IllegalArgumentException("Bounded framebuffer dimensions required");int logicalWidth=(int)Math.round((double)width*w.getScreenWidth()/w.getWidth()),logicalHeight=(int)Math.round((double)height*w.getScreenHeight()/w.getHeight());w.setWindowed(logicalWidth,logicalHeight);org.lwjgl.glfw.GLFW.glfwSetWindowSize(w.getWindow(),logicalWidth,logicalHeight);out.addProperty("resize_requested",true);out.addProperty("requested_framebuffer_width",width);out.addProperty("requested_framebuffer_height",height);}

                    case "prepare"->{if(main())Installed.bindOwners();if(THREAD.isThreadCpuTimeSupported()&&!THREAD.isThreadCpuTimeEnabled())THREAD.setThreadCpuTimeEnabled(true);if(ModList.get().isLoaded("distanthorizons")){setDh("generation","false");setDh("chunkRenderDistance","128");setDh("renderingMode","DISABLED");}}
                    case "window_start"->{idle();if(G08BoundProbe.observing())throw new IllegalStateException("Stop untimed native cloud bound observation before timing");if(MipFamilyProbe.observing())throw new IllegalStateException("Stop untimed mip family observation before timing");if(G08CloudProbe.observing())throw new IllegalStateException("Stop untimed G08 observation before timing");if(main()&&com.pp.shaderlod.optimization.makeupprime.C01Cost.observing())throw new IllegalStateException("Stop untimed C01 costs before timing");if(G01TemporalProbe.active()||G01GraphProbe.active())throw new IllegalStateException("Stop untimed G01 observation before timing");if(savedAuthoredOpened&&!savedAuthoredReady())throw new IllegalStateException("Settled owned saved scene required");if(main()&&(Installed.frameCopyObserving()||Installed.bslMipObserving()))throw new IllegalStateException("Stop diagnostics before performance capture");if(main()&&Installed.authoredActive()&&(!Installed.authoredReady()||!Installed.authoredPoseReady()))throw new IllegalStateException("Settled authored pose required");if(c.level==null||c.screen!=null||!c.isWindowActive()||c.isPaused()||Iris.isFallback())throw new IllegalStateException("Focused native world required");if(main())Installed.idle();if(CpuTimers.recording()||GpuTimers.recording())throw new IllegalStateException("Detailed profiler must be off");if(r.has("qualification")&&r.get("qualification").getAsBoolean()&&qualificationObservers().entrySet().stream().anyMatch(e->e.getValue().getAsBoolean()))throw new IllegalStateException("Qualification instrumentation must be disabled and disarmed");capture=new Capture(r,snapshot(),renderFrames,worldFrames);if(main()&&!capture.qualification)Installed.startWork();}
                    case "window_stop"->{out.add("window",stopWindow());}
                    case "cancel"->{if(main()&&CpuAdmissionCostProbe.active())CpuAdmissionCostProbe.stop(renderFrames);if(motion!=null)finishMotion(c,"cancelled",true);if(capture!=null){capture.failure="Cancelled";out.add("window",stopWindow());}if(main()&&Installed.owned())Installed.benchmarkCancel();else restore();}
                    case "restore"->{idle();restore();}
                    case "close_coast"->{idle();restore(false);if(c.level!=null){c.level.disconnect();c.disconnect(new TitleScreen());c.setScreen(new TitleScreen());}}
                    case "benchmark_start"->{idle();if(!main())throw new IllegalStateException("Menu benchmark requires main mod");Installed.benchmarkStart(r.has("distance")?r.get("distance").getAsInt():c.options.renderDistance().get(),r.has("diagnostic")&&r.get("diagnostic").getAsBoolean());}
                    case "benchmark_cancel"->{if(main())Installed.benchmarkCancel();}
                    case "benchmark_status"->{if(main())out.add("benchmark",Installed.benchmarkStatus());}
                    case "quit"->{idle();restore();closing=3;}
                    default->throw new IllegalArgumentException("Unknown benchmark control action");
                }out.addProperty("success",true);
            }catch(Exception failure){out.addProperty("success",false);out.addProperty("error",failure.toString());var stack=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(stack));out.addProperty("error_stack",stack.toString().substring(0,Math.min(stack.toString().length(),16000)));}
            out.addProperty("motion_active",motion!=null);out.add("motion",lastMotion.deepCopy());
            if(motion!=null){out.getAsJsonObject("motion").addProperty("ticks",motion.ticks);out.getAsJsonObject("motion").addProperty("seconds",motion.ticks/20.0);}
            if(capture==null)out.add("state",snapshot());else{out.addProperty("recording",true);out.addProperty("capture_label",capture.label);}
            Path tmp=Files.createTempFile(receipt.getParent(),id,".tmp");Files.writeString(tmp,JSON.toJson(out));Files.move(tmp,receipt,StandardCopyOption.ATOMIC_MOVE);
        }catch(Exception failure){com.mojang.logging.LogUtils.getLogger().warn("Benchmark control rejected: {}",failure.toString());}
    }
    private static void requireMain(){if(!main())throw new IllegalStateException("Authored world requires main mod");}
    private JsonObject g06SamplerObserve(JsonObject request,String id)throws Exception {
        String operation=request.has("operation")?request.get("operation").getAsString():"status";
        if(operation.equals("stop"))return com.pp.shaderlod.dev.timing.CloudSamplerStateProbe.stopQuadCapture();
        if(operation.equals("status"))return com.pp.shaderlod.dev.timing.CloudSamplerStateProbe.quadCaptureStatus();
        if(!operation.equals("arm"))throw new IllegalArgumentException("arm/status/stop required");
        String name=request.get("pack").getAsString(),pin=request.get("sha256").getAsString();
        if(!java.util.Set.of("7cd8263884669ca6244d9dee5a66b20a350f1b1dbfea24f7cb9f852f6583a441","4262726768bb500fc3e570819d0fbd6e7cc4ea0ff2ea1323e435f1be24502188").contains(pin)||!name.matches("[A-Za-z0-9_.-]+\\.zip"))
            throw new IllegalArgumentException("Exact G06 r2 or audited r4 archive required");
        var c=Minecraft.getInstance();
        var pack=Iris.getCurrentPack().orElse(null);var pipeline=Iris.getPipelineManager().getPipelineNullable();var world=c.level;
        var camera=c.getCameraEntity();
        if(pack==null||pipeline==null||world==null||camera!=c.player||!Installed.authoredReady()||!Installed.authoredPoseReady()
            ||motion!=null||Installed.frameCopyObserving()||Installed.bslMipObserving()||CpuTimers.recording()||GpuTimers.recording()
            ||com.pp.shaderlod.ShaderLodConfig.ENABLED.get()||Installed.featureProfile().values().stream().anyMatch("true"::equals)
            ||!BackgroundLaunch.qualityWindowReady(c)||c.screen!=null||c.isPaused()||Iris.isFallback()
            ||!world.dimension().location().toString().equals("minecraft:overworld")||!Iris.getShaderPackOptionQueue().isEmpty()
            ||c.getWindow().getWidth()!=1920||c.getWindow().getHeight()!=1200||!name.equals(Iris.getCurrentPackName()))
            throw new IllegalStateException("Settled native100 G06 On Overworld1920x1200, coreOff/empty requests, exclusive untimed observer required");
        Path file=Iris.getShaderpacksDirectory().resolve(name);
        if(Files.isSymbolicLink(file)||!Files.isRegularFile(file)||!LoadedArtifactEvidence.fileSha256(file).equals(pin))
            throw new IllegalStateException("Selected archive hash mismatch");
        var options=pack.getShaderPackOptions();var effective=new TreeMap<String,String>();
        for(var key:options.getOptionSet().getBooleanOptions().keySet())effective.put(key,Boolean.toString(options.getOptionValues().getBooleanValueOrDefault(key)));
        for(var key:options.getOptionSet().getStringOptions().keySet())effective.put(key,options.getOptionValues().getStringValueOrDefault(key));
        if(!"2".equals(effective.get("G06_AO_SPATIAL")))throw new IllegalStateException("Select mode2 before arming; this action never reloads");
        try(var input=BenchmarkControlProbe.class.getResourceAsStream("/assets/shaderlod/makeupquad/native100.json")){
            if(input==null)throw new IllegalStateException("Retained native100 resource absent");
            var nativeOptions=JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if(nativeOptions.size()!=100)throw new IllegalStateException("Native option count");
            for(var value:nativeOptions.entrySet())if(!value.getValue().getAsString().equals(effective.get(value.getKey())))
                throw new IllegalStateException("Native option changed: "+value.getKey());
        }
        double x=camera.getX(),y=camera.getY(),z=camera.getZ();float yaw=camera.getYRot(),pitch=camera.getXRot();
        var binding=new JsonObject();binding.addProperty("pack",name);binding.addProperty("archive_sha256",pin);
        binding.addProperty("mode",2);binding.addProperty("native_options",100);binding.addProperty("core_enabled",false);
        binding.addProperty("pack_identity",System.identityHashCode(pack));binding.addProperty("pipeline_identity",System.identityHashCode(pipeline));
        return com.pp.shaderlod.dev.timing.CloudSamplerStateProbe.armQuadCapture(root.resolve("g06-sampler-"+id+".json"),binding,
            ()->Iris.getCurrentPack().orElse(null)==pack&&Iris.getPipelineManager().getPipelineNullable()==pipeline&&c.level==world
                &&c.getCameraEntity()==camera&&camera.getX()==x&&camera.getY()==y&&camera.getZ()==z&&camera.getYRot()==yaw&&camera.getXRot()==pitch
                &&c.getWindow().getWidth()==1920&&c.getWindow().getHeight()==1200&&c.screen==null&&!c.isPaused()&&!Iris.isFallback()
                &&Iris.getCurrentPackName().equals(name)&&Iris.getShaderPackOptionQueue().isEmpty()
                &&!com.pp.shaderlod.ShaderLodConfig.ENABLED.get()&&Installed.featureProfile().values().stream().noneMatch("true"::equals));
    }
    private void advanceMotion(Minecraft c){
        if(motion==null)return;var m=motion;
        if(c.level!=m.world||c.player!=m.camera||Iris.getCurrentPack().orElse(null)!=m.pack||Iris.getPipelineManager().getPipelineNullable()!=m.pipeline||Iris.isFallback()||!BackgroundLaunch.qualityWindowReady(c)||c.screen!=null||c.isPaused()||System.nanoTime()-m.started>60_000_000_000L){finishMotion(c,"ownership/focus/graph/deadline failure",false);return;}
        m.path.applyAt(c,Math.min(m.path.duration(),++m.ticks/20.0));
        // One additional tick leaves the final pose available to normal camera interpolation.
        if(m.ticks>m.path.duration()*20+1)finishMotion(c,"completed",false);
    }
    private void finishMotion(Minecraft c,String status,boolean restore){
        if(motion==null)return;var m=motion;motion=null;
        lastMotion.addProperty("status",status);lastMotion.addProperty("ticks",m.ticks);lastMotion.addProperty("wall_seconds",(System.nanoTime()-m.started)/1e9);
        if(restore&&c.level==m.world&&c.player==m.camera)m.path.restore(c);
    }
    /** Private experiment caller only; production pack admission remains in PackSelectionService. */
    private static void diagnosticPack(JsonObject request)throws Exception {
        diagnosticPack(request,false);
    }
    private void observedPack(JsonObject request)throws Exception {
        requireMain();requireSavedAuthored();
        boolean selectionAllowed=request.has("dimension")&&ObservedPackReload.permitsSelection(
            Boolean.getBoolean("shaderlod.dev.blissAoCoverage"),Boolean.getBoolean("shaderlod.dev.privateObservedReload"),
            Boolean.getBoolean("shaderlod.dev.mipFamilyRuntime"),request.get("dimension").getAsString());
        if(!selectionAllowed||!savedAuthoredReady()
            ||!request.has("observe")||!request.get("observe").getAsBoolean()
            ||!request.has("reload")||!request.get("reload").getAsBoolean()
            ||request.has("rearm_settled")
            ||motion!=null||MipFamilyProbe.observing()||CpuTimers.recording()||GpuTimers.recording()
            ||Installed.bslMipObserving()||Installed.frameCopyObserving()
            ||com.pp.shaderlod.ShaderLodConfig.ENABLED.get()
            ||Installed.featureProfile().values().stream().anyMatch("true"::equals)
            ||!Iris.getShaderPackOptionQueue().isEmpty()||Iris.isFallback())
            throw new IllegalStateException("Exclusive untimed owned observed reload, Off/empty requests/options and authorized dimension required");
        diagnosticPack(request,true);
    }
    private static void diagnosticPack(JsonObject request,boolean observed)throws Exception {
        if(main()&&Installed.frameCopyObserving())throw new IllegalStateException("Stop copy observation before pack reload");
        String name=request.get("pack").getAsString(),expected=request.get("sha256").getAsString();
        if(!DiagnosticArchiveIdentity.allows(name,expected))throw new IllegalArgumentException("Pinned ZIP basename and SHA256 required");
        Path file=Iris.getShaderpacksDirectory().resolve(name);
        if(Files.isSymbolicLink(file)||!Files.isRegularFile(file)||!LoadedArtifactEvidence.fileSha256(file).equals(expected))throw new IllegalArgumentException("Diagnostic archive identity mismatch");
        if(!main())try(var archive=new java.util.zip.ZipFile(file.toFile())) {
            if(archive.getEntry("shaderlod-selection.json")!=null)throw new IllegalStateException("Generated graphs require the installed normal Apply transaction");
        }
        var config=Iris.getIrisConfig();String prior=config.getShaderPackName().orElse("");boolean enabled=config.areShadersEnabled();
        if(request.has("dimension")){
            var level=Minecraft.getInstance().level;
            if(level==null||!request.get("dimension").getAsString().equals(level.dimension().location().toString()))
                throw new IllegalStateException("Diagnostic pack dimension differs before reload");
        }
        ObservedPackReload.Step reload=()->{config.setShaderPackName(name);config.setShadersEnabled(true);config.save();Iris.reload();
            if(Iris.isFallback()||!name.equals(Iris.getCurrentPackName()))throw new IllegalStateException("Diagnostic pack failed to load");
        };
        ObservedPackReload.run(()->{if(observed){org.lwjgl.opengl.GL11.glFinish();MipFamilyProbe.observe(true);}},reload,
            ()->{if(observed)MipFamilyProbe.observe(false);},
            ()->{config.setShaderPackName(prior);config.setShadersEnabled(enabled);config.save();Iris.reload();});
    }
    private static void g01Size(JsonObject r){var c=Minecraft.getInstance();if(!Boolean.getBoolean("shaderlod.dev.g01ExposureProbe"))throw new IllegalStateException("Untimed G01 launch required");int width=r.get("width").getAsInt(),height=r.get("height").getAsInt();if(!Set.of(960,800).contains(width)||!Set.of(600,500).contains(height))throw new IllegalArgumentException("Bounded diagnostic extents required");if(c.getWindow().isFullscreen()){if(!r.has("exit_fullscreen")||!r.get("exit_fullscreen").getAsBoolean())throw new IllegalStateException("Explicit untimed fullscreen exit required");c.getWindow().toggleFullScreen();}org.lwjgl.glfw.GLFW.glfwSetWindowSize(c.getWindow().getWindow(),width,height);}
    private static void g01Dimension(JsonObject r)throws Exception{var c=Minecraft.getInstance();if(!Boolean.getBoolean("shaderlod.dev.g01ExposureProbe")||c.player==null||c.getSingleplayerServer()==null)throw new IllegalStateException("Untimed owned world required");boolean nether=r.get("nether").getAsBoolean();var server=c.getSingleplayerServer();var playerId=c.player.getUUID();server.submit(()->{var player=server.getPlayerList().getPlayer(playerId);var level=server.getLevel(nether?net.minecraft.world.level.Level.NETHER:net.minecraft.world.level.Level.OVERWORLD);if(player!=null&&level!=null)player.teleportTo(level,.5,nether?200:74,nether?.5:-20.5,Set.of(),0,4);}).get(2,TimeUnit.SECONDS);}
    /** Client-only held render input; never changes server inventory or saves. Restore before disconnect. */
    private void glint(boolean enabled){var c=Minecraft.getInstance();if(!Boolean.getBoolean("shaderlod.dev.g01ExposureProbe"))throw new IllegalStateException("Untimed G01 launch required");
        if(enabled){if(c.player==null||c.gameMode==null||glintPrevious!=null)throw new IllegalStateException("Idle held item required");glintPlayer=c.player;glintPrevious=c.player.getMainHandItem().copy();glintGameMode=c.gameMode.getPlayerMode();c.gameMode.setLocalMode(net.minecraft.world.level.GameType.CREATIVE);var item=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);item.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);c.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,item);}
        else{if(glintPrevious!=null&&c.player==glintPlayer){c.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,glintPrevious);if(c.gameMode!=null)c.gameMode.setLocalMode(glintGameMode);}glintPrevious=null;glintPlayer=null;glintGameMode=null;}
    }
    private void idle(){if(capture!=null||main()&&Installed.owned())throw new IllegalStateException("Capture owns client");}
    /** Standalone prototype caller only; avoids inherited per-family requests in native/Off comparisons. */
    private void diagnosticEmptyRequests(JsonObject request)throws Exception {
        if(motion!=null||Installed.frameCopyObserving()||Iris.isFallback()||!Iris.getShaderPackOptionQueue().isEmpty())
            throw new IllegalStateException("Idle settled graph without queued options required");
        String name=request.get("pack").getAsString(),pin=request.get("sha256").getAsString();
        if(!DiagnosticArchiveIdentity.allows(name,pin)||!name.equals(Iris.getCurrentPackName()))
            throw new IllegalArgumentException("Actual selected pinned ZIP required");
        Path file=Iris.getShaderpacksDirectory().resolve(name);
        if(Files.isSymbolicLink(file)||!Files.isRegularFile(file)||!LoadedArtifactEvidence.fileSha256(file).equals(pin))
            throw new IllegalStateException("Diagnostic archive identity mismatch");
        try(var archive=new java.util.zip.ZipFile(file.toFile())) {
            if(archive.getEntry("shaderlod-selection.json")!=null)throw new IllegalStateException("Generated graphs require the normal Apply transaction");
        }
        Installed.clearDiagnosticRequests();
    }
    private static void requireSavedAuthored(){if(!Boolean.getBoolean("shaderlod.dev.savedAuthoredScene"))throw new IllegalStateException("Explicit saved-scene diagnostic launch required");}
    private boolean savedAuthoredActive(){var c=Minecraft.getInstance();return savedAuthoredOpened&&previous!=null&&c.level!=null&&c.player!=null&&c.getSingleplayerServer()==previous.server();}
    private boolean savedAuthoredReady(){var c=Minecraft.getInstance();var p=savedNether?NETHER_POSE:SAVED_SMOKE.equals(savedWorld)?naturalPose:SAVED_POSES.get(savedPoseIndex);return p!=null&& savedAuthoredActive()&&c.level.dimension()==(savedNether?net.minecraft.world.level.Level.NETHER:net.minecraft.world.level.Level.OVERWORLD)&&c.levelRenderer.hasRenderedAllSections()&&c.getCameraEntity()==c.player&&c.player.getX()==p.x()&&c.player.getY()==p.y()&&c.player.getZ()==p.z()&&c.player.getYRot()==p.yaw()&&c.player.getXRot()==p.pitch()&&c.level.getDayTime()==p.day()&&c.level.getRainLevel(1)==0&&c.level.getThunderLevel(1)==0;}
    private void requireCpuNaturalSaved(){if(!Boolean.getBoolean("shaderlod.dev.nativeTerrainScene")||!SAVED_SMOKE.equals(savedWorld)||!savedAuthoredOpened||Minecraft.getInstance().getSingleplayerServer()==null||Minecraft.getInstance().player==null||Minecraft.getInstance().level==null||!Minecraft.getInstance().getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().equals(Minecraft.getInstance().gameDirectory.toPath().resolve("saves/"+SAVED_SMOKE).toAbsolutePath().normalize())||(previous!=null&&Minecraft.getInstance().getSingleplayerServer()!=previous.server()))throw new IllegalStateException("Owned prepared CPU natural save required");}
    private void requireNaturalSaved(){if(!Boolean.getBoolean("shaderlod.dev.c01PreparedSmoke")||!Boolean.getBoolean("shaderlod.dev.c01Bounds")||!SAVED_SMOKE.equals(savedWorld)||!savedAuthoredOpened||Minecraft.getInstance().getSingleplayerServer()==null||Minecraft.getInstance().player==null||Minecraft.getInstance().level==null||!Minecraft.getInstance().getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().equals(Minecraft.getInstance().gameDirectory.toPath().resolve("saves/"+SAVED_SMOKE).toAbsolutePath().normalize())||(previous!=null&&Minecraft.getInstance().getSingleplayerServer()!=previous.server()))throw new IllegalStateException("Owned prepared natural save required");}
    private boolean naturalCoastOwned(){var c=Minecraft.getInstance();var server=c.getSingleplayerServer();return Boolean.getBoolean("shaderlod.dev.c01Bounds")&&previous!=null&&server==previous.server()&&!server.isPublished()&&server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().equals(c.gameDirectory.toPath().resolve("saves/"+WORLD).toAbsolutePath().normalize());}
    private void pose()throws Exception{pose(WORLD,256.5,95,768.5,135,9,7000);}
    private void pose(String world,double x,double y,double z,float yaw,float pitch,long day)throws Exception{var c=Minecraft.getInstance();var server=c.getSingleplayerServer();if(c.level==null||c.player==null||server==null||server.isPublished())throw new IllegalStateException("Private world required");if(!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toRealPath().equals(c.gameDirectory.toPath().resolve("saves/"+world).toRealPath()))throw new IllegalStateException("Wrong save");
        if(previous==null){var d=(net.minecraft.world.level.storage.ServerLevelData)server.overworld().getLevelData();previous=new WorldState(server,c.player.getX(),c.player.getY(),c.player.getZ(),c.player.getYRot(),c.player.getXRot(),server.overworld().getDayTime(),d.getRainTime(),d.getThunderTime(),d.isRaining(),d.isThundering(),server.tickRateManager().isFrozen(),c.options.renderDistance().get(),c.options.hideGui,c.gameMode.getPlayerMode());}
        server.submit(()->{var level=server.overworld();var player=server.getPlayerList().getPlayer(c.player.getUUID());player.teleportTo(level,x,y,z,Set.of(),yaw,pitch);if(SAVED_SMOKE.equals(savedWorld))player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);level.setDayTime(day);level.setWeatherParameters(1_000_000,0,false,false);server.tickRateManager().setFrozen(true);}).get(2,TimeUnit.SECONDS);
        // A frozen server may not send a time update before the capture. Keep
        // the observing client on the same explicitly selected time.
        c.level.setDayTime(day);if(SAVED_SMOKE.equals(savedWorld))c.gameMode.setLocalMode(net.minecraft.world.level.GameType.SPECTATOR);
        c.player.setPos(x,y,z);c.player.setYRot(yaw);c.player.setXRot(pitch);c.options.hideGui=true;c.level.setRainLevel(0);c.level.setThunderLevel(0);c.setScreen(null);
    }
    private void restore()throws Exception{restore(true);}
    private void restore(boolean restoreDh)throws Exception{var c=Minecraft.getInstance();if(glintPrevious!=null)glint(false);if(previous!=null){var p=previous;var server=c.getSingleplayerServer();if(server!=p.server())throw new IllegalStateException("Restore owner changed");server.submit(()->{var level=server.overworld();var player=server.getPlayerList().getPlayer(c.player.getUUID());player.teleportTo(level,p.x(),p.y(),p.z(),Set.of(),p.yaw(),p.pitch());player.setGameMode(p.gameType());level.setDayTime(p.day());level.setWeatherParameters(p.rain(),p.thunder(),p.raining(),p.thundering());server.tickRateManager().setFrozen(p.frozen());}).get(2,TimeUnit.SECONDS);c.level.setDayTime(p.day());c.gameMode.setLocalMode(p.gameType());c.player.setPos(p.x(),p.y(),p.z());c.player.setYRot(p.yaw());c.player.setXRot(p.pitch());c.options.renderDistance().set(p.distance());c.options.hideGui=p.hideGui();previous=null;}
        if(restoreDh){for(var e:dhPrevious.entrySet()){if(!Objects.equals(dhValue(e.getKey(),"getApiValue"),dhLast.get(e.getKey())))throw new IllegalStateException("DH override changed externally");if(e.getValue()==null)api("interfaces.config.IDhApiConfigValue").getMethod("clearValue").invoke(e.getKey());else api("interfaces.config.IDhApiConfigValue").getMethod("setValue",Object.class).invoke(e.getKey(),e.getValue());}dhPrevious.clear();dhLast.clear();}
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private void setDh(String key,String value)throws Exception{if(!ModList.get().isLoaded("distanthorizons"))throw new IllegalStateException("DH absent");Object config=dhConfig(key);if(!dhPrevious.containsKey(config)){dhPrevious.put(config,dhValue(config,"getApiValue"));dhLast.put(config,dhValue(config,"getApiValue"));}if(!Objects.equals(dhLast.get(config),dhValue(config,"getApiValue")))throw new IllegalStateException("DH changed externally");Object v=key.equals("generation")?Boolean.valueOf(value):key.equals("chunkRenderDistance")?Integer.valueOf(value):Enum.valueOf((Class)api("enums.rendering.EDhApiRendererMode"),value);boolean ok=(Boolean)api("interfaces.config.IDhApiConfigValue").getMethod("setValue",Object.class).invoke(config,v);if(!ok)throw new IllegalStateException("DH override refused");dhLast.put(config,dhValue(config,"getApiValue"));}
    private Object dhConfig(String key)throws Exception{if(!dhConfigs.containsKey(key)){Object configs=api("DhApi$Delayed").getField("configs").get(null);String group=key.equals("generation")?"worldGenerator":"graphics";Object g=api("interfaces.config.IDhApiConfig").getMethod(group).invoke(configs);dhConfigs.put(key,api(key.equals("generation")?"interfaces.config.both.IDhApiWorldGenerationConfig":"interfaces.config.client.IDhApiGraphicsConfig").getMethod(key.equals("generation")?"enableDistantWorldGeneration":key).invoke(g));}return dhConfigs.get(key);}
    private static Class<?> api(String suffix)throws Exception{return Class.forName("com.seibel.distanthorizons.api."+suffix);}
    private static Object dhValue(Object config,String getter)throws Exception{return api("interfaces.config.IDhApiConfigValue").getMethod(getter).invoke(config);}
    private void video(JsonObject r){BackgroundLaunch.requireForeground("fullscreen video");var c=Minecraft.getInstance();var w=c.getWindow();if(r.has("width")){var monitor=w.findBestMonitor();monitor.refreshVideoModes();com.mojang.blaze3d.platform.VideoMode selected=null;for(int i=0;i<monitor.getModeCount();i++){var mode=monitor.getMode(i);if(mode.getWidth()==r.get("width").getAsInt()&&mode.getHeight()==r.get("height").getAsInt()&&mode.getRefreshRate()==60){selected=mode;break;}}if(selected==null)throw new IllegalArgumentException("Mode not advertised");w.setPreferredFullscreenVideoMode(Optional.of(selected));if(!w.isFullscreen())w.toggleFullScreen();else w.changeFullscreenVideoMode();}int limit=r.has("fps_limit")?r.get("fps_limit").getAsInt():260;if(limit<10||limit>260)throw new IllegalArgumentException("FPS10..260");boolean vsync=r.has("vsync")&&r.get("vsync").getAsBoolean();c.options.framerateLimit().set(limit);c.options.enableVsync().set(vsync);w.setFramerateLimit(limit);w.updateVsync(vsync);c.options.save();}
    private JsonObject snapshot()throws Exception{var c=Minecraft.getInstance();var o=new JsonObject();o.add("qualification_observers",qualificationObservers());o.addProperty("at_ns",System.nanoTime());o.addProperty("first_usable_render_ns",firstUsableRenderNs);o.addProperty("first_usable_render_frame",firstUsableRenderFrame);o.addProperty("render_age_armed",renderingAgeArmed);o.addProperty("client_pid",ProcessHandle.current().pid());o.addProperty("client_started_at",ProcessHandle.current().info().startInstant().map(Instant::toString).orElse("unavailable"));o.addProperty("game_directory",c.gameDirectory.toPath().toAbsolutePath().normalize().toString());if(c.getCameraEntity()!=null){var camera=c.getCameraEntity();var position=new JsonObject();position.addProperty("x",camera.getX());position.addProperty("y",camera.getY());position.addProperty("z",camera.getZ());position.addProperty("yaw",camera.getYRot());position.addProperty("pitch",camera.getXRot());o.add("camera",position);}o.addProperty("day_time",c.level==null?-1:c.level.getDayTime());o.addProperty("rain_level",c.level==null?-1:c.level.getRainLevel(1));o.addProperty("main_mod",main());o.addProperty("world_loaded",c.level!=null);o.addProperty("focused",c.isWindowActive());o.addProperty("screen",c.screen==null?"none":c.screen.getClass().getSimpleName());o.addProperty("meshes_ready",c.level!=null&&c.levelRenderer.hasRenderedAllSections());o.addProperty("fallback",Iris.isFallback());o.addProperty("pack",Iris.getCurrentPackName());o.addProperty("pipeline_identity",System.identityHashCode(Iris.getPipelineManager().getPipelineNullable()));o.addProperty("pack_identity",System.identityHashCode(Iris.getCurrentPack().orElse(null)));o.addProperty("render_frames",renderFrames);o.addProperty("world_frames",worldFrames);o.addProperty("width",c.getWindow().getWidth());o.addProperty("height",c.getWindow().getHeight());o.addProperty("distance",c.options.renderDistance().get());o.addProperty("fps_limit",c.options.framerateLimit().get());o.addProperty("vsync",c.options.enableVsync().get());o.addProperty("fullscreen",c.getWindow().isFullscreen());o.addProperty("gpu_renderer",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));o.addProperty("gpu_driver",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION));o.addProperty("background_launch",BackgroundLaunch.enabled());o.addProperty("window_visible",org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(c.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_VISIBLE)==org.lwjgl.glfw.GLFW.GLFW_TRUE);o.addProperty("window_iconified",org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(c.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_ICONIFIED)==org.lwjgl.glfw.GLFW.GLFW_TRUE);o.addProperty("native_animation",!ShaderAnimationClock.fixed());o.addProperty("iris_frame_time_counter",net.irisshaders.iris.uniforms.SystemTimeUniforms.TIMER.getFrameTimeCounter());o.addProperty("iris_last_frame_time",net.irisshaders.iris.uniforms.SystemTimeUniforms.TIMER.getLastFrameTime());o.addProperty("iris_frame_counter",net.irisshaders.iris.uniforms.SystemTimeUniforms.COUNTER.getAsInt());o.addProperty("native_resident_memory","unavailable; heap is not RSS");o.addProperty("gpu_memory","unavailable; unified memory residency not inferred");var heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();o.addProperty("heap_used_bytes",heap.getUsed());o.addProperty("heap_committed_bytes",heap.getCommitted());o.addProperty("heap_max_bytes",heap.getMax());o.addProperty("heap_init_bytes",heap.getInit());o.add("heap_launch_arguments",JSON.toJsonTree(ManagementFactory.getRuntimeMXBean().getInputArguments().stream().filter(a->a.startsWith("-Xmx")||a.startsWith("-Xms")||a.startsWith("-XX:ActiveProcessorCount=")).toList()));
        var mods=new TreeMap<String,String>();for(var m:ModList.get().getMods())mods.put(m.getModId(),m.getVersion().toString());o.add("loaded_stack",JSON.toJsonTree(mods));o.add("selected_resource_packs",JSON.toJsonTree(c.getResourcePackRepository().getSelectedIds()));var gc=new JsonObject();for(var bean:ManagementFactory.getGarbageCollectorMXBeans()){var g=new JsonObject();g.addProperty("count",bean.getCollectionCount());g.addProperty("time_ms",bean.getCollectionTime());gc.add(bean.getName(),g);}o.add("gc",gc);
        var opts=new TreeMap<String,String>();var p=Iris.getCurrentPack().orElse(null);if(p!=null){var options=p.getShaderPackOptions();for(var k:options.getOptionSet().getBooleanOptions().keySet())opts.put(k,Boolean.toString(options.getOptionValues().getBooleanValueOrDefault(k)));for(var k:options.getOptionSet().getStringOptions().keySet())opts.put(k,options.getOptionValues().getStringValueOrDefault(k));}o.add("effective_options",JSON.toJsonTree(opts));
        o.addProperty("dimension",c.level==null?"unavailable":c.level.dimension().location().toString());
        o.addProperty("hide_gui",c.options.hideGui);o.addProperty("saved_authored_opened",savedAuthoredOpened);o.addProperty("saved_world",savedWorld);o.addProperty("saved_authored_pose",savedPoseIndex);o.addProperty("saved_authored_ready",savedAuthoredReady());o.addProperty("authored_active",main()&&Installed.authoredActive());o.addProperty("authored_ready",main()&&Installed.authoredReady());o.addProperty("authored_pose_ready",main()&&Installed.authoredPoseReady());o.addProperty("authored_version",main()?Installed.authoredVersion():"unavailable: main mod absent");o.addProperty("authored_status",main()?Installed.authoredStatus():"unavailable: main mod absent");
        o.addProperty("standard_feature_scene_ready",standardFeatureScene.ready());if(main())o.add("installed",Installed.snapshot());boolean dh=ModList.get().isLoaded("distanthorizons");o.addProperty("dh_installed",dh);if(dh){o.addProperty("dh_mode",String.valueOf(dhValue(dhConfig("renderingMode"),"getValue")));o.addProperty("dh_rendering_enabled",String.valueOf(dhValue(dhConfig("renderingEnabled"),"getValue")));try{var cached=Class.forName("net.irisshaders.iris.compat.dh.DHCompatInternal").getDeclaredField("dhEnabled");cached.setAccessible(true);o.addProperty("iris_dh_enabled_cached",cached.getBoolean(null));}catch(ReflectiveOperationException inaccessible){o.addProperty("iris_dh_enabled_cached_unavailable",inaccessible.toString());}o.addProperty("dh_radius",String.valueOf(dhValue(dhConfig("chunkRenderDistance"),"getValue")));var pools=new JsonObject();try{var t=Class.forName("com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil");for(String n:List.of("getRenderLoadingExecutor","getChunkToLodBuilderExecutor","getUpdatePropagatorExecutor","getFileHandlerExecutor")){Object ex=t.getMethod(n).invoke(null);var v=new JsonObject();if(ex!=null)for(String getter:List.of("getQueueSize","getRunningTaskCount","getCompletedTaskCount"))v.add(getter,JSON.toJsonTree(ex.getClass().getMethod(getter).invoke(ex)));pools.add(n,v);}}catch(Exception e){pools.addProperty("unavailable",e.toString());}o.add("dh_pools",pools);o.add("dh_draw_counters",com.pp.shaderlod.dev.timing.DhRenderEvidence.liveCounters());}
        return o;
    }
    /** Explicit boundary status, outside scored rendering. No profiler work is armed here. */
    private static JsonObject qualificationObservers(){
        var r=new JsonObject();
        r.addProperty("mip_family",MipFamilyProbe.observing());
        r.addProperty("cloud_bound",G08BoundProbe.observing());
        r.addProperty("cloud_reuse",G08CloudProbe.observing());
        r.addProperty("copy_graph",G01GraphProbe.active());
        r.addProperty("copy_temporal",G01TemporalProbe.active());
        r.addProperty("cpu_profiler",CpuTimers.recording());
        r.addProperty("gpu_profiler",GpuTimers.recording());
        r.addProperty("cpu_body_probe",CpuAdmissionCostProbe.active());
        if(main())Installed.qualificationObservers(r);
        return r;
    }
    private JsonObject stopWindow()throws Exception{if(capture==null)throw new IllegalStateException("No capture");var w=capture;var cpuWindow=w.clock.pauseCpuWindow();long stopped=System.nanoTime();try{w.affinity.close();}catch(RuntimeException|LinkageError error){w.failure="Render CPU affinity: "+error;}guard(w);w.samples.addAll(w.clock.drain());w.clock.enabled(false);capture=null;if(main()&&!w.qualification)Installed.stopWork();var after=snapshot();if(!w.before.get("effective_options").equals(after.get("effective_options")))w.failure="Native options changed";if(main()&&!w.featureProfile.equals(Installed.featureProfile()))w.failure="Requested optimization vector changed";
        var policy=new AnalysisPolicy(32,50,2,1,1,"within-session exploratory; chronology preserved; no cross-JVM confidence claim",50);boolean initialUnavailable=!w.samples.isEmpty()&&w.samples.getFirst().frameWallNs()==null;var measuredRows=initialUnavailable?w.samples.subList(1,w.samples.size()):w.samples;var statistics=BenchmarkStatistics.summarize(measuredRows,policy);boolean timingValid=w.recording&&BenchmarkStatistics.completeCoverage(statistics);if(w.recording&&!timingValid&&w.failure.isEmpty())w.failure="Incomplete required total-frame coverage: "+statistics.reasons();var dir=root.resolve("windows").resolve(w.label);Files.createDirectories(dir);if(Files.exists(dir.resolve("result.json")))throw new IllegalStateException("Window label exists");
        var clocks=new ArrayList<BenchmarkResult.Clock>();for(String id:List.of("frame_wall","render_wall","render_cpu","process_cpu")){long n=w.samples.stream().filter(x->switch(id){case "frame_wall"->x.frameWallNs()!=null;case "render_cpu"->x.renderCpuNs()!=null;case "process_cpu"->x.processCpuNs()!=null;default->true;}).count();clocks.add(new BenchmarkResult.Clock(id,"frame_wall=GameRenderer start interval; render scopes=head/return",id,"shared FrameMeasurement",(n>0&&(!id.equals("render_cpu")&&!id.equals("process_cpu")||w.samples.stream().anyMatch(x->{Long v=id.equals("render_cpu")?x.renderCpuNs():x.processCpuNs();return v!=null&&v>0;})))?BenchmarkAnalysis.Availability.AVAILABLE:BenchmarkAnalysis.Availability.UNAVAILABLE,null,"every recorded render frame","no GPU tail",w.samples.size(),n,w.samples.size()-n,0,List.of("simulation/presentation included only in frame interval"),id.equals("render_cpu")||id.equals("process_cpu")?List.of(com.pp.shaderlod.measurement.CpuMeasurementReport.observations(null,w.samples).get(id.equals("render_cpu")?"render_cpu.status":"render_scope_process_cpu.status")):List.of()));}
        clocks.add(new BenchmarkResult.Clock("gpu_elapsed","GPU command timeline, not busy","GameRenderer","disabled in performance mode",BenchmarkAnalysis.Availability.UNAVAILABLE,null,"off","not issued",w.samples.size(),0,w.samples.size(),0,List.of(),List.of("Profile separately")));
        Map<String,String> options=map(w.before.getAsJsonObject("effective_options"));var config=new BenchmarkResult.Configuration(w.before.get("pack").getAsString(),"selected archive; native options pinned in before.json",options,Map.of("selected_ids",w.before.get("selected_resource_packs").toString(),"scope","Selected repository IDs; material-channel consumption needs shader/source and rendered evidence"),"native selected AA options",w.width,w.height,w.rd,Minecraft.getInstance().options.simulationDistance().get(),w.before.has("dh_mode")?w.before.get("dh_mode").getAsString():"ABSENT","warm logical scene; OS cache uncontrolled");
        var work=List.of(new BenchmarkResult.Work("world_render_entries","frames",null,null,(double)(worldFrames-w.worldStart),BenchmarkAnalysis.Availability.AVAILABLE,"GameRenderer renderLevel HEAD entries; paired renderer returns checked separately",List.of("before.json","after.json"),List.of("Does not establish shader effect execution")),new BenchmarkResult.Work("calibration_spin_iterations","iterations",w.delayNs>0?(double)w.delayNs:null,null,(double)w.spinIterations,BenchmarkAnalysis.Availability.AVAILABLE,"explicit calibration only",List.of(),List.of("Never an optimization baseline")));
        var temporal=new TreeMap<String,String>();temporal.put("clocks","native Iris frameTime/frameCounter; no replay");temporal.put("scene_status",w.sceneStatus);temporal.put("camera",w.x+","+w.y+","+w.z+","+w.yaw+","+w.pitch);temporal.put("day",w.before.get("day_time").getAsString());temporal.put("rain",w.before.get("rain_level").getAsString());
        var scenario=new BenchmarkResult.Scenario(w.authored?"authored-controls":"coast-controls",w.sceneVersion,w.authored?"authored-pose":"fixed-pose-v1","rd"+w.rd,w.dimension,temporal);
        var observations=new TreeMap<>(map(w.before));observations.putAll(com.pp.shaderlod.measurement.CpuMeasurementReport.observations(cpuWindow,w.samples));observations.put("cpu_placement",JSON.toJson(w.cpuPlacement.snapshot()));observations.put("render_cpu_affinity",JSON.toJson(w.affinity.snapshot()));
        var row=new BenchmarkResult.Window(1,w.label,w.comparison,w.repeat,0,BenchmarkResult.Arm.valueOf(w.arm),w.featureProfile,w.generation,(long)w.before.get("pipeline_identity").getAsInt(),null,BenchmarkResult.Temperature.WARM,BenchmarkResult.Stage.CONTROL,BenchmarkResult.Motion.STILL,scenario,config,w.recording?"PERFORMANCE":"RECORDING_OFF",Map.of("calibration_delay_ns",Long.toString(w.delayNs),"cpu_placement_requested",w.cpuPlacement.snapshot().get("requested").toString(),"cpu_placement_cost","Two boundary reads when requested; overhead uncalibrated"),clocks,new BenchmarkResult.Warmup(null,null,List.of("caller-owned warmup"),List.of("not automatically certified"),List.of(),List.of("guard.json")),observations,work,w.samples.isEmpty()?null:w.samples.getFirst().frame(),w.samples.isEmpty()?null:w.samples.getLast().frame(),w.samples.stream().map(BenchmarkResult.Sample::from).toList(),statistics,w.recording?BenchmarkAnalysis.Availability.AVAILABLE:BenchmarkAnalysis.Availability.UNAVAILABLE,BenchmarkAnalysis.Activation.UNKNOWN,!w.recording?BenchmarkAnalysis.Validity.UNTESTED:w.failure.isEmpty()&&timingValid?BenchmarkAnalysis.Validity.VALID:BenchmarkAnalysis.Validity.INVALID,w.failure.isEmpty()?List.of():List.of(w.failure));
        var env=new BenchmarkResult.Environment("neoforge-1.21.1",map(w.before.getAsJsonObject("loaded_stack")),w.before.get("gpu_renderer").getAsString(),System.getProperty("os.name")+" "+System.getProperty("os.version"),w.before.get("gpu_driver").getAsString(),System.getProperty("java.version"));var manifest=new BenchmarkResult.Manifest(1,BenchmarkAnalysis.ANALYSIS_VERSION,w.label,Instant.now().toString(),env,List.of(row),List.of(),new BenchmarkResult.Evidence(BenchmarkResult.EvidenceVerdict.UNTESTED,BenchmarkResult.EvidenceVerdict.UNTESTED,List.of(),List.of(),List.of(),List.of("Instrument/control capture; not a qualified optimization")),new BenchmarkResult.Recovery(BenchmarkResult.RecoveryStatus.PENDING,null,false,List.of("controller retains world until explicit restore")),new BenchmarkResult.SearchCoverage(0,0,List.of(),List.of(),false,List.of("not a search")),List.of("before.json","after.json","guard.json"),List.of("No source binary equivalence or hardware-busy inference"));
        BenchmarkResultWriter.write(dir.resolve("result.json"),manifest);Files.writeString(dir.resolve("before.json"),JSON.toJson(w.before));Files.writeString(dir.resolve("after.json"),JSON.toJson(after));var result=new JsonObject();result.addProperty("excluded_initial_unavailable_interval",initialUnavailable);result.addProperty("label",w.label);result.addProperty("path",dir.toString());result.addProperty("recording",w.recording);result.addProperty("timing_valid",timingValid);result.addProperty("timing_availability",w.recording?"AVAILABLE":"UNAVAILABLE");result.addProperty("valid",w.failure.isEmpty()&&w.completed>0);result.addProperty("failure",w.failure);result.addProperty("completed_render_frames",w.completed);result.add("completed_render_ends_ns",JSON.toJsonTree(w.completedRenderEndsNs));result.addProperty("wall_ns",stopped-w.started);result.addProperty("started_ns",w.started);result.addProperty("stopped_ns",stopped);result.addProperty("first_usable_render_ns",firstUsableRenderNs);result.addProperty("rendering_age_start_seconds",firstUsableRenderNs==0?-1:(w.started-firstUsableRenderNs)/1e9);result.addProperty("rendering_age_stop_seconds",firstUsableRenderNs==0?-1:(stopped-firstUsableRenderNs)/1e9);result.addProperty("count_throughput_fps",w.completed*1e9/(stopped-w.started));result.add("measurement_cpu_window",JSON.toJsonTree(cpuWindow));result.add("cpu_placement",JSON.toJsonTree(w.cpuPlacement.snapshot()));result.add("render_cpu_affinity",JSON.toJsonTree(w.affinity.snapshot()));result.add("cpu_observations",JSON.toJsonTree(com.pp.shaderlod.measurement.CpuMeasurementReport.observations(cpuWindow,w.samples)));result.add("statistics",JSON.toJsonTree(statistics));Files.writeString(dir.resolve("guard.json"),JSON.toJson(result));return result;
    }
    private static Map<String,String> map(JsonObject o){var m=new TreeMap<String,String>();for(var e:o.entrySet())m.put(e.getKey(),e.getValue().isJsonPrimitive()?e.getValue().getAsString():e.getValue().toString());return m;}
    private static String text(JsonObject o,String k,String fallback){return o.has(k)?o.get(k).getAsString():fallback;}
    /** This class is resolved only after the loader proves the main mod is installed. */
    private static final class Installed {
        static void settingsApply(boolean enabled,String preset){if(!com.pp.shaderlod.ShaderLodSettings.apply(enabled,com.pp.shaderlod.LodPreset.valueOf(preset)))throw new IllegalStateException(com.pp.shaderlod.ShaderLodSettings.failureMessage());}

        static boolean authoredActive(){return com.pp.shaderlod.benchmark.BenchmarkWorld.active();}
        static boolean authoredReady(){return com.pp.shaderlod.benchmark.BenchmarkWorld.ready();}
        static boolean authoredPoseReady(){return com.pp.shaderlod.benchmark.BenchmarkWorld.poseReady();}
        static String authoredVersion(){return com.pp.shaderlod.benchmark.BenchmarkWorld.version();}
        static String authoredStatus(){return com.pp.shaderlod.benchmark.BenchmarkWorld.status();}
        static void openAuthored(){com.pp.shaderlod.BenchmarkUi.openDemo();}
        static void authoredPose(int index){com.pp.shaderlod.benchmark.BenchmarkWorld.pose(index);}
        static void closeAuthored(){com.pp.shaderlod.benchmark.BenchmarkWorld.exit();}
        static void bindOwners(){com.pp.shaderlod.tuning.AutoTuner.setBenchmarkOwnerCheck(()->com.pp.shaderlod.benchmark.BenchmarkRunSession.owned()||owner!=null&&owner.capture!=null);com.pp.shaderlod.benchmark.BenchmarkRunSession.setProfilerOwnerCheck(()->owner!=null&&owner.capture!=null||CpuTimers.recording()||GpuTimers.recording());}
        private static Map<String,Long> cacheBefore=Map.of(),cacheAfter=Map.of();
        static Map<String,Object> cacheWindow(){return Map.of("before",cacheBefore,"after",cacheAfter);}
        static void qualificationObservers(JsonObject r){
            var counters=com.pp.shaderlod.optimization.RendererWorkDiagnostics.snapshot();
            r.addProperty("work_counters_enabled",com.pp.shaderlod.optimization.RendererWorkDiagnostics.ENABLED);
            r.addProperty("work_counters_armed",Boolean.TRUE.equals(counters.get("armed")));
            r.addProperty("frame_copy",frameCopyObserving());r.addProperty("bsl_mips",bslMipObserving());
            r.addProperty("occlusion_cost",com.pp.shaderlod.optimization.makeupprime.C01Cost.observing());
            r.addProperty("cpu_admission_cost",com.pp.shaderlod.optimization.NativeAdmissionCost.observing());
            r.addProperty("bloom_receipt",G10BloomReceipt.observing());
            var witness=com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpPostSampleAlphaWitness.snapshot();
            r.addProperty("alpha_witness",Boolean.TRUE.equals(witness.get("observing")));
        }
        static void startWork(){com.pp.shaderlod.optimization.RendererWorkDiagnostics.beginWindow();cacheAfter=Map.of();cacheBefore=com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.admissionCacheCounters();}
        static void stopWork(){cacheAfter=com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.admissionCacheCounters();com.pp.shaderlod.optimization.RendererWorkDiagnostics.endWindow();}
        static long generation(){return com.pp.shaderlod.optimization.OptimizationConfig.generation();}
        static boolean owned(){return com.pp.shaderlod.benchmark.BenchmarkRunSession.owned();}
        static void idle(){com.pp.shaderlod.tuning.AutoTuner.requireIdleForBenchmark();if(com.pp.shaderlod.measurement.RuntimeMeasurement.requested()||com.pp.shaderlod.benchmark.BenchmarkRunSession.active())throw new IllegalStateException("Normal benchmark/timer owns client");}
        static void master(boolean value){if(!com.pp.shaderlod.ShaderLodSettings.apply(value,com.pp.shaderlod.LodPreset.PERFORMANCE))throw new IllegalStateException(com.pp.shaderlod.ShaderLodSettings.failureMessage());}
        static void features(JsonArray list){var flags=com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested();for(var x:list)flags=flags.with(com.pp.shaderlod.optimization.OptimizationId.fromKey(x.getAsString()),true);var r=com.pp.shaderlod.optimization.OptimizationEditTransaction.apply(flags,Iris::reload,Iris::isFallback,com.pp.shaderlod.optimization.CompileOptionControl::open);if(!r.success())throw new IllegalStateException(r.message());}
        static boolean bslMipObserving(){return com.pp.shaderlod.optimization.mips.BslMipWitness.observing();}
        static JsonObject bslMipSnapshot(){return JSON.toJsonTree(com.pp.shaderlod.optimization.mips.BslMipWitness.snapshot()).getAsJsonObject();}
        static void bslMipObserve(boolean enabled,boolean reset){boolean was=com.pp.shaderlod.optimization.mips.BslMipWitness.observing();com.pp.shaderlod.optimization.mips.BslMipWitness.setObserving(enabled);if(reset&&was==enabled)com.pp.shaderlod.optimization.mips.BslMipWitness.resetCounters();}
        static boolean frameCopyObserving(){return com.pp.shaderlod.optimization.framecopy.FrameCopyRuntime.observing();}
        static void clearDiagnosticRequests()throws java.io.IOException{if(com.pp.shaderlod.ShaderLodConfig.ENABLED.get())throw new IllegalStateException("Diagnostic request reset requires mod Off");com.pp.shaderlod.optimization.OptimizationConfig.replace(com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested());}
        static void frameCopyObserve(boolean enabled,boolean reset){com.pp.shaderlod.optimization.framecopy.FrameCopyRuntime.setObserving(false);if(reset)com.pp.shaderlod.optimization.framecopy.FrameCopyRuntime.resetCounters();com.pp.shaderlod.optimization.framecopy.FrameCopyRuntime.setObserving(enabled);}
        static JsonObject settingsCancel(String feature)throws Exception{return JSON.toJsonTree(SettingsRecoveryProbe.cancel(feature)).getAsJsonObject();}
        static JsonObject settingsRecovery(boolean enabled,String preset)throws Exception{return JSON.toJsonTree(SettingsRecoveryProbe.check(enabled,com.pp.shaderlod.LodPreset.valueOf(preset))).getAsJsonObject();}
        static Map<String,String> featureProfile(){var flags=new TreeMap<String,String>();com.pp.shaderlod.optimization.OptimizationConfig.snapshot().flags().forEach((id,value)->flags.put(id.key(),Boolean.toString(value)));return Map.copyOf(flags);}
        static Map<String,Object> familyAlphaSnapshot(){
            String family=com.pp.shaderlod.optimization.OptimizationConfig.activeFamily();
            return switch(family){
                case "bsl-shaders"->Map.of("family",family,"counters",com.pp.shaderlod.optimization.bslearlyalpha.BslEarlyAlphaRuntime.diagnosticCounters(),"status",com.pp.shaderlod.optimization.bslearlyalpha.BslEarlyAlphaRuntime.status());
                case "complementary-reimagined"->Map.of("family",family,"counters",com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaRuntime.diagnosticCounters(),"status",com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaRuntime.status());
                case "complementary-unbound"->Map.of("family",family,"counters",com.pp.shaderlod.optimization.unboundearlyalpha.UnboundEarlyAlphaRuntime.diagnosticCounters(),"status",com.pp.shaderlod.optimization.unboundearlyalpha.UnboundEarlyAlphaRuntime.status());
                case "sildurs-vibrant-shaders"->Map.of("family",family,"counters",com.pp.shaderlod.optimization.sildurearlyalpha.SildurEarlyAlphaRuntime.diagnosticCounters(),"status",com.pp.shaderlod.optimization.sildurearlyalpha.SildurEarlyAlphaRuntime.status());
                case "solas-shader"->Map.of("family",family,"counters",com.pp.shaderlod.optimization.solasearlyalpha.SolasEarlyAlphaRuntime.diagnosticCounters(),"status",com.pp.shaderlod.optimization.solasearlyalpha.SolasEarlyAlphaRuntime.status());
                case "astralex"->Map.of("family",family,"counters",com.pp.shaderlod.optimization.astraearlyalpha.AstraEarlyAlphaRuntime.diagnosticCounters(),"status",com.pp.shaderlod.optimization.astraearlyalpha.AstraEarlyAlphaRuntime.status());
                default->Map.of("family",family);
            };
        }
        static Map<String,Object> aoScaleSnapshot(){
            String family=com.pp.shaderlod.optimization.OptimizationConfig.activeFamily();
            return switch(family){
                case "bsl-shaders"->Map.of("family",family,"counters",com.pp.shaderlod.optimization.bsl.BslAoNormalBridge.costCounters(),"status",com.pp.shaderlod.optimization.bsl.BslAoNormalBridge.status());
                case "complementary-reimagined"->Map.of("family",family,"counters",com.pp.shaderlod.optimization.reimaginedao.ReimaginedAoRuntime.costCounters(),"status",com.pp.shaderlod.optimization.reimaginedao.ReimaginedAoRuntime.status());
                case "complementary-unbound"->Map.of("family",family,"counters",com.pp.shaderlod.optimization.unboundao.UnboundAoRuntime.costCounters(),"status",com.pp.shaderlod.optimization.unboundao.UnboundAoRuntime.status());
                default->Map.of("family",family);
            };
        }
        static Map<String,Object> depthReuseSnapshot(){
            String family=com.pp.shaderlod.optimization.OptimizationConfig.activeFamily();
            return switch(family){
                case "bsl-shaders"->Map.of("family",family,"counters",com.pp.shaderlod.optimization.bsl.BslAoDepthRuntime.costCounters());
                case "complementary-reimagined"->Map.of("family",family,"counters",com.pp.shaderlod.optimization.reimagineddepth.ReimaginedAoDepthRuntime.costCounters());
                case "complementary-unbound"->Map.of("family",family,"counters",com.pp.shaderlod.optimization.unbounddepth.UnboundAoDepthRuntime.costCounters());
                default->Map.of("family",family);
            };
        }
        static Map<String,Object> reflectionReuseSnapshot(){
            String family=com.pp.shaderlod.optimization.OptimizationConfig.activeFamily();
            return switch(family){
                case "makeup-ultra-fast-shaders"->Map.of("family",family,"counters",com.pp.shaderlod.optimization.makeupshadow.MakeUpReflectionRuntime.costCounters(),"status",com.pp.shaderlod.optimization.makeupshadow.MakeUpReflectionRuntime.status());
                case "bsl-shaders"->Map.of("family",family,"counters",com.pp.shaderlod.optimization.bslexact.BslExactSsrRuntime.costCounters(),"status",com.pp.shaderlod.optimization.bslexact.BslExactSsrRuntime.status());
                default->Map.of("family",family);
            };
        }
        static JsonObject snapshot(){var r=new JsonObject();r.add("reflection_reuse_work",JSON.toJsonTree(reflectionReuseSnapshot()));r.add("depth_reuse_work",JSON.toJsonTree(depthReuseSnapshot()));r.add("ao_scale_work",JSON.toJsonTree(aoScaleSnapshot()));r.add("family_early_alpha",JSON.toJsonTree(familyAlphaSnapshot()));r.add("cpu_admission_cost",JSON.toJsonTree(com.pp.shaderlod.optimization.NativeAdmissionCost.snapshot()));r.addProperty("g10_bloom_observing",G10BloomReceipt.observing());r.add("post_sample_draw_witness",JSON.toJsonTree(com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpPostSampleAlphaWitness.snapshot()));r.add("early_alpha_source_setup",JSON.toJsonTree(com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpPostSampleAlpha.setupSnapshot()));r.add("early_alpha_draw_counters",JSON.toJsonTree(com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime.costCounters()));if("makeup-ultra-fast-shaders".equals(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily())) {
            var pack=Iris.getCurrentPack().orElse(null);
            if(pack!=null) {
                var water=pack.getProgramSet(new net.irisshaders.iris.shaderpack.materialmap.NamespacedId("minecraft","overworld")).get(net.irisshaders.iris.shaderpack.loading.ProgramId.Water);
                r.addProperty("g04_selected_source_fastpath",water.isPresent()&&water.get().isValid()&&water.get().getFragmentSource().orElse("").contains("if (blockType == 0.0)"));
                var programs=pack.getProgramSet(new net.irisshaders.iris.shaderpack.materialmap.NamespacedId("minecraft","overworld")).getComposite(net.irisshaders.iris.shaderpack.loading.ProgramArrayId.Deferred);
                if(programs.length>0&&programs[0]!=null) {
                    var source=programs[0];var fragment=source.getFragmentSource().orElse("");
                    r.addProperty("g06_deferred_valid",source.isValid());
                    r.addProperty("g06_selected_source_skip",fragment.contains("bool shaderlodG06Skipped"));
                    r.addProperty("g06_selected_source_assignment",fragment.contains("final_ao = mix(dbao(dither)"));
                    r.addProperty("g06_selected_source",fragment);
                }
            }
        }r.addProperty("profile_status",com.pp.shaderlod.ShaderOptimizationProfiles.status());r.addProperty("preset",com.pp.shaderlod.ShaderLodConfig.PRESET.get().name());r.addProperty("family",com.pp.shaderlod.optimization.OptimizationConfig.activeFamily());r.addProperty("dead_frame_copy_setup",com.pp.shaderlod.optimization.framecopy.MakeUpDeadCopySetup.status());r.addProperty("dead_mip_setup",com.pp.shaderlod.optimization.mips.DeadMipAdmission.status(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily()));if("bsl-shaders".equals(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily()))r.add("bsl_mip_graph",JSON.toJsonTree(com.pp.shaderlod.optimization.mips.DeadMipAdmission.bslGraphObservation()));r.add("flags",JSON.toJsonTree(featureProfile()));r.addProperty("master",com.pp.shaderlod.ShaderLodConfig.ENABLED.get());r.addProperty("generation",generation());r.addProperty("reloads",com.pp.shaderlod.measurement.RuntimeMeasurement.reloadCount());r.addProperty("compaction_admitted",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled(com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION));r.addProperty("validation",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.admissionCacheStatus());r.add("renderer_work",JSON.toJsonTree(com.pp.shaderlod.optimization.RendererWorkDiagnostics.snapshot()));r.add("frame_copy",JSON.toJsonTree(com.pp.shaderlod.optimization.framecopy.FrameCopyRuntime.snapshot()));return r;}
        static void benchmarkStart(int distance,boolean diagnostic){var c=Minecraft.getInstance();c.options.renderDistance().set(distance);if(diagnostic)com.pp.shaderlod.benchmark.BenchmarkRunSession.startDiagnostic();else com.pp.shaderlod.BenchmarkUi.benchmark();}
        static void benchmarkCancel(){com.pp.shaderlod.benchmark.BenchmarkRunSession.cancel();}
        static JsonObject benchmarkStatus(){var r=new JsonObject();r.addProperty("active",com.pp.shaderlod.benchmark.BenchmarkRunSession.active());r.addProperty("status",com.pp.shaderlod.benchmark.BenchmarkRunSession.status());r.addProperty("restoration_pending",com.pp.shaderlod.benchmark.BenchmarkRunSession.restorationPending());r.addProperty("result_path",com.pp.shaderlod.benchmark.BenchmarkRunSession.lastResult()==null?null:com.pp.shaderlod.benchmark.BenchmarkRunSession.lastResult().toString());return r;}
    }
}
