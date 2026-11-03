package com.pp.shaderlod.optimization.bslearlyalpha;

import java.nio.file.*;
import java.util.*;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.integration.bslquad.BslQuadEligibility;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpPostSampleAlpha;
import com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpPostSampleAlphaSource;
import com.pp.shaderlod.optimization.bslearlyalpha.mixin.BslEarlyAlphaPlugin;
import com.pp.shaderlod.optimization.sourcecache.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.*;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/** Normal public BSL BASIC source-only route. All admission and ownership work is
 * at shader setup, Apply, or a new ready context; no draw permission is installed. */
public final class BslPostSampleAlpha {
    private BslPostSampleAlpha() {}
    public static final String FAMILY="bsl-shaders";
    private record Receipt(ProgramSource source,ProgramSet set,AlphaTest alpha,long generation,
            OptimizationSnapshot flags,Object world,Object resources,String selected) {}
    private static final CompilationLinkEvidence<Receipt> LINK=new CompilationLinkEvidence<>();
    private record Linked(Receipt receipt,Object program) {}
    private static final Map<Object,Linked> PIPELINES=new WeakHashMap<>();
    private static long rewrites,links;
    private static String last="not consumed";
    public static boolean platform(String os,String arch){return "Linux".equals(os)&&"aarch64".equals(arch);}
    public static boolean singleton(OptimizationSnapshot flags){
        if(flags==null)return false;
        for(var id:OptimizationId.values())if(flags.enabled(id)!=(id==OptimizationId.EARLY_ALPHA))return false;
        return true;
    }
    public static boolean requested(boolean loaded,String family,boolean enabled,LodPreset preset,OptimizationSnapshot flags){
        return loaded&&FAMILY.equals(family)&&enabled&&preset==LodPreset.PERFORMANCE&&singleton(flags);
    }
    public static boolean stackMatches(Map<String,String> stack){
        return stack!=null&&"5.27.24+mc1.21.1".equals(stack.get("modernfix"))
            &&"7.0.3".equals(stack.get("ferritecore"))&&"1.6.14+1.21.1".equals(stack.get("immediatelyfast"))
            &&MakeUpPostSampleAlpha.stackMatches(stack);
    }
    public static void invalidate(){LINK.clear();PIPELINES.clear();}
    /** Cheap context-presence test for the tick gate; no program or GL lookup. */
    public static boolean hasOwnedPipeline(){return PIPELINES.containsKey(Iris.getPipelineManager().getPipelineNullable());}
    /** Setup/Apply/context-only check against the actual named CUTOUT object, not a numeric GL handle. */
    public static boolean active(){
        var pipeline=Iris.getPipelineManager().getPipelineNullable();var linked=PIPELINES.get(pipeline);
        if(linked==null)return false;
        if(!(pipeline instanceof IrisRenderingPipeline iris)||iris.getSodiumPrograms().getProgram(
                net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.CUTOUT)!=linked.program()){
            PIPELINES.remove(pipeline);return false;
        }
        return true;
    }
    public static String status(){return "BSL public BASIC setup; rewrites="+rewrites+"; links="+links+"; currentLinked="+active()+"; last="+last;}
    /** Called only at setup/Apply/context transitions, never a steady frame/draw. */
    private static boolean nativeInputs(boolean authenticBuild){
        try {
            if(!platform(System.getProperty("os.name"),System.getProperty("os.arch"))||!BslEarlyAlphaPlugin.exactApi()
                    ||Iris.isFallback()||!Iris.getShaderPackOptionQueue().isEmpty())return false;
            var c=Minecraft.getInstance();var pack=Iris.getCurrentPack().orElse(null);
            if(c.level==null||c.level.dimension()!=Level.OVERWORLD||pack==null
                    ||!List.copyOf(c.getResourcePackRepository().getSelectedIds()).equals(List.of("vanilla","mod_resources"))
                    ||!stackMatches(MakeUpQuadRuntime.loadedStack())
                    ||!MakeUpPostSampleAlphaSource.host(System.getProperty("os.name"),System.getProperty("os.arch"),
                        org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_RENDERER),
                        org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_VERSION)))return false;
            String name=Iris.getIrisConfig().getShaderPackName().orElse(null);
            if(name==null||!name.equals(Path.of(name).getFileName().toString()))return false;
            var path=Iris.getShaderpacksDirectory().resolve(name);
            var options=EffectiveOptions.current(pack.getShaderPackOptions());
            if(Files.isSymbolicLink(path)||!BslQuadEligibility.validate(path,options).eligible())return false;
            // Build context joins actual Iris-read raw sources/options/properties to this archive.
            return !authenticBuild||IrisSourceLifecycle.proof(List.of(BslPostSampleAlpha.class,pack),
                ()->BslQuadEligibility.validate(path,options).eligible());
        }catch(Exception|LinkageError refused){return false;}
    }
    public static boolean requiresReload(boolean enabled,LodPreset preset,OptimizationSnapshot flags){
        try {
            boolean wanted=requested(OptimizationConfig.loaded(),OptimizationConfig.activeFamily(),enabled,preset,flags)&&nativeInputs(false);
            return wanted!=active();
        }catch(RuntimeException|LinkageError refused){return false;}
    }
    public static void verifyAfterReload()throws java.io.IOException {
        var core=OptimizationRuntimeContext.core();
        if(core!=null&&requiresReload(core.settings().enabled(),core.preset(),OptimizationConfig.snapshot()))
            throw new java.io.IOException("BSL requested source and actual linked BASIC pipeline differ after reload");
    }
    public static Map<PatchShaderType,String> adapt(ProgramSource source,AlphaTest alpha,ProgramSet set,Map<PatchShaderType,String> stages){
        if(source==null||set==null||source.getParent()!=set||!"gbuffers_terrain".equals(source.getName())
                ||alpha==null||alpha.function()!=AlphaTestFunction.GREATER||Float.compare(alpha.reference(),0.1f)!=0)return stages;
        var core=OptimizationRuntimeContext.core();var flags=OptimizationConfig.snapshot();
        if(core==null||!requested(OptimizationConfig.loaded(),OptimizationConfig.activeFamily(),core.settings().enabled(),core.preset(),flags))return stages;
        last="native: setup qualification refused";
        if(Iris.getCurrentPack().orElse(null)!=set.getPack()||!nativeInputs(true))return stages;
        String rewritten=BslPostSampleAlphaSource.rewrite(stages.get(PatchShaderType.FRAGMENT),alpha,BslPostSampleAlphaSource.Variant.BASIC);
        if(rewritten==null){last="native: complete CUTOUT or uniform policy refused";return stages;}
        var c=Minecraft.getInstance();
        var receipt=new Receipt(source,set,alpha,OptimizationConfig.generation(),flags,c.level,c.getResourceManager(),Iris.getCurrentPackName());
        var out=new EnumMap<PatchShaderType,String>(PatchShaderType.class);out.putAll(stages);out.put(PatchShaderType.FRAGMENT,rewritten);
        LINK.transformed(out,receipt);rewrites++;last="BASIC transformed; awaiting compile/link";return out;
    }
    public static void compiled(Map<PatchShaderType,String> text,Map<?,?> shaders){LINK.compiled(text,shaders);}
    public static void linked(IrisRenderingPipeline pipeline,net.irisshaders.iris.pipeline.programs.SodiumPrograms.Pass pass,
            ProgramSource source,AlphaTest alpha,Map<?,?> shaders,Object program){
        if(pass!=net.irisshaders.iris.pipeline.programs.SodiumPrograms.Pass.TERRAIN_CUTOUT)return;
        // A native or refused rebuild cannot inherit a previous candidate certificate.
        PIPELINES.remove(pipeline);
        var receipt=LINK.linked(shaders);var c=Minecraft.getInstance();
        if(program==null||receipt==null||receipt.source()!=source||!receipt.alpha().equals(alpha)
                ||source.getParent()!=receipt.set()||Iris.getCurrentPack().orElse(null)!=receipt.set().getPack()
                ||receipt.generation()!=OptimizationConfig.generation()||!receipt.flags().equals(OptimizationConfig.snapshot())
                ||receipt.world()!=c.level||receipt.resources()!=c.getResourceManager()
                ||!receipt.selected().equals(Iris.getCurrentPackName())||!Iris.getShaderPackOptionQueue().isEmpty())return;
        PIPELINES.put(pipeline,new Linked(receipt,program));links++;last="actual BASIC compiled/link pipeline; no draw owner";
    }
}
