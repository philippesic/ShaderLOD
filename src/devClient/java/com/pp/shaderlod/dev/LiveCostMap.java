package com.pp.shaderlod.dev;

import com.google.gson.*;
import com.pp.shaderlod.dev.timing.*;
import static com.pp.shaderlod.dev.timing.costmap.CostMapRecords.*;
import java.nio.file.*;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

/** Native-only diagnostic owner. Overrides require explicit owned transition-test requests. */
public final class LiveCostMap {
    private static final Gson JSON=new GsonBuilder().serializeNulls().create();
    private static Path root;
    private static Object renderingOverride,priorOverride,lastOverride;
    private static Class<?> configApi()throws Exception{return Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue");}
    private static void rendering(boolean enabled)throws Exception {
        if(root==null)throw new IllegalStateException("Active native profile required");
        var api=configApi();
        if(renderingOverride==null){
            Object configs=Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed").getField("configs").get(null);
            Object graphics=Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig").getMethod("graphics").invoke(configs);
            renderingOverride=Class.forName("com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig").getMethod("renderingEnabled").invoke(graphics);
            priorOverride=lastOverride=api.getMethod("getApiValue").invoke(renderingOverride);
        }
        if(!Objects.equals(lastOverride,api.getMethod("getApiValue").invoke(renderingOverride)))throw new IllegalStateException("Concurrent DH override edit");
        boolean ok=(Boolean)api.getMethod("setValue",Object.class).invoke(renderingOverride,enabled);
        lastOverride=api.getMethod("getApiValue").invoke(renderingOverride);
        if(!ok||!Objects.equals(enabled,api.getMethod("getValue").invoke(renderingOverride)))throw new IllegalStateException("DH override refused");
    }
    private static void restoreRendering()throws Exception {
        if(renderingOverride==null)return;
        var api=configApi();if(!Objects.equals(lastOverride,api.getMethod("getApiValue").invoke(renderingOverride)))throw new IllegalStateException("Concurrent DH override edit at restore");
        boolean ok=(Boolean)(priorOverride==null?api.getMethod("clearValue").invoke(renderingOverride):api.getMethod("setValue",Object.class).invoke(renderingOverride,priorOverride));
        if(!ok||!Objects.equals(priorOverride,api.getMethod("getApiValue").invoke(renderingOverride)))throw new IllegalStateException("DH override restoration failed");
        renderingOverride=priorOverride=lastOverride=null;
    }
    private static com.pp.shaderlod.dev.timing.costmap.CohortBoundary boundaries;
    private static String context;
    private static int cohort,graph;
    private static Path graphEvidence;
    private static JsonObject observation(){
        var out=new JsonObject();out.addProperty("at_ns",System.nanoTime());out.add("dh_submissions",DhRenderEvidence.liveCounters());
        var thread=java.lang.management.ManagementFactory.getThreadMXBean();var rows=new JsonArray();
        for(long id:thread.getAllThreadIds()){var info=thread.getThreadInfo(id);if(info==null)continue;var row=new JsonObject();row.addProperty("id",id);row.addProperty("name",info.getThreadName());row.addProperty("cpu_ns",thread.isThreadCpuTimeSupported()?thread.getThreadCpuTime(id):-1);rows.add(row);}
        out.add("threads",rows);out.addProperty("heap_used",java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());return out;
    }
    public static JsonObject command(JsonObject request,Path directory)throws Exception {
        String op=request.get("op").getAsString();
        if(op.equals("start")) {
            if(!Boolean.getBoolean("shaderlod.dev.costMap")||root!=null||CostMapSession.active())throw new IllegalStateException("Exclusive cost-map launch required");
            root=directory.resolve("live-cost-map");if(Files.exists(root)){root=null;throw new IllegalStateException("Fresh capture root required");}
            Files.createDirectories(root);cohort=0;graph=0;graphEvidence=null;context=null;boundaries=new com.pp.shaderlod.dev.timing.costmap.CohortBoundary();
            try{com.pp.shaderlod.dev.BenchmarkMeasurementGuard.armDiagnostic();}catch(RuntimeException failure){root=null;throw failure;}
            CpuTimers.setRecording(true);GpuTimers.setRecording(true);
        } else if(op.equals("dh_rendering")){rendering(request.get("enabled").getAsBoolean());}
        else if(op.equals("stop")) { stop("owned stop"); }
        else throw new IllegalArgumentException("cost-map op");
        var out=new JsonObject();out.addProperty("root",directory.resolve("live-cost-map").toString());return out;
    }
    private static String setting(String group,String method)throws Exception {
        Class<?> delayed=Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");Object configs=delayed.getField("configs").get(null);
        Object g=Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig").getMethod(group).invoke(configs);
        String api=group.equals("graphics")?"client.IDhApiGraphicsConfig":"both.IDhApiWorldGenerationConfig";
        Object v=Class.forName("com.seibel.distanthorizons.api.interfaces.config."+api).getMethod(method).invoke(g);
        return String.valueOf(Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue").getMethod("getValue").invoke(v));
    }
    public static void boundary() {
        if(root==null)return;
        try {
            var c=Minecraft.getInstance();boolean installed=ModList.get().isLoaded("distanthorizons");
            Map<String,Fact> settings=new TreeMap<>();DhState dh=DhState.ABSENT;
            if(installed) {
                for(String key:List.of("renderingMode","renderingEnabled","chunkRenderDistance","horizontalQuality","verticalQuality"))settings.put(key,new Fact(setting("graphics",key),"effective DH API"));
                settings.put("generation",new Fact(setting("worldGenerator","enableDistantWorldGeneration"),"effective DH API"));
                Object proxy=Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed").getField("worldProxy").get(null);
                boolean loaded=proxy!=null&&(Boolean)Class.forName("com.seibel.distanthorizons.api.interfaces.world.IDhApiWorldProxy").getMethod("worldLoaded").invoke(proxy);
                settings.put("api_world_loaded",new Fact(Boolean.toString(loaded),"DH world proxy; not residency"));
                boolean enabled=!settings.get("renderingMode").value().equals("DISABLED")&&settings.get("renderingEnabled").value().equals("true");
                // Mesh-ready alone does not establish DH residency. Retain not-ready until that evidence exists.
                dh=enabled?DhState.ENABLED_NOT_READY:DhState.INSTALLED_RENDERING_DISABLED;
            }
            Object next=Iris.getPipelineManager().getPipelineNullable();
            String nextContext=(c.level==null?"disconnected":c.level.dimension().location().toString())+"/"+c.options.renderDistance().get()+"/"+dh+"/"+settings+"/fallback="+Iris.isFallback()+"/meshes="+(c.level!=null&&c.levelRenderer.hasRenderedAllSections());
            if(boundaries.observe(next,nextContext)) {
                GpuTimers.suspendQueries("cohort transition");CpuTimers.disarm();
                if(CostMapSession.active())finish("pipeline/dimension/settings/readiness transition");
                BenchmarkMeasurementGuard.disarm();
                boolean graphChanged=graph!=boundaries.graphGeneration();context=nextContext;
                if(c.level==null||next==null||Iris.isFallback())return;
                BenchmarkMeasurementGuard.armDiagnostic();
                if(graphChanged||graphEvidence==null){graph=boundaries.graphGeneration();graphEvidence=NativeGraphDump.write(root.resolve("graph-"+graph),observation());}
                cohort++;var facts=new EnumMap<CaptureField,Fact>(CaptureField.class);
                for(var key:CaptureField.values())facts.put(key,new Fact("unknown","not observed by this adapter"));
                facts.put(CaptureField.GPU,new Fact(org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER),"live OpenGL"));
                facts.put(CaptureField.DIMENSION,new Fact(c.level.dimension().location().toString(),"live level"));
                facts.put(CaptureField.NATIVE_RESOLUTION,new Fact(c.getWindow().getWidth()+"x"+c.getWindow().getHeight(),"live framebuffer"));
                facts.put(CaptureField.WORLD,new Fact(c.getSingleplayerServer()==null?"remote":c.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toString(),"live server"));
                facts.put(CaptureField.SCENE,new Fact("prepared authored save","owned launcher"));
                facts.put(CaptureField.CAMERA,new Fact(c.gameRenderer.getMainCamera().getPosition().toString(),"live camera"));
                facts.put(CaptureField.DRIVER,new Fact(org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION),"live OpenGL"));
                facts.put(CaptureField.JDK_VERSION,new Fact(System.getProperty("java.version"),"runtime"));
                facts.put(CaptureField.OS,new Fact(System.getProperty("os.name")+" "+System.getProperty("os.version"),"runtime"));
                facts.put(CaptureField.VSYNC,new Fact(c.options.enableVsync().get().toString(),"effective client option"));
                facts.put(CaptureField.FRAME_CAP,new Fact(c.options.framerateLimit().get().toString(),"effective client option"));
                facts.put(CaptureField.GRAPH_REVISION,new Fact(Integer.toString(graph),"pipeline object lifetime; linked evidence"));
                var dhFacts=new EnumMap<DhField,Fact>(DhField.class);
                for(var key:DhField.values())dhFacts.put(key,new Fact(installed?"unknown":"not_applicable",installed?"not observed":"DH absent"));
                var capture=new Capture(1,"live-"+ProcessHandle.current().pid()+"-"+cohort,"cohort-"+cohort,"live","process-"+ProcessHandle.current().pid(),facts,new DhIdentity(dh,"effective API; residency unverified",System.nanoTime(),dhFacts,settings),1,4);
                var request=new JsonObject();request.add("capture",JSON.toJsonTree(capture));
                var manifest=new JsonObject();manifest.addProperty("boundary",context);manifest.addProperty("graph_generation",graph);manifest.addProperty("graph_evidence",graphEvidence.toString());manifest.addProperty("selected_pack",Iris.getCurrentPackName());manifest.addProperty("dynamic_inventory",true);manifest.addProperty("workload",installed?"generation="+settings.get("generation").value()+"; loading/residency and separate streaming cost unmeasured":"DH absent");manifest.add("before_observation",observation());request.add("manifest",manifest);request.add("scopes",new JsonArray());
                CostMapSession.start(request);CpuTimers.setRecording(true);GpuTimers.setRecording(true);
            }
        }catch(Exception failure){try{stop("live adapter failure: "+failure);}catch(Exception cleanup){failure.addSuppressed(cleanup);}throw new IllegalStateException(failure);}
    }
    private static void finish(String reason)throws Exception {
        CostMapSession.evidence(null,observation(),JSON.toJsonTree(BenchmarkMeasurementGuard.status()));
        CostMapSession.finish(root.resolve("cohort-"+cohort),reason);
    }
    private static void stop(String reason)throws Exception {
        GpuTimers.suspendQueries(reason);CpuTimers.disarm();
        try{if(root!=null)finish(reason);}
        finally{try{restoreRendering();}finally{root=null;com.pp.shaderlod.dev.BenchmarkMeasurementGuard.disarm();}}
    }
}
