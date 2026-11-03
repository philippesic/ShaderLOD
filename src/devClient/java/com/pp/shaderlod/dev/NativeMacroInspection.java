package com.pp.shaderlod.dev;

import com.google.gson.*;
import com.pp.shaderlod.optimization.sourcecache.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.*;

/** Dev evidence only. A consumed-input observation never grants native macro admission. */
public final class NativeMacroInspection {
    private NativeMacroInspection() {}
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final int LIMIT=2*1024*1024, MAX=8;
    private static final CompilationLinkEvidence<Input> LINK=new CompilationLinkEvidence<>();
    private static final IdentityHashMap<ProgramSource,Input> PENDING=new IdentityHashMap<>();
    private static final List<Linked> OWNERS=new ArrayList<>();
    private static long epoch;
    private static int attempts;
    private static boolean overflow;
    private static final IdentityHashMap<Map<?,?>,Map<?,?>> TEXT=new IdentityHashMap<>();
    private static final Set<Map<?,?>> SHADERS=Collections.newSetFromMap(new IdentityHashMap<>());
    private static String last="not observed";
    private record Input(Object observer,ProgramSource source,ProgramSet set,IrisCompiledSource.PackInput pack,
            IrisCompiledSource.SetInput setInput, IrisCompiledSource.Stages raw,AlphaTest alpha,Object textures,
            String selected,Map<String,String> options,long epoch) {}
    private record Linked(Input input,IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,GlProgram<?> program,int handle) {}
    public static boolean enabled(){return Boolean.getBoolean("shaderlod.dev.nativeMacroInspection");}
    public static synchronized void invalidate(){epoch++;PENDING.clear();LINK.clear();OWNERS.clear();TEXT.clear();SHADERS.clear();attempts=0;overflow=false;last="invalidated";}
    public static synchronized String status(){return last+"; retained="+OWNERS.size()+"; native replay at untimed export";}
    private static String selected(){return Iris.getIrisConfig().getShaderPackName().orElse("");}
    private static String family(String selected){return switch(selected){
        case "ComplementaryReimagined_r5.9.3.zip"->"complementary-reimagined";
        case "ComplementaryUnbound_r5.9.3.zip"->"complementary-unbound";
        default->null;};}
    private static boolean controlsOff(){
        if(com.pp.shaderlod.ShaderLodConfig.ENABLED.get())return false;
        for(var id:com.pp.shaderlod.optimization.OptimizationId.values())
            if(com.pp.shaderlod.optimization.OptimizationConfig.requested(id))return false;
        return true;
    }
    private static boolean bounded(IrisCompiledSource.Stages s){
        long n=0;for(String text:Arrays.asList(s.vertex(),s.geometry(),s.control(),s.evaluation(),s.fragment()))if(text!=null)n+=text.length();
        return s.vertex()!=null&&s.fragment()!=null&&s.geometry()==null&&s.control()==null&&s.evaluation()==null&&n<=LIMIT;
    }
    public static synchronized void prepare(Object observer,ProgramSource source,ProgramSet set,AlphaTest alpha,
            String vertex,String geometry,String control,String evaluation,String fragment,Object textures){
        if(!enabled())return;
        if(overflow)return;
        // Unrelated shadow/translucent compiles do not spend or revoke terrain evidence.
        if(source==null||!"gbuffers_terrain".equals(source.getName())||family(selected())==null)return;
        if(PENDING.containsKey(source)||OWNERS.stream().anyMatch(o->o.input.source==source))reject("terrain input replaced");
        last="consumed input refused";
        if(observer==null||source==null||set==null||source.getParent()!=set||alpha==null
                ||!"gbuffers_terrain".equals(source.getName())||family(selected())==null
                ||!(set instanceof IrisCompiledSource.SetView sv)||!(set.getPack() instanceof IrisCompiledSource.PackView pv))return;
        var p=pv.shaderlod$makeupPackInput();var si=sv.shaderlod$makeupSetInput();
        var raw=new IrisCompiledSource.Stages(vertex,geometry,control,evaluation,fragment);
        if(p==null||si==null||!controlsOff()||si.directory()==null||!"/world0".equals(si.directory().getPathString())
                ||si.pack()!=set.getPack()||si.provider()!=p.provider()||si.properties()!=p.properties()
                ||textures!=set.getPackDirectives().getTextureMap()||!bounded(raw)||!raw.equals(IrisCompiledSource.Stages.of(source))
                ||!p.options().current(set.getPack().getShaderPackOptions()))return;
        if(++attempts>MAX){invalidate();overflow=true;last="accepted terrain attempt overflow; reload required";return;}
        PENDING.put(source,new Input(observer,source,set,p,si,raw,alpha,textures,selected(),p.options().values(),epoch));
        last="actual patchSodium arguments captured; awaiting compile/link";
    }
    public static synchronized void transformed(Object observer,ProgramSource source,ProgramSet set,AlphaTest alpha,Map<PatchShaderType,String> output){
        if(!enabled())return;
        var p=PENDING.remove(source);
        long size=0;if(output!=null)for(var text:output.values())if(text!=null)size+=text.length();
        if(p==null)return;
        if(p.observer!=observer||p.set!=set||!p.alpha.equals(alpha)||size>LIMIT||output==null||output.isEmpty()||!current(p)){
            reject("accepted transform rejected");return;
        }
        TEXT.put(output,Collections.unmodifiableMap(new HashMap<>(output)));LINK.transformed(output,p);
    }
    public static synchronized void compiled(Map<?,?> input,Map<?,?> output){
        if(!enabled())return;
        if(!TEXT.containsKey(input))return;
        if(!TEXT.get(input).equals(input)){reject("transformed map mutated");return;}
        LINK.compiled(input,output);
        if(output==null||LINK.linked(output)==null){reject("accepted shader map rejected");return;}
        if(!SHADERS.contains(output)&&SHADERS.size()>=MAX){invalidate();overflow=true;last="compiled evidence overflow; reload required";return;}
        SHADERS.add(output);
    }
    public static synchronized void linked(Object observer,IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,
            ProgramSource source,AlphaTest alpha,Map<?,?> shaders,GlProgram<?> program,int handle){
        if(!enabled())return;
        if(!SHADERS.contains(shaders))return;
        if(TEXT.entrySet().stream().anyMatch(e->!e.getValue().equals(e.getKey()))){reject("transformed map mutated after compile");return;}
        var p=LINK.linked(shaders);
        if(p==null||p.observer!=observer||p.source!=source||!p.alpha.equals(alpha)||program==null||pipeline==null||handle<=0
                ||pass==null||!Set.of("TERRAIN","TERRAIN_CUTOUT").contains(pass.name())||!current(p)||!live(program,handle)){reject("accepted link rejected");return;}
        if(OWNERS.size()>=MAX){invalidate();overflow=true;last="linked owner overflow; reload required";return;}
        OWNERS.add(new Linked(p,pipeline,pass,program,handle));last="successful actual createShader return captured";
    }
    private static boolean current(Input p){
        return p.epoch==epoch&&controlsOff()&&p.selected.equals(selected())&&p.source.getParent()==p.set
            &&Iris.getCurrentPack().orElse(null)==p.set.getPack()
            &&p.set.getPack() instanceof IrisCompiledSource.PackView v&&v.shaderlod$makeupPackInput()==p.pack
            &&p.set instanceof IrisCompiledSource.SetView sv&&sv.shaderlod$makeupSetInput()==p.setInput
            &&p.pack.options().current(p.set.getPack().getShaderPackOptions())&&p.options.equals(p.pack.options().values())
            &&p.textures==p.set.getPackDirectives().getTextureMap()&&p.raw.equals(IrisCompiledSource.Stages.of(p.source));
    }
    private static void reject(String reason){PENDING.clear();LINK.clear();TEXT.clear();SHADERS.clear();OWNERS.clear();last=reason;}
    static boolean live(GlProgram<?> program,int handle){try{return program!=null&&handle>0&&program.handle()==handle;}catch(IllegalStateException deleted){return false;}}
    /** All proof is requested untimed; this inspection never grants draw permission. */
    public static synchronized JsonObject export(Path output)throws Exception{
        try{return verifiedExport(output);}catch(Exception failure){reject("export rejected: "+failure.getMessage());throw failure;}
    }
    private static JsonObject verifiedExport(Path output)throws Exception{
        if(!enabled()||Iris.isFallback()||overflow)throw new IllegalStateException("Native observation unavailable");
        Object pipeline=Iris.getPipelineManager().getPipelineNullable();
        if(OWNERS.isEmpty())throw new IllegalStateException("No linked native world0 terrain observation");
        if(TEXT.entrySet().stream().anyMatch(e->!e.getValue().equals(e.getKey()))||SHADERS.stream().anyMatch(m->LINK.linked(m)==null))throw new IllegalStateException("Retained compilation maps mutated");
        var rows=new JsonArray();NativeMacroReplay.Proof proof=null;Input input=null;
        for(var owner:OWNERS){var p=owner.input;
            if(owner.pipeline!=pipeline||!live(owner.program,owner.handle)||!current(p))throw new IllegalStateException("Stale/deleted native observation");
            Path archive=Iris.getShaderpacksDirectory().resolve(p.selected).toAbsolutePath().normalize();
            var checked=NativeMacroReplay.prove(archive,family(p.selected),p.pack.options(),p.pack.propertyText(),p.pack.glslDefines(),p.setInput.directory(),p.raw,p.pack.provider());
            if(proof!=null&&(!proof.equals(checked)||!input.options.equals(p.options)))throw new IllegalStateException("Native observations disagree");
            proof=checked;input=p;
            var row=new JsonObject();row.addProperty("family_candidate",family(p.selected));row.addProperty("selected",p.selected);row.add("native_options",JSON.toJsonTree(p.options));row.addProperty("pass",owner.pass.name());row.addProperty("program_handle",owner.handle);
            row.addProperty("pipeline_identity",System.identityHashCode(owner.pipeline));row.addProperty("program_identity",System.identityHashCode(owner.program));
            row.addProperty("source_identity",System.identityHashCode(p.source));row.addProperty("set_identity",System.identityHashCode(p.set));
            row.addProperty("pack_identity",System.identityHashCode(p.set.getPack()));row.addProperty("provider_identity",System.identityHashCode(p.pack.provider()));
            row.addProperty("observer_identity",System.identityHashCode(p.observer));row.addProperty("epoch",p.epoch);rows.add(row);
        }
        // Recheck currency/handles after replay and immediately before publication.
        for(var o:OWNERS)if(o.pipeline!=Iris.getPipelineManager().getPipelineNullable()||!current(o.input)||!live(o.program,o.handle))throw new IllegalStateException("Observation changed during replay");
        Path destination=output.toAbsolutePath().resolveSibling(output.getFileName()+".proof");
        var receipt=new JsonObject();receipt.addProperty("schema",2);receipt.addProperty("family",family(input.selected));receipt.addProperty("selected",input.selected);
        receipt.addProperty("source_stage","actual_native_preprocessed_fragment");receipt.addProperty("native_proof_verified",true);
        receipt.addProperty("draw_permission",false);receipt.addProperty("normalization_point","after /lib/lighting/mainLighting.glsl in /program/gbuffers_terrain.glsl; stable through later active writes and fragment end");
        receipt.addProperty("source_path",destination.resolve("fragment.fsh").toString());receipt.addProperty("source_sha256",NativeMacroReplay.sha(proof.stages().fragment().getBytes(StandardCharsets.UTF_8)));
        receipt.addProperty("archive_sha256",proof.archiveSha256());receipt.addProperty("catalog_original_source_digest",proof.sourceDigest());
        receipt.add("native_options",JSON.toJsonTree(input.options));receipt.add("normalized_defines",JSON.toJsonTree(proof.defines()));
        receipt.add("glsl_defines",JSON.toJsonTree(input.pack.glslDefines()));receipt.add("property_defines",JSON.toJsonTree(input.pack.propertyDefines()));
        receipt.add("property_text",JSON.toJsonTree(input.pack.propertyText()));receipt.add("later_relevant_macro_writes",JSON.toJsonTree(proof.laterWrites()));
        receipt.add("consumed_stages",JSON.toJsonTree(proof.stages()));receipt.add("observations",rows);
        publishProof(destination,Map.of("receipt.json",JSON.toJson(receipt),"fragment.fsh",proof.stages().fragment()));
        var result=new JsonObject();result.addProperty("path",destination.resolve("receipt.json").toString());result.addProperty("sha256",NativeMacroReplay.sha(Files.readAllBytes(destination.resolve("receipt.json"))));
        result.addProperty("native_proof_verified",true);result.addProperty("draw_permission",false);result.addProperty("observations",rows.size());return result;
    }
    static void publishProof(Path destination,Map<String,String> files)throws Exception{
        long size=0;for(String text:files.values())size+=text.getBytes(StandardCharsets.UTF_8).length;
        if(size<=0||size>32*1024*1024)throw new java.io.IOException("Proof exceeds 32 MiB");
        if(Files.exists(destination)){
            try(var names=Files.list(destination)){if(!names.map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(files.keySet()))throw new java.io.IOException("Existing proof members differ");}
            for(var e:files.entrySet())if(!Files.readString(destination.resolve(e.getKey()),StandardCharsets.UTF_8).equals(e.getValue()))throw new java.io.IOException("Existing proof differs");return;
        }
        Path tmp=Files.createTempDirectory(destination.getParent(),"native-macro-proof-");
        try{for(var e:files.entrySet())Files.writeString(tmp.resolve(e.getKey()),e.getValue(),StandardCharsets.UTF_8);
            Files.move(tmp,destination,StandardCopyOption.ATOMIC_MOVE);
        }finally{if(Files.exists(tmp)){try(var paths=Files.list(tmp)){for(var p:paths.toList())Files.deleteIfExists(p);}Files.deleteIfExists(tmp);}}
    }
    static void publish(Path output,String text)throws java.io.IOException{
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);if(bytes.length==0||bytes.length>32*1024*1024)throw new java.io.IOException("Export exceeds 32 MiB");
        output=output.toAbsolutePath();if(Files.exists(output)){
            if(Files.size(output)!=bytes.length||!Arrays.equals(bytes,Files.readAllBytes(output)))throw new java.io.IOException("Existing export differs");return;}
        Path tmp=Files.createTempFile(output.getParent(),"native-macro-",".tmp");
        try{Files.write(tmp,bytes);Files.move(tmp,output,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(tmp);}
    }
}
