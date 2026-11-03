package com.pp.shaderlod;
import com.pp.shaderlod.optimization.*;

import com.pp.shaderlod.measurement.MeasurementPins;
import com.pp.shaderlod.measurement.RuntimeMeasurement;
import net.irisshaders.iris.Iris;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 1.21.1 NeoForge selected-pack boundary; source recognition runs only on pack identity changes. */
public final class ShaderOptimizationProfiles {
    static {
        OptimizationRuntimeContext.configureImplementation(OptimizationId.SHADOW_PREFIX,
            ()->"Historical duplicate label: use pom_empty_height_prefix; no separate shadow-prefix algorithm");
        OptimizationRuntimeContext.configureImplementation(OptimizationId.PHYSICAL_RENDER_SCALE,
            ()->"Unavailable: original fixed-scale trial failed fine-detail quality; window resizing is diagnostic only");
        OptimizationRuntimeContext.configureImplementation(OptimizationId.SPATIAL_SHADOW_REUSE,
            ()->"Unavailable independently: existing spatial lighting field combines lighting with shadow/AO");
    }
    private static Object previous;
    private static final ReadyContext readyContext=new ReadyContext();
    private static String selectedFamily;
    private static String status="Awaiting selected shader";
    private ShaderOptimizationProfiles() {}
    /** A failed admission is retried only when an actual prepared context changes. */
    static final class ReadyContext {
        private Object world,pipeline,pack;
        boolean claim(Object nextWorld,Object nextPipeline,Object nextPack){
            if(nextWorld==null||nextPipeline==null||nextPack==null)return false;
            if(world==nextWorld&&pipeline==nextPipeline&&pack==nextPack)return false;
            world=nextWorld;pipeline=nextPipeline;pack=nextPack;return true;
        }
        void clear(){world=null;pipeline=null;pack=null;}
    }
    public static String status(){return status;}
    /** A singleton G01 route needs the real world before the normal selection transaction. */
    static boolean deadCopyProfile(String family,com.pp.shaderlod.CoreUniformFrame.Core core,OptimizationSnapshot flags){
        return "makeup-ultra-fast-shaders".equals(family)&&core!=null&&core.settings().enabled()
            &&core.preset()==LodPreset.PERFORMANCE
            &&com.pp.shaderlod.optimization.framecopy.MakeUpLeanGraph.onlyDeadCopy(flags);
    }
    /** G02's lean selection likewise requires its real world and pipeline certificate. */
    static boolean deadMipProfile(String family,com.pp.shaderlod.CoreUniformFrame.Core core,OptimizationSnapshot flags){
        return "bsl-shaders".equals(family)&&core!=null&&core.settings().enabled()
            &&core.preset()==LodPreset.PERFORMANCE
            &&com.pp.shaderlod.optimization.mips.BslLeanGraph.onlyDeadMip(flags);
    }
    /** The Asahi singleton G03 route also needs a ready-world lean selection. */
    static boolean postSampleAlphaProfile(String os,String arch,String family,
            com.pp.shaderlod.CoreUniformFrame.Core core,OptimizationSnapshot flags){
        return "Linux".equals(os)&&"aarch64".equals(arch)
            &&"makeup-ultra-fast-shaders".equals(family)&&core!=null&&core.settings().enabled()
            &&core.preset()==LodPreset.PERFORMANCE
            &&com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.onlyEarlyAlpha(flags);
    }
    static boolean postSampleAlphaOptions(java.util.Map<String,String> actual){
        return com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.optionsMatch(actual)
            ||com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.transitionProfile(actual);
    }
    /** A world-entry transaction must never normalize a user's edited native map. */
    static boolean deadMipNativeOptions(java.util.Map<String,String> actual){
        return deadMipNativeOptions(actual,com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions());
    }
    static boolean deadMipNativeOptions(java.util.Map<String,String> actual,java.util.Map<String,String> certified){
        if(actual==null||certified==null||certified.size()!=384)return false;
        int nativeCount=0;
        for(var entry:actual.entrySet()){
            String key=entry.getKey();
            if(key==null)return false;
            if(key.startsWith("SHADERLOD_"))continue;
            String expected=certified.get(key);
            if(expected==null||!expected.equals(entry.getValue()))return false;
            nativeCount++;
        }
        return nativeCount==384;
    }
    static boolean postSampleAlphaOptionsForAo(java.util.Map<String,String> actual){
        return com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.optionsMatch(actual)
            ||com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph.transitionProfile(actual);
    }
    public static void tick(ClientTickEvent.Post event) {refresh();}
    public static void refresh() {
        if(GeneratedControl.holdsCurrentSelection())return;
        // Defer profile/graph mutation while a measurement or its restoration owns the selection.
        var pair=RuntimeMeasurement.pair();
        if(com.pp.shaderlod.tuning.AutoTuner.active()||RuntimeMeasurement.requested()
                ||pair!=null&&(pair.active()||pair.restorationPending()))return;
        Object pack=Iris.getCurrentPack().orElse(null);
        if(pack==null){previous=null;selectedFamily=null;readyContext.clear();return;}
        try {
            boolean familyChanged=false;
            if(pack!=previous){
                String family=MeasurementPins.selectedFamily();
                if(family.equals("unavailable")){status="Selected shader identity unavailable; profile unchanged";return;}
                previous=pack;selectedFamily=family;
                familyChanged=!family.equals(OptimizationConfig.activeFamily())&&OptimizationConfig.selectFamily(family);
            }
            var core=OptimizationRuntimeContext.core();
            boolean copyProfile=deadCopyProfile(selectedFamily,core,OptimizationConfig.snapshot())
                &&!net.neoforged.fml.ModList.get().isLoaded("distanthorizons")
                &&!net.neoforged.fml.ModList.get().isLoaded("voxy");
            boolean mipProfile=deadMipProfile(selectedFamily,core,OptimizationConfig.snapshot())
                &&!net.neoforged.fml.ModList.get().isLoaded("voxy");
            boolean alphaProfile=postSampleAlphaProfile(System.getProperty("os.name"),System.getProperty("os.arch"),
                    selectedFamily,core,OptimizationConfig.snapshot())
                &&!net.neoforged.fml.ModList.get().isLoaded("distanthorizons")
                &&!net.neoforged.fml.ModList.get().isLoaded("voxy");
            boolean exactAoProfile="Linux".equals(System.getProperty("os.name"))&&"aarch64".equals(System.getProperty("os.arch"))
                &&"makeup-ultra-fast-shaders".equals(selectedFamily)&&core!=null&&core.settings().enabled()
                &&core.preset()==LodPreset.PERFORMANCE
                &&com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph.singleton(OptimizationConfig.snapshot())
                &&!net.neoforged.fml.ModList.get().isLoaded("distanthorizons")&&!net.neoforged.fml.ModList.get().isLoaded("voxy");
            boolean bslAlphaProfile=com.pp.shaderlod.optimization.bslearlyalpha.BslPostSampleAlpha.platform(System.getProperty("os.name"),System.getProperty("os.arch"))
                &&"bsl-shaders".equals(selectedFamily)
                &&(com.pp.shaderlod.optimization.bslearlyalpha.BslPostSampleAlpha.singleton(OptimizationConfig.snapshot())
                    ||com.pp.shaderlod.optimization.bslearlyalpha.BslPostSampleAlpha.hasOwnedPipeline());
            boolean cpuProfile=core!=null&&core.settings().enabled()&&core.preset()==LodPreset.PERFORMANCE
                &&OptimizationEditTransaction.cpuOnlyFamily(selectedFamily)
                // Graph routing permits more CPU/proof requests than reload-free editing.
                &&com.pp.shaderlod.packs.PackSelectionService.nativeRendererRequests(OptimizationConfig.snapshot());
            if(cpuProfile||copyProfile||mipProfile||alphaProfile||exactAoProfile||bslAlphaProfile){
                // At the menu the exact world/API/pipeline certificate cannot exist. Retain
                // selection until a new ready context can be checked once, not on every tick.
                var world=net.minecraft.client.Minecraft.getInstance().level;
                var pipeline=Iris.getPipelineManager().getPipelineNullable();
                if(world==null||pipeline==null){status="Shader profile awaiting a ready world/pipeline";return;}
                if(alphaProfile&&!world.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)){
                    status="G03 profile retained; world is outside its certified tuple";return;
                }
                if(mipProfile&&!deadMipNativeOptions(com.pp.shaderlod.optimization.mips.DeadMipAdmission.effective(Iris.getCurrentPack().orElse(null)))){
                    status="G02 profile retained; native option map is outside its certified tuple";return;
                }
                if(!readyContext.claim(world,pipeline,pack))return;
                if(bslAlphaProfile){
                    if(core!=null&&com.pp.shaderlod.optimization.bslearlyalpha.BslPostSampleAlpha.requiresReload(core.settings().enabled(),core.preset(),OptimizationConfig.snapshot())){
                        applyProfile(selectedFamily,true);
                        previous=Iris.getCurrentPack().orElse(null);
                        readyContext.claim(world,Iris.getPipelineManager().getPipelineNullable(),previous);
                    }
                    return;
                }
                // This full-map validation belongs to the context transition, never the steady tick.
                if(alphaProfile&&!postSampleAlphaOptions(
                        com.pp.shaderlod.optimization.mips.DeadMipAdmission.effective(Iris.getCurrentPack().orElse(null)))){
                    status="G03 profile retained; native option map is outside its certified tuple";return;
                }
                if(exactAoProfile&&(!world.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                    ||!postSampleAlphaOptionsForAo(com.pp.shaderlod.optimization.mips.DeadMipAdmission.effective(Iris.getCurrentPack().orElse(null))))) {
                    status="G04/G06 profile retained; context outside certified tuple";return;
                }
                if(cpuProfile&&!com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.nativeGraphAvailable()){
                    status="CPU shader profile retained; native source unavailable in this world/pipeline context";return;
                }
                boolean requireMipReload=mipProfile&&(!"1".equals(com.pp.shaderlod.optimization.mips.DeadMipAdmission.effective(Iris.getCurrentPack().orElse(null)).get(CompileOptionControl.BSL_DEAD_MIP))
                    ||!"bsl-shaders".equals(com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.qualifiedFamily()));
                applyProfile(selectedFamily,requireMipReload);
                // A successful owned reload changes pack/pipeline identity. Acknowledge that
                // exact result so our own transition cannot trigger an Apply loop.
                previous=Iris.getCurrentPack().orElse(null);
                readyContext.claim(net.minecraft.client.Minecraft.getInstance().level,Iris.getPipelineManager().getPipelineNullable(),previous);
            } else if(familyChanged)applyProfile(selectedFamily);
        }catch(RuntimeException failure){status="Shader profile selection failed: "+failure.getMessage();ShaderLod.LOGGER.warn(status,failure);}
    }
    static CompileOptionControl.Session readyReload(CompileOptionControl.Session delegate,boolean required){
        if(!required)return delegate;
        return new CompileOptionControl.Session(){
            public boolean prepare()throws Exception{delegate.prepare();return true;}
            public void reload(OptimizationEditTransaction.Reload reload)throws Exception{delegate.reload(reload);}
            public void restore()throws Exception{delegate.restore();}
            public void recoveryReload(OptimizationEditTransaction.Reload reload)throws Exception{delegate.recoveryReload(reload);}
            public void finish(){delegate.finish();}
            public void releaseRecovery(){delegate.releaseRecovery();}
        };
    }
    private static void applyProfile(String family){applyProfile(family,false);}
    private static void applyProfile(String family,boolean requireReload){
        var result=OptimizationEditTransaction.apply(OptimizationConfig.snapshot(),Iris::reload,Iris::isFallback,
            flags->readyReload(CompileOptionControl.open(flags),requireReload));
        status="Shader profile "+family+": "+result.message();
        previous=Iris.getCurrentPack().orElse(null);
        if(!result.success())ShaderLod.LOGGER.warn(status);
    }
}
