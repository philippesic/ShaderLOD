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
            OptimizationSnapshot flags,Object world,Object resources,String selected,Map<PatchShaderType,String> text) {}
    private static final CompilationLinkEvidence<Receipt> LINK=new CompilationLinkEvidence<>();
    private static final Map<Map<?,?>,Receipt> TEXT=new IdentityHashMap<>();
    private record Linked(Receipt receipt,Object program,Object context) {}
    private static final Map<Object,Linked> PIPELINES=new WeakHashMap<>();
    private static long rewrites,links;
    private static boolean observing;
    private static long completedBatches,submittedCommands;
    private static final Map<Integer,Long> observedPrograms=new HashMap<>();
    private static final Map<String,Long> refusals=new HashMap<>();
    private static String witnessLast="not observed";
    private static String last="not consumed";
    public static boolean platform(String os,String arch){return MakeUpPostSampleAlphaSource.platform(os,arch);}
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
    public static void invalidate(){observing=false;LINK.clear();TEXT.clear();PIPELINES.clear();witnessLast="invalidated";}
    /** Cheap context-presence test for the tick gate; no program or GL lookup. */
    public static boolean hasOwnedPipeline(){return PIPELINES.containsKey(Iris.getPipelineManager().getPipelineNullable());}
    /** Setup/Apply/context-only check against the actual named CUTOUT object, not a numeric GL handle. */
    public static boolean active(){
        var pipeline=Iris.getPipelineManager().getPipelineNullable();var linked=PIPELINES.get(pipeline);
        if(linked==null)return false;
        if(!(pipeline instanceof IrisRenderingPipeline iris))return false;
        // getProgram(CUTOUT) selects SHADOW_CUTOUT during shadow rendering.
        // Read the actual terrain slot directly and retain an unpublished link
        // until construction finishes. An object mismatch never authorizes it.
        return iris.getSodiumPrograms() instanceof BslTerrainProgramView programs
            &&programs.shaderlod$bslTerrainCutout()==linked.program();
    }

    public static String status(){return "BSL public BASIC setup; rewrites="+rewrites+"; links="+links+"; currentLinked="+active()+"; last="+last;}
    public static boolean hostAvailable(){
        try {
            return platform(System.getProperty("os.name"),System.getProperty("os.arch"))
                &&com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()
                &&MakeUpPostSampleAlphaSource.host(System.getProperty("os.name"),System.getProperty("os.arch"),
                    org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_RENDERER),
                    org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_VERSION));
        }catch(RuntimeException|LinkageError missing){return false;}
    }
    /** Untimed owned benchmark observation; grants no draw or shader permission. */
    public static void observe(boolean enabled,boolean reset){
        if(enabled&&System.getProperty("shaderlod.dev.benchmarkControlProbe","").isBlank())
            throw new IllegalStateException("BSL post-sample observation requires owned benchmark");
        observing=enabled;
        if(reset){completedBatches=submittedCommands=0;observedPrograms.clear();refusals.clear();witnessLast="reset";}
    }
    public static Map<String,Object> snapshot(){
        var pipeline=Iris.getPipelineManager().getPipelineNullable();var linked=PIPELINES.get(pipeline);
        boolean current=active();
        int program=current&&linked.program() instanceof net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?> p?p.handle():0;
        var result=new HashMap<String,Object>();
        result.putAll(Map.of("active",current,"program",program,"pipeline_identity",System.identityHashCode(pipeline),
            "generation",OptimizationConfig.generation(),"linked_programs",PIPELINES.size(),"observing",observing,
            "completed_batches",completedBatches,"submitted_commands",submittedCommands,"last",witnessLast,
            "scope","Current rewritten CUTOUT ownership plus untimed completed native submissions; not fragments or exact timed draw counts"));
        result.put("observed_program_commands",Map.copyOf(observedPrograms));result.put("refusals",Map.copyOf(refusals));
        return Map.copyOf(result);
    }
    private static boolean witnessed(boolean valid,String reason){
        if(!valid){witnessLast=reason;refusals.merge(reason,1L,Long::sum);}return valid;
    }
    public static void completedDraw(int program,int commands){
        if(!observing||commands<=0)return;
        observedPrograms.merge(program,(long)commands,Long::sum);
        var pipeline=Iris.getPipelineManager().getPipelineNullable();var linked=PIPELINES.get(pipeline);
        if(!witnessed(linked!=null,"no linked terrain"))return;
        if(!witnessed(active(),"terrain object differs"))return;
        if(!witnessed(linked.program() instanceof net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?>,"program type"))return;
        var p=(net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?>)linked.program();
        if(!witnessed(p.handle()==program,"different bound program"))return;
        if(!witnessed(linked.context()==org.lwjgl.opengl.GL.getCapabilities(),"GL context differs"))return;
        var receipt=linked.receipt();var c=Minecraft.getInstance();
        // This observer certifies the immutable linked program, not a mutable
        // request generation. A ready-world profile persistence can increment
        // that generation without changing any flag or rebuilding the program.
        // Actual current program/context/pack/world/resources and exact flags
        // remain required; the benchmark separately pins its window generation.
        if(!witnessed(receipt.flags().equals(OptimizationConfig.snapshot()),"flags differ")
                ||!witnessed(receipt.world()==c.level,"world differs")
                ||!witnessed(receipt.resources()==c.getResourceManager(),"resources differ")
                ||!witnessed(Iris.getCurrentPack().orElse(null)==receipt.set().getPack(),"pack differs")
                ||!witnessed(receipt.selected().equals(Iris.getCurrentPackName()),"selection differs")
                ||!witnessed(Iris.getShaderPackOptionQueue().isEmpty(),"options pending")
                ||!witnessed(!Iris.isFallback(),"fallback")
                ||!witnessed(com.pp.shaderlod.ShaderLodConfig.snapshot().enabled(),"master off")
                ||!witnessed(OptimizationConfig.requested(OptimizationId.EARLY_ALPHA),"early alpha off"))return;
        completedBatches++;submittedCommands+=commands;witnessLast="current rewritten CUTOUT native submission completed";
    }
    /** Called only at setup/Apply/context transitions, never a steady frame/draw. */
    private static boolean nativeInputs(boolean authenticBuild){
        try {
            if(!platform(System.getProperty("os.name"),System.getProperty("os.arch"))||!BslEarlyAlphaPlugin.exactApi()
                    ||Iris.isFallback()||!Iris.getShaderPackOptionQueue().isEmpty())return false;
            var c=Minecraft.getInstance();var pack=Iris.getCurrentPack().orElse(null);
            if(c.level==null||c.level.dimension()!=Level.OVERWORLD||pack==null
                    ||!List.copyOf(c.getResourcePackRepository().getSelectedIds()).equals(List.of("vanilla","mod_resources"))
                    ||!stackMatches(MakeUpQuadRuntime.loadedStack())
                    ||!hostAvailable())return false;
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
        var out=new EnumMap<PatchShaderType,String>(PatchShaderType.class);out.putAll(stages);out.put(PatchShaderType.FRAGMENT,rewritten);
        var expected=new EnumMap<PatchShaderType,String>(PatchShaderType.class);expected.putAll(out);
        var receipt=new Receipt(source,set,alpha,OptimizationConfig.generation(),flags,c.level,c.getResourceManager(),Iris.getCurrentPackName(),Collections.unmodifiableMap(expected));
        TEXT.put(out,receipt);LINK.transformed(out,receipt);rewrites++;last="BASIC transformed; awaiting compile/link";return out;
    }
    public static void compiled(Map<PatchShaderType,String> text,Map<PatchShaderType,net.caffeinemc.mods.sodium.client.gl.shader.GlShader> shaders){
        var receipt=TEXT.remove(text);
        if(receipt!=null){
            boolean valid=shaders!=null&&shaders.keySet().equals(Set.of(PatchShaderType.VERTEX,PatchShaderType.FRAGMENT));
            if(valid)for(var entry:shaders.entrySet()){
                int id=entry.getValue().handle();
                int type=entry.getKey()==PatchShaderType.VERTEX?org.lwjgl.opengl.GL20.GL_VERTEX_SHADER:org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
                if(!org.lwjgl.opengl.GL20.glIsShader(id)||org.lwjgl.opengl.GL20.glGetShaderi(id,org.lwjgl.opengl.GL20.GL_SHADER_TYPE)!=type
                        ||org.lwjgl.opengl.GL20.glGetShaderi(id,org.lwjgl.opengl.GL20.GL_COMPILE_STATUS)!=org.lwjgl.opengl.GL11.GL_TRUE
                        ||!Objects.equals(receipt.text().get(entry.getKey()),org.lwjgl.opengl.GL20.glGetShaderSource(id))){valid=false;break;}
            }
            if(!valid){LINK.compiled(Map.of(),shaders);last="actual shader text/type/compile refused";return;}
        }
        LINK.compiled(text,shaders);
    }
    public static void linked(IrisRenderingPipeline pipeline,net.irisshaders.iris.pipeline.programs.SodiumPrograms.Pass pass,
            ProgramSource source,AlphaTest alpha,Map<?,?> shaders,Object program){
        if(pass!=net.irisshaders.iris.pipeline.programs.SodiumPrograms.Pass.TERRAIN_CUTOUT)return;
        // A native or refused rebuild cannot inherit a previous candidate certificate.
        PIPELINES.remove(pipeline);
        var receipt=LINK.linked(shaders);var c=Minecraft.getInstance();
        if(!(program instanceof net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?> actual)
                ||actual.getClass()!=net.caffeinemc.mods.sodium.client.gl.shader.GlProgram.class||actual.handle()<=0
                ||!org.lwjgl.opengl.GL20.glIsProgram(actual.handle())
                ||org.lwjgl.opengl.GL20.glGetProgrami(actual.handle(),org.lwjgl.opengl.GL20.GL_LINK_STATUS)!=org.lwjgl.opengl.GL11.GL_TRUE
                ||receipt==null||receipt.source()!=source||!receipt.alpha().equals(alpha)
                ||source.getParent()!=receipt.set()||Iris.getCurrentPack().orElse(null)!=receipt.set().getPack()
                ||receipt.generation()!=OptimizationConfig.generation()||!receipt.flags().equals(OptimizationConfig.snapshot())
                ||receipt.world()!=c.level||receipt.resources()!=c.getResourceManager()
                ||!receipt.selected().equals(Iris.getCurrentPackName())||!Iris.getShaderPackOptionQueue().isEmpty())return;
        PIPELINES.put(pipeline,new Linked(receipt,program,org.lwjgl.opengl.GL.getCapabilities()));links++;last="actual BASIC compiled/link pipeline; no draw owner";
    }
}
