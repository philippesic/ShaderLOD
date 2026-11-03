package com.pp.shaderlod.measurement;

import java.util.*;
import java.util.function.Function;

/** Shared post-capture observations for normal, neutral and thin-controller exports. */
public final class CpuMeasurementReport {
    private CpuMeasurementReport() {}
    public static Map<String,String> observations(CpuWindowMeasurement.Result window,List<FrameMeasurement.Sample> samples) {
        var out=new TreeMap<String,String>();
        out.put("cpu_window.scope", "Whole main/render-thread loop from first eligible render start to pause/stop; includes intervening ticks and caller CPU; process includes all JVM threads; excludes descheduled waiting");
        out.put("cpu_window.resolution", "Uncalibrated; nanosecond storage does not establish effective resolution or confidence");
        if(window==null) {
            out.put("cpu_window.threadStatus","unavailable: recording off or no eligible render");
            out.put("cpu_window.processStatus","unavailable: recording off or no eligible render");
        } else {
            out.put("cpu_window.completedFrames",Long.toString(window.completedFrames()));
            out.put("cpu_window.elapsedWallNs",Long.toString(window.elapsedWallNs()));
            out.put("cpu_window.threadStatus",window.threadStatus());out.put("cpu_window.processStatus",window.processStatus());
            put(out,"threadCpuNs",window.threadCpuNs());put(out,"processCpuNs",window.processCpuNs());
            put(out,"threadCpuMsPerFrame",window.threadCpuMsPerFrame());put(out,"processCpuMsPerFrame",window.processCpuMsPerFrame());
            endpoint(out,"start",window.start());endpoint(out,"end",window.end());
        }
        diagnostic(out,"render_cpu",samples,FrameMeasurement.Sample::renderCpuNs);
        diagnostic(out,"render_scope_process_cpu",samples,FrameMeasurement.Sample::processCpuNs);
        return Collections.unmodifiableMap(out);
    }
    private static void put(Map<String,String> out,String key,Object value){out.put("cpu_window."+key,value==null?"unavailable":value.toString());}
    private static void endpoint(Map<String,String> out,String name,CpuWindowMeasurement.Snapshot s){
        put(out,name+".threadId",s.threadId());put(out,name+".wallNs",s.wallNs());
        put(out,name+".threadCpuNs",s.threadCpuNs());put(out,name+".processCpuNs",s.processCpuNs());
    }
    private static void diagnostic(Map<String,String> out,String key,List<FrameMeasurement.Sample> samples,Function<FrameMeasurement.Sample,Long> value){
        var values=samples.stream().map(value).filter(Objects::nonNull).filter(v->v>=0).toList();
        long zeros=values.stream().filter(v->v==0).count();
        out.put(key+".scope","GameRenderer HEAD/RETURN only; distinct from whole-loop cumulative CPU and render elapsed wall time");
        out.put(key+".zeroSamples",Long.toString(zeros));out.put(key+".availableSamples",Integer.toString(values.size()));
        out.put(key+".distinctPositiveDeltas",Long.toString(values.stream().filter(v->v>0).distinct().count()));
        out.put(key+".minimumPositiveDeltaNs",values.stream().filter(v->v>0).min(Long::compare).map(Object::toString).orElse("unavailable"));
        out.put(key+".status",values.isEmpty()?"unavailable":zeros==values.size()?"counter did not advance in any recorded render scope; inadequate observed per-render resolution":zeros*2>=values.size()?"observed coarse/zero-heavy deltas; inadequate per-render precision; quantization possible; resolution uncalibrated":"diagnostic only; effective resolution uncalibrated");
    }
}
