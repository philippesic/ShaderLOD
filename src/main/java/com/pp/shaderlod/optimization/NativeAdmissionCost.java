package com.pp.shaderlod.optimization;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Map;

/** Explicit diagnostic only; never grants permission or changes a consumer result. */
public final class NativeAdmissionCost {
    public static final boolean ENABLED=Boolean.getBoolean("shaderlod.dev.cpuAdmissionCost");
    private static final ThreadMXBean CPU=ENABLED?ManagementFactory.getThreadMXBean():null;
    private static final long[] count=new long[9],accepted=new long[9],wall=new long[9],cpu=new long[9],maxWall=new long[9],maxCpu=new long[9];
    public static final int COMPACTOR=2, PUBLICATION=3, SCALAR_LIFECYCLE=4, FILL=5, MAXIMUM_QUERY=6, NATIVE_SUBMISSION=7, CLOCK_BASELINE=8;
    private static boolean observing;
    private static String error="";
    private NativeAdmissionCost() {}
    public static boolean observing(){return observing;}
    public static void observe(boolean enabled){
        if(enabled){
            if(!ENABLED||CPU==null||!CPU.isCurrentThreadCpuTimeSupported()||!CPU.isThreadCpuTimeEnabled())
                throw new IllegalStateException("Explicit supported CPU admission-cost diagnostic required");
            for(var values:List.of(count,accepted,wall,cpu,maxWall,maxCpu))java.util.Arrays.fill(values,0);
            error="";
        }
        observing=enabled;
        if(enabled)for(int i=0;i<4096;i++){
            long start=wallStart(),cpu=cpuStart(start);finish(CLOCK_BASELINE,start,cpu,true);
        }
    }
    public static long wallStart(){return ENABLED&&observing?System.nanoTime():0;}
    public static long cpuStart(long start){return start==0?0:CPU.getCurrentThreadCpuTime();}
    public static void finish(int kind,long start,long cpuStart,boolean allowed){
        if(start==0)return;
        long cpuEnd=CPU.getCurrentThreadCpuTime(),wallEnd=System.nanoTime();
        if(!observing||kind<0||kind>=9||count[kind]>=100_000||cpuStart<0||cpuEnd<cpuStart||wallEnd<start){
            error="Admission cost bound/clock/state changed";observing=false;return;
        }
        long elapsedWall=wallEnd-start,elapsedCpu=cpuEnd-cpuStart;
        if(elapsedWall>5_000_000_000L||elapsedCpu>5_000_000_000L){error="Admission observation exceeded interval bound";observing=false;return;}
        count[kind]++;if(allowed)accepted[kind]++;
        wall[kind]+=elapsedWall;cpu[kind]+=elapsedCpu;
        maxWall[kind]=Math.max(maxWall[kind],elapsedWall);maxCpu[kind]=Math.max(maxCpu[kind],elapsedCpu);
    }
    private static Map<String,Object> row(int i){return Map.of("calls",count[i],"accepted",accepted[i],
        "wall_ns",wall[i],"thread_cpu_ns",cpu[i],"maximum_wall_ns",maxWall[i],"maximum_cpu_ns",maxCpu[i]);}
    public static Map<String,Object> snapshot(){
        var result=new java.util.LinkedHashMap<String,Object>();
        result.put("enabled",ENABLED);result.put("observing",observing);result.put("error",error);
        var names=List.of("compaction","scalar","compactor_body","scalar_publication","scalar_lifecycle","native_fill_inclusive","maximum_query_inclusive","native_submission_delegate","clock_baseline");
        for(int i=0;i<names.size();i++)result.put(names.get(i),row(i));
        result.put("scope","Untimed actual consumer predicates and bodies. Fill/query intervals include nested probes; do not sum them. Native submission is CPU delegate time, not GPU completion. Empty clock-pair calibration is contextual, not a subtraction guarantee. Clock overhead included, all clocks disarmed in scored windows; no permission reuse.");
        return Map.copyOf(result);
    }
}
