package com.pp.shaderlod.optimization.mips;

import com.pp.shaderlod.*;
import com.pp.shaderlod.optimization.*;
import java.util.Map;

/** Immutable Apply input. Legacy service callers never infer an owned lean mode. */
public final class BslLeanGraph {
    public static final String ADAPTER = "SHADERLOD_REFINEMENT_ADAPTER";
    public static final Map<String,String> OWNED = Map.of(ADAPTER,"0");
    public record Route(OptimizationSnapshot requests, boolean loaded, String family,
            boolean exactCompiler, boolean overworld) {
        public boolean eligible(boolean enabled,LodPreset preset) {
            return enabled && preset != LodPreset.QUALITY && loaded && java.util.Set.of("bsl-shaders","solas-shader","astralex").contains(family)
                && exactCompiler && overworld && onlyDeadMip(requests);
        }
    }
    public static final Route LEGACY = new Route(OptimizationSnapshot.noneRequested(),false,"global",false,false);
    private BslLeanGraph() {}
    public static boolean onlyDeadMip(OptimizationSnapshot requests) {
        if(requests==null)return false;
        for(var id:OptimizationId.values())if(requests.enabled(id)!=(id==OptimizationId.DEAD_MIP_CHAIN))return false;
        return true;
    }
    public static Route capture(OptimizationSnapshot requests,boolean overworld) {
        boolean loaded=OptimizationConfig.loaded();String family=OptimizationConfig.activeFamily();
        if(loaded&&"makeup-ultra-fast-shaders".equals(family)&&overworld
                &&(com.pp.shaderlod.optimization.framecopy.MakeUpLeanGraph.onlyDeadCopy(requests)
                    ||com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.onlyEarlyAlpha(requests)
                    ||com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph.singleton(requests))) {
            boolean exact=false;
            try {exact=(com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.onlyEarlyAlpha(requests)
                    ?com.pp.shaderlod.optimization.makeupearlyalpha.mixin.MakeUpEarlyAlphaPlugin.exactApi()
                    :com.pp.shaderlod.optimization.framecopy.FrameCopyPins.exactApi())
                &&!net.neoforged.fml.ModList.get().isLoaded("distanthorizons")
                &&!net.neoforged.fml.ModList.get().isLoaded("voxy");}
            catch(RuntimeException|LinkageError unavailable) { }
            return new Route(requests,loaded,family,exact,overworld);
        }
        if(!loaded||!java.util.Set.of("bsl-shaders","solas-shader","astralex").contains(family)||!overworld||!onlyDeadMip(requests))
            return new Route(requests,loaded,family,false,overworld);
        boolean exact=false;
        try {exact=!net.neoforged.fml.ModList.get().isLoaded("distanthorizons") && !net.neoforged.fml.ModList.get().isLoaded("voxy") && BslMipWitnessPins.exactApi() && DeadMipAdmission.bslCompiler(DeadMipAdmission.apiTuple(),
            net.irisshaders.iris.gl.shader.StandardMacros.getMcVersion(),
            net.irisshaders.iris.gl.shader.StandardMacros.getFormattedIrisVersion());}
        catch(RuntimeException|LinkageError unavailable) { }
        return new Route(requests,loaded,family,exact,overworld);
    }
    public static boolean profile(Map<String,String> actual) {return DeadMipAdmission.expectedLeanOptions(actual);}
    /** Native384 is projected from the actual parsed map; no sparse sidecar or normalization. */
    public static boolean nativeProfile(Map<String,String> actual) {
        return com.pp.shaderlod.integration.bslquad.BslQuadEligibility.optionsMatch(actual)
            || com.pp.shaderlod.optimization.bslprefix.BslPrefixProfile.nativeSource(actual);
    }
}
