package com.pp.shaderlod.optimization.sourcecache;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;
import java.util.zip.ZipFile;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import com.pp.shaderlod.optimization.SourceArchiveDigest;

/** Build-owned source proofs. No validator, archive read or option inventory is reachable from a lookup. */
public final class IrisSourceLifecycle {
    private IrisSourceLifecycle() {}
    private static long generation;
    private static final ThreadLocal<Build> BUILD=new ThreadLocal<>();
    private static final Map<WorldRenderingPipeline,Loaded> LOADED=new IdentityHashMap<>();
    private record Resources(Object manager,List<String> selected,List<String> packs) {
        static Resources capture(){var c=net.minecraft.client.Minecraft.getInstance();return new Resources(c.getResourceManager(),List.copyOf(c.getResourcePackRepository().getSelectedIds()),c.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList());}
        boolean current(){var c=net.minecraft.client.Minecraft.getInstance();return manager==c.getResourceManager()&&selectedResourcesMatch(selected,c.getResourcePackRepository().getSelectedIds())&&packs.equals(c.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList());}
    }
    private record Loaded(ShaderPack pack,IrisCompiledSource.PackInput input,String selected,Resources resources,WorldRenderingPipeline pipeline,String family,SourceProofEpoch proof) {
        boolean current(){return pack==Iris.getCurrentPack().orElse(null)&&pack instanceof IrisCompiledSource.PackView view
            &&view.shaderlod$makeupPackInput()==input&&input.options().current(pack.getShaderPackOptions())
            &&selected.equals(Iris.getIrisConfig().getShaderPackName().orElse(null))&&resources.current();}
    }
    private record Build(Loaded context,SourceProofEpoch.Attempt attempt) {}
    static boolean selectedResourcesMatch(List<String> captured,Collection<String> actual){return captured.equals(List.copyOf(actual));}
    public static boolean loading(){return BUILD.get()!=null;}
    public static void invalidate(){generation++;for(var receipt:LOADED.values())receipt.proof().invalidate();LOADED.clear();var build=BUILD.get();if(build!=null)build.context().proof().invalidate();}
    public static void destroy(Object pipeline){var receipt=LOADED.remove(pipeline);if(receipt!=null)receipt.proof().invalidate();if(BUILD.get()!=null)invalidate();}
    /** Missing authentic producer, changed options/resources or failed build always refuses. */
    @FunctionalInterface public interface Validation { boolean run() throws Exception; }
    public static boolean proof(Object key,Validation validation){
        var build=BUILD.get();
        if(build!=null)return build.context().current()&&build.attempt().validate(key,()->{try{return validation.run();}catch(Exception|LinkageError refused){return false;}});
        var receipt=current();return receipt!=null&&receipt.proof().current(receipt.pipeline(),key);
    }
    private static Loaded current(){
        if(LOADED.isEmpty())return null;
        var pipeline=Iris.getPipelineManager().getPipelineNullable();var receipt=LOADED.get(pipeline);if(receipt==null)return null;
        if(!receipt.current()){LOADED.remove(pipeline);receipt.proof().invalidate();return null;}
        return !Iris.isFallback()&&Iris.getShaderPackOptionQueue().isEmpty()?receipt:null;
    }
    /** Generated-source provenance for a uniform-only edit of this already loaded graph. No disk lookup. */
    public static String qualifiedFamily(){var receipt=current();return receipt==null?null:receipt.family();}
    /** Opaque, per-publication identity; the same family/pack can own multiple distinct pipelines. */
    public static Object qualifiedReceipt(){return current();}
    static boolean matchesInputs(Path directory,String name,Object pack,Object values,Object declarations,Map<String,String> options){
        if(!(pack instanceof ShaderPack))return false;
        var build=BUILD.get();var receipt=build==null?current():build.context();
        return receipt!=null&&pack==receipt.pack()&&receipt.current()&&receipt.selected().equals(name)
            &&Iris.getShaderpacksDirectory().toAbsolutePath().normalize().equals(directory.toAbsolutePath().normalize())
            &&receipt.pack().getShaderPackOptions().getOptionValues()==values&&receipt.pack().getShaderPackOptions().getOptionSet()==declarations
            &&receipt.input().options().values().equals(options);
    }

