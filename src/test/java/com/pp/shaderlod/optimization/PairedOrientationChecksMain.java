package com.pp.shaderlod.optimization;

import com.pp.shaderlod.measurement.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** CPU-only bracket contract checks using real scoped request persistence and exporter entry points. */
public final class PairedOrientationChecksMain {
    private static int checks;
    private static final OptimizationId SELECTED=OptimizationId.AO_BUDGET;
    private static void check(boolean value,String description) {checks++;if(!value)throw new AssertionError(description);}
    private static void publish(OptimizationSnapshot flags) {
        try {OptimizationConfig.replace(flags);}catch(java.io.IOException error){throw new RuntimeException(error);}
    }
    private static OptimizationSnapshot original(boolean on) {
        // Exercise a selected flag alongside independently mixed peer requests, not an all-Off fixture.
        return OptimizationSnapshot.noneRequested().with(OptimizationId.EARLY_ALPHA,true)
                .with(OptimizationId.REFLECTION_TRACE_BUDGET,true).with(SELECTED,on);
    }
    private static void collect(PairedMeasurement pair,long frameMs) {
        pair.accept(new FrameMeasurement.Sample(0,1,null,1_000_000L,null,null,null,"unsupported: CPU fixture"));
        for(int frame=1;frame<=2;frame++)pair.accept(new FrameMeasurement.Sample(frame,1,frameMs*1_000_000L,
                1_000_000L,500_000L,700_000L,null,"unsupported: CPU fixture"));
        check(pair.readyToAdvance(),"warmup excluded; exactly two usable samples advance");
    }
    private static Properties properties(Path path)throws Exception {
        var result=new Properties();try(var reader=Files.newBufferedReader(path)){result.load(reader);}return result;
    }
    private static void completeBracket(Path directory,boolean on)throws Exception {
        var original=original(on);publish(original);
        var transitions=new ArrayList<OptimizationSnapshot>();
        var changes=new AtomicInteger();
        var pair=PairedMeasurement.prepare(SELECTED,1,2,()->Map.of("scene","fixed","source_sha256","exact-source"),flags->{
            check(flags.with(SELECTED,on).equals(original),"every Apply preserves all peer flags");
            if(!OptimizationConfig.snapshot().equals(flags))changes.incrementAndGet();
            transitions.add(flags);publish(flags);return true;
        });
        check(!pair.active(),"prepare retains callback ownership without starting");pair.start();
        var baseline=on?PairedMeasurement.Phase.ON:PairedMeasurement.Phase.OFF;
        var opposite=on?PairedMeasurement.Phase.OFF:PairedMeasurement.Phase.ON;
        check(pair.phase()==baseline&&pair.baselinePhase()==baseline,"starts in actual original selected state");
        collect(pair,on?10:20);pair.advance();
        check(pair.phase()==opposite,"middle window samples the opposite selected state");
        collect(pair,on?20:10);pair.advance();
        check(pair.phase()==PairedMeasurement.Phase.RETURN&&OptimizationConfig.snapshot().equals(original),"RETURN already consumes full original snapshot");
        pair.validate();check(pair.active(),"RETURN validates the true selected state");
        collect(pair,on?12:22);pair.advance();
        check(pair.phase()==PairedMeasurement.Phase.COMPLETE&&!pair.restorationPending(),"complete owns no pending restoration");
        check(changes.get()==2&&transitions.size()==4,"only two actual changes; initial/final Apply retain verification");
        check(OptimizationConfig.snapshot().equals(original)&&OptimizationConfig.snapshot("complementary-unbound").equals(original),"completion restores active and persisted family snapshot");
        check(pair.rows().size()==6&&pair.rows().get(0).phase()==baseline&&pair.rows().get(2).phase()==opposite,"three raw windows retain actual orientation and denominators");
        check(pair.summary().get("frame_wall_delta_ms")==-10.0,"ON minus OFF improvement sign independent of order");
        check(pair.summary().get("frame_wall_baseline_drift_ms")==2.0,"RETURN drift compared with same-state initial window");
        check(!pair.summary().containsKey("gpu_delta_ms"),"no inferred GPU duration");
        var exported=MeasurementExport.write(directory,pair,List.of(),pair.status());
        var metadata=properties(exported.resolve("metadata.properties"));
        check(metadata.getProperty("return_state").equals(baseline.name())&&metadata.getProperty("baseline_state").equals(baseline.name()),"export explicitly labels RETURN's selected state");
        check(metadata.getProperty("phase_order").equals(on?"ON,OFF,RETURN":"OFF,ON,RETURN"),"export declares sampling order");
        check(Files.readAllLines(exported.resolve("samples.csv")).get(1).startsWith(baseline.name()+","),"export raw row order follows actual sampling");
    }
    private static void cancelAndFailures(boolean on) {
        var original=original(on);publish(original);
        var source=new AtomicInteger(1);
        PairedMeasurement.Apply apply=flags->{publish(flags);source.set(flags.enabled(SELECTED)==on?1:2);return true;};
        var pair=new PairedMeasurement(SELECTED,1,2,()->Map.of("source_sha256","original"),apply);
        collect(pair,10);pair.advance();pair.cancel();
        check(pair.phase()==PairedMeasurement.Phase.FAILED&&!pair.restorationPending()&&OptimizationConfig.snapshot().equals(original)&&source.get()==1,"cancellation from opposite state restores full flags and callback-owned source");

        var pins=new HashMap<>(Map.of("source_sha256","original"));
        pair=new PairedMeasurement(SELECTED,1,2,()->Map.copyOf(pins),apply);
        pins.put("source_sha256","external-edit");pair.validate();
        check(pair.phase()==PairedMeasurement.Phase.FAILED&&OptimizationConfig.snapshot().equals(original),"deep source pin mismatch refuses and restores");

        var failRestore=new AtomicBoolean(false);
        pair=new PairedMeasurement(SELECTED,1,2,()->Map.of("scene","fixed"),flags->{
            if(failRestore.get()&&flags.equals(original))return false;
            publish(flags);return true;
        });
        collect(pair,10);pair.advance();failRestore.set(true);pair.cancel();
        check(pair.phase()==PairedMeasurement.Phase.FAILED&&pair.restorationPending()&&!OptimizationConfig.snapshot().equals(original),"failed cancellation recovery retains ownership");
        failRestore.set(false);check(pair.retryRestore()&&!pair.restorationPending()&&OptimizationConfig.snapshot().equals(original),"retained recovery retries exact original snapshot");

        var first=new AtomicBoolean(true);
        pair=new PairedMeasurement(SELECTED,1,2,()->Map.of("scene","fixed"),flags->{
            if(first.getAndSet(false)){publish(flags.with(SELECTED,!on));throw new IllegalStateException("partial preparation");}
            publish(flags);return true;
        });
        check(pair.phase()==PairedMeasurement.Phase.FAILED&&!pair.restorationPending()&&OptimizationConfig.snapshot().equals(original),"initial same-state preparation exception still restores partial mutation");

        pair=new PairedMeasurement(SELECTED,1,2,()->Map.of("scene","fixed"),flags->{
            publish(flags);if(!flags.equals(original))throw new IllegalStateException("opposite-state source preparation failed");return true;
        });
        collect(pair,10);pair.advance();
        check(pair.phase()==PairedMeasurement.Phase.FAILED&&!pair.restorationPending()&&OptimizationConfig.snapshot().equals(original),"middle Apply exception restores exact initial state");

        var finalFailure=new AtomicBoolean(false);
        pair=new PairedMeasurement(SELECTED,1,2,()->Map.of("scene","fixed"),flags->{
            if(finalFailure.get())return false;publish(flags);return true;
        });
        collect(pair,10);pair.advance();collect(pair,20);pair.advance();collect(pair,12);
        finalFailure.set(true);pair.advance();
        check(pair.phase()==PairedMeasurement.Phase.FAILED&&pair.restorationPending(),"final same-state verification refusal stays failed with retained recovery");
        finalFailure.set(false);check(pair.retryRestore()&&!pair.restorationPending()&&OptimizationConfig.snapshot().equals(original),"final verification failure retries source/flags Apply even when flags already original");

        pair=new PairedMeasurement(SELECTED,1,2,()->Map.of("scene","fixed"),apply);
        collect(pair,10);pair.advance();collect(pair,20);pair.advance();
        publish(original.with(SELECTED,!on));pair.validate();
        check(pair.phase()==PairedMeasurement.Phase.FAILED&&OptimizationConfig.snapshot().equals(original),"RETURN rejects the wrong actual selected state");
    }
    public static void main(String[] args)throws Exception {
        var previous=OptimizationConfig.state();var directory=Files.createTempDirectory("paired-orientation-");
        try {
            OptimizationConfig.restoreState(new OptimizationConfig.State(OptimizationSnapshot.noneRequested(),null,false,false));
            OptimizationConfig.enableShaderProfiles();OptimizationConfig.load(directory.resolve("flags.properties"));OptimizationConfig.selectFamily("complementary-unbound");
            for(boolean on:List.of(false,true)){completeBracket(directory,on);cancelAndFailures(on);}
            System.out.println("PASS "+checks+" bracket orientation/export/restoration checks; no client or GPU");
        }finally {
            OptimizationConfig.restoreState(previous);
            try(var paths=Files.walk(directory)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
        }
    }
}
