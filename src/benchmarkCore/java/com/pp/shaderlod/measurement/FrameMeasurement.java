package com.pp.shaderlod.measurement;
import java.util.*;
import java.util.function.LongSupplier;
/** One render-thread query owner. Backend results are asynchronous and never wait for the GPU. */
public final class FrameMeasurement {
    public interface GpuBackend {
        String status(); int counterBits();
        int create(); boolean busy(); void begin(int query); void end();
        boolean available(int query); long nanoseconds(int query); void delete(int query);
        /** Frozen backend opt-in; only successfully read positive results may enter the pool. */
        default boolean reuseCompletedQueries() { return false; }
    }
    public record Sample(long frame,long epoch,Long frameWallNs,long renderWallNs,Long renderCpuNs,
                         Long processCpuNs,Long gpuNs,String gpuStatus) {
        public Double fps() { return frameWallNs==null||frameWallNs<=0?null:1_000_000_000.0/frameWallNs; }
    }
    private record Pending(int query,Sample sample,long issuedAt) {}
    private final GpuBackend gpu;
    private final boolean reuseCompletedQueries;
    private final LongSupplier wall,threadCpu,processCpu;
    private CpuWindowMeasurement cpuWindow;
    private final ArrayDeque<Pending> pending=new ArrayDeque<>();
    private final ArrayDeque<Sample> completed=new ArrayDeque<>();
    private final ArrayDeque<Integer> reusable=new ArrayDeque<>();
    private long queryCreates,queryReuses;
    private boolean enabled,open; private long epoch,frame,previousStart=-1,start,cpu,process;
    private Long interval; private int query; private String queryStatus="disabled";
    public FrameMeasurement(GpuBackend gpu,LongSupplier wall,LongSupplier threadCpu,LongSupplier processCpu) {
        this.gpu=Objects.requireNonNull(gpu);this.wall=wall;this.threadCpu=threadCpu;this.processCpu=processCpu;
        this.reuseCompletedQueries=gpu.reuseCompletedQueries();
    }
    public void enabled(boolean value) {
        if(enabled==value)return;
        if(!value)pauseCpuWindow();
        if(value)invalidate();else invalidateFrames();
        enabled=value;
        if(value)cpuWindow=null;
    }
    public boolean enabled() { return enabled; }
    public long epoch() { return epoch; }
    public void invalidate() {
        cpuWindow=null;
        invalidateFrames();
    }
    private void invalidateFrames() {
        epoch++;previousStart=-1;RuntimeException failure=null;
        if(open&&query!=0) {
            try {gpu.end();}catch(RuntimeException error){failure=error;}
            try {gpu.delete(query);}catch(RuntimeException error){if(failure==null)failure=error;}
        }
        open=false;query=0;
        while(!pending.isEmpty()) {
            try {gpu.delete(pending.removeFirst().query());}catch(RuntimeException error){if(failure==null)failure=error;}
        }
        while(!reusable.isEmpty()) {
            try {gpu.delete(reusable.removeFirst());}catch(RuntimeException error){if(failure==null)failure=error;}
        }
        completed.clear();
        if(failure!=null)throw failure;
    }
    public void begin() {
        if(!enabled)return; // no clocks, allocations or GL calls while disabled
        if(open)invalidate(); // prior renderer threw: never carry its query into another frame
        poll();frame++;start=wall.getAsLong();cpu=threadCpu.getAsLong();process=processCpu.getAsLong();
        if(cpuWindow==null)cpuWindow=new CpuWindowMeasurement(new CpuWindowMeasurement.Snapshot(Thread.currentThread().threadId(),start,cpu,process));
        cpuWindow.frameStarted(Thread.currentThread().threadId());
        interval=previousStart<0?null:positive(start-previousStart);previousStart=start;open=true;
        queryStatus=gpu.status();
        if(gpu.counterBits()<=0)return;
        if(pending.size()>=8) {queryStatus="query ring full; sample skipped";return;}
        if(gpu.busy()) {queryStatus="another elapsed query owns scope; sample skipped";return;}
        if(reusable.isEmpty()){query=gpu.create();queryCreates++;}
        else {query=reusable.removeFirst();queryReuses++;}
        gpu.begin(query);queryStatus="pending";
    }
    public void end() {
        if(!enabled||!open)return;
        long stop=wall.getAsLong();Long cpuNs=delta(cpu,threadCpu.getAsLong()),processNs=delta(process,processCpu.getAsLong());
        if(cpuWindow!=null)cpuWindow.frameCompleted(Thread.currentThread().threadId());
        var sample=new Sample(frame,epoch,interval,Math.max(0,stop-start),cpuNs,processNs,null,queryStatus);
        if(query!=0) {gpu.end();pending.addLast(new Pending(query,sample,stop));query=0;}
        else publish(sample);
        open=false;poll();
    }
    public void poll() {
        if(!enabled)return;
        var iterator=pending.iterator();
        while(iterator.hasNext()) {
            var item=iterator.next();boolean available;
            try {available=gpu.available(item.query());}
            catch(RuntimeException failure) {
                if(reuseCompletedQueries)discardFailed(iterator,item.query(),failure);
                throw failure;
            }
            if(!available) {
                if(wall.getAsLong()-item.issuedAt()>5_000_000_000L) {
                    if(reuseCompletedQueries) {iterator.remove();gpu.delete(item.query());}
                    else {gpu.delete(item.query());iterator.remove();}
                    var s=item.sample();
                    if(s.epoch()==epoch)publish(new Sample(s.frame(),s.epoch(),s.frameWallNs(),s.renderWallNs(),s.renderCpuNs(),s.processCpuNs(),null,"GPU query timeout after 5 seconds"));
                }
                continue;
            }
            long ns;String validStatus=null;
            try {
                ns=gpu.nanoseconds(item.query());
                if(reuseCompletedQueries&&ns>0&&item.sample().epoch()==epoch)validStatus=gpu.status();
            } catch(RuntimeException failure) {
                if(reuseCompletedQueries)discardFailed(iterator,item.query(),failure);
                throw failure;
            }
            if(reuseCompletedQueries) {
                iterator.remove();
                // pending + active + reusable ownership never exceeds the existing eight-query ring.
                if(ns>0&&item.sample().epoch()==epoch)reusable.addLast(item.query());
                else gpu.delete(item.query());
            } else {gpu.delete(item.query());iterator.remove();}
            var s=item.sample();
            if(s.epoch()==epoch)publish(new Sample(s.frame(),s.epoch(),s.frameWallNs(),s.renderWallNs(),s.renderCpuNs(),s.processCpuNs(),
                    ns>0?ns:null,ns>0?"valid elapsed query; "+(reuseCompletedQueries?validStatus:gpu.status()):"zero/invalid GPU result"));
        }
    }
    private void discardFailed(Iterator<Pending> iterator,int failed,RuntimeException failure) {
        iterator.remove();
        try {gpu.delete(failed);}catch(RuntimeException cleanup) {if(cleanup!=failure)failure.addSuppressed(cleanup);}
    }
    private void publish(Sample sample) { if(completed.size()==8192)completed.removeFirst();completed.addLast(sample); }
    public List<Sample> drain() {var result=List.copyOf(completed);completed.clear();return result;}
    /** Explicitly discard the current CPU epoch before another owned window. */
    public void resetCpuWindow() { cpuWindow=null; }
    /** Nonmutating view for export while recording; stopped windows retain their frozen result. */
    public CpuWindowMeasurement.Result snapshotCpuWindow() {
        if(cpuWindow!=null&&cpuWindow.result()!=null)return cpuWindow.result();
        return cpuWindow==null?null:cpuWindow.snapshot(new CpuWindowMeasurement.Snapshot(Thread.currentThread().threadId(),wall.getAsLong(),threadCpu.getAsLong(),processCpu.getAsLong()));
    }
    /** Freeze before query draining, settings inspection or stopped export; idempotent. */
    public CpuWindowMeasurement.Result pauseCpuWindow() {
        if(cpuWindow!=null&&cpuWindow.result()!=null)return cpuWindow.result();
        return cpuWindow==null?null:cpuWindow.finish(new CpuWindowMeasurement.Snapshot(Thread.currentThread().threadId(),wall.getAsLong(),threadCpu.getAsLong(),processCpu.getAsLong()));
    }
    public int pendingQueries() { return pending.size(); }
    /** Render-thread observation only; counters do not perform GL queries. */
    public Map<String,Long> queryAllocationCounts() {
        return Map.of("created",queryCreates,"reused",queryReuses,"pooled",(long)reusable.size(),
                "pending",(long)pending.size(),"active",query==0?0L:1L);
    }
    private static Long positive(long value) {return value>0?value:null;}
    private static Long delta(long start,long end) {return start>=0&&end>=start?end-start:null;}
}
