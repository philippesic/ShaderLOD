package com.pp.shaderlod.integration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.makeupquad.GeneratedRendererEligibility;
import com.pp.shaderlod.optimization.makeupao.MakeUpAoContract;
import com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource;
import com.pp.shaderlod.packs.PackSelectionService;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipFile;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MakeUpAlphaVertexContractTest {
    @TempDir Path directory;
    private static Map<String,byte[]> archive(Path path)throws Exception {
        var result=new TreeMap<String,byte[]>();try(var zip=new ZipFile(path.toFile())){
            for(var entry:Collections.list(zip.entries()))if(!entry.isDirectory())result.put(entry.getName(),zip.getInputStream(entry).readAllBytes());
        }return result;
    }
    private static IncludeGraph vertexGraph(Map<String,byte[]> files,List<String> roots)throws Exception {
        var pending=new ArrayDeque<AbsolutePackPath>();
        for(var root:roots)pending.add(AbsolutePackPath.fromAbsolutePath(root.substring("shaders".length())));
        var nodes=new LinkedHashMap<AbsolutePackPath,FileNode>();
        while(!pending.isEmpty()){
            var path=pending.removeFirst();if(nodes.containsKey(path))continue;
            var bytes=Objects.requireNonNull(files.get("shaders"+path.getPathString()));
            var node=new FileNode(path,ImmutableList.copyOf(new String(bytes,StandardCharsets.UTF_8).split("\\R")));
            nodes.put(path,node);pending.addAll(node.getIncludes().values());
        }
        var constructor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);constructor.setAccessible(true);
        return constructor.newInstance(ImmutableMap.copyOf(nodes),ImmutableMap.of());
    }
    private static String tokens(String source){return source.replaceAll("(?s)/\\*.*?\\*/","").replaceAll("(?m)//[^\\r\\n]*","").replaceAll("(?m)^\\s*#line[^\\r\\n]*","").replaceAll("\\s+","");}
    @Test void unusedPresenceDefinitionPreservesEveryActiveVertexAndCpuAdmission()throws Exception {
        Files.copy(Path.of("dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip"),directory.resolve("Original.zip"));
        var result=new PackSelectionService().apply(directory,"Original.zip",true,LodPreset.PERFORMANCE,Map.of(),false);
        assertTrue(result.accepted(),result.detail());Path generated=directory.resolve(result.packFile());
        var files=archive(generated);
        var roots=files.keySet().stream().filter(p->p.startsWith("shaders/")&&Path.of(p).getFileName().toString().startsWith("gbuffers_")&&p.endsWith(".vsh")).toList();
        assertFalse(roots.isEmpty());String helper="shaders/lib/shaderlod/makeup_ao_option.glsl";
        String current=new String(files.get(helper),StandardCharsets.UTF_8);
        String addition="\n// Presence follows the EARLY_ALPHA request through the normal owned reload.\n#define SHADERLOD_MAKEUP_EARLY_ALPHA 0 // [0 1]\n";
        assertTrue(current.endsWith(addition));
        var previous=new TreeMap<>(files);previous.put(helper,current.substring(0,current.length()-addition.length()).getBytes(StandardCharsets.UTF_8));
        // Reconstruct the certified predecessor platform guards; both exact
        // and framecopy routes remain inactive in this adapter1 proof.
        String asahiGate="(defined MC_OS_MAC || (defined MC_OS_LINUX && ((SHADERLOD_G06_AO_ATTENUATION == 1 && defined SHADERLOD_G06_ASAHI_RUNTIME_GUARD) || (SHADERLOD_G04_TRANSLUCENT_FASTPATH == 1 && defined SHADERLOD_G04_ASAHI_RUNTIME_GUARD))))";
        int changedGuards=0;
        for(String root:roots){
            String next=new String(previous.get(root),StandardCharsets.UTF_8);
            if(next.contains(asahiGate)){
                next=next.replace(asahiGate,"defined MC_OS_MAC");
                if(root.endsWith("gbuffers_armor_glint.vsh"))next=next.replace("(defined MC_OS_MAC || defined MC_OS_LINUX)","defined MC_OS_MAC");
                previous.put(root,next.getBytes(StandardCharsets.UTF_8));changedGuards++;
            }
        }
        assertEquals(18,changedGuards);
        var oldGraph=vertexGraph(previous,roots);var oldOptions=new ShaderPackOptions(oldGraph,Map.of("SHADERLOD_REFINEMENT_ADAPTER","1"));
        var oldInput=IrisCompiledSource.OptionsInput.capture(oldOptions,oldGraph);
        var defines=List.of(new StringPair("IS_IRIS","1"),new StringPair("IRIS_VERSION","10812"),new StringPair("MC_VERSION","12101"));
        for(String mode:List.of("0","1")){
            // Use Iris's real override across all declarations, including G01's
            // peer-control declaration, rather than conflicting raw defaults.
            var graph=vertexGraph(files,roots);var options=new ShaderPackOptions(graph,
                Map.of("SHADERLOD_REFINEMENT_ADAPTER","1","SHADERLOD_MAKEUP_EARLY_ALPHA",mode));
            var input=IrisCompiledSource.OptionsInput.capture(options,graph);
            // G01's exclusive route now inspects peer controls in inactive branches.
            // Actual Iris replay below must still preserve every active vertex token
            // when early-alpha changes under this adapter1 graph.
            for(String root:roots){
                String path=root.substring("shaders".length());int slash=path.lastIndexOf('/');
                var dir=AbsolutePackPath.fromAbsolutePath(path.substring(0,slash+1));String name=path.substring(slash+1,path.length()-4);
                var old=IrisCompiledSource.replay(oldInput,defines,dir,name);var next=IrisCompiledSource.replay(input,defines,dir,name);
                assertNotNull(old);assertNotNull(next);assertNotNull(old.vertex());assertNotNull(next.vertex());
                assertEquals(tokens(old.vertex()),tokens(next.vertex()),root+" mode"+mode);
            }
            var effective=new TreeMap<>(MakeUpAoContract.generated("0"));effective.put("SHADERLOD_MAKEUP_EARLY_ALPHA",mode);
            assertTrue(GeneratedRendererEligibility.validate(generated,effective),"Normal regenerated source must retain CPU admission in both modes");
        }
        try(var zip=FileSystems.newFileSystem(generated)){
            var path=zip.getPath("/"+helper);Files.writeString(path,Files.readString(path)+"\n#define FOREIGN_VERTEX_INPUT 1\n");
        }
        assertFalse(GeneratedRendererEligibility.validate(generated,MakeUpAoContract.generated("0")),"Refreshed exact closure still rejects unapproved source changes");
    }
}
