package com.pp.shaderlod.dev.timing;

import com.google.gson.*;
import com.pp.shaderlod.dev.timing.costmap.*;
import java.util.*;
import java.nio.file.*;
import static com.pp.shaderlod.dev.timing.costmap.CostMapRecords.*;

/** Optional canonical sink for existing live hooks. All registration precedes the first frame. */
public final class CostMapSession {
    private static CostMapSession current;
    private final Capture capture;
    private final ScopeRegistry registry;
    private final Map<String,Integer> names=new HashMap<>();
    private final ArrayDeque<Invocation> cpu=new ArrayDeque<>();
    private final Map<String,Long> unregistered=new LinkedHashMap<>();
    private long unregisteredOverflow;
    private void unknownHook(String name){if(unregistered.containsKey(name)||unregistered.size()<512)unregistered.merge(name,1L,Long::sum);else unregisteredOverflow++;}
    private final List<ScopeRegistry.Entry> events=new ArrayList<>();
    private final List<Coverage> coverage=new ArrayList<>();
    private final List<Frame> frames=new ArrayList<>();
    private final JsonObject manifest;
    private FrameId frame;
    private long frameStart;
    private final Clock cpuClock=new Clock("System.nanoTime",64,1000,0);
    private static final Gson JSON=new GsonBuilder().serializeNulls().create();
    private CostMapSession(JsonObject request) {
        capture=JSON.fromJson(request.get("capture"),Capture.class);
        registry=new ScopeRegistry(capture.captureId(),capture.cohortId(),512,100000,128,4096);
        manifest=request.getAsJsonObject("manifest").deepCopy();manifest.add("identity",JSON.toJsonTree(capture));
        manifest.addProperty("dh_state",capture.dh().state().name());
        for(JsonElement element:request.getAsJsonArray("scopes")) {
            var s=element.getAsJsonObject();String name=s.get("name").getAsString();
            var scope=registry.register(new ScopeKey(1,s.get("stage").getAsString(),s.get("program").getAsString(),
                Operation.valueOf(s.get("operation").getAsString()),name),name,Set.of(),"unmapped; verify offline",new Outcome(Status.UNKNOWN,"graph inventory supplied by owner"));
            if(names.put(name,scope.id())!=null)throw new IllegalArgumentException("duplicate scope name in graph inventory");
        }
    }
    public static void start(JsonObject request){if(current!=null)throw new IllegalStateException("canonical owner active");current=new CostMapSession(request);}
    public static void abort(Path directory,String reason)throws Exception { finish(directory,reason); }
    public static boolean active(){return current!=null;}
    public static void frame(long id,boolean world){
        if(current==null)return;
        var c=current;long now=System.nanoTime();
        if(c.frame!=null)c.frames.add(new Frame(c.frame,c.frameStart,now,null,null,false,false,false,!world,Set.of(),new Outcome(world?Status.UNKNOWN:Status.INVALID,"boundary observed; root coverage separate"),"next frame start"));
        if(c.frames.size()>=100000)throw new IllegalStateException("canonical frame cap exceeded");
        c.frame=new FrameId(c.capture.captureId(),c.capture.cohortId(),1,1,id,c.manifest.has("graph_generation")?c.manifest.get("graph_generation").getAsLong():1);c.frameStart=now;
        c.registry.beginFrame(c.frame);c.cpu.clear();c.collect();
        if(c.events.size()+c.coverage.size()>600000)throw new IllegalStateException("canonical event cap exceeded; finalize partial capture");
    }
    private void collect(){var b=registry.drain();events.addAll(b.events());coverage.addAll(b.coverage());}
    private boolean known(String name) {
        if(names.containsKey(name))return true;
        if(!manifest.has("dynamic_inventory")||!manifest.get("dynamic_inventory").getAsBoolean()||names.size()>=512){unknownHook(name);return false;}
        var scope=registry.register(new ScopeKey(manifest.get("graph_generation").getAsLong(),"observed-hook",name,Operation.WRAPPER,name),name,Set.of(),"physical hook; semantic coverage unverified",new Outcome(Status.UNKNOWN,"observed hook, no inferred resource identity"));
        names.put(name,scope.id());return true;
    }
    private ScopeContext context(String name) {
        String enclosing="unknown";
        for(var inv:cpu) {var scope=registry.scopes().get(inv.scopeId()-1);if(scope.name().startsWith("dh-total/")){enclosing=scope.name().substring(scope.name().lastIndexOf('/')+1);break;}}
        return ScopeContext.of(name,enclosing,capture.dh().state()==DhState.ABSENT);
    }
    public static Long beginCpu(String name,long ns,Long threadNs){
        var c=current;if(c==null || c.frame==null)return null;
        if(!c.known(name))return null;
        var context=c.context(name);var i=c.registry.begin(c.names.get(name),Thread.currentThread().threadId(),"render",context.dhStage(),context.worldShadow(),context.opacity(),ns,threadNs,c.cpuClock);c.cpu.push(i);return i.eventId();
    }
    public static void endCpu(Long id,long ns,Long threadNs){
        var c=current;if(c==null || id==null)return;
        c.registry.end(id,ns,threadNs);if(!c.cpu.isEmpty())c.cpu.pop();
    }
    public static Long issueGpu(String name,boolean timestamps,int bits){
        var c=current;if(c==null || c.frame==null)return null;
        if(!c.known(name))return null;
        var context=c.context(name);var i=c.registry.begin(c.names.get(name),Thread.currentThread().threadId(),"render",context.dhStage(),context.worldShadow(),context.opacity(),System.nanoTime(),null,c.cpuClock);
        c.registry.issueGpu(i,new Clock("OpenGL GPU (uncorrelated)",bits,1,1),timestamps?Backend.TIMESTAMP:Backend.ELAPSED);
        c.registry.endMissing(i.eventId(),new Outcome(Status.UNKNOWN,"GPU submission record; CPU separately observed"));return i.eventId();
    }
    public static void resolveGpu(Long id,Long begin,Long end,long elapsed){
        var c=current;if(c==null || id==null)return;
        c.registry.resolveGpu(id,c.frame,begin,end,elapsed,new Outcome(Status.MEASURED,"available OpenGL query"));
    }
    public static void evidence(JsonObject before,JsonObject after,JsonElement guard){if(current!=null){current.manifest.add("before",before);current.manifest.add("after",after);current.manifest.add("guard",guard);}}
    public static void finish(Path directory,String reason)throws Exception {
        var c=current;if(c==null)return;current=null;
        try{c.registry.close(reason);}catch(RuntimeException failure){c.manifest.addProperty("finalization_error",failure.toString());}
        c.collect();
        if(c.frame!=null)c.frames.add(new Frame(c.frame,c.frameStart,null,null,null,false,false,true,false,Set.of(),new Outcome(Status.UNKNOWN,"partial final frame"),reason));
        Map<FrameId,CpuEvent> roots=new HashMap<>();
        Integer rootId=c.names.get("run-tick");
        if(rootId!=null)for(var entry:c.events)if(entry.cpu()!=null && entry.cpu().sampling().status()==Status.MEASURED && entry.invocation().scopeId()==rootId)roots.put(entry.invocation().frame(),entry.cpu());
        for(int i=0;i<c.frames.size();i++){
            var f=c.frames.get(i);var root=roots.get(f.id());
            if(root!=null)c.frames.set(i,new Frame(f.id(),f.startNs(),f.nextStartNs(),root.wallStartNs(),root.wallEndNs(),f.warmup(),f.partialStart(),f.partialEnd(),f.transition(),f.sampledScopeSetIds(),f.eligibility(),f.endReason()));
        }
        c.manifest.add("unregistered_hook_entries",JSON.toJsonTree(c.unregistered));c.manifest.addProperty("unregistered_hook_overflow",c.unregisteredOverflow);
        var out=new JsonObject();out.addProperty("schema_version",1);out.add("manifest",c.manifest);
        var scopes=new JsonArray();for(var s:c.registry.scopes()){
            var row=new JsonObject();row.addProperty("id",s.id());row.addProperty("name",s.name());row.addProperty("program",s.key().programId());row.addProperty("graph_generation",s.key().graphGeneration());row.add("key",JSON.toJsonTree(s.key()));scopes.add(row);
        }
        out.add("scopes",scopes);out.add("events",JSON.toJsonTree(c.events));out.add("coverage",JSON.toJsonTree(c.coverage));out.add("frames",JSON.toJsonTree(c.frames));out.add("health",JSON.toJsonTree(c.registry.health()));
        Files.createDirectories(directory);Files.writeString(directory.resolve("capture.json"),JSON.toJson(out));
    }
}
