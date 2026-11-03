package com.pp.shaderlod.pccontrol;

import com.google.gson.JsonObject;
import com.pp.shaderlod.benchmark.BenchmarkWorld;
import com.pp.shaderlod.measurement.RuntimeMeasurement;
import com.pp.shaderlod.optimization.OptimizationConfig;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

/** Explicit, finite PC test windows. No GL queries, disk reads or source validation in monitor(). */
final class PcBenchmarkControl {
    private static final long MAX_NS=90_000_000_000L;
    private Window window;
    private Object originalDhApi,lastDhApi;
    private boolean dhOwned;
    private static final class Window {
        final Object world,server,player,camera,pack,pipeline;
        final String scene,selected,settings;
        final java.util.Map<String,String> nativeOptions;
        final float rain,thunder;
        final long started,reloads,generation,day;
        final int width,height,limit;
        final boolean vsync,fullscreen,core;
        final Object preset;
        final double x,y,z;
        final float yaw,pitch;
        long checkedFrames;
        String failure;
        Window(Minecraft c) {
            world=c.level;server=c.getSingleplayerServer();player=c.player;camera=c.getCameraEntity();
            pack=Iris.getCurrentPack().orElse(null);pipeline=Iris.getPipelineManager().getPipelineNullable();
            scene=BenchmarkWorld.status();selected=Iris.getIrisConfig().getShaderPackName().orElse("");settings=c.options.dumpOptionsForReport();nativeOptions=java.util.Map.copyOf(com.pp.shaderlod.tuning.SavedTunings.nativeOptions());rain=c.level.getRainLevel(1);thunder=c.level.getThunderLevel(1);started=System.nanoTime();reloads=RuntimeMeasurement.reloadCount();generation=OptimizationConfig.generation();day=c.level.getDayTime();
            width=c.getWindow().getWidth();height=c.getWindow().getHeight();limit=c.options.framerateLimit().get();vsync=c.options.enableVsync().get();fullscreen=c.getWindow().isFullscreen();
            core=com.pp.shaderlod.ShaderLodConfig.ENABLED.get();preset=com.pp.shaderlod.ShaderLodConfig.PRESET.get();
            x=cameraEntity(c).getX();y=cameraEntity(c).getY();z=cameraEntity(c).getZ();yaw=cameraEntity(c).getYRot();pitch=cameraEntity(c).getXRot();
        }
    }
    boolean owned(){return window!=null;}
    private static net.minecraft.world.entity.Entity cameraEntity(Minecraft c){return c.getCameraEntity();}
    void start(Minecraft c,JsonObject request,JsonObject result) {
        if(window!=null)throw new IllegalStateException("Existing controller window must be exported first");
        requireScene(c);
        if(!RuntimeMeasurement.gpuQueryMode().equals("diagnostic_off"))
            throw new IllegalStateException("PC wall-time windows require -Dshaderlod.dev.disableGpuQueries=true");
        if(!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Pending Iris options");
        window=new Window(c);
        result.addProperty("admission_cache_before",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.admissionCacheStatus());
        com.pp.shaderlod.optimization.RendererWorkDiagnostics.beginWindow();
        try{RuntimeMeasurement.request(true);}
        catch(RuntimeException failure){com.pp.shaderlod.optimization.RendererWorkDiagnostics.endWindow();window=null;throw failure;}
        result.addProperty("measurement_started",true);
        result.addProperty("measurement_deadline_seconds",90);
        result.addProperty("measurement_scene",window.scene);
        result.add("measurement_native_options",new com.google.gson.Gson().toJsonTree(window.nativeOptions));
    }
    private static void requireScene(Minecraft c) {
        if(!BenchmarkWorld.ready()||!BenchmarkWorld.poseReady()||c.level==null||c.player==null||cameraEntity(c)==null||c.getSingleplayerServer()==null)
            throw new IllegalStateException("Settled owned BenchmarkWorld pose required");
        if(!c.isWindowActive()||c.isPaused()||c.screen!=null||c.getOverlay()!=null||Iris.isFallback()||!Iris.getIrisConfig().areShadersEnabled())
            throw new IllegalStateException("Focused unpaused shader world without GUI/overlay required");
    }
    void monitor(Minecraft c) {
        Window w=window;if(w==null||w.failure!=null)return;
        w.checkedFrames++;
        String failure=null;
        if(System.nanoTime()-w.started>MAX_NS)failure="90-second controller deadline exceeded";
        else if(!c.isWindowActive()||c.isPaused()||c.screen!=null||c.getOverlay()!=null)failure="focus/pause/GUI/overlay changed";
        else if(c.level!=w.world||c.player!=w.player||cameraEntity(c)!=w.camera||c.getSingleplayerServer()!=w.server||!BenchmarkWorld.ready())failure="world/player/camera ownership changed";
        else if(Iris.isFallback()||!Iris.getIrisConfig().areShadersEnabled()||Iris.getCurrentPack().orElse(null)!=w.pack||Iris.getPipelineManager().getPipelineNullable()!=w.pipeline||RuntimeMeasurement.reloadCount()!=w.reloads||!w.selected.equals(Iris.getIrisConfig().getShaderPackName().orElse(""))||!Iris.getShaderPackOptionQueue().isEmpty())failure="shader graph/reload/options changed";
        else if(OptimizationConfig.generation()!=w.generation||com.pp.shaderlod.ShaderLodConfig.ENABLED.get()!=w.core||com.pp.shaderlod.ShaderLodConfig.PRESET.get()!=w.preset)failure="ShaderLOD configuration changed";
        else if(c.getWindow().getWidth()!=w.width||c.getWindow().getHeight()!=w.height||c.getWindow().isFullscreen()!=w.fullscreen||c.options.framerateLimit().get()!=w.limit||c.options.enableVsync().get()!=w.vsync)failure="presentation changed";
        else if(c.level.getDayTime()!=w.day||c.level.getRainLevel(1)!=w.rain||c.level.getThunderLevel(1)!=w.thunder||cameraEntity(c).getX()!=w.x||cameraEntity(c).getY()!=w.y||cameraEntity(c).getZ()!=w.z||cameraEntity(c).getYRot()!=w.yaw||cameraEntity(c).getXRot()!=w.pitch)failure="scene/time/camera changed";
        if(failure!=null){w.failure=failure;RuntimeMeasurement.request(false);}
    }
    void stop(Minecraft c,JsonObject result)throws Exception {
        Window w=window;if(w==null)throw new IllegalStateException("No controller window owns samples");
        var cpuResult=RuntimeMeasurement.pauseCpuWindow();
        try {
            monitor(c);
            if(w.failure==null&&!RuntimeMeasurement.requested())w.failure="measurement clock stopped unexpectedly: "+RuntimeMeasurement.status();
            if(w.failure==null&&(!w.nativeOptions.equals(com.pp.shaderlod.tuning.SavedTunings.nativeOptions())||!w.settings.equals(c.options.dumpOptionsForReport())))w.failure="native options or Minecraft settings changed";
            if(RuntimeMeasurement.requested())RuntimeMeasurement.pauseWindow();
            com.pp.shaderlod.optimization.RendererWorkDiagnostics.endWindow();
            result.addProperty("admission_cache_after",com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.admissionCacheStatus());
            result.add("renderer_work",new com.google.gson.Gson().toJsonTree(com.pp.shaderlod.optimization.RendererWorkDiagnostics.snapshot()));
            RuntimeMeasurement.pollSamples();
            if(RuntimeMeasurement.pendingQueries()!=0)throw new IllegalStateException("Unexpected pending GPU queries; retain window for retry");
            var path=RuntimeMeasurement.export(c.gameDirectory.toPath().resolve("shaderlod-pc-measurements"));
            result.addProperty("measurement_export",path.toString());
            result.addProperty("measurement_runtime_status",RuntimeMeasurement.status());
            result.addProperty("measurement_valid",w.failure==null);
            result.addProperty("measurement_failure",w.failure);
            result.addProperty("measurement_elapsed_seconds",cpuResult==null?0:cpuResult.elapsedWallNs()/1e9);
            result.addProperty("measurement_checked_observations",w.checkedFrames);
            result.addProperty("measurement_scene",w.scene);
            result.add("measurement_cpu_window",new com.google.gson.Gson().toJsonTree(cpuResult));
            window=null;
        } catch(Exception|LinkageError failure) {
            if(w.failure==null)w.failure="measurement stop failed: "+failure;
            throw failure;
        } finally {com.pp.shaderlod.optimization.RendererWorkDiagnostics.endWindow();RuntimeMeasurement.request(false);}
    }
    void appendStatus(Minecraft c,JsonObject result) {
        result.addProperty("controller_measurement_owned",window!=null);
        if(window!=null){result.addProperty("measurement_valid",window.failure==null);result.addProperty("measurement_failure",window.failure);result.addProperty("measurement_checked_observations",window.checkedFrames);}
        result.addProperty("measurement_status",RuntimeMeasurement.status());
        result.addProperty("gpu_query_mode",RuntimeMeasurement.gpuQueryMode());
        result.addProperty("reload_count",RuntimeMeasurement.reloadCount());
        result.addProperty("option_queue_size",Iris.getShaderPackOptionQueue().size());
        result.addProperty("pose_ready",BenchmarkWorld.poseReady());
        result.addProperty("paused",c.isPaused());result.addProperty("screen",c.screen==null?"none":c.screen.getClass().getName());result.addProperty("overlay",c.getOverlay()==null?"none":c.getOverlay().getClass().getName());
        if(c.level!=null){result.addProperty("dimension",c.level.dimension().location().toString());result.addProperty("day_time",c.level.getDayTime());result.addProperty("rain_level",c.level.getRainLevel(1));result.addProperty("thunder_level",c.level.getThunderLevel(1));}
        if(cameraEntity(c)!=null){var camera=cameraEntity(c);var pose=new JsonObject();pose.addProperty("x",camera.getX());pose.addProperty("y",camera.getY());pose.addProperty("z",camera.getZ());pose.addProperty("yaw",camera.getYRot());pose.addProperty("pitch",camera.getXRot());result.add("camera",pose);}
        // Reflection and artifact inspection are excluded from active measurement receipts.
        if(window==null) {
            result.add("dh",dhStatus());
            var identity=com.pp.shaderlod.measurement.ModArtifactIdentity.capture(com.pp.shaderlod.measurement.RuntimeMeasurement.class);
            var bounded=new JsonObject();
            for(String key:new String[]{"mod_artifact_valid","mod_artifact_kind","mod_artifact_sha256","mod_artifact_manifest_sha256","mod_artifact_files","mod_artifact_bytes","mod_artifact_error"})
                if(identity.containsKey(key))bounded.addProperty(key,identity.get(key));
            result.add("production_artifact",bounded);
        }
    }
    private static Class<?> dhClass(String suffix)throws ClassNotFoundException{return Class.forName("com.seibel.distanthorizons.api."+suffix);}
    private static Object graphics()throws ReflectiveOperationException {
        Object configs=dhClass("DhApi$Delayed").getField("configs").get(null);
        if(configs==null)throw new IllegalStateException("DH config API not ready");
        return dhClass("interfaces.config.IDhApiConfig").getMethod("graphics").invoke(configs);
    }
    private static Object modeConfig()throws ReflectiveOperationException{return dhClass("interfaces.config.client.IDhApiGraphicsConfig").getMethod("renderingMode").invoke(graphics());}
    private static Object value(Object config,String getter)throws ReflectiveOperationException{return dhClass("interfaces.config.IDhApiConfigValue").getMethod(getter).invoke(config);}
    void dhMode(String requested,JsonObject result)throws ReflectiveOperationException {
        if(!requested.equals("DEFAULT")&&!requested.equals("DISABLED")&&!requested.equals("RESTORE"))throw new IllegalArgumentException("DH mode DEFAULT, DISABLED or RESTORE required");
        if(requested.equals("RESTORE")){restoreDh();result.add("dh",dhStatus());return;}
        if(!ModList.get().isLoaded("distanthorizons"))throw new IllegalStateException("DH is not installed");
        Object config=modeConfig();
        if(!dhOwned){originalDhApi=value(config,"getApiValue");lastDhApi=originalDhApi;dhOwned=true;}
        else if(!java.util.Objects.equals(lastDhApi,value(config,"getApiValue")))
            throw new IllegalStateException("DH API override changed outside controller; refusing another owned write");
        @SuppressWarnings({"unchecked","rawtypes"}) Object mode=Enum.valueOf((Class)dhClass("enums.rendering.EDhApiRendererMode"),requested);
        Object accepted=dhClass("interfaces.config.IDhApiConfigValue").getMethod("setValue",Object.class).invoke(config,mode);
        lastDhApi=value(config,"getApiValue");
        if(!Boolean.TRUE.equals(accepted)||!requested.equals(String.valueOf(value(config,"getValue"))))throw new IllegalStateException("DH public API refused mode "+requested);
        result.add("dh",dhStatus());
    }
    void restoreDh()throws ReflectiveOperationException {
        if(!dhOwned)return;
        Object config=modeConfig();
        if(!java.util.Objects.equals(lastDhApi,value(config,"getApiValue")))throw new IllegalStateException("DH API override changed outside controller; original retained without overwrite");
        Object accepted=originalDhApi==null?dhClass("interfaces.config.IDhApiConfigValue").getMethod("clearValue").invoke(config):dhClass("interfaces.config.IDhApiConfigValue").getMethod("setValue",Object.class).invoke(config,originalDhApi);
        if(!Boolean.TRUE.equals(accepted)||!java.util.Objects.equals(originalDhApi,value(config,"getApiValue")))throw new IllegalStateException("DH original API override restoration refused");
        dhOwned=false;originalDhApi=null;lastDhApi=null;
    }
    private JsonObject dhStatus() {
        var result=new JsonObject();boolean installed=ModList.get().isLoaded("distanthorizons");result.addProperty("installed",installed);result.addProperty("controller_restore_pending",dhOwned);
        if(!installed)return result;
        ModList.get().getModContainerById("distanthorizons").ifPresent(mod->result.addProperty("version",mod.getModInfo().getVersion().toString()));
        try {
            Object graphics=graphics();Object world=dhClass("DhApi$Delayed").getField("worldProxy").get(null);
            result.addProperty("rendering_mode",String.valueOf(value(modeConfig(),"getValue")));
            var api=dhClass("interfaces.config.client.IDhApiGraphicsConfig");
            result.addProperty("rendering_enabled",String.valueOf(value(api.getMethod("renderingEnabled").invoke(graphics),"getValue")));
            result.addProperty("chunk_render_distance",String.valueOf(value(api.getMethod("chunkRenderDistance").invoke(graphics),"getValue")));
            result.addProperty("world_loaded",world!=null&&Boolean.TRUE.equals(dhClass("interfaces.world.IDhApiWorldProxy").getMethod("worldLoaded").invoke(world)));
        }catch(ReflectiveOperationException|RuntimeException failure){result.addProperty("error",failure.toString());}
        return result;
    }
}
