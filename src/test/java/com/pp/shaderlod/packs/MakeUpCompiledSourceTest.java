package com.pp.shaderlod.packs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpCompiledSource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Iris option rewriting/include/preprocessing APIs, not substitute shader compilation. */
class MakeUpCompiledSourceTest {
    @Test void rawOverrideFastPathDetectsDirectBackingMapMutations() throws Exception {
        var text=Map.of("/gbuffers_terrain.fsh","#define FEATURE\n#define QUALITY 2 // [1 2 3]\n#ifdef FEATURE\nconst int active=1;\n#endif\n");
        var raw=graph(text);var options=new ShaderPackOptions(raw,Map.of("FEATURE","false","QUALITY","3"));
        var input=MakeUpCompiledSource.OptionsInput.capture(options,raw);
        assertNotNull(input.overrides(),"pinned Iris option/default implementations admit sparse override comparison");
        assertTrue(input.current(options));
        var values=(MutableOptionValues)options.getOptionValues();
        values.getBooleanValues().put("FEATURE",true);assertFalse(input.current(options));
        values.getBooleanValues().put("FEATURE",false);assertTrue(input.current(options));
        values.getStringValues().remove("QUALITY");assertFalse(input.current(options));
        values.getStringValues().put("QUALITY","3");assertTrue(input.current(options));
        values.getStringValues().put("UNKNOWN_OVERRIDE","anything");assertFalse(input.current(options));
        assertThrows(UnsupportedOperationException.class,()->input.overrides().strings().put("QUALITY","1"));
    }
    private static IncludeGraph graph(Map<String,String> text) throws Exception {
        var nodes=ImmutableMap.<AbsolutePackPath,FileNode>builder();
        for(var entry:text.entrySet()) {
            var path=AbsolutePackPath.fromAbsolutePath(entry.getKey());
            nodes.put(path,new FileNode(path,ImmutableList.copyOf(entry.getValue().split("\\R"))));
        }
        var constructor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);
        constructor.setAccessible(true);return constructor.newInstance(nodes.build(),ImmutableMap.of());
    }
    private static Map<String,String> sources() {
        return Map.of("/settings.glsl","#define QUALITY 2 // [1 2 3]\n",
            "/gbuffers_terrain.vsh","#version 150\n#include \"/settings.glsl\"\nvoid main(){gl_Position=vec4(QUALITY);}\n",
            "/gbuffers_terrain.fsh","#version 150\n#include \"/settings.glsl\"\n#if CERT_TEST == 1\nconst int observed=QUALITY;\n#else\nconst int observed=99;\n#endif\nvoid main(){}\n");
    }
    @Test void rawGraphMustMatchVerifiedSnapshotAndCurrentOptionsMustRemainUnchanged() throws Exception {
        var text=sources();var raw=graph(text);var options=new ShaderPackOptions(raw,Map.of("QUALITY","3"));
        var input=MakeUpCompiledSource.OptionsInput.capture(options,raw);
        var members=new HashMap<String,byte[]>();text.forEach((key,value)->members.put("shaders"+key,value.getBytes(StandardCharsets.UTF_8)));
        var snapshot=new CapturedRendererInputs("makeup-ultra-fast-shaders",input.values(),members);
        assertTrue(MakeUpCompiledSource.matchesRaw(snapshot,input));assertTrue(input.current(options));
        var foreign=new ShaderPackOptions(raw,Map.of("QUALITY","3"));assertFalse(input.current(foreign));
        ((MutableOptionValues)options.getOptionValues()).addAll(Map.of("QUALITY","1"));
        assertFalse(input.current(options));
        members.put("shaders/settings.glsl","#define QUALITY 1 // [1 2 3]".getBytes(StandardCharsets.UTF_8));
        assertFalse(MakeUpCompiledSource.matchesRaw(new CapturedRendererInputs("makeup-ultra-fast-shaders",input.values(),members),input));
        assertFalse(MakeUpCompiledSource.matchesRaw(new CapturedRendererInputs("foreign",input.values(),members),input));
    }
    @Test void sharedInputProofRetainsExactAdapterFamilyIdentity() throws Exception {
        var text=sources();var raw=graph(text);var options=new ShaderPackOptions(raw,Map.of("QUALITY","3"));
        var input=MakeUpCompiledSource.OptionsInput.capture(options,raw);var members=new HashMap<String,byte[]>();
        text.forEach((key,value)->members.put("shaders"+key,value.getBytes(StandardCharsets.UTF_8)));
        var bsl=new CapturedRendererInputs("bsl-shaders",input.values(),members);
        assertTrue(com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.matchesRaw(bsl,input,"bsl-shaders"));
        assertFalse(MakeUpCompiledSource.matchesRaw(bsl,input));
        assertFalse(com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.matchesRaw(bsl,input,"photon"));
    }
    @Test void actualIrisReplayRetainsDefinesEffectiveOptionsAndAbsentStages() throws Exception {
        var raw=graph(sources());var options=new ShaderPackOptions(raw,Map.of("QUALITY","3"));
        var input=MakeUpCompiledSource.OptionsInput.capture(options,raw);var directory=AbsolutePackPath.fromAbsolutePath("/");
        var stages=MakeUpCompiledSource.replay(input,List.of(new StringPair("CERT_TEST","1")),directory);
        assertNotNull(stages);assertTrue(stages.fragment().matches("(?s).*observed\\s*=\\s*3.*"));
        assertNull(stages.geometry());assertNull(stages.control());assertNull(stages.evaluation());
        var other=MakeUpCompiledSource.replay(input,List.of(new StringPair("CERT_TEST","0")),directory);
        assertNotEquals(stages,other);assertTrue(other.fragment().matches("(?s).*observed\\s*=\\s*99.*"));
        assertTrue(stages.arguments(stages.vertex(),null,null,null,stages.fragment()));
        assertFalse(stages.arguments(stages.vertex(),"foreign geometry",null,null,stages.fragment()));
        assertFalse(stages.arguments(stages.vertex(),null,null,null,other.fragment()));
        ((MutableOptionValues)options.getOptionValues()).addAll(Map.of("QUALITY","1"));
        assertEquals(stages,MakeUpCompiledSource.replay(input,List.of(new StringPair("CERT_TEST","1")),directory),
            "replay uses captured compile values, not mutable current options");
    }
    @Test void sildurReplayUsesItsActualTexturedProgramInsteadOfTerrain() throws Exception {
        var text=new TreeMap<String,String>();sources().forEach((key,value)->text.put(key.replace("gbuffers_terrain","gbuffers_textured"),value));
        var raw=graph(text);var options=new ShaderPackOptions(raw,Map.of("QUALITY","3"));
        var input=MakeUpCompiledSource.OptionsInput.capture(options,raw);var directory=AbsolutePackPath.fromAbsolutePath("/");
        var actual=com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.replay(input,List.of(new StringPair("CERT_TEST","1")),directory,"gbuffers_textured");
        assertNotNull(actual.vertex());assertNotNull(actual.fragment());
        var missing=MakeUpCompiledSource.replay(input,List.of(new StringPair("CERT_TEST","1")),directory);
        assertNull(missing.vertex());assertNull(missing.fragment());assertNotEquals(actual,missing);
    }
    @Test void propertiesUseActualIrisLatinOneAndPreserveMissingVersusPresent() {
        var snapshot=new CapturedRendererInputs("makeup-ultra-fast-shaders",Map.of(),
            Map.of("shaders/shaders.properties",new byte[]{'#',(byte)0xe9,'\n'}));
        assertTrue(snapshot.matchesIrisProperties("shaders/shaders.properties","#é\n"));
        assertFalse(snapshot.matchesIrisProperties("shaders/shaders.properties","#é"));
        assertFalse(snapshot.matchesIrisProperties("shaders/shaders.properties",null));
        assertTrue(snapshot.matchesIrisProperties("shaders/dimension.properties",null));
        assertFalse(snapshot.matchesIrisProperties("shaders/dimension.properties",""));
    }
    @Test void observedAbsentPropertiesAreRetainedInAnImmutableDefensiveCopy() {
        var observed=new HashMap<String,String>();observed.put("dimension.properties",null);
        observed.put("shaders.properties","# native properties\n");
        var input=new MakeUpCompiledSource.PackInput(null,null,null,List.of(),List.of(),observed);
        assertTrue(input.propertyText().containsKey("dimension.properties"));
        assertNull(input.propertyText().get("dimension.properties"));
        observed.put("dimension.properties","changed after capture");
        assertNull(input.propertyText().get("dimension.properties"));
        assertThrows(UnsupportedOperationException.class,()->input.propertyText().put("other","foreign"));
    }
}
