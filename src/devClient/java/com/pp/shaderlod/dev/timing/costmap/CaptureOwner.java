package com.pp.shaderlod.dev.timing.costmap;

import java.util.*;
import static com.pp.shaderlod.dev.timing.costmap.CostMapRecords.*;

/** Render-thread owner. Backends never own frame identity or reinterpret a late result. */
public final class CaptureOwner implements AutoCloseable {
    public enum Mode { DISARMED, DIAGNOSTIC, SCORED }
    public interface Queries {
        long begin(int scopeId);
        void end(long query);
        boolean available(long query);
        Result read(long query);
        void delete(long query);
    }
    public record Result(Long begin, Long end, long elapsedNs) {}
    private record Pending(long query, Invocation invocation) {}
    private final ScopeRegistry registry;
    private final Queries queries;
    private final Clock clock;
    private final Backend backend;
    private final int queryLimit, cadence;
    private final Map<Long, Pending> pending = new LinkedHashMap<>();
    private final ArrayDeque<Long> active = new ArrayDeque<>();
    private FrameId frame;
    private Mode mode = Mode.DISARMED, requested = Mode.DISARMED;
    private boolean closed, sampled;
    private long lateWorkers;
    public CaptureOwner(ScopeRegistry registry, Queries queries, Clock clock, Backend backend, int queryLimit, int cadence) {
        if(queryLimit<1 || cadence<1)throw new IllegalArgumentException("positive limits required");
        this.registry=registry;this.queries=queries;this.clock=clock;this.backend=backend;
        this.queryLimit=queryLimit;this.cadence=cadence;
    }
    public void request(Mode next) {
        if(closed)throw new IllegalStateException("closed capture");
        if(next==Mode.SCORED && (mode==Mode.DIAGNOSTIC || !pending.isEmpty() || !active.isEmpty()))
            throw new IllegalStateException("diagnostic owner must close before scored timing");
        requested=next;
    }
    public void frame(FrameId next, boolean ready) {
        if(closed)throw new IllegalStateException("closed capture");
        // An exception/missing return must never leave an elapsed query open into another frame.
        for(long id:List.copyOf(active)) { Pending p=pending.remove(id); queries.end(p.query);queries.delete(p.query); }
        active.clear();
        frame=next;registry.beginFrame(next);mode=requested;
        sampled=mode==Mode.DIAGNOSTIC && ready && (next.frameId()-1)%cadence==0;
        if(sampled)poll(queryLimit);
    }
    public Invocation begin(int scope, long ns, Long threadNs, String dhStage, String worldShadow, String opacity) {
        if(mode!=Mode.DIAGNOSTIC)throw new IllegalStateException("diagnostic owner required");
        return registry.begin(scope,Thread.currentThread().threadId(),"render",dhStage,worldShadow,opacity,ns,threadNs,clock);
    }
    public Outcome issue(Invocation invocation, boolean selectedLeaf) {
        if(!sampled || !selectedLeaf)return new Outcome(Status.UNSAMPLED,"frame/scope set not selected");
        if(backend==Backend.ELAPSED && !active.isEmpty())return new Outcome(Status.CONFLICT,"elapsed query already active");
        if(pending.size()==queryLimit)return new Outcome(Status.DROPPED,"bounded query pool full");
        long query=queries.begin(invocation.scopeId());
        try {registry.issueGpu(invocation,clock,backend);}catch(RuntimeException e){queries.end(query);queries.delete(query);throw e;}
        pending.put(invocation.eventId(),new Pending(query,invocation));active.push(invocation.eventId());
        return new Outcome(Status.PENDING,"issued by diagnostic owner");
    }
    public void end(Invocation invocation,long ns,Long threadNs) {
        if(active.contains(invocation.eventId())) {
            if(active.peek()!=invocation.eventId())throw new IllegalStateException("unbalanced query end");
            active.pop();queries.end(pending.get(invocation.eventId()).query);
        }
        registry.end(invocation.eventId(),ns,threadNs);
    }
    private void poll(int budget) {
        int count=0;
        for(var item:List.copyOf(pending.entrySet())) {
            if(count++>=budget)break;
            Pending p=item.getValue();if(active.contains(item.getKey()) || !queries.available(p.query))continue;
            Result result=queries.read(p.query);
            registry.resolveGpu(item.getKey(),frame,result.begin,result.end,result.elapsedNs,new Outcome(Status.MEASURED,"nonblocking available result"));
            queries.delete(p.query);pending.remove(item.getKey());
        }
    }
    /** Worker evidence is accepted only by its original immutable owner/epoch. */
    public boolean acceptsWorker(FrameId issued) {
        boolean accepted=!closed && mode==Mode.DIAGNOSTIC && frame!=null && frame.equals(issued);
        if(!accepted)lateWorkers++;
        return accepted;
    }
    public long lateWorkers(){return lateWorkers;}
    public Mode mode(){return mode;}
    /** Bounded attempts, no sleeping/waiting. Off, Cancel, error and disconnect use the same terminal path. */
    public void stop(String reason,int drainAttempts) {
        if(closed)return;
        RuntimeException failure=null;
        for(long id:List.copyOf(active)) {
            try {queries.end(pending.get(id).query);}catch(RuntimeException e){failure=e;}
        }
        active.clear();
        if(failure==null)try {
            for(int i=0;i<Math.min(Math.max(drainAttempts,0),8);i++)poll(queryLimit);
        }catch(RuntimeException e){failure=e;}
        for(Pending p:pending.values()) {
            try{queries.delete(p.query);}catch(RuntimeException e){if(failure==null)failure=e;else failure.addSuppressed(e);}
        }
        pending.clear();
        try{registry.close(reason);}finally{mode=requested=Mode.DISARMED;closed=true;}
        if(failure!=null)throw failure;
    }
    @Override public void close(){stop("Off/Cancel/owner closed",0);}
}
