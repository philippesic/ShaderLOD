package com.pp.shaderlod.optimization.makeupprime;
import java.lang.management.ManagementFactory;
import java.util.*;
/** Private bounded invocation records. Inclusive scopes overlap; never sum them. */
public final class C01Cost {
 private C01Cost(){}
 static final boolean ENABLED=Boolean.getBoolean("shaderlod.dev.c01Cost")&&Boolean.getBoolean("shaderlod.dev.c01Bounds")&&Boolean.getBoolean("shaderlod.dev.c01NativeR66");
 private static final java.lang.management.ThreadMXBean CPU=ENABLED?ManagementFactory.getThreadMXBean():null;
 private static final List<Map<String,Object>> rows=new ArrayList<>();
 private static long dropped; private static int frame,irisFrame=-1;
 private static boolean observing;
 public static boolean observing(){return observing;}
 public static void observe(boolean value){
  if(value){if(!ENABLED)throw new IllegalStateException("Explicit private C01 cost launch required");
   if(!CPU.isCurrentThreadCpuTimeSupported()||!CPU.isThreadCpuTimeEnabled())throw new IllegalStateException("Current thread CPU clock unavailable");
   reset();}
  observing=value;
 }
 static long[] begin(){if(!ENABLED||!observing||!C01Bounds.active())return null;return new long[]{System.nanoTime(),cpu()};}
 private static long cpu(){return CPU.isCurrentThreadCpuTimeSupported()&&CPU.isThreadCpuTimeEnabled()?CPU.getCurrentThreadCpuTime():-1;}
 static void end(String scope,long[] start){if(start==null||!ENABLED)return;record(scope,start,Map.of());}
 static void end(String scope,long[] start,Map<String,Object> context){if(start==null||!ENABLED)return;record(scope,start,context);}
 private static synchronized void record(String scope,long[] start,Map<String,Object> context){long c=cpu(),w=System.nanoTime();if(rows.size()>=50000){dropped++;return;}var row=new LinkedHashMap<String,Object>(context);row.putAll(Map.of("scope",scope,"frame",frame,"iris_frame",irisFrame,"thread",Thread.currentThread().threadId(),"wall_ns",w-start[0],"thread_cpu_ns",c<0||start[1]<0?-1:c-start[1]));rows.add(row);}
 static void frame(int value){frame=value;if(ENABLED&&observing)irisFrame=net.irisshaders.iris.uniforms.SystemTimeUniforms.COUNTER.getAsInt();}
 static synchronized void reset(){rows.clear();dropped=0;frame=0;irisFrame=-1;}
 public static synchronized Map<String,Object> snapshot(){return Map.of("enabled",ENABLED,"observing",observing,"rows",List.copyOf(rows),"dropped",dropped,"scope","inclusive invocation wall/threadCPU; wall minus CPU is not pure GPU wait; timer and allocation overhead included");}
}
