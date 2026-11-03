package com.pp.shaderlod.dev;

import java.util.Map;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import com.pp.shaderlod.optimization.*;
import net.irisshaders.iris.Iris;

/** Bounded untimed observation; never changes an admission result or shader input. */
final class CpuAdmissionCostProbe {
    private static long startFrame,targetFrame,generation,completed;
    private static Object pipeline,pack;
    private static Map<String,Long> cacheBefore=Map.of(),cacheAfter=Map.of();
    private static String error="";
    private static boolean active;
    static boolean active(){return active;}
    private static boolean diagnosticRequests(OptimizationSnapshot flags){
        // The product's reload-free predicate deliberately excludes scalar caching.
        // This read-only diagnostic follows ordinary Apply and admits exactly the scored triple.
        var allowed=java.util.Set.of(OptimizationId.QUAD_COMMAND_COMPACTION,OptimizationId.SHADOW_ADMISSION_CACHE,OptimizationId.INDEX_SCALAR_CACHE);
        for(var id:OptimizationId.values())if(flags.enabled(id)!=allowed.contains(id))return false;
        return true;
    }
    static void start(long frame,int frames){
        if(active||frames<16||frames>256||!MakeUpQuadRuntime.compactionRecordingRequested()
            ||!diagnosticRequests(OptimizationConfig.snapshot())||!MakeUpQuadRuntime.nativeGraphAvailable())
            throw new IllegalStateException("Bounded current native CPU compaction/cache context required");
        startFrame=frame;targetFrame=frame+frames;completed=0;error="";
        generation=com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation();
        pipeline=Iris.getPipelineManager().getPipelineNullable();pack=Iris.getCurrentPack().orElse(null);
        cacheBefore=MakeUpQuadRuntime.admissionCacheCounters();cacheAfter=Map.of();
        NativeAdmissionCost.observe(true);active=true;
    }
    static void stop(long frame){
        NativeAdmissionCost.observe(false);active=false;completed=frame-startFrame;
        cacheAfter=MakeUpQuadRuntime.admissionCacheCounters();
    }
    static void tick(long frame){
        if(!active)return;
        if(Iris.isFallback()||pipeline!=Iris.getPipelineManager().getPipelineNullable()
            ||pack!=Iris.getCurrentPack().orElse(null)||generation!=com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation()){
            error="Admission cost context changed";stop(frame);
        }else if(frame>=targetFrame||!NativeAdmissionCost.observing())stop(frame);
    }
    static Map<String,Object> status(){return Map.of("active",active,"error",error,"completed_render_frames",completed,
        "requested_render_frames",targetFrame-startFrame,"cache_before",cacheBefore,"cache_after",cacheAfter,"cost",NativeAdmissionCost.snapshot());}
    static Map<String,Object> modeTransitions(){
        if(active||!MakeUpQuadRuntime.compactionRecordingRequested()||!MakeUpQuadRuntime.nativeGraphAvailable())
            throw new IllegalStateException("Stopped admitted CPU context required for mode transition check");
        String dh=MakeUpQuadRuntime.DH_EXPERIMENT,photon=com.pp.shaderlod.packs.PackSelectionService.PHOTON_QUAD_ORIGINAL_GRAPH,
            research=com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES;
        String oldDh=System.getProperty(dh),oldPhoton=System.getProperty(photon),oldResearch=System.getProperty(research);
        var rows=new java.util.ArrayList<Map<String,Object>>();
        var result=new java.util.LinkedHashMap<String,Object>();
        try{
            for(boolean value:new boolean[]{false,true,false}){
                System.setProperty(dh,Boolean.toString(value));var before=MakeUpQuadRuntime.admissionCacheCounters();
                boolean allowed=MakeUpQuadRuntime.nativeGraphAvailable();var after=MakeUpQuadRuntime.admissionCacheCounters();
                rows.add(Map.of("dh_opt_in",value,"allowed",allowed,"before",before,"after",after));
            }
            var before=MakeUpQuadRuntime.admissionCacheCounters();
            System.setProperty(photon,Boolean.toString(!Boolean.parseBoolean(oldPhoton)));
            System.setProperty(research,Boolean.toString(!Boolean.parseBoolean(oldResearch)));
            boolean allowed=MakeUpQuadRuntime.nativeGraphAvailable();var after=MakeUpQuadRuntime.admissionCacheCounters();
            result.putAll(Map.of("dh_transitions",rows,"irrelevant_modes",Map.of("allowed",allowed,"before",before,"after",after),
                "dh_installed",net.neoforged.fml.ModList.get().isLoaded("distanthorizons"),
                "scope","Own-JVM flag transitions, no rendering between changes; non-DH context checks invalidation, not actual DH admission"));
        }finally{
            restore(dh,oldDh);restore(photon,oldPhoton);restore(research,oldResearch);
            result.put("properties_restored",java.util.Objects.equals(oldDh,System.getProperty(dh))
                &&java.util.Objects.equals(oldPhoton,System.getProperty(photon))&&java.util.Objects.equals(oldResearch,System.getProperty(research)));
            result.put("allowed_after_restore",MakeUpQuadRuntime.nativeGraphAvailable());
        }
        return Map.copyOf(result);
    }
    private static void restore(String key,String value){if(value==null)System.clearProperty(key);else System.setProperty(key,value);}
}
