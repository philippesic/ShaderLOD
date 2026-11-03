package com.pp.shaderlod.dev;

import com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource;
import com.pp.shaderlod.packs.SupportedPackCatalog;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;

/** Untimed native-only proof. Never called on a draw path and never compiles its probe. */
final class NativeMacroReplay {
    private NativeMacroReplay() {}
    private static final int MAX_BYTES=32*1024*1024;
    private static final List<String> FLAGS=List.of("OVERWORLD","IS_IRIS","CUSTOM_PBR","GENERATED_NORMALS","COATED_TEXTURES","DISTANT_HORIZONS","VOXY","VOXY_PATCH","PIXELATED_SHADOWS","PIXELATED_BLOCKLIGHT","PIXELATED_AO","SNOWY_WORLD","COLOR_CODED_PROGRAMS","TAA");
    private static final List<String> VALUES=List.of("RP_MODE","COLORED_LIGHTING_INTERNAL","COLORED_LIGHTING","RAIN_PUDDLES","SHOW_LIGHT_LEVEL","MC_VERSION","IRIS_VERSION");
    record Proof(String archiveSha256,String sourceDigest,IrisCompiledSource.Stages stages,Map<String,Object> defines,List<String> laterWrites) {}
    static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Native proof rejected: "+message);}
    static Map<String,byte[]> archive(Path path,String family)throws Exception{
        require(Files.isRegularFile(path)&&Files.size(path)<=256L*1024*1024,"missing/oversized archive");
        Map<String,byte[]> members=new TreeMap<>();int total=0;
        try(var zip=new ZipFile(path.toFile())){
            var entries=zip.entries();Set<String> names=new HashSet<>();
            while(entries.hasMoreElements()){
                var e=entries.nextElement();String n=e.getName();require(names.add(n),"duplicate archive member");
                if(e.isDirectory()||!SupportedPackCatalog.isSourceMember(n))continue;
                require(n.startsWith("shaders/")&&!n.contains("\\")&&!Arrays.asList(n.split("/")).contains(".."),"unsafe member");
                try(var in=zip.getInputStream(e)){byte[] bytes=in.readNBytes(MAX_BYTES-total+1);total+=bytes.length;require(total<=MAX_BYTES,"archive source bound");members.put(n,bytes);}
            }
        }
        var hashes=new TreeMap<String,String>();for(var e:members.entrySet())hashes.put(e.getKey(),sha(e.getValue()));
        var recognition=SupportedPackCatalog.recognize(hashes).orElseThrow(()->new IllegalStateException("Native proof rejected: current catalog original missing"));
        require(recognition.stage()==SupportedPackCatalog.Stage.RECOGNIZED_ORIGINAL&&family.equals(recognition.pack().id())&&"r5.9.3".equals(recognition.pack().version()),"catalog family/version/original");
        return members;
    }
    static void raw(Map<String,byte[]> members,IrisCompiledSource.OptionsInput options,Map<String,String> properties)throws Exception{
        require(options!=null&&options.captured().rawGraph().getFailures().isEmpty()&&options.rewritten().getFailures().isEmpty(),"graph failures");
        require(options.current(options.owner())&&options.owner().getOptionSet().getBooleanOptions().keySet().equals(options.captured().booleanOptions().keySet())&&options.owner().getOptionSet().getStringOptions().keySet().equals(options.captured().stringOptions().keySet()),"complete current options");
        for(var node:options.captured().rawGraph().getNodes().entrySet()){
            byte[] b=members.get("shaders"+node.getKey().getPathString());require(b!=null,"raw graph member absent");
            String s=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(b)).toString();
            require(List.of(s.split("\\R")).equals(node.getValue().getLines()),"raw UTF8 lines differ");
        }
        require(properties.keySet().stream().allMatch(Set.of("shaders.properties","dimension.properties")::contains),"unexpected property observation");
        for(String name:List.of("shaders.properties","dimension.properties")){
            byte[] b=members.get("shaders/"+name);String expected=b==null?null:new String(b,StandardCharsets.ISO_8859_1);
            require(Objects.equals(expected,properties.get(name)),"ISO8859-1 property/absence differs: "+name);
        }
    }
    static IrisCompiledSource.Stages provider(Function<AbsolutePackPath,String> provider,AbsolutePackPath directory){
        String b=directory.getPathString()+"/gbuffers_terrain";
        return new IrisCompiledSource.Stages(provider.apply(AbsolutePackPath.fromAbsolutePath(b+".vsh")),provider.apply(AbsolutePackPath.fromAbsolutePath(b+".gsh")),provider.apply(AbsolutePackPath.fromAbsolutePath(b+".tcs")),provider.apply(AbsolutePackPath.fromAbsolutePath(b+".tes")),provider.apply(AbsolutePackPath.fromAbsolutePath(b+".fsh")));
    }
    static Proof prove(Path archive,String family,IrisCompiledSource.OptionsInput options,Map<String,String> properties,List<StringPair> defines,AbsolutePackPath directory,IrisCompiledSource.Stages consumed,Function<AbsolutePackPath,String> provider)throws Exception{
        require("/world0".equals(directory.getPathString()),"dimension");
        var members=archive(archive,family);raw(members,options,properties);
        var replay=IrisCompiledSource.replay(options,defines,directory);
        require(replay!=null&&replay.vertex()!=null&&replay.fragment()!=null&&replay.geometry()==null&&replay.control()==null&&replay.evaluation()==null&&replay.equals(consumed)&&replay.equals(provider(provider,directory)),"five replay/consumed/provider stages");
        var hashes=new TreeMap<String,String>();for(var e:members.entrySet())hashes.put(e.getKey(),sha(e.getValue()));
        var writes=new ArrayList<String>();var macros=normalized(options,defines,writes);
        // Full ZIP identity is recorded alongside the current catalog executable/control digest.
        String digest;try(var in=Files.newInputStream(archive)){var md=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];int n;while((n=in.read(b))>=0)md.update(b,0,n);digest=HexFormat.of().formatHex(md.digest());}
        require(digest.equals(switch(family){case "complementary-reimagined"->"fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38";case "complementary-unbound"->"98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a";default->"";}),"authentic native archive digest");
        return new Proof(digest,SupportedPackCatalog.shaderSourceDigest(hashes),replay,macros,List.copyOf(writes));
    }
    private static String expanded(IncludeProcessor p,String file){var lines=p.getIncludedFile(AbsolutePackPath.fromAbsolutePath(file));require(lines!=null,"expanded member absent");return String.join("\n",lines)+"\n";}
    static Map<String,Object> normalized(IrisCompiledSource.OptionsInput options,List<StringPair> defines,List<String> writes){
        var p=new IncludeProcessor(options.rewritten());String full=expanded(p,"/world0/gbuffers_terrain.fsh"),lighting=expanded(p,"/lib/lighting/mainLighting.glsl");
        require(!full.contains("SLD_NATIVE_PROBE_"),"probe namespace collision");
        int start=full.indexOf(lighting);require(start>=0&&full.indexOf(lighting,start+1)<0,"unique spatial normalization point");int point=start+lighting.length();
        // This is exactly the native include boundary where the spatial option/export is inserted.
        // Observe every subsequent relevant define/undef inside its ORIGINAL conditional context.
        // A temporary change followed by restoration must not be mistaken for stability.
        var relevant=new ArrayList<String>(FLAGS);relevant.addAll(VALUES);
        var mutation=Pattern.compile("^\\s*#\\s*(?:define|undef)\\s+("+String.join("|",relevant)+")\\b.*");
        StringBuilder probe=new StringBuilder(full.substring(0,point)).append(probe(0));int count=1;
        for(String line:full.substring(point).split("\n",-1)){
            probe.append(line).append('\n');if(mutation.matcher(line).matches()){
                require(!line.endsWith("\\"),"continued relevant macro mutation");writes.add(line.trim());probe.append(probe(count++));
            }
        }
        probe.append(probe(count++));require(probe.length()<=8*1024*1024,"expanded probe bound");
        String output=JcppProcessor.glslPreprocessSource(probe.toString(),defines);
        var first=readProbe(output,0,true);
        for(int i=1;i<count;i++){var state=readProbe(output,i,false);if(!state.isEmpty())require(first.equals(state),"later active macro mutation differs at checkpoint "+i);}
        require(!readProbe(output,count-1,true).isEmpty(),"end stability probe absent");return Map.copyOf(first);
    }
    private static String probe(int point){
        StringBuilder b=new StringBuilder("\n");int i=0;
        for(String key:FLAGS){String token="SLD_NATIVE_PROBE_"+point+"_"+(i++);b.append("#ifdef ").append(key).append('\n').append(token).append(" true\n#else\n").append(token).append(" false\n#endif\n");}
        for(String key:VALUES){String token="SLD_NATIVE_PROBE_"+point+"_"+(i++);b.append("#ifdef ").append(key).append('\n').append(token).append(" ").append(key).append(" SLD_NATIVE_END\n#else\n").append(token).append(" SLD_NATIVE_ABSENT SLD_NATIVE_END\n#endif\n");}return b.toString();
    }
    private static Map<String,Object> readProbe(String output,int point,boolean required){
        Map<String,Object> out=new TreeMap<>();var keys=new ArrayList<String>(FLAGS);keys.addAll(VALUES);
        for(int i=0;i<keys.size();i++){
            var m=Pattern.compile("(?m)^[ \\t]*SLD_NATIVE_PROBE_"+point+"_"+i+"[ \\t]+([^\\r\\n]+)$").matcher(output);
            if(!m.find()){require(!required,"normalization probe inactive");continue;}String value=m.group(1).trim();require(!m.find(),"duplicate probe result");
            if(i<FLAGS.size()){require(Set.of("true","false").contains(value),"boolean macro probe");out.put(keys.get(i),Boolean.valueOf(value));}
            else{require(value.endsWith("SLD_NATIVE_END"),"numeric macro delimiter");value=value.substring(0,value.length()-"SLD_NATIVE_END".length()).trim();require(!value.isEmpty(),"empty numeric expansion");out.put(keys.get(i),value.equals("SLD_NATIVE_ABSENT")?Boolean.FALSE:value);}
        }
        require(out.isEmpty()||out.size()==keys.size(),"partial probe result");return out;
    }
}
