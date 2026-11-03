package com.pp.shaderlod.measurement;
import com.pp.shaderlod.optimization.*;
import java.util.*;
import java.util.function.Supplier;
/** Finite original/opposite/original bracket. Changes one flag only; each phase warms anew. */
public final class PairedMeasurement {
    public enum Phase { OFF, ON, RETURN, COMPLETE, FAILED }
    public interface Apply { boolean apply(OptimizationSnapshot flags); }
    public record Row(Phase phase,FrameMeasurement.Sample sample) {}
    private final OptimizationId selected; private final OptimizationSnapshot original; private final String originalFamily;
    private final Phase baselinePhase;
    private final Supplier<Map<String,String>> pins; private Map<String,String> fixed=Map.of();
    private final Apply apply; private final int warmup,sampleCount;
    private final List<Row> rows=new ArrayList<>();
    private final Map<Phase,Map<String,String>> phasePins=new EnumMap<>(Phase.class);
    private Phase phase;private int skipped,collected;private String failure="";
    private boolean started,restorePending=true;private Throwable primaryFailure;
    public PairedMeasurement(OptimizationId selected,int warmup,int sampleCount,Supplier<Map<String,String>> pins,Apply apply) {
        this(selected,warmup,sampleCount,pins,apply,true);
    }
    private PairedMeasurement(OptimizationId selected,int warmup,int sampleCount,Supplier<Map<String,String>> pins,Apply apply,boolean start) {
        if(warmup<1||sampleCount<2)throw new IllegalArgumentException("Require warmup>=1 and samples>=2");
        this.selected=Objects.requireNonNull(selected);this.warmup=warmup;this.sampleCount=sampleCount;
        this.pins=Objects.requireNonNull(pins);this.apply=Objects.requireNonNull(apply);original=OptimizationConfig.snapshot();originalFamily=OptimizationConfig.activeFamily();
        baselinePhase=original.enabled(selected)?Phase.ON:Phase.OFF;phase=baselinePhase;
        requireIndependentSelection(originalFamily,selected,original);
        if(start)start();
    }
    /** Pure refusal before scene freeze, shader preparation, request publication or callback ownership. */
    public static void requireIndependentSelection(String family,OptimizationId selected,OptimizationSnapshot flags) {
        Objects.requireNonNull(selected);Objects.requireNonNull(flags);
        if(!Set.of("photon","bliss-shader").contains(family))return;
        OptimizationId other=switch(selected){
            case SPATIAL_LIGHTING_REUSE->OptimizationId.SPATIAL_LIGHTING_RATE;
            case SPATIAL_LIGHTING_RATE->OptimizationId.SPATIAL_LIGHTING_REUSE;
            default->null;
        };
        if(other!=null&&flags.enabled(other))throw new IllegalStateException("Turn "+other.key()+" Off before comparing "+selected.key()+"; these lighting modes are mutually exclusive and every other request must stay fixed");
    }
    /** Runtime owns the object before the first callback, retaining a failed restoration for retry. */
    public static PairedMeasurement prepare(OptimizationId selected,int warmup,int sampleCount,Supplier<Map<String,String>> pins,Apply apply) {
        return new PairedMeasurement(selected,warmup,sampleCount,pins,apply,false);
    }
    public void start() {
        if(started)throw new IllegalStateException("Paired measurement already started");started=true;
        final boolean applied;
        try {
            var observed=new TreeMap<>(pins.get());observed.remove("controlled_option_values");observed.remove("backend_implementation");
            observed.put("other_optimization_flags",original.with(selected,false).flags().toString());fixed=Map.copyOf(observed);
            applied=apply.apply(original);
        }catch(Throwable error) {fail(baselinePhase+" preparation failed: "+describe(error),error);return;}
        if(!applied)fail(baselinePhase+" apply failed");else validate();
    }
    public boolean restorationPending(){return restorePending;}
    public boolean retryRestore(){if(!restorePending)return true;restoreOriginal(null);return !restorePending;}
    static Throwable append(Throwable primary,Throwable extra){
        if(primary==null)return extra;if(extra!=primary)try{
            for(var seen:primary.getSuppressed())if(seen==extra)return primary;
            primary.addSuppressed(extra);
        }catch(Throwable ignored){}return primary;
    }
    @SuppressWarnings("unchecked") static <T extends Throwable> void throwUnchecked(Throwable error)throws T{throw (T)error;}
    static String describe(Throwable error){return error.getClass().getSimpleName()+": "+error.getMessage();}
    public boolean readyToAdvance() {return active()&&collected>=sampleCount;}
    public Phase phase() {return phase;}
    public boolean active() {return started&&(phase==Phase.OFF||phase==Phase.ON||phase==Phase.RETURN);}
    public String status() {return phase+" warmup="+skipped+"/"+warmup+" samples="+collected+"/"+sampleCount+(failure.isEmpty()?"":" "+failure);}
    public List<Row> rows() {return List.copyOf(rows);}
    public Map<Phase,Map<String,String>> phasePins() {return Map.copyOf(phasePins);}
    public Map<String,String> fixedPins() {return fixed;}
    public OptimizationId selected() {return selected;}
    /** The selected flag state shared by the first and RETURN sampling windows. */
    public Phase baselinePhase() {return baselinePhase;}
    private boolean selectedOn() {return phase==Phase.RETURN?baselinePhase==Phase.ON:phase==Phase.ON;}
    public void validate() {
        if(!active())return;
        final Map<String,String> observed;
        try {
            var raw=Map.copyOf(pins.get());phasePins.put(phase,raw);observed=new TreeMap<>(raw);observed.remove("controlled_option_values");observed.remove("backend_implementation");observed.put("other_optimization_flags",original.with(selected,false).flags().toString());
        }catch(Throwable error) {fail("pin observation failed: "+describe(error),error);return;}
        if(!fixed.equals(observed)) {fail("settings/source/hardware/scene pins changed");return;}
        if(!OptimizationConfig.snapshot().equals(original.with(selected,selectedOn())))fail("another optimization flag changed");
    }
    public void accept(FrameMeasurement.Sample sample) {
        if(!active()||collected>=sampleCount)return;
        if(skipped++<warmup)return;
        if(sample.frameWallNs()==null)return; // first frame after reload has no interval
        rows.add(new Row(phase,sample));collected++;
    }
    /** Caller invokes between frames after all async queries for this phase have drained. */
    public boolean advance() {
        if(!active()||collected<sampleCount)return false;
        validate();if(!active())return false;
        if(phase==Phase.RETURN) {
            final boolean restored;
            try{restored=apply.apply(original);}catch(Throwable error){fail("final restoration failed: "+describe(error),error);return true;}
            if(!restored||!OptimizationConfig.snapshot().equals(original)){fail("final restoration failed");return true;}
            restorePending=false;phase=Phase.COMPLETE;return true;
        }
        phase=phase==baselinePhase?(baselinePhase==Phase.ON?Phase.OFF:Phase.ON):Phase.RETURN;skipped=0;collected=0;
        final boolean applied;
        try{applied=apply.apply(original.with(selected,selectedOn()));}
        catch(Throwable error){fail("phase apply failed: "+describe(error),error);return true;}
        if(!applied)fail("phase apply failed");else validate();return true;
    }
    public void abort(String reason) {if(active())fail(reason);else if(restorePending)retryRestore();}
    public void cancel() {if(!started){started=true;fail("cancelled before start");}else if(active())fail("cancelled");else if(restorePending)retryRestore();}
    private void fail(String reason) {fail(reason,null);}
    private void fail(String reason,Throwable primary) {
        phase=Phase.FAILED;failure=reason;primaryFailure=primary;
        Throwable recovery=restoreOriginal(primary);
        if(primary instanceof Error || recovery instanceof Error)PairedMeasurement.throwUnchecked(primary==null?recovery:primary);
    }
    private Throwable restoreOriginal(Throwable primary) {
        restorePending=true;Throwable recovery=null;
        try {
            if(apply.apply(original)&&OptimizationConfig.snapshot(originalFamily).equals(original))restorePending=false;
            else recovery=new IllegalStateException("original flags/source restoration refused");
        }catch(Throwable error){recovery=error;}
        if(recovery!=null){failure+="; original flags restoration failed: "+describe(recovery);if(primary!=null)append(primary,recovery);else if(primaryFailure!=null)append(primaryFailure,recovery);}
        if(primary==null&&recovery instanceof Error)PairedMeasurement.throwUnchecked(recovery);
        return recovery;
    }
    public Map<String,Double> summary() {
        var result=new LinkedHashMap<String,Double>();
        for(String metric:List.of("frame_wall","render_wall","render_cpu","process_cpu","gpu")) {
            var off=mean(Phase.OFF,metric);var on=mean(Phase.ON,metric);var returned=mean(Phase.RETURN,metric);
            result.put(metric+"_off_valid_samples",(double)count(Phase.OFF,metric));
            result.put(metric+"_on_valid_samples",(double)count(Phase.ON,metric));
            result.put(metric+"_return_valid_samples",(double)count(Phase.RETURN,metric));
            if(off!=null)result.put(metric+"_off_ms",off);
            if(on!=null)result.put(metric+"_on_ms",on);
            if(returned!=null)result.put(metric+"_return_ms",returned);
            if(off!=null&&on!=null)result.put(metric+"_delta_ms",on-off);
            var baseline=baselinePhase==Phase.ON?on:off;
            if(baseline!=null&&returned!=null)result.put(metric+"_baseline_drift_ms",returned-baseline);
        }
        var off=mean(Phase.OFF,"frame_wall");var on=mean(Phase.ON,"frame_wall");
        if(off!=null&&off>0)result.put("off_fps",1000/off);if(on!=null&&on>0)result.put("on_fps",1000/on);
        return Collections.unmodifiableMap(result);
    }
    private int count(Phase phase,String metric) {
        int count=0;for(var row:rows)if(row.phase()==phase) {
            var s=row.sample();Long value=switch(metric) {
                case "frame_wall"->s.frameWallNs();case "render_wall"->s.renderWallNs();case "render_cpu"->s.renderCpuNs();
                case "process_cpu"->s.processCpuNs();case "gpu"->s.gpuNs();default->null;
            };if(value!=null)count++;
        }return count;
    }
    private Double mean(Phase phase,String metric) {
        double total=0;int count=0;
        for(var row:rows)if(row.phase()==phase) {
            var s=row.sample();Long value=switch(metric) {
                case "frame_wall"->s.frameWallNs();case "render_wall"->s.renderWallNs();case "render_cpu"->s.renderCpuNs();
                case "process_cpu"->s.processCpuNs();case "gpu"->s.gpuNs();default->null;
            };
            if(value!=null) {total+=value;count++;}
        }
        return count==0?null:total/count/1_000_000.0;
    }
}
