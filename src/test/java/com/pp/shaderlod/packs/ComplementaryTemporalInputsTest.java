package com.pp.shaderlod.packs;
import com.google.common.collect.*;
import com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ComplementaryTemporalInputsTest {
    @Test void actualQualityThreeThroughFiveSurvivesSnapshotReplayAndCurrentChecksWithoutReceiver() throws Exception {
        var text=Map.of("/settings.glsl","#define SHADOW_QUALITY 2 // [2 3 4 5]\n",
            "/gbuffers_terrain.vsh","#version 150\n#include \"/settings.glsl\"\nvoid main(){gl_Position=vec4(SHADOW_QUALITY);}\n",
            "/gbuffers_terrain.fsh","#version 150\n#include \"/settings.glsl\"\n#if SHADOW_QUALITY == 2\nuniform int shaderlodComplementaryReceiverPermission;\n#endif\nconst int actualQuality=SHADOW_QUALITY;\nvoid main(){}\n");
        var nodes=ImmutableMap.<AbsolutePackPath,FileNode>builder();var members=new HashMap<String,byte[]>();
        text.forEach((name,value)->{var path=AbsolutePackPath.fromAbsolutePath(name);nodes.put(path,new FileNode(path,ImmutableList.copyOf(value.split("\\R"))));members.put("shaders"+name,value.getBytes(StandardCharsets.UTF_8));});
        var constructor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);constructor.setAccessible(true);
        var raw=constructor.newInstance(nodes.build(),ImmutableMap.of());
        for(String family:List.of("complementary-unbound","complementary-reimagined"))for(String quality:List.of("3","4","5")){
            var options=new ShaderPackOptions(raw,Map.of("SHADOW_QUALITY",quality));var input=IrisCompiledSource.OptionsInput.capture(options,raw);
            var snapshot=new CapturedRendererInputs(family,input.values(),members);
            assertEquals(quality,snapshot.effectiveOptions().get("SHADOW_QUALITY"));assertTrue(IrisCompiledSource.matchesRaw(snapshot,input,family));
            var normalized=new TreeMap<>(input.values());normalized.put("SHADOW_QUALITY","2");
            assertFalse(IrisCompiledSource.matchesRaw(new CapturedRendererInputs(family,normalized,members),input,family));
            var stages=IrisCompiledSource.replay(input,List.of(),AbsolutePackPath.fromAbsolutePath("/"));
            assertTrue(stages.fragment().matches("(?s).*actualQuality\\s*=\\s*"+quality+".*"));
            assertFalse(stages.fragment().contains("shaderlodComplementaryReceiverPermission"));
            assertTrue(input.current(options));((MutableOptionValues)options.getOptionValues()).addAll(Map.of("SHADOW_QUALITY","2"));assertFalse(input.current(options));
            assertEquals(stages,IrisCompiledSource.replay(input,List.of(),AbsolutePackPath.fromAbsolutePath("/")));
        }
    }
}
