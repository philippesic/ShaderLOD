package com.pp.shaderlod.dev.readiness;

import com.google.gson.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import net.minecraft.client.Minecraft;

/** Bounded opt-in diagnostic. No GL calls, production linkage, or economic estimator. */
public final class ReadinessEvents {
    public static final boolean ENABLED=!System.getProperty("shaderlod.dev.readinessEvents","").isBlank();
    private static final int CAPACITY=500_000, OBJECT_LIMIT=100_000;
    private static final long[] DATA=ENABLED?new long[CAPACITY*8]:new long[0];
    private static final IdentityHashMap<Object,Long> IDS=new IdentityHashMap<>();
    private static final TreeMap<Long,String> TYPES=new TreeMap<>();
    private static final Map<String,Long> COUNTS=new TreeMap<>();
    private static final LinkedHashMap<String,Integer> KINDS=new LinkedHashMap<>();
    private static final Set<String> HOOKS=new TreeSet<>();
    private static final List<Manager> MANAGERS=new ArrayList<>();
    private static long attempted,stored,dropped,objectOverflow,frames,lastGauge,origin;
    private static boolean stopped;
    private static String failure="";
    private static Fence fence;
    private record Manager(Object owner,ChunkBuilder builder,ConcurrentLinkedDeque<?> queue) {}
    private record Fence(Object world,Object camera,double x,double y,double z,float yaw,float pitch,long day,float rain,float thunder) {
        static Fence current(){var c=Minecraft.getInstance();var a=c.getCameraEntity();return new Fence(c.level,a,a.getX(),a.getY(),a.getZ(),a.getYRot(),a.getXRot(),c.level.getDayTime(),c.level.getRainLevel(1),c.level.getThunderLevel(1));}
        boolean matches(){var c=Minecraft.getInstance();var a=c.getCameraEntity();return c.level==world&&a==camera&&a.getX()==x&&a.getY()==y&&a.getZ()==z&&a.getYRot()==yaw&&a.getXRot()==pitch&&c.level.getDayTime()==day&&c.level.getRainLevel(1)==rain&&c.level.getThunderLevel(1)==thunder
            &&c.screen==null&&!c.isPaused()&&c.isWindowActive()&&!net.irisshaders.iris.Iris.isFallback()
            &&c.getWindow().getWidth()==2560&&c.getWindow().getHeight()==1600&&c.getWindow().isFullscreen()
            &&c.options.renderDistance().get()==12&&c.options.framerateLimit().get()==260&&!c.options.enableVsync().get();}
    }
    private ReadinessEvents() {}
    public static synchronized long id(Object value) {
        if(value==null)return 0;
        Long found=IDS.get(value);if(found!=null)return found;
        if(IDS.size()>=OBJECT_LIMIT){objectOverflow++;fail("OBJECT_ID_OVERFLOW");return 0;}
        long next=IDS.size()+1L;IDS.put(value,next);TYPES.put(next,value.getClass().getName());return next;
    }
    public static synchronized void event(String kind,Object a,Object b,long x,long y) {
        if(!ENABLED||stopped)return;
        try {
            long sequence=++attempted;
            COUNTS.merge(kind,1L,Long::sum);
            int code=KINDS.computeIfAbsent(kind,k->KINDS.size()+1);
            if(stored>=CAPACITY){dropped++;fail("EVENT_OVERFLOW");return;}
            int p=(int)stored++*8;
            DATA[p]=sequence;DATA[p+1]=System.nanoTime();DATA[p+2]=Thread.currentThread().threadId();DATA[p+3]=code;
            DATA[p+4]=id(a);DATA[p+5]=id(b);DATA[p+6]=x;DATA[p+7]=y;
        } catch(Throwable observerFailure){fail("OBSERVER_EXCEPTION:"+observerFailure);}
    }
    public static synchronized void fail(String reason){if(failure.isEmpty())failure=reason;}
    public static synchronized void hook(String target){if(ENABLED)HOOKS.add(target);}
    public static void failed(String where,Throwable cause){fail(where+":"+cause.getClass().getName());event("NATIVE_EXCEPTION",cause,null,0,0);}
    public static synchronized void manager(Object owner,ChunkBuilder builder,ConcurrentLinkedDeque<?> queue) {
        if(!ENABLED)return;
        if(MANAGERS.size()>=32){fail("MANAGER_LIMIT");return;}
        MANAGERS.add(new Manager(owner,builder,queue));event("MANAGER_DEFINED",owner,builder,id(queue),0);
    }
    public static synchronized void firstUsable(long ns){if(!ENABLED)return;if(origin!=0)fail("DUPLICATE_ORIGIN");origin=ns;fence=Fence.current();event("FIRST_USABLE",null,null,ns,frames);}
    public static void frame() {
        if(!ENABLED||stopped)return;
        try {
            synchronized(ReadinessEvents.class) {
                frames++;if(fence!=null&&!fence.matches())fail("FIXED_SCENE_PRESENTATION_OR_FALLBACK_CHANGED");event("FRAME_END",null,null,frames,0);
                long now=System.nanoTime();if(now-lastGauge<1_000_000_000L)return;lastGauge=now;
                var c=Minecraft.getInstance();event("READINESS",c.level,null,c.level!=null&&c.levelRenderer.hasRenderedAllSections()?1:0,c.screen==null?0:1);
                for(var m:MANAGERS) {
                    event("GAUGE_QUEUE_BUSY",m.owner,m.builder,m.builder.getScheduledJobCount(),m.builder.getBusyThreadCount());
                    event("GAUGE_RESULT_EFFORT",m.owner,m.queue,m.queue.size(),m.builder.getScheduledEffort());
                }
            }
        }catch(Throwable e){failed("FRAME_OBSERVER",e);}
    }
    public static synchronized JsonObject status() {
        var o=new JsonObject();o.addProperty("enabled",ENABLED);o.addProperty("stopped",stopped);
        o.addProperty("attempted",attempted);o.addProperty("stored",stored);o.addProperty("dropped",dropped);o.addProperty("object_overflow",objectOverflow);
        o.addProperty("capacity",CAPACITY);o.addProperty("object_limit",OBJECT_LIMIT);o.addProperty("failure",failure);o.addProperty("first_usable_ns",origin);o.addProperty("frames",frames);
        o.addProperty("java_home",System.getProperty("java.home"));o.addProperty("java_runtime_version",System.getProperty("java.runtime.version"));o.addProperty("java_vm_version",System.getProperty("java.vm.version"));o.addProperty("exchange_requested",Boolean.getBoolean("shaderlod.experimentalMakeUpHistoryExchange"));
        o.add("counts",new Gson().toJsonTree(COUNTS));o.add("hooks",new Gson().toJsonTree(HOOKS));
        long start=System.nanoTime();o.addProperty("utc_epoch_ms",System.currentTimeMillis());o.addProperty("jvm_uptime_ms",ManagementFactory.getRuntimeMXBean().getUptime());
        try{o.addProperty("caller_native_tid",Path.of("/proc/thread-self").toRealPath().getFileName().toString());}catch(IOException e){o.addProperty("caller_native_tid","unavailable:"+e);}
        o.addProperty("caller_java_tid",Thread.currentThread().threadId());o.addProperty("anchor_before_ns",start);o.addProperty("anchor_after_ns",System.nanoTime());return o;
    }
    public static JsonObject sourcePins() throws Exception {
        var out=new JsonObject();
        for(String name:List.of("com.pp.shaderlod.dev.BenchmarkControlProbe","com.pp.shaderlod.dev.readiness.ReadinessEvents","com.pp.shaderlod.dev.readiness.ReadinessMixinPlugin","com.pp.shaderlod.dev.readiness.mixin.BuilderReadinessMixin","com.pp.shaderlod.dev.readiness.mixin.JobReadinessMixin","com.pp.shaderlod.dev.readiness.mixin.ManagerReadinessMixin","com.pp.shaderlod.dev.readiness.mixin.ReloadReadinessMixin")){
            String path=name.replace('.','/')+".class";try(var in=ReadinessEvents.class.getClassLoader().getResourceAsStream(path)){
                if(in==null)throw new IOException("Missing loaded source resource "+path);
                out.addProperty(path,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
            }
        }return out;
    }
    public static synchronized JsonObject export() throws IOException {
        if(!ENABLED)throw new IllegalStateException("Readiness recorder not enabled");
        event("EXPORT",null,null,0,0);stopped=true;
        Path dir=Path.of(System.getProperty("shaderlod.dev.readinessEvents")).toAbsolutePath();Files.createDirectories(dir);
        try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(dir.resolve("events.bin"),StandardOpenOption.CREATE_NEW)))) {
            for(int i=0;i<stored*8;i++)out.writeLong(DATA[i]);
        }
        var o=status();o.addProperty("format","big-endian signed64[sequence,nano_ns,java_tid,kind,id_a,id_b,value_x,value_y];64bytes/row");o.add("kinds",new Gson().toJsonTree(KINDS));o.add("objects",new Gson().toJsonTree(TYPES));
        Files.writeString(dir.resolve("events.json"),new GsonBuilder().setPrettyPrinting().create().toJson(o),StandardOpenOption.CREATE_NEW);return o;
    }
    /** Installed only into the exact manager's new empty queue, before any result supplier can use it. */
    public static final class ResultQueue<E> extends ConcurrentLinkedDeque<E> {
        public ResultQueue(){event("QUEUE_DEFINED",this,null,0,0);}
        @Override public boolean add(E result){event("ENQUEUE_BEGIN",this,result,0,0);try{boolean ok=super.offerLast(result);event("ENQUEUE_RETURN",this,result,ok?1:0,0);return ok;}catch(RuntimeException|Error e){failed("ENQUEUE",e);throw e;}}
        @Override public E poll(){E result=super.pollFirst();if(result!=null)event("DRAIN",this,result,0,0);return result;}
        @Override public void clear(){if(!isEmpty()){fail("NONEMPTY_RESULT_QUEUE_CLEAR");event("QUEUE_CLEAR",this,null,size(),0);}super.clear();}
    }
}
