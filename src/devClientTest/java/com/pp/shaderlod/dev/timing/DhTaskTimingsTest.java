package com.pp.shaderlod.dev.timing;

public final class DhTaskTimingsTest {
    public static void main(String[] args)throws Exception {
        if(DhResidencyBounds.beyond(-512,0,1024,0,0,12)||DhResidencyBounds.beyond(192,0,64,0,0,12)||!DhResidencyBounds.beyond(193,0,64,0,0,12)||!DhResidencyBounds.beyond(-512,0,64,0,0,12))throw new AssertionError("far AABB bounds");
        DhTaskTimings.start();var old=DhTaskTimings.submitted();
        var t=DhTaskTimings.begin(old,"worker",0);
        var first=DhTaskTimings.stop();
        if(!first.get("unfinished_or_late").equals(1L))throw new AssertionError(first);
        DhTaskTimings.start();DhTaskTimings.end(t,false);
        if(!((java.util.List<?>)DhTaskTimings.stop().get("samples")).isEmpty())throw new AssertionError("late callback crossed owner");
        DhTaskTimings.start();var w=DhTaskTimings.owner();
        Thread worker=new Thread(()->{var token=DhTaskTimings.begin(DhTaskTimings.submitted(),"worker",0);try{throw new RuntimeException();}catch(RuntimeException expected){DhTaskTimings.end(token,true);}},"test worker");
        worker.start();worker.join();
        for(int i=0;i<17000;i++){var token=DhTaskTimings.begin(w,"upload/DATA",32);DhTaskTimings.end(token,false);}
        var result=DhTaskTimings.stop();
        var samples=(java.util.List<?>)result.get("samples");
        if(samples.size()!=16384||!result.get("dropped").equals(617L))throw new AssertionError(result);
        var sample=(DhTaskTimings.Sample)samples.getFirst();
        if(!sample.failed()||sample.threadId()==Thread.currentThread().threadId()||sample.endNs()<sample.startNs())throw new AssertionError(sample);
        System.out.println("DH task owner, failure, worker clock and bound checks passed");
    }
}
