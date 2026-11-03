package com.pp.shaderlod.optimization.framecopy;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.optimization.mips.BslLeanGraph;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility;
import java.util.Map;
import java.util.Set;

/** Singleton copy graph: native quality with unrelated refinement code compiled out. */
public final class MakeUpLeanGraph {
    private MakeUpLeanGraph() {}
    public static boolean onlyDeadCopy(OptimizationSnapshot flags) {
        if(com.pp.shaderlod.optimization.makeupexact.MakeUpCombinationGraph.selected(flags,OptimizationId.DEAD_FRAME_COPY))return true;
        if(flags==null)return false;
        for(var id:OptimizationId.values())if(flags.enabled(id)!=(id==OptimizationId.DEAD_FRAME_COPY))return false;
        return true;
    }
    public static boolean eligible(BslLeanGraph.Route route,boolean enabled,LodPreset preset) {
        return enabled&&preset==LodPreset.PERFORMANCE&&route.loaded()&&route.exactCompiler()
            &&route.overworld()&&"makeup-ultra-fast-shaders".equals(route.family())&&onlyDeadCopy(route.requests());
    }
    /** The producer already verifies the current owned graph before choosing its next mode. */
    public static boolean transitionProfile(Map<String,String> actual) {
        if(actual==null)return false;
        if("0".equals(actual.get(BslLeanGraph.ADAPTER)))return profile(actual);
        if(!"1".equals(actual.get(BslLeanGraph.ADAPTER)))return false;
        var next=new java.util.HashMap<>(actual);next.put(BslLeanGraph.ADAPTER,"0");
        return profile(next);
    }
    /** No admission cache: examine the current full map and reject extra or mutated options. */
    public static boolean profile(Map<String,String> actual) {
        actual=com.pp.shaderlod.optimization.makeupexact.MakeUpCombinationGraph.copyProfile(actual);
        if(actual==null||!"0".equals(actual.get(BslLeanGraph.ADAPTER)))return false;
        var nativeValues=MakeUpQuadEligibility.nativeOptions();
        if(nativeValues.size()!=100)return false;
        var peers=Set.of(CompileOptionControl.MAKEUP_AO_HALF,CompileOptionControl.MAKEUP_DEPTH,
            CompileOptionControl.MAKEUP_EARLY_ALPHA,CompileOptionControl.MAKEUP_TRANSLUCENT_FASTPATH,CompileOptionControl.MAKEUP_AO_ATTENUATION,CompileOptionControl.MAKEUP_SPATIAL_REUSE,
            CompileOptionControl.MAKEUP_SPATIAL_RATE,CompileOptionControl.MAKEUP_WORLD_PIXEL);
        var markers=Set.of("SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","SHADERLOD_REFINEMENT_BUDGET_INCLUDED",
            "SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","SHADERLOD_OPTIMIZATION_FLAGS_INCLUDED");
        for(var entry:actual.entrySet()) {
            String key=entry.getKey(),value=entry.getValue();
            if(nativeValues.containsKey(key)){if(!nativeValues.get(key).equals(value))return false;}
            else if(key.equals(BslLeanGraph.ADAPTER)||peers.contains(key)){if(!"0".equals(value))return false;}
            else if(key.equals(CompileOptionControl.MAKEUP_DEAD_FRAME_COPY)){if(!Set.of("0","1").contains(value))return false;}
            else if(markers.contains(key)){if(!"true".equals(value))return false;}
            else return false;
        }
        return actual.keySet().containsAll(nativeValues.keySet())
            &&actual.keySet().containsAll(peers.stream().filter(k->!Set.of(CompileOptionControl.MAKEUP_TRANSLUCENT_FASTPATH,CompileOptionControl.MAKEUP_AO_ATTENUATION).contains(k)).toList())&&actual.keySet().containsAll(markers)
            &&actual.containsKey(CompileOptionControl.MAKEUP_DEAD_FRAME_COPY);
    }
}