    public static WorldRenderingPipeline build(Supplier<WorldRenderingPipeline> factory){
        if(BUILD.get()!=null){invalidate();return factory.get();}
        long observedGeneration=generation;
        var pack=Iris.getCurrentPack().orElse(null);String selected=Iris.getIrisConfig().getShaderPackName().orElse(null);
        if(!(pack instanceof IrisCompiledSource.PackView view)||view.shaderlod$makeupPackInput()==null||selected==null
                ||!Path.of(selected).getFileName().toString().equals(selected))return factory.get();
        var input=view.shaderlod$makeupPackInput();
        var context=new Loaded(pack,input,selected,Resources.capture(),null,null,new SourceProofEpoch());
        var path=Iris.getShaderpacksDirectory().resolve(selected);
        String stamp;String family;
        try{stamp=SourceArchiveDigest.sha256(path);if(!matchesArchive(path,input))return factory.get();
            var snapshot=com.pp.shaderlod.packs.PackSelectionService.captureGeneratedRendererInputs(path,input.options().values());
            family=snapshot==null?null:snapshot.adapterId();
            if(snapshot!=null&&!IrisCompiledSource.matchesRaw(snapshot,input,family))return factory.get();}
        catch(Exception|LinkageError unavailable){return factory.get();}
        try(var attempt=context.proof().begin()){
            BUILD.set(new Build(context,attempt));
            try{
                SourceValidationWarmup.qualify(pack);
                var pipeline=factory.get();
                var result=new Loaded(pack,input,selected,context.resources(),pipeline,family,context.proof());
                if(attempt.publish(pipeline,()->{
                    try{return pipeline!=null&&pipeline.getClass()==net.irisshaders.iris.pipeline.IrisRenderingPipeline.class&&!Iris.isFallback()&&generation==observedGeneration&&result.current()&&stamp.equals(SourceArchiveDigest.sha256(path));}
                    catch(Exception|LinkageError unavailable){return false;}
                }))LOADED.put(pipeline,result);
                return pipeline;
            }finally{BUILD.remove();}
        }
    }
    /** Qualifies the actual raw graph and property text read by Iris, even for same-stat replacements. */
    static boolean matchesArchive(Path path,IrisCompiledSource.PackInput input)throws Exception {
        if(input.observation()==null||!input.observation().matchesArchive(path)||!input.options().current(input.options().owner())||!input.options().captured().rawGraph().getFailures().isEmpty()
                ||!input.options().rewritten().getFailures().isEmpty())return false;
        try(var archive=new ZipFile(path.toFile())){
            var names=new HashSet<String>();var entries=archive.entries();
            while(entries.hasMoreElements()){var entry=entries.nextElement();if(!names.add(entry.getName()))return false;}
            for(var entry:input.options().captured().rawGraph().getNodes().entrySet()){
                var member=archive.getEntry("shaders"+entry.getKey().getPathString());if(member==null||member.isDirectory())return false;
                String text;try(var in=archive.getInputStream(member)){text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(in.readAllBytes())).toString();}
                if(!List.of(text.split("\\R")).equals(entry.getValue().getLines()))return false;
            }
            for(String name:List.of("shaders.properties","dimension.properties")){
                var member=archive.getEntry("shaders/"+name);String observed=input.propertyText().get(name);
                if(member==null){if(observed!=null)return false;}
                else {try(var in=archive.getInputStream(member)){if(!new String(in.readAllBytes(),StandardCharsets.ISO_8859_1).equals(observed))return false;}}
            }
            return true;
        }
    }
}
