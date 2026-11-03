package com.pp.shaderlod.experiment.sourceonlyalpha;

import java.nio.file.*;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.*;
import com.pp.shaderlod.integration.bslquad.BslQuadEligibility;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import com.pp.shaderlod.optimization.bslearlyalpha.BslPostSampleAlphaSource;
import com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpPostSampleAlpha;
import com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpPostSampleAlphaSource;
import com.pp.shaderlod.optimization.sourcecache.EffectiveOptions;

/** Private DEV setup-only BSL candidate. No public request, draw owner or sampler assumptions. */
public final class BslPostSampleAlphaR1 {
    public static final String PROPERTY="shaderlod.dev.bslPostSampleAlphaR1";
    private static boolean enabled;
    private static BslPostSampleAlphaSource.Variant variant=BslPostSampleAlphaSource.Variant.TWO_TEST;
    private static long rewrites,refusals;
    private static String last="not consumed",alphaFunction="unobserved";
    private static String candidateSha="unobserved";
    private static Map<String,Object> inputProof=Map.of();
    private static float alphaReference=Float.NaN;
    private BslPostSampleAlphaR1() {}
    public static boolean optedIn(){return Boolean.getBoolean(PROPERTY);}
    public static Map<String,Object> diagnose(){
        boolean current=nativeInputsCurrent();var proof=new LinkedHashMap<String,Object>(status());
        proof.put("native_inputs_current",current);return Map.copyOf(proof);
    }
    private static boolean refuse(String why){last="native: "+why;return false;}
    public static Map<String,Object> status(){
        var result=new LinkedHashMap<String,Object>(Map.of("opted_in",optedIn(),"enabled",enabled,"rewrites",rewrites,"refusals",refusals,
            "last",last,"alpha_function",alphaFunction,"alpha_reference",Float.isFinite(alphaReference)?(double)alphaReference:-1.0,
            "candidate_sha256",candidateSha,"native_stage_sha256",BslPostSampleAlphaSource.NATIVE_CUTOUT_SHA,"input_proof",inputProof));
        result.put("variant",variant.name());return Map.copyOf(result);
    }
    /** Real synchronous Iris reload; a rejected reload restores the prior private state. */
    public static void configure(boolean next)throws Exception {
        configure(next,variant.name());
    }
    public static void configure(boolean next,String shape)throws Exception {
        if(!optedIn()||!SourceOnlyAlphaR23.exactApi())throw new IllegalStateException("Explicit pinned BSL private control required");
        var requested=BslPostSampleAlphaSource.Variant.valueOf(shape);
        if(enabled==next&&(!next||variant==requested))return;
        boolean previous=enabled;var previousVariant=variant;enabled=next;variant=requested;
        try {Iris.reload();if(Iris.isFallback())throw new IllegalStateException("BSL private reload fell back");}
        catch(Exception failure){enabled=previous;variant=previousVariant;try{Iris.reload();}catch(Exception recovery){failure.addSuppressed(recovery);}throw failure;}
    }
    public static boolean nativeInputsCurrent() {
        try {
            if(!optedIn()||!SourceOnlyAlphaR23.exactApi()||Iris.isFallback())return refuse("opt-in/API/fallback refused");
            var c=net.minecraft.client.Minecraft.getInstance();
            if(c.level==null||c.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD
                ||!Iris.getShaderPackOptionQueue().isEmpty())return refuse("world/dimension/queue refused");
            var pack=Iris.getCurrentPack().orElse(null);if(pack==null)return refuse("current pack absent");
            var options=EffectiveOptions.current(pack.getShaderPackOptions());
            inputProof=Map.of("effective_options_count",options.size(),"selected_resource_ids",List.copyOf(c.getResourcePackRepository().getSelectedIds()),
                "resource_manager_entries",c.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList(),
                "option_queue",Map.copyOf(Iris.getShaderPackOptionQueue()));
            var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();
            String name=Iris.getCurrentPackName();if(!name.matches("[A-Za-z0-9_.-]+\\.zip"))return refuse("archive name refused");
            var file=dir.resolve(name);if(Files.isSymbolicLink(file)||!BslQuadEligibility.validate(file,options).eligible())return refuse("original archive/current native384 refused");
            var stack=MakeUpQuadRuntime.loadedStack();
            if(stack.containsKey("moreculling")||stack.containsKey("cloth_config"))return refuse("private optimized3-only module tuple refused");
            if(!"5.27.24+mc1.21.1".equals(stack.get("modernfix"))||!"7.0.3".equals(stack.get("ferritecore"))
                ||!"1.6.14+1.21.1".equals(stack.get("immediatelyfast"))||!MakeUpPostSampleAlpha.stackMatches(stack))return refuse("complete optimized module tuple refused");
            if(!MakeUpPostSampleAlphaSource.host(System.getProperty("os.name"),System.getProperty("os.arch"),
                org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_RENDERER),org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_VERSION)))return refuse("exact Asahi host refused");
                // NeoForge's selected mod_resources virtual pack may expand into
                // multiple manager entries; selection IDs are the actual user input.
            if(!List.copyOf(c.getResourcePackRepository().getSelectedIds()).equals(List.of("vanilla","mod_resources")))return refuse("selected vanilla resources refused");
            return true;
        }catch(Exception|LinkageError refused){return refuse("input exception: "+refused);}
    }
    public static Map<PatchShaderType,String> adapt(ProgramSource source,AlphaTest alpha,ProgramSet set,Map<PatchShaderType,String> stages) {
        if(!optedIn()||!enabled||source==null||!"gbuffers_terrain".equals(source.getName())
            ||alpha==null||alpha.function()!=net.irisshaders.iris.gl.blending.AlphaTestFunction.GREATER)return stages;
        alphaFunction=alpha.function().toString();alphaReference=alpha.reference();
        if(set==null||source.getParent()!=set||Iris.getCurrentPack().orElse(null)!=set.getPack()||!nativeInputsCurrent()){
            refusals++;last="native: current original/archive/options/API/host/stack/world/resource qualification refused";return stages;
        }
        String rewritten=BslPostSampleAlphaSource.rewrite(stages.get(PatchShaderType.FRAGMENT),alpha,variant);
        if(rewritten==null){refusals++;last="native: complete stage/alpha policy refused";return stages;}
        try{candidateSha=BslQuadEligibility.sha(rewritten.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        catch(Exception impossible){throw new IllegalStateException(impossible);}
        var out=new EnumMap<PatchShaderType,String>(PatchShaderType.class);out.putAll(stages);out.put(PatchShaderType.FRAGMENT,rewritten);
        rewrites++;last="complete native BSL CUTOUT rewritten after unchanged atlas/shadow reads";return out;
    }
}
