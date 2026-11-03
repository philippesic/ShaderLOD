package com.pp.shaderlod.dev;

import java.util.*;
import java.util.function.IntSupplier;

/** Optional read-only renderer-boundary CPU census, never an affinity/governor change. */
final class NativeCpuPlacement {
    private static final int LIMIT=4096;
    private final boolean requested;
    private final IntSupplier reader;
    private final long[] heads=new long[LIMIT],tails=new long[LIMIT];
    private long starts,ends,unknownHeads,unknownTails,changed;
    private int head=-1;
    private boolean pending;
    private String error="";
    NativeCpuPlacement(){
        this(Boolean.getBoolean("shaderlod.dev.cpuPlacement"),Backend::read);
    }
    NativeCpuPlacement(boolean requested,IntSupplier reader){this.requested=requested;this.reader=reader;}
    static void warm(){if(Boolean.getBoolean("shaderlod.dev.cpuPlacement"))Backend.read();}
    private int read(){
        try {int cpu=reader.getAsInt();return cpu>=0&&cpu<LIMIT?cpu:-1;}
        catch(Exception|LinkageError failure){error=failure.getClass().getSimpleName();return -1;}
    }
    void begin(){
        if(!requested)return;
        if(pending)error="Nested renderer CPU placement interval";
        pending=true;starts++;head=read();if(head<0)unknownHeads++;else heads[head]++;
    }
    void end(){
        if(!requested)return;
        if(!pending){error="Renderer CPU placement end without start";return;}
        pending=false;ends++;int tail=read();if(tail<0)unknownTails++;else tails[tail]++;
        if(head>=0&&tail>=0&&head!=tail)changed++;
    }
    private static Map<String,Long> histogram(long[] values){
        var result=new TreeMap<String,Long>();for(int i=0;i<values.length;i++)if(values[i]>0)result.put(Integer.toString(i),values[i]);return Map.copyOf(result);
    }
    Map<String,Object> snapshot(){
        var result=new LinkedHashMap<String,Object>();result.put("requested",requested);
        result.put("starts",starts);result.put("ends",ends);result.put("head_cpus",histogram(heads));result.put("tail_cpus",histogram(tails));
        result.put("unknown_heads",unknownHeads);result.put("unknown_tails",unknownTails);result.put("changed_boundary_cpu",changed);
        result.put("pending",pending);result.put("error",error);
        result.put("scope","Two read-only glibc sched_getcpu boundary calls per recorded renderer frame; observer overhead uncalibrated. Not full residence/per-core CPU time; migration returning to the same CPU is invisible. This reader does not mutate affinity/governors; see render_cpu_affinity for any separately declared diagnostic.");
        return Map.copyOf(result);
    }
    private static final class Backend {
        static final org.lwjgl.system.SharedLibrary LIBRARY;
        static final long FUNCTION;
        static {
            org.lwjgl.system.SharedLibrary library=null;long function=0;
            try {
                if("Linux".equals(System.getProperty("os.name"))){
                    library=org.lwjgl.system.Library.loadNative(NativeCpuPlacement.class,"org.lwjgl","libc.so.6",false);
                    function=library.getFunctionAddress("sched_getcpu");
                }
            } catch(Exception|LinkageError unavailable){function=0;}
            LIBRARY=library;FUNCTION=function;
        }
        static int read(){return FUNCTION==0?-1:org.lwjgl.system.JNI.callI(FUNCTION);}
    }
}
