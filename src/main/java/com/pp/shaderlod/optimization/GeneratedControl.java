package com.pp.shaderlod.optimization;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import com.pp.shaderlod.packs.PackSelectionService;
import java.util.Map;
import net.irisshaders.iris.Iris;

/** Untimed developer controls; ordinary recipe verification and recovery remain authoritative. */
public final class GeneratedControl {
    private GeneratedControl() {}
    private static Object heldPack,heldPipeline;
    private static boolean cloudHold;
    /** Developer-only control is bound to the exact successfully selected all-Off context. */
    public static void holdCurrentSelection() {
        cloudHold=false;
        heldPack=Iris.getCurrentPack().orElse(null);heldPipeline=Iris.getPipelineManager().getPipelineNullable();
    }
    public static boolean holdsCurrentSelection() {
        if(heldPack==null)return false;
        var core=OptimizationRuntimeContext.core();
        if(core!=null&&core.settings().enabled()&&core.preset()==LodPreset.PERFORMANCE
                &&(OptimizationConfig.snapshot().equals(OptimizationSnapshot.noneRequested())
                    ||cloudHold&&cloudOnly(OptimizationConfig.snapshot()))
                &&Iris.getShaderPackOptionQueue().isEmpty()
                &&heldPack==Iris.getCurrentPack().orElse(null)&&heldPipeline==Iris.getPipelineManager().getPipelineNullable())return true;
        heldPack=null;heldPipeline=null;cloudHold=false;return false;
    }
    static boolean cloudOnly(OptimizationSnapshot flags) {
        for(var id:OptimizationId.values())if(id!=OptimizationId.CLOUD_PRIMARY_BUDGET&&flags.enabled(id))return false;
        return true;
    }
    private static Map<String,String> fullOptions(net.irisshaders.iris.shaderpack.ShaderPack pack) {
        var result=new java.util.TreeMap<String,String>();var options=pack.getShaderPackOptions();var values=options.getOptionValues();
        options.getOptionSet().getBooleanOptions().keySet().forEach(key->result.put(key,Boolean.toString(values.getBooleanValueOrDefault(key))));
        options.getOptionSet().getStringOptions().keySet().forEach(key->result.put(key,values.getStringValueOrDefault(key)));
        return Map.copyOf(result);
    }
    /** Dev caller only. Normal Apply still owns persistence, live-uniform eligibility and recovery.
     * iris(false) disables nativeRendererGraph; PERFORMANCE also excludes nativeQualityGraph.
     * The real selection service must therefore verify/reuse the generated archive without reload.
     */
    public static CompileOptionControl.Session openMakeUpCloud(OptimizationSnapshot requested)throws Exception {
        OptimizationRuntimeContext.requireApplySupported();
        var core=OptimizationRuntimeContext.core();var previous=OptimizationConfig.snapshot();
        var platform=net.irisshaders.iris.platform.IrisPlatformHelpers.getInstance();
        var client=net.minecraft.client.Minecraft.getInstance();
        if(core==null||!core.settings().enabled()||core.preset()!=LodPreset.PERFORMANCE
                ||!"makeup-ultra-fast-shaders".equals(OptimizationConfig.activeFamily())
                ||client.level==null||client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD
                ||platform.isModLoaded("distanthorizons")||platform.isModLoaded("voxy")
                ||Boolean.getBoolean(com.pp.shaderlod.integration.RasterExperimentSelection.OPT_IN)
                ||!cloudOnly(previous)||!cloudOnly(requested)
                ||!OptimizationEditTransaction.liveUniformChangesAllowed(previous,requested))
            throw new IllegalStateException("Same-program cloud requires live-uniform MakeUp Performance Overworld, no DH/VOXY and cloud-only flags");
        var queue=Iris.getShaderPackOptionQueue();
        if(!queue.isEmpty()||Iris.isFallback())throw new IllegalStateException("Pending options or fallback");
        String selected=Iris.getIrisConfig().getShaderPackName().orElse(null);
        var pair=PackSelectionService.planSelection(Iris.getShaderpacksDirectory(),selected,true,LodPreset.PERFORMANCE);
        if(pair==null||selected==null||!selected.equals(pair.generatedFile())||!selected.equals(Iris.getCurrentPackName()))
            throw new IllegalStateException("Same-program cloud requires the selected generated archive");
        var pack=Iris.getCurrentPack().orElseThrow();var pipeline=Iris.getPipelineManager().getPipelineNullable();
        if(pipeline==null)throw new IllegalStateException("No current pipeline");
        var options=pack.getShaderPackOptions();var values=options.getOptionValues();
        var effective=fullOptions(pack);
        for(String key:java.util.List.of("SHADERLOD_MAKEUP_AO_HALF_RES","SHADERLOD_MAKEUP_DEPTH_PRIMING",
                "SHADERLOD_MAKEUP_SPATIAL_REUSE","SHADERLOD_MAKEUP_SPATIAL_RATE","SHADERLOD_MAKEUP_WORLD_PIXEL","SHADERLOD_MAKEUP_EARLY_ALPHA","SHADERLOD_MAKEUP_DEAD_FRAME_COPY"))
            if(!options.getOptionSet().getStringOptions().containsKey(key)||!"0".equals(values.getStringValueOrDefault(key)))
                throw new IllegalStateException("Private compile control is not zero: "+key);
        var selection=CompileOptionControl.openSelection(queue,Map.of(),()->PackSelectionTransaction.iris(false));
        var delegate=CompileOptionControl.session(selection,core,requested,false,queue,Map.of(),false);
        return sameProgramSession(delegate,()->{
            selection.verifyPreparedSelection();
            if(pack!=Iris.getCurrentPack().orElse(null)||pipeline!=Iris.getPipelineManager().getPipelineNullable()
                    ||!selected.equals(Iris.getCurrentPackName())||!queue.isEmpty()||Iris.isFallback()
                    ||!effective.equals(fullOptions(pack)))
                throw new java.io.IOException("Same-program cloud selection changed");
        },()->{heldPack=pack;heldPipeline=pipeline;cloudHold=true;});
    }
    @FunctionalInterface interface Verification {void run()throws Exception;}
    /** Bounded wrapper around the real compile/selection session; no replacement transaction. */
    static CompileOptionControl.Session sameProgramSession(CompileOptionControl.Session delegate,Verification verify,Runnable hold) {
        return new CompileOptionControl.Session(){
            boolean verified;
            public boolean prepare()throws Exception {
                if(delegate.prepare())throw new IllegalStateException("Same-program cloud refused required reload");
                verify.run();verified=true;return false;
            }
            public void reload(OptimizationEditTransaction.Reload reload){throw new IllegalStateException("Same-program cloud forbids reload");}
            public void restore()throws Exception {verified=false;delegate.restore();}
            public void recoveryReload(OptimizationEditTransaction.Reload reload)throws Exception {delegate.recoveryReload(reload);}
            public void finish(){
                if(verified)try{verify.run();}catch(Exception failure){throw new IllegalStateException(failure);}
                delegate.finish();
                if(verified)hold.run();
            }
            public void releaseRecovery(){delegate.releaseRecovery();}
        };
    }
    public static CompileOptionControl.Session open(OptimizationSnapshot requested) throws Exception {
        OptimizationRuntimeContext.requireApplySupported();
        var core=OptimizationRuntimeContext.core();
        if(core==null||!core.settings().enabled()||core.preset()!=LodPreset.PERFORMANCE)
            throw new IllegalStateException("Generated control requires enabled Performance");
        if(!requested.equals(OptimizationSnapshot.noneRequested())
                ||!OptimizationConfig.snapshot().equals(OptimizationSnapshot.noneRequested()))
            throw new IllegalStateException("Generated control requires all current/requested flags Off");
        var queue=Iris.getShaderPackOptionQueue();
        if(!queue.isEmpty())throw new IllegalStateException("Native shader options are pending");
        String selected=Iris.getIrisConfig().getShaderPackName().orElse(null);
        var pair=PackSelectionService.planSelection(Iris.getShaderpacksDirectory(),selected,false,LodPreset.QUALITY);
        if(pair==null||!selected.equals(pair.originalFile()))
            throw new IllegalStateException("Generated control must begin with the actual original pack");
        // This controls graph selection only. Normal recipe verification and transaction recovery remain.
        var selection=CompileOptionControl.openSelection(queue,Map.of(),()->PackSelectionTransaction.iris(false));
        return CompileOptionControl.session(selection,core,requested,false,queue,Map.of(),false);
    }
}
