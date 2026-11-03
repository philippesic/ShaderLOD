package com.pp.shaderlod.optimization.sildurearlyalpha;

import com.pp.shaderlod.CoreUniformFrame;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.optimization.sourcecache.CompilationLinkEvidence;
import com.pp.shaderlod.optimization.sourcecache.LinkedCompilationProof;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

/** One pinned compile attempt. An absence receipt can never grant an early-alpha owner. */
public final class SildurAlphaAbsence {
    private SildurAlphaAbsence() {}
    private static final String FAMILY="sildurs-vibrant-shaders";
    private static final Set<OptimizationId> ALLOWED=Set.of(OptimizationId.QUAD_COMMAND_COMPACTION,
        OptimizationId.SHADOW_ADMISSION_CACHE,OptimizationId.BINDING_CERTIFICATE_CACHE,
        OptimizationId.INDEX_SCALAR_CACHE,OptimizationId.SHADOW_POSITIVE_VERTEX);
    public record Request(OptimizationSnapshot flags,long generation,String family,String selected,
                          CoreUniformFrame.Core core,long epoch) {
        public static Request capture() {
            try {
            var core=OptimizationRuntimeContext.core();var flags=OptimizationConfig.snapshot();
            String selected=Iris.getIrisConfig().getShaderPackName().orElse(null);
            if(!com.pp.shaderlod.integration.makeupquad.MakeUpQuadApiPins.exactApi()||
                !OptimizationConfig.loaded()||!FAMILY.equals(OptimizationConfig.activeFamily())||
                core==null||!core.settings().enabled()||core.preset()!=LodPreset.PERFORMANCE||
                !flags.equals(core.optimizations())||
                selected==null||!java.nio.file.Path.of(selected).getFileName().toString().equals(selected)||
                !Iris.getShaderPackOptionQueue().isEmpty()||Iris.isFallback())return null;
            var client=net.minecraft.client.Minecraft.getInstance();
            if(client==null||client.level==null||client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||
                net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered())return null;
            var platform=net.irisshaders.iris.platform.IrisPlatformHelpers.getInstance();
            if(platform.isModLoaded("distanthorizons")||platform.isModLoaded("voxy"))return null;
            for(var id:OptimizationId.values())if(flags.enabled(id)&&!ALLOWED.contains(id))return null;
            return new Request(flags,OptimizationConfig.generation(),FAMILY,selected,core,SildurEarlyAlphaRuntime.epoch());
            }catch(RuntimeException|LinkageError unavailable){return null;}
        }
        public boolean current() {
            try {
            var now=OptimizationRuntimeContext.core();
            var client=net.minecraft.client.Minecraft.getInstance();
            if(client==null||client.level==null||client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||
                Iris.isFallback()||net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered())return false;
            var platform=net.irisshaders.iris.platform.IrisPlatformHelpers.getInstance();
            if(platform.isModLoaded("distanthorizons")||platform.isModLoaded("voxy"))return false;
            return flags.equals(OptimizationConfig.snapshot())&&generation==OptimizationConfig.generation()&&
                family.equals(OptimizationConfig.activeFamily())&&selected.equals(Iris.getIrisConfig().getShaderPackName().orElse(null))&&
                core!=null&&now!=null&&core.settings().equals(now.settings())&&core.preset()==now.preset()&&
                core.optimizations().equals(now.optimizations())&&
                epoch==SildurEarlyAlphaRuntime.epoch()&&Iris.getShaderPackOptionQueue().isEmpty();
            }catch(RuntimeException|LinkageError unavailable){return false;}
        }
    }
    public record Receipt(Request request,LinkedCompilationProof.Prepared prepared,ProgramSource source,ProgramSet set,
                          AlphaTest alpha,String vertex,String geometry,String control,String evaluation,
                          String raw,String specialized,Object textures) {}
    private static final Map<ProgramSource,Receipt> PENDING=new IdentityHashMap<>();
    private static final Map<ProgramSource,Receipt> CONSUMED=new IdentityHashMap<>();
    private static final CompilationLinkEvidence<Receipt> LINK=new CompilationLinkEvidence<>();
    private record ShaderIds(Receipt receipt,int vertex,int fragment) {}
    private static final Map<Map<?,?>,ShaderIds> SHADER_IDS=new IdentityHashMap<>();
    private static final Map<Map<PatchShaderType,String>,Receipt> SPECIALIZED=new IdentityHashMap<>();
    record ProgramReceipt(Object pipeline,Object pack,int program,Receipt receipt) {}
    private static final Map<Object,ProgramReceipt> PROGRAMS=new IdentityHashMap<>();
    public static String status(){return "absentPrograms="+PROGRAMS.size()+", currentAbsent="+
        activeOnMismatch(OptimizationConfig.snapshot().with(OptimizationId.EARLY_ALPHA,true))+", pending="+PENDING.size()+", consumed="+CONSUMED.size();}
    public static void invalidateCompilation(){PENDING.clear();CONSUMED.clear();SPECIALIZED.clear();SHADER_IDS.clear();LINK.clear();}
    public static void destroy(Object pipeline){PROGRAMS.remove(pipeline);invalidateCompilation();}
    public static void begin(ProgramSource source,ProgramSet set,AlphaTest alpha,
            String vertex,String geometry,String control,String evaluation,String raw,String specialized,
            Object textures,LinkedCompilationProof.Prepared prepared) {
        PENDING.remove(source);CONSUMED.remove(source);
        if(source==null||set==null||alpha==null||prepared==null||specialized==null||
           alpha.function()!=AlphaTestFunction.GREATER||!Float.isFinite(alpha.reference())||
           alpha.reference()<0||alpha.reference()>1||source.getParent()!=set||
           !"gbuffers_textured".equals(source.getName())||
           !specialized.equals(SildurAlphaInterface.absent(raw)))return;
        var request=Request.capture();
        if(request==null||prepared.source().source()!=source||prepared.source().set()!=set||
           !prepared.alpha().equals(alpha)||!request.selected().equals(prepared.source().selected())||
           !prepared.source().stages().arguments(vertex,geometry,control,evaluation,raw)||
           textures!=set.getPackDirectives().getTextureMap())return;
        var options=prepared.source().input().options().values();
        if(!nativeProfile(options))return;
        PENDING.put(source,new Receipt(request,prepared,source,set,alpha,vertex,geometry,control,evaluation,raw,specialized,textures));
    }
    public static boolean nativeProfile(Map<String,String> options) {
        if(options==null||!com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts(FAMILY,options)||
           !"0".equals(options.get("nMap"))||!"true".equals(options.get("TAA")))return false;
        for(var peer:List.of(CompileOptionControl.SILDUR_QUERY,CompileOptionControl.SILDUR_DEPTH,
            CompileOptionControl.SILDUR_AO_HALF,CompileOptionControl.SILDUR_SPATIAL_REUSE,
            CompileOptionControl.SILDUR_LIGHTING_RATE,CompileOptionControl.SILDUR_PIXEL_GRAPH))
            if(!"0".equals(options.get(peer)))return false;
        return true;
    }
    public static void discard(ProgramSource source){PENDING.remove(source);CONSUMED.remove(source);}
    public static boolean pending(ProgramSource source){return PENDING.containsKey(source);}
    public static String rawForRevert(ProgramSource source,String consumed) {
        var receipt=PENDING.get(source);
        return receipt!=null&&receipt.specialized().equals(consumed)?receipt.raw():null;
    }
    /** Called after Depth's order-1100 observer, immediately before the pinned transform. */
    public static boolean arguments(ProgramSource source,ProgramSet set,AlphaTest alpha,
            String vertex,String geometry,String control,String evaluation,String fragment,Object textures) {
        var receipt=PENDING.remove(source);CONSUMED.remove(source);
        if(receipt==null||!receipt.request().current()||receipt.set()!=set||
            !receipt.alpha().equals(alpha)||!Objects.equals(receipt.vertex(),vertex)||
            !Objects.equals(receipt.geometry(),geometry)||!Objects.equals(receipt.control(),control)||
            !Objects.equals(receipt.evaluation(),evaluation)||
            !receipt.specialized().equals(fragment)||receipt.textures()!=textures)return false;
        CONSUMED.put(source,receipt);return true;
    }
    /** Exact-purpose predicate for Depth; it never accepts arbitrary const-zero text. */
    public static boolean consumedFragment(ProgramSource source,ProgramSet set,AlphaTest alpha,
            String raw,String consumed,Object textures) {
        var receipt=PENDING.get(source);
        return receipt!=null&&receipt.request().current()&&receipt.set()==set&&
            receipt.alpha().equals(alpha)&&receipt.textures()==textures&&
            receipt.raw().equals(raw)&&receipt.specialized().equals(consumed);
    }
    public static void transformed(ProgramSource source,ProgramSet set,AlphaTest alpha,
            Map<PatchShaderType,String> text) {
        var receipt=CONSUMED.remove(source);
        if(text!=null)SPECIALIZED.remove(text);
        boolean accepted=receipt!=null&&receipt.request().current()&&receipt.set()==set&&
            receipt.alpha().equals(alpha)&&text!=null&&
            SildurAlphaInterface.absentTransformed(text.get(PatchShaderType.FRAGMENT),alpha);
        LINK.transformed(text,accepted?receipt:null);
        if(accepted)SPECIALIZED.put(text,receipt);
        else if(receipt!=null||source!=null&&"gbuffers_textured".equals(source.getName())&&
            text!=null&&SildurAlphaInterface.containsAbsentDeclaration(text.get(PatchShaderType.FRAGMENT)))
            throw new IllegalStateException("Sildur alpha absence transform has no current receipt");
    }
    public static void compiled(Map<PatchShaderType,String> text,Map<?,?> shaders){
        var receipt=SPECIALIZED.get(text);
        if(shaders!=null)SHADER_IDS.remove(shaders);
        if(shaders==null){if(receipt!=null)throw new IllegalStateException("Sildur alpha absence shaders missing");return;}
        if(receipt==null){LINK.compiled(null,shaders);return;}
        boolean valid=receipt.request().current();
        try {
            if(text==null){LINK.compiled(null,shaders);return;}
            for(var entry:shaders.entrySet()) {
                if(!(entry.getKey() instanceof PatchShaderType type)||
                   !(entry.getValue() instanceof net.caffeinemc.mods.sodium.client.gl.shader.GlShader stage)||
                   !Objects.equals(text.get(type),org.lwjgl.opengl.GL20C.glGetShaderSource(stage.handle()))||
                   org.lwjgl.opengl.GL20C.glGetShaderi(stage.handle(),org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS)!=org.lwjgl.opengl.GL11C.GL_TRUE) {
                    LINK.compiled(null,shaders);
                    if(receipt!=null)throw new IllegalStateException("Sildur alpha absence driver source refused");
                    return;
                }
            }
            LINK.compiled(text,shaders);
            if(receipt!=null&&(!valid||LINK.linked(shaders)!=receipt))
                throw new IllegalStateException("Sildur alpha absence compiled receipt refused");
            if(shaders.size()!=2||!(shaders.get(PatchShaderType.VERTEX) instanceof net.caffeinemc.mods.sodium.client.gl.shader.GlShader vertex)||
               !(shaders.get(PatchShaderType.FRAGMENT) instanceof net.caffeinemc.mods.sodium.client.gl.shader.GlShader fragment))
                throw new IllegalStateException("Sildur alpha absence requires two graphics stages");
            // Iris deletes/invalidateHandle()s these wrappers before createShader RETURN.
            // Save their IDs only after exact text/object validation, while still live.
            SHADER_IDS.put(shaders,new ShaderIds(receipt,vertex.handle(),fragment.handle()));
        }catch(RuntimeException|LinkageError unavailable){
            SHADER_IDS.remove(shaders);
            LINK.compiled(null,shaders);
            if(receipt!=null)throw new IllegalStateException("Sildur alpha absence compile refused",unavailable);
        }
    }
    public static Receipt compiledAbsence(ProgramSource source,AlphaTest alpha,Map<?,?> shaders) {
        var receipt=LINK.linked(shaders);
        return receipt!=null&&receipt.source()==source&&receipt.alpha().equals(alpha)?receipt:null;
    }
    public static Receipt linked(ProgramSource source,AlphaTest alpha,Map<?,?> shaders) {
        var receipt=compiledAbsence(source,alpha,shaders);
        return receipt!=null&&receipt.request().current()?receipt:null;
    }
    public static void clearProgram(Object pipeline){PROGRAMS.remove(pipeline);}
    public static boolean recordProgram(Object pipeline,Object pack,int program,Receipt receipt,
            Map<PatchShaderType,net.caffeinemc.mods.sodium.client.gl.shader.GlShader> shaders) {
        if(pipeline==null||pack==null||program<=0||receipt==null||receipt.source().getParent().getPack()!=pack||
           shaders==null||shaders.size()!=2||!shaders.containsKey(PatchShaderType.VERTEX)||
           !shaders.containsKey(PatchShaderType.FRAGMENT))return false;
        var ids=SHADER_IDS.get(shaders);
        if(ids==null||ids.receipt()!=receipt||LINK.linked(shaders)!=receipt||!receipt.request().current())return false;
        try {
            int vertex=ids.vertex(),fragment=ids.fragment();
            if(org.lwjgl.opengl.GL20C.glGetProgrami(program,org.lwjgl.opengl.GL20C.GL_ATTACHED_SHADERS)!=2)return false;
            int[] count=new int[1],attached=new int[2];
            org.lwjgl.opengl.GL20C.glGetAttachedShaders(program,count,attached);
            if(org.lwjgl.opengl.GL20C.glGetProgrami(program,org.lwjgl.opengl.GL20C.GL_LINK_STATUS)==org.lwjgl.opengl.GL11C.GL_TRUE&&
               count[0]==2&&((attached[0]==vertex&&attached[1]==fragment)||
               (attached[1]==vertex&&attached[0]==fragment)))
                {PROGRAMS.put(pipeline,new ProgramReceipt(pipeline,pack,program,receipt));return true;}
        }catch(RuntimeException|LinkageError unavailable){ /* Native link cannot be certified. */ }
        return false;
    }
    /** Any still-live cached Off pipeline for this pack requires a reload before On can succeed. */
    static boolean cachedOnMismatch(Object pack,java.util.function.Predicate<ProgramReceipt> live) {
        return cachedOnMismatch(PROGRAMS,pack,live);
    }
    static boolean cachedOnMismatch(Map<Object,ProgramReceipt> programs,Object pack,
            java.util.function.Predicate<ProgramReceipt> live) {
        if(pack==null)return false;
        for(var saved:programs.values())if(saved.pack()==pack&&live.test(saved))return true;
        return false;
    }
    private static boolean liveProgram(ProgramReceipt saved) {
        try {
            if(!(saved.pipeline() instanceof IrisRenderingPipeline pipeline))return true;
            var programs=pipeline.getSodiumPrograms();
            if(programs==null||programs.getClass()!=SodiumPrograms.class)return true;
            var field=SodiumPrograms.class.getDeclaredField("shaders");field.setAccessible(true);
            Object map=field.get(programs);
            if(!(map instanceof EnumMap<?,?> values))return true;
            Object cutout=values.get(SodiumPrograms.Pass.TERRAIN_CUTOUT);
            // A missing or unknown pass cannot prove that the constant-zero program was replaced.
            return !(cutout instanceof net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?> program)||
                program.handle()==saved.program();
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){return true;}
    }
    /** An On request must reload even when its Off Overworld pipeline is cached off-dimension. */
    public static boolean activeOnMismatch(OptimizationSnapshot next) {
        if(!next.enabled(OptimizationId.EARLY_ALPHA)||!FAMILY.equals(OptimizationConfig.activeFamily()))return false;
        try {
            return cachedOnMismatch(Iris.getCurrentPack().orElse(null),SildurAlphaAbsence::liveProgram);
        }catch(RuntimeException|LinkageError unavailable){return true;}
    }
}
