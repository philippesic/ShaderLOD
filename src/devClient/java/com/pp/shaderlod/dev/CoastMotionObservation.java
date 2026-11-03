package com.pp.shaderlod.dev;

import com.google.gson.*;
import com.pp.shaderlod.benchmark.BenchmarkWorld;
import com.pp.shaderlod.measurement.RuntimeMeasurement;
import com.pp.shaderlod.optimization.OptimizationConfig;
import com.pp.shaderlod.tuning.AutoTuner;
import java.nio.file.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** DEV-only, normal-clock coast screen. No timing, GL state or permission changes. */
final class CoastMotionObservation {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path BASE = Path.of("/Users/pippo/github/shaderlod/dev/logs/open-items-closure-20261023-r1/motion-r1");
    private static CoastMotionObservation active;
    private static JsonObject last = new JsonObject();
    private final Object world, server, pack, pipeline;
    private final com.pp.shaderlod.CoreUniformFrame.Core core;
    private final LocalPlayer player;
    private final long generation, reloads, started;
    private final float yaw, pitch, oldYaw, oldPitch, head, oldHead, body, oldBody;
    private final double x,y,z;
    private final JsonObject options;
    private final Path output;
    private final String name;
    private final JsonArray samples = new JsonArray();
    private final JsonArray captures = new JsonArray();
    private float appliedYaw, appliedPitch;
    private int captured;
    private double elapsed;

