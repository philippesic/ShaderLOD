package com.pp.shaderlod.optimization.makeupearlyalpha;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.optimization.mips.BslLeanGraph;
import com.pp.shaderlod.optimization.framecopy.MakeUpLeanGraph;
import java.util.*;
import net.irisshaders.iris.helpers.StringPair;

/** Exact singleton early-alpha route. Other MakeUp consumers keep their own contracts. */
public final class MakeUpEarlyAlphaLeanGraph {
    public static final String DEFINE="SHADERLOD_EARLY_ALPHA_RUNTIME_GUARD";
    private MakeUpEarlyAlphaLeanGraph() {}
    public static boolean onlyEarlyAlpha(OptimizationSnapshot flags) {
        if(com.pp.shaderlod.optimization.makeupexact.MakeUpCombinationGraph.selected(flags,OptimizationId.EARLY_ALPHA))return true;
        if(flags==null)return false;
        for(var id:OptimizationId.values())if(flags.enabled(id)!=(id==OptimizationId.EARLY_ALPHA))return false;
        return true;
    }
    public static boolean eligible(BslLeanGraph.Route route,boolean enabled,LodPreset preset) {
        return enabled&&preset==LodPreset.PERFORMANCE&&route.loaded()&&route.exactCompiler()
            &&route.overworld()&&"makeup-ultra-fast-shaders".equals(route.family())&&onlyEarlyAlpha(route.requests());
    }
    public static boolean profile(Map<String,String> actual) {
        if(actual==null||!Set.of("0","1").contains(actual.getOrDefault(CompileOptionControl.MAKEUP_EARLY_ALPHA,"invalid"))
                ||!("0".equals(actual.get(CompileOptionControl.MAKEUP_DEAD_FRAME_COPY))
                    ||com.pp.shaderlod.optimization.makeupexact.MakeUpCombinationGraph.enabled()&&"1".equals(actual.get(CompileOptionControl.MAKEUP_DEAD_FRAME_COPY))))return false;
        var normalized=new TreeMap<>(actual);normalized.put(CompileOptionControl.MAKEUP_EARLY_ALPHA,"0");
        return MakeUpLeanGraph.profile(normalized);
    }
    public static boolean transitionProfile(Map<String,String> actual) {
        if(actual==null||!Set.of("0","1").contains(actual.getOrDefault(BslLeanGraph.ADAPTER,"invalid")))return false;
        var normalized=new TreeMap<>(actual);normalized.put(BslLeanGraph.ADAPTER,"0");return profile(normalized);
    }
    /** Constructor-only capability; spoofed incoming definitions cannot authorize the path. */
    public static com.google.common.collect.ImmutableList<StringPair> constructorDefines(List<StringPair> input,boolean exactApi) {
        var result=new ArrayList<StringPair>(input.size()+1);
        for(var define:input)if(!DEFINE.equals(define.key()))result.add(define);
        if(exactApi)result.add(new StringPair(DEFINE,"1"));
        return com.google.common.collect.ImmutableList.copyOf(result);
    }
}
