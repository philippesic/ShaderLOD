package com.pp.shaderlod.dev;

import com.google.gson.*;
import com.pp.shaderlod.dev.timing.*;
import com.pp.shaderlod.measurement.*;
import com.pp.shaderlod.optimization.RendererWorkDiagnostics;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.irisshaders.iris.Iris;
import java.lang.management.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Explicit development-only diagnostics; file output and full observations stay outside timed windows. */
final class BottleneckProbe {
    private static final Gson JSON=new GsonBuilder().serializeNulls().registerTypeAdapter(Double.class,
        (JsonSerializer<Double>)(v,t,c)->Double.isFinite(v)?new JsonPrimitive(v):JsonNull.INSTANCE).create();
    private static final Map<Object,Object> previous=new LinkedHashMap<>(),last=new LinkedHashMap<>();
    private static final Map<String,Object> configs=new HashMap<>();
    private static Integer originalDistance, ownedDistance;
    private static SceneLease lease;
    private static Window window;
    private static boolean ownsGuard;
    private static final ThreadMXBean THREAD=ManagementFactory.getThreadMXBean();
    private record Window(String label,Path root,long firstFrame,long started,JsonObject before,
        List<CpuTimers.Sample> cpu,List<GpuTimers.Sample> gpu) {}
    private static Class<?> api(String name)throws Exception{return Class.forName("com.seibel.distanthorizons.api."+name);}
    private static Object value(Object config,String method)throws Exception{return api("interfaces.config.IDhApiConfigValue").getMethod(method).invoke(config);}
    private static Object group(String name)throws Exception{return api("interfaces.config.IDhApiConfig").getMethod(name).invoke(api("DhApi$Delayed").getField("configs").get(null));}
    private static Object config(String name)throws Exception {
        if(!configs.containsKey(name))configs.put(name,name.equals("generation")?
            api("interfaces.config.both.IDhApiWorldGenerationConfig").getMethod("enableDistantWorldGeneration").invoke(group("worldGenerator")):
            api("interfaces.config.client.IDhApiGraphicsConfig").getMethod(name).invoke(group("graphics")));
        return configs.get(name);
    }
    private static void set(Object c,Object v)throws Exception {
        if(!previous.containsKey(c)){previous.put(c,value(c,"getApiValue"));last.put(c,previous.get(c));}
        if(!Objects.equals(last.get(c),value(c,"getApiValue")))throw new IllegalStateException("External DH override changed");
        last.put(c,v);
        boolean ok=(Boolean)api("interfaces.config.IDhApiConfigValue").getMethod("setValue",Object.class).invoke(c,v);
        last.put(c,value(c,"getApiValue"));if(!ok||!Objects.equals(v,value(c,"getValue")))throw new IllegalStateException("DH override refused");
    }
    static void tick(){
        if(window==null)return;
        window.cpu().addAll(CpuTimers.drainSamples());window.gpu().addAll(GpuTimers.drainSamples());
        if(window.cpu().size()>600000||window.gpu().size()>100000)throw new IllegalStateException("Diagnostic sample cap exceeded");
    }
    static JsonObject consume(JsonObject request,Path receipts)throws Exception {
        try { return consumeInternal(request,receipts); }
        catch(Exception failure) {
            if(window==null && previous.isEmpty() && lease==null && originalDistance==null && !CostMapSession.active() && !ownsGuard)throw failure;
            try { CostMapSession.abort(window==null?receipts.getParent().resolve("profiles").resolve("failed-"+System.nanoTime()):window.root(),"probe error: "+failure); }
            catch(Exception exportFailure){failure.addSuppressed(exportFailure);}
            CpuTimers.disarm();GpuTimers.suspendQueries("probe error");
            if(ownsGuard){BenchmarkMeasurementGuard.disarm();ownsGuard=false;}RendererWorkDiagnostics.endWindow();window=null;
            try { var restore=new JsonObject();restore.addProperty("op","restore");consumeInternal(restore,receipts); }
            catch(Exception cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static JsonObject consumeInternal(JsonObject request,Path receipts)throws Exception {
        String op=request.get("op").getAsString();var client=Minecraft.getInstance();boolean dh=ModList.get().isLoaded("distanthorizons");
        if(op.equals("profile_off")) {
            if(window==null)throw new IllegalStateException("No diagnostic window");
            tick();var w=window;long lastFrame=GpuTimers.frameId(),stopped=System.nanoTime();BenchmarkMeasurementGuard.disarm();ownsGuard=false;
            var guard=JSON.toJsonTree(BenchmarkMeasurementGuard.status());RendererWorkDiagnostics.endWindow();
            GpuTimers.suspendQueries("profile stop; bounded unresolved tail");CpuTimers.disarm();window=null;
            var end=snapshot();var dir=w.root();Files.createDirectories(dir);
            CostMapSession.evidence(w.before(),end,guard);
            CostMapSession.finish(dir,"profile stop; unresolved tail retained");
            try(var out=Files.newBufferedWriter(dir.resolve("cpu.csv"))){out.write("frame,epoch,scope,wall_ms,thread_ms\n");
                for(var s:w.cpu())out.write(s.frameId()+","+s.epoch()+","+s.scope()+","+s.wallMilliseconds()+","+(s.threadMilliseconds()==null?"":s.threadMilliseconds())+"\n");}
            try(var out=Files.newBufferedWriter(dir.resolve("gpu.csv"))){out.write("frame,epoch,scope,gpu_ms\n");
                for(var s:w.gpu())out.write(s.frameId()+","+s.epoch()+","+s.scope()+","+s.milliseconds()+"\n");}
            var result=new JsonObject();result.addProperty("label",w.label());result.addProperty("first_frame_exclusive",w.firstFrame());result.addProperty("last_frame_exclusive",lastFrame);
            result.addProperty("wall_ns",stopped-w.started());result.add("before",w.before());result.add("after",end);result.add("guard",guard);
            result.add("gpu",JSON.toJsonTree(GpuTimers.snapshot()));result.add("cpu",JSON.toJsonTree(CpuTimers.snapshot()));result.add("work",JSON.toJsonTree(RendererWorkDiagnostics.snapshot()));
            result.addProperty("limits","Diagnostic instrumentation; GPU timeline elapsed is not busy time. Aggregate repeated CPU invocations by frame; sparse layers are not zero on unsampled frames. Pending GPU tail/first and last partial frame excluded explicitly.");
            Files.writeString(dir.resolve("report.json"),JSON.toJson(result));result.addProperty("report",dir.resolve("report.json").toString());return result;
        }
        if(!op.equals("restore") && (RuntimeMeasurement.requested()||window!=null))throw new IllegalStateException("An existing timing owner must stop first");
        if(op.equals("prepare")) {
            if(THREAD.isThreadCpuTimeSupported()&&!THREAD.isThreadCpuTimeEnabled())THREAD.setThreadCpuTimeEnabled(true);
            // Preparation observes native settings; overrides require explicit configure inputs.
        } else if(op.equals("configure")) {
            if(client.level==null||client.getSingleplayerServer()==null||client.getSingleplayerServer().isPublished())throw new IllegalStateException("Private benchmark world required");
            if(request.has("distance") && originalDistance==null)originalDistance=client.options.renderDistance().get();
            if(lease==null){lease=SceneLease.prepare();lease.freeze();}
            int distance=request.has("distance")?request.get("distance").getAsInt():client.options.renderDistance().get();
            if(request.has("distance")){if(distance<2||distance>32)throw new IllegalArgumentException("Vanilla distance outside 2..32");ownedDistance=distance;client.options.renderDistance().set(distance);}
            if(dh){String mode=request.has("mode")?request.get("mode").getAsString():null;if(mode!=null && !Set.of("DEFAULT","DISABLED").contains(mode))throw new IllegalArgumentException("DH mode");
                if(request.has("generation"))set(config("generation"),request.get("generation").getAsBoolean());
                if(request.has("radius"))set(config("chunkRenderDistance"),request.get("radius").getAsInt());
                if(mode!=null)set(config("renderingMode"),Enum.valueOf((Class)api("enums.rendering.EDhApiRendererMode"),mode));}
            else if(request.has("mode")&&!request.get("mode").getAsString().equals("DISABLED"))throw new IllegalStateException("DH absent");
        } else if(op.equals("profile_on")) {
            if(!Boolean.getBoolean("shaderlod.dev.manualProfiling"))throw new IllegalStateException("Manual profiling launch required");
            String label=request.get("label").getAsString();if(!label.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Diagnostic label");
            Path dir=receipts.getParent().resolve("profiles").resolve(label);if(Files.exists(dir))throw new IllegalStateException("Existing diagnostic label");
            GpuTimers.suspendQueries("new diagnostic owner");CpuTimers.disarm();
            if(request.has("capture"))CostMapSession.start(request);
            var before=snapshot();BenchmarkMeasurementGuard.armDiagnostic();ownsGuard=true;RendererWorkDiagnostics.beginWindow();GpuTimers.resetSamples();
            window=new Window(label,dir,GpuTimers.frameId(),System.nanoTime(),before,new ArrayList<>(),new ArrayList<>());
            CpuTimers.setRecording(true);GpuTimers.setRecording(true);
        } else if(op.equals("restore")) {
            if(window!=null)CostMapSession.finish(window.root(),"restore/Cancel");
            CpuTimers.disarm();GpuTimers.suspendQueries("restore");if(ownsGuard){BenchmarkMeasurementGuard.disarm();ownsGuard=false;}RendererWorkDiagnostics.endWindow();window=null;
            Exception restorationFailure=null;
            for(var e:new ArrayList<>(previous.entrySet())){
                try {
                    if(!Objects.equals(last.get(e.getKey()),value(e.getKey(),"getApiValue")))throw new IllegalStateException("External DH override changed before restore");
                    boolean ok=(Boolean)(e.getValue()==null?api("interfaces.config.IDhApiConfigValue").getMethod("clearValue").invoke(e.getKey()):api("interfaces.config.IDhApiConfigValue").getMethod("setValue",Object.class).invoke(e.getKey(),e.getValue()));
                    if(!ok||!Objects.equals(e.getValue(),value(e.getKey(),"getApiValue")))throw new IllegalStateException("DH restore failed");
                    previous.remove(e.getKey());last.remove(e.getKey());
                } catch(Exception failure) {if(restorationFailure==null)restorationFailure=failure;else restorationFailure.addSuppressed(failure);}
            }
            try {
                if(originalDistance!=null){
                    if(ownedDistance!=null && !Objects.equals(client.options.renderDistance().get(),ownedDistance))throw new IllegalStateException("External vanilla distance edit preserved");
                    client.options.renderDistance().set(originalDistance);originalDistance=ownedDistance=null;
                }
            }catch(Exception failure){if(restorationFailure==null)restorationFailure=failure;else restorationFailure.addSuppressed(failure);}
            try {if(lease!=null){lease.close();lease=null;}}
            catch(Exception failure){if(restorationFailure==null)restorationFailure=failure;else restorationFailure.addSuppressed(failure);}
            if(restorationFailure!=null)throw restorationFailure;
        } else if(op.equals("reload")) Iris.reload();
        else if(!op.equals("snapshot"))throw new IllegalArgumentException("Unknown diagnostic operation");
        return snapshot();
    }
    private static JsonObject snapshot()throws Exception {
        var c=Minecraft.getInstance();var r=new JsonObject();r.addProperty("at_ns",System.nanoTime());r.addProperty("frame",GpuTimers.frameId());
        var monitor=c.getWindow().findBestMonitor();var modes=new JsonArray();
        if(monitor!=null){monitor.refreshVideoModes();for(int i=0;i<monitor.getModeCount();i++){var mode=monitor.getMode(i);var x=new JsonObject();x.addProperty("width",mode.getWidth());x.addProperty("height",mode.getHeight());x.addProperty("refresh",mode.getRefreshRate());modes.add(x);}}r.add("monitor_modes",modes);
        r.addProperty("framebuffer_width",c.getWindow().getWidth());r.addProperty("framebuffer_height",c.getWindow().getHeight());
        r.addProperty("profiling_gpu",GpuTimers.recording());r.addProperty("profiling_cpu",CpuTimers.recording());r.addProperty("profile_active",window!=null);
        r.addProperty("generation_policy",ModList.get().isLoaded("distanthorizons")?String.valueOf(value(config("generation"),"getValue")):"not_applicable");
        r.addProperty("gpu_queue_evictions",GpuTimers.queueEvictions());r.addProperty("gpu_unresolved_tail",GpuTimers.unresolvedTail());
        r.addProperty("distance",c.options.renderDistance().get());r.addProperty("simulation_owned",lease!=null);r.addProperty("gpu_renderer",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
        var pack=Iris.getCurrentPack().orElse(null);boolean macro=false;
        if(pack instanceof com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.PackView view&&view.shaderlod$makeupPackInput()!=null){
            var defines=view.shaderlod$makeupPackInput().glslDefines();r.add("loaded_glsl_defines",JSON.toJsonTree(defines));for(var define:defines)if(define.key().equals("DISTANT_HORIZONS"))macro=true;
        }
        r.addProperty("loaded_dh_macro",macro);r.addProperty("selected_pack",Iris.getCurrentPackName());
        r.add("loaded_stack",JSON.toJsonTree(com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.loadedStack()));
        boolean dh=ModList.get().isLoaded("distanthorizons");r.addProperty("dh_installed",dh);
        if(dh){for(String name:List.of("renderingMode","renderingEnabled","chunkRenderDistance","horizontalQuality","verticalQuality"))r.addProperty(name,String.valueOf(value(config(name),"getValue")));
            var pools=new JsonObject();try{var type=Class.forName("com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil");
                for(String name:List.of("getRenderLoadingExecutor","getFileHandlerExecutor","getUpdatePropagatorExecutor","getChunkToLodBuilderExecutor","getWorldGenExecutor")){
                    var pool=new JsonObject();Object executor=type.getMethod(name).invoke(null);
                    if(executor!=null)for(String getter:List.of("getQueueSize","getRunningTaskCount","getCompletedTaskCount","getAverageRunTimeInMs"))try{pool.add(getter,JSON.toJsonTree(executor.getClass().getMethod(getter).invoke(executor)));}catch(ReflectiveOperationException e){pool.addProperty(getter,"unavailable");}
                    pools.add(name,pool);
                }
            }catch(ReflectiveOperationException e){pools.addProperty("error",e.toString());}r.add("dh_pools",pools);
            for(String name:List.of("DRAW_CALLS","INDEX_COUNTS")){var f=DhRenderEvidence.class.getDeclaredField(name);f.setAccessible(true);r.add(name,JSON.toJsonTree(((long[])f.get(null)).clone()));}
        }
        var threads=new JsonObject();for(long id:THREAD.getAllThreadIds()){var info=THREAD.getThreadInfo(id);if(info==null)continue;var t=new JsonObject();t.addProperty("name",info.getThreadName());t.addProperty("cpu_ns",THREAD.isThreadCpuTimeSupported()?THREAD.getThreadCpuTime(id):-1);threads.add(Long.toString(id),t);}r.add("threads",threads);
        var gc=new JsonObject();for(var bean:ManagementFactory.getGarbageCollectorMXBeans()){var x=new JsonObject();x.addProperty("count",bean.getCollectionCount());x.addProperty("time_ms",bean.getCollectionTime());gc.add(bean.getName(),x);}r.add("gc",gc);
        r.addProperty("heap_used",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        if(ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os){r.addProperty("process_cpu_ns",os.getProcessCpuTime());r.addProperty("physical_free",os.getFreeMemorySize());}
        return r;
    }
}