    private CoastMotionObservation(Minecraft c, Path output, String name) {
        this.output=output;this.name=name;world=c.level;server=c.getSingleplayerServer();player=c.player;
        pack=Iris.getCurrentPack().orElse(null);pipeline=Iris.getPipelineManager().getPipelineNullable();
        core=com.pp.shaderlod.optimization.OptimizationRuntimeContext.core();
        generation=OptimizationConfig.generation();reloads=RuntimeMeasurement.reloadCount();options=WorkloadInputs.effectiveOptions().deepCopy();
        x=player.getX();y=player.getY();z=player.getZ();yaw=player.getYRot();pitch=player.getXRot();
        oldYaw=player.yRotO;oldPitch=player.xRotO;head=player.yHeadRot;oldHead=player.yHeadRotO;body=player.yBodyRot;oldBody=player.yBodyRotO;
        appliedYaw=yaw;appliedPitch=pitch;started=System.nanoTime();
    }
    static boolean active(){return active!=null;}
    static JsonObject status(){return last.deepCopy();}
    static void arm(JsonObject request) throws Exception {
        require(active==null,"Motion already active");
        var c=Minecraft.getInstance();normal(c);
        var s=c.getSingleplayerServer();
        require(!BenchmarkWorld.active()&&s!=null&&!s.isPublished()&&c.player!=null
            &&s.getWorldPath(LevelResource.ROOT).toRealPath().equals(c.gameDirectory.toPath().resolve("saves/ShaderLOD-Landscape").toRealPath()),"Existing private coast required");
        require("minecraft:overworld".equals(c.level.dimension().location().toString())&&c.getCameraEntity()==c.player,"Coast player camera required");
        require(Math.abs(c.player.getYRot())<=350&&Math.abs(c.player.getXRot())<=80,"Bounded initial pose required");
        Path out=Path.of(request.get("output").getAsString()).toRealPath();
        require(out.startsWith(BASE.toRealPath()),"Assigned evidence directory required");
        String n=request.get("name").getAsString();require(n.matches("[A-Za-z0-9_-]{1,40}"),"Invalid motion name");
        require(Path.of(System.getProperty("shaderlod.dev.controlCaptureReceipt","")).toRealPath().equals(out.resolve("captures").toRealPath()),"Existing capture API must target owned evidence");
        require(!Files.exists(out.resolve(n+".json")),"Refusing existing motion result");
        for(int i=0;i<3;i++)require(!Files.exists(out.resolve("captures").resolve(n+"_"+i+".json"))&&!Files.exists(c.gameDirectory.toPath().resolve("screenshots").resolve(n+"_"+i+".png")),"Refusing existing capture");
        active=new CoastMotionObservation(c,out,n);last=new JsonObject();last.addProperty("state","running");
    }
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    private static void normal(Minecraft c) {
        var pair=RuntimeMeasurement.pair();var w=c.getWindow();var t=c.getMainRenderTarget();
        require(!AutoTuner.active()&&!BenchmarkProfileComparison.active()&&!RuntimeMeasurement.requested()
            &&(pair==null||!pair.active()&&!pair.restorationPending())&&!ShaderAnimationClock.fixed()
            &&!com.pp.shaderlod.optimization.RendererWorkDiagnostics.ENABLED
            &&!Boolean.TRUE.equals(BenchmarkMeasurementGuard.status().get("armed")),"Untimed normal clock only");
        for(String key: new String[]{"gpuTiming","cpuTiming","gpuPassTiming","gpuAttribution","cpuLayerTiming","workTrace"})
            require(!Boolean.getBoolean("shaderlod.dev."+key),"Diagnostic timing disabled");
        require(c.level!=null&&c.player!=null&&c.screen==null&&c.getOverlay()==null&&!c.isPaused()&&c.isWindowActive()
            &&w.isFullscreen()&&org.lwjgl.glfw.GLFW.glfwGetWindowMonitor(w.getWindow())!=0
            &&w.getWidth()==1920&&w.getHeight()==1200&&t.width==1920&&t.height==1200,"Focused native 1920x1200 required");
        require(!Iris.isFallback()&&net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse()
            &&Iris.getIrisConfig().getShaderPackName().orElse("").equals(Iris.getCurrentPackName())
            &&Iris.getShaderPackOptionQueue().isEmpty(),"Stable native shader required");
    }
    private void guard(Minecraft c) {
        normal(c);
        require(c.level==world&&c.player==player&&c.getCameraEntity()==player&&c.getSingleplayerServer()==server
            &&!c.getSingleplayerServer().isPublished()&&player.level()==world,"World/player ownership changed");
        require(Iris.getCurrentPack().orElse(null)==pack&&Iris.getPipelineManager().getPipelineNullable()==pipeline
            &&reloads==RuntimeMeasurement.reloadCount()&&generation==OptimizationConfig.generation()
            &&java.util.Objects.equals(core,com.pp.shaderlod.optimization.OptimizationRuntimeContext.core()),"Pipeline/pack/reload/control generation changed");
        require(Math.abs(player.getX()-x)<.02&&Math.abs(player.getY()-y)<.02&&Math.abs(player.getZ()-z)<.02,"Player position changed");
        require(player.getYRot()==appliedYaw&&player.getXRot()==appliedPitch,"External camera input changed");
        require(samples.size()<4096,"Sample bound exceeded");
        require(System.nanoTime()-started<=6_000_000_000L,"Six second hard observation bound exceeded");
    }
    // Smooth, closed yaw/pitch path; excursion < 11 degrees, duration four seconds.
    static double wave(double seconds){return Math.sin(2*Math.PI*Math.max(0,Math.min(1,seconds/4)));}
    public static void beginRender(RenderFrameEvent.Pre event) {
        var a=active;if(a==null)return;
        try {
            var c=Minecraft.getInstance();a.guard(c);a.elapsed=(System.nanoTime()-a.started)/1e9;
            double v=wave(a.elapsed);a.appliedYaw=a.yaw+(float)(5*v);a.appliedPitch=a.pitch+(float)v;
            a.player.setYRot(a.appliedYaw);a.player.setXRot(a.appliedPitch);
            a.player.yRotO=a.appliedYaw;a.player.xRotO=a.appliedPitch;
        } catch(Throwable failure){a.finish(failure.toString());}
    }
    public static void endRender(RenderFrameEvent.Post event) {
        var a=active;if(a==null)return;
        try {
            var c=Minecraft.getInstance();a.guard(c);
            var row=new JsonObject();row.addProperty("seconds",a.elapsed);row.addProperty("yaw",a.player.getYRot());row.addProperty("pitch",a.player.getXRot());
            row.addProperty("iris_frame_counter",SystemTimeUniforms.COUNTER.getAsInt());row.addProperty("iris_frame_time_counter",SystemTimeUniforms.TIMER.getFrameTimeCounter());
            a.samples.add(row);
            if(a.captured<3&&a.elapsed>=.8+a.captured*1.2){String n=a.name+"_"+a.captured++;ControlCaptureReceipt.capture(c,n);a.captures.add(n);}
            if(a.elapsed>=4){require(a.captured==3,"Too few rendered capture milestones");require(a.options.equals(WorkloadInputs.effectiveOptions()),"Native options changed");a.finish(null);}
        } catch(Throwable failure){a.finish(failure.toString());}
    }
    static void tick(){var a=active;if(a==null)return;try{a.guard(Minecraft.getInstance());}catch(Throwable failure){a.finish(failure.toString());}}
    static void cancel(){if(active!=null)active.finish("Caller cancelled");}
    private void finish(String failure) {
        active=null;var c=Minecraft.getInstance();boolean restored=false;
        // Never write the current replacement player or a player now in another level.
        if(c.player==player&&c.level==world&&player.level()==world){
            player.setYRot(yaw);player.setXRot(pitch);player.yRotO=oldYaw;player.xRotO=oldPitch;
            player.yHeadRot=head;player.yHeadRotO=oldHead;player.yBodyRot=body;player.yBodyRotO=oldBody;restored=true;
        }
        var r=new JsonObject();r.addProperty("state",failure==null&&restored?"completed":"aborted");
        r.addProperty("failure",failure);r.addProperty("pose_restored",restored);
        r.addProperty("restoration_scope",restored?"Same original world/player":"Skipped: original player/world no longer current; no replacement mutated");
        r.addProperty("same_world_reference_at_finish",c.level==world);r.addProperty("same_player_reference_at_finish",c.player==player);
        r.addProperty("player_uuid",player.getUUID().toString());r.addProperty("pipeline_identity",System.identityHashCode(pipeline));r.addProperty("pack_identity",System.identityHashCode(pack));
        r.addProperty("reload_count",reloads);r.addProperty("control_generation",generation);r.addProperty("restored_yaw",restored?player.getYRot():null);r.addProperty("restored_pitch",restored?player.getXRot():null);
        r.addProperty("frame_counter_scope","Iris CPU uniform source sampled after rendered frame, not temporal-history parity");
        r.addProperty("initial_yaw",yaw);r.addProperty("initial_pitch",pitch);r.addProperty("elapsed_seconds",(System.nanoTime()-started)/1e9);
        r.addProperty("scope","Untimed gross continuous-motion visual screen only; no temporal parity, material sampling or FPS qualification");
        r.add("samples",samples);r.add("capture_names",captures);r.add("initial_options",options);last=r;
        try{Files.writeString(output.resolve(name+".json"),JSON.toJson(r)+"\n",StandardOpenOption.CREATE_NEW);}catch(Exception e){last.addProperty("write_failure",e.toString());last.addProperty("state","aborted");}
    }
}
