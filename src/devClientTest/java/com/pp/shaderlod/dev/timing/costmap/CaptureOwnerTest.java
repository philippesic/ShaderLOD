package com.pp.shaderlod.dev.timing.costmap;

import java.util.*;
import static com.pp.shaderlod.dev.timing.costmap.CostMapRecords.*;

public final class CaptureOwnerTest {
    static int checks;
    static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
    static FrameId frame(long epoch,long id){return new FrameId("capture","cohort",1,epoch,id,1);}
    static final Clock CLOCK=new Clock("monotonic",64,1000,1);
    static class Mock implements CaptureOwner.Queries {
        int begins,polls,ends,deletes,depth;long next;boolean ready=true;
        public long begin(int scope){begins++;depth++;check(depth==1);return ++next;}
        public void end(long q){ends++;depth--;}
        public boolean available(long q){polls++;return ready;}
        public CaptureOwner.Result read(long q){return new CaptureOwner.Result(null,null,100);}
        public void delete(long q){deletes++;}
    }
    static ScopeRegistry registry(){return new ScopeRegistry("capture","cohort",16,1000,16,16);}
    static Scope scope(ScopeRegistry r){return r.register(new ScopeKey(1,"composite","deferred",Operation.DRAW_GROUP,"test"),"AO + clouds",Set.of("AO","clouds"),"fixture",new Outcome(Status.UNKNOWN,"fixture"));}
    public static void main(String[] args)throws Exception {
        Mock q=new Mock();ScopeRegistry r=registry();Scope s=scope(r);
        CaptureOwner o=new CaptureOwner(r,q,CLOCK,Backend.ELAPSED,4,2);
        o.request(CaptureOwner.Mode.DIAGNOSTIC);o.frame(frame(1,1),true);
        var a=o.begin(s.id(),10L,null,"main","world","opaque");check(o.issue(a,true).status()==Status.PENDING);
        var b=o.begin(s.id(),20L,null,"main","world","opaque");check(o.issue(b,true).status()==Status.CONFLICT);
        o.end(b,30L,null);o.end(a,50L,null);
        o.frame(frame(1,2),true);check(q.polls==0);
        var c=o.begin(s.id(),60L,null,"main","world","opaque");check(o.issue(c,true).status()==Status.UNSAMPLED);o.end(c,80L,null);check(q.begins==1);
        o.frame(frame(2,1),true);check(q.polls==1);
        var batch=r.drain();var gpu=batch.events().stream().filter(e->e.gpu()!=null && e.gpu().elapsedNs()!=null).findFirst().orElseThrow();
        check(gpu.invocation().frame().epoch()==1);check(gpu.gpu().retrievalFrame().epoch()==2);
        check(!o.acceptsWorker(frame(1,1)));check(o.lateWorkers()==1);
        try{o.request(CaptureOwner.Mode.SCORED);throw new AssertionError();}catch(IllegalStateException expected){checks++;}
        o.frame(frame(2,2),false);check(q.polls==1);
        o.stop("disconnect",0);check(q.deletes==1);check(o.mode()==CaptureOwner.Mode.DISARMED);check(!o.acceptsWorker(frame(2,2)));
        for(String reason:List.of("Off","Cancel","error","readiness lost","residency changed")) {
            q=new Mock();q.ready=false;r=registry();s=scope(r);o=new CaptureOwner(r,q,CLOCK,Backend.ELAPSED,4,1);
            o.request(CaptureOwner.Mode.DIAGNOSTIC);o.frame(frame(1,1),true);
            a=o.begin(s.id(),0,null,"main","world","opaque");o.issue(a,true);
            o.stop(reason,2);check(q.depth==0);check(q.deletes==1);check(r.health().unresolvedTail()==1);
            o.close();check(q.deletes==1);
        }
        var values=new HashMap<String,String>();values.put("radius","128");
        var overrides=new OwnedOverrides<String,String>(new OwnedOverrides.Access<>() {
            public String get(String key){return values.get(key);}
            public void set(String key,String value){if(value==null)values.remove(key);else values.put(key,value);}
        });
        overrides.set("generation","false");overrides.set("radius","256");overrides.close();check(!values.containsKey("generation"));check(values.get("radius").equals("128"));
        overrides.set("radius","256");values.put("radius","512");
        try{overrides.close();throw new AssertionError();}catch(IllegalStateException expected){check(values.get("radius").equals("512"));}
        System.out.println("CaptureOwnerTest: "+checks+" checks passed");
    }
}
