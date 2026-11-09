package com.pp.shaderlod.dev.timing;

import java.lang.management.ManagementFactory;
import java.util.*;

/** Window-owned CPU tasks, never charged to a render frame. No renderer timer on workers. */
public final class DhTaskTimings {
    private static volatile Window current;
    public record Sample(String kind, long threadId, String thread, long startNs, long endNs,
                         Long cpuNs, long bytes, boolean failed) {}
    public record Token(Window owner, String kind, long start, Long cpu, long bytes) {}
    public static final class Window {
        private final java.util.concurrent.ConcurrentLinkedQueue<Sample> samples=new java.util.concurrent.ConcurrentLinkedQueue<>();
        private volatile boolean closed;
        private final java.util.concurrent.atomic.AtomicLong submitted=new java.util.concurrent.atomic.AtomicLong(), completed=new java.util.concurrent.atomic.AtomicLong(), dropped=new java.util.concurrent.atomic.AtomicLong();
        public void submitted(){submitted.incrementAndGet();}
        public void add(Sample sample) {
            if(closed)return;
            long ordinal=completed.incrementAndGet();
            if(ordinal<=16384)samples.add(sample);else dropped.incrementAndGet();
        }
        public Map<String,Object> close() {
            closed=true;
            var retained=new ArrayList<Sample>();Sample s;while((s=samples.poll())!=null)retained.add(s);
            return Map.of("samples",retained,"dropped",dropped.get(),"submitted",submitted.get(),
                "unfinished_or_late",Math.max(0,submitted.get()-retained.size()-dropped.get()),
                "semantics","CPU wall/thread intervals; overlapping tasks nonadditive; no GPU transfer execution claim; terminal races counted as unfinished");
        }
    }
    public static Window owner(){return current;}
    public static Window submitted(){var w=current;if(w!=null)w.submitted();return w;}
    public static void start(){current=new Window();}
    public static Map<String,Object> stop(){var w=current;current=null;return w==null?Map.of():w.close();}
    private static Long cpu(){var b=ManagementFactory.getThreadMXBean();if(!b.isCurrentThreadCpuTimeSupported()||!b.isThreadCpuTimeEnabled())return null;long n=b.getCurrentThreadCpuTime();return n<0?null:n;}
    public static Token begin(Window owner,String kind,long bytes){if(owner==null)return null;if(kind.startsWith("upload/"))owner.submitted();return new Token(owner,kind,System.nanoTime(),cpu(),bytes);}
    public static void end(Token t,boolean failed){if(t==null)return;long end=System.nanoTime();Long c=cpu();t.owner.add(new Sample(t.kind,Thread.currentThread().threadId(),Thread.currentThread().getName(),t.start,end,c==null||t.cpu==null?null:c-t.cpu,t.bytes,failed));}
}
