package com.pp.shaderlod.experiment.sourceonlyalpha;

import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.*;
import com.pp.shaderlod.optimization.makeupearlyalpha.*;

/** DEV-only positive configuration over the already admitted public stage; no draw owner. */
public final class MakeUpSingleAlphaR1 {
    private static boolean enabled;
    private static long rewrites,refusals;
    private static String last="not consumed",candidate="unobserved";
    private MakeUpSingleAlphaR1() {}
    private static boolean refuse(String why){refusals++;last=why;return false;}
    public static boolean optedIn(){return Boolean.getBoolean("shaderlod.dev.makeupSingleAlphaR1");}
    public static Map<String,Object> status(){return Map.of("opted_in",optedIn(),"enabled",enabled,"rewrites",rewrites,"refusals",refusals,"last",last,"candidate_sha256",candidate,"current_sha256",MakeUpSingleAlphaSource.CURRENT_SHA);}
    public static void configure(boolean next)throws Exception {
        if(!optedIn()||!SourceOnlyAlphaR23.exactApi())throw new IllegalStateException("Explicit pinned MakeUp single-test DEV control required");
        if(enabled==next)return;
        boolean previous=enabled;enabled=next;
        try{Iris.reload();if(Iris.isFallback())throw new IllegalStateException("Single-test reload fell back");}
        catch(Exception failure){enabled=previous;try{Iris.reload();}catch(Exception recovery){failure.addSuppressed(recovery);}throw failure;}
    }
    public static Map<PatchShaderType,String> adapt(ProgramSource source,AlphaTest alpha,ProgramSet set,Map<PatchShaderType,String> stages){
        if(!optedIn()||!enabled)return stages;
        if(!SourceOnlyAlphaR23.exactApi()||source==null||!"gbuffers_terrain".equals(source.getName())||set==null||source.getParent()!=set||Iris.getCurrentPack().orElse(null)!=set.getPack()||!Iris.getShaderPackOptionQueue().isEmpty()){refuse("source/API/parent/current/queue refused");return stages;}
        if(!MakeUpEarlyAlphaCompilation.enabled(source,set)||!MakeUpEarlyAlphaRuntime.source(set.getPack())||Iris.isFallback()){refuse("public compilation/source/fallback refused");return stages;}
        var c=net.minecraft.client.Minecraft.getInstance();var stack=com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.loadedStack();
        if(c.level==null||c.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||!List.copyOf(c.getResourcePackRepository().getSelectedIds()).equals(List.of("vanilla","mod_resources"))
            ||!"5.27.24+mc1.21.1".equals(stack.get("modernfix"))||!"7.0.3".equals(stack.get("ferritecore"))||!"1.6.14+1.21.1".equals(stack.get("immediatelyfast"))||!MakeUpPostSampleAlpha.stackMatches(stack)
            ||!MakeUpPostSampleAlphaSource.host(System.getProperty("os.name"),System.getProperty("os.arch"),org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_RENDERER),org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_VERSION))){refuse("world/resource/optimized3/Asahi refused");return stages;}
        if(!(set.getPack() instanceof com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.PackView view)){refuse("captured pack input absent");return stages;}
        var input=view.shaderlod$makeupPackInput();if(input==null||!input.options().current(set.getPack().getShaderPackOptions())||!MakeUpEarlyAlphaLeanGraph.profile(input.options().values())){refuse("captured native profile/current refused");return stages;}
        // Exact7ab can only be produced here by the existing fully admitted public lowering.
        // A refused public source/host/options/module/alpha path cannot match this full stage.
        String rewritten=MakeUpSingleAlphaSource.rewrite(stages.get(PatchShaderType.FRAGMENT),alpha);
        if(rewritten==null){refusals++;last="current stage/alpha refused";return stages;}
        try{candidate=com.pp.shaderlod.integration.bslquad.BslQuadEligibility.sha(rewritten.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        catch(Exception impossible){throw new IllegalStateException(impossible);}
        var out=new EnumMap<PatchShaderType,String>(PatchShaderType.class);out.putAll(stages);out.put(PatchShaderType.FRAGMENT,rewritten);
        rewrites++;last="exact public7ab duplicate final alpha test removed only";return out;
    }
}
