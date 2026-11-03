package com.pp.shaderlod.optimization;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SildurCoreLodTest {
    private static final String CORE = "(defined GL_ARB_shader_texture_lod || (defined IS_IRIS && defined MC_GLSL_VERSION && MC_GLSL_VERSION >= 330))";
    private String resource(String path) throws Exception {
        try (var in=getClass().getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/"+path)) {
            assertNotNull(in); return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private static String function(String source,String signature) {
        int begin=source.indexOf(signature);assertTrue(begin>=0,signature);
        int end=source.indexOf('{',begin)+1,depth=1;
        while(depth>0){char c=source.charAt(end++);if(c=='{')depth++;else if(c=='}')depth--;}
        return source.substring(begin,end);
    }
    private List<String> consumers() throws Exception {
        var patches=JsonParser.parseString(resource("sildurs-vibrant-shaders.json")).getAsJsonObject().getAsJsonArray("patches");
        String finalDeferred=null;
        for(var e:patches){
            var p=e.getAsJsonObject();
            if(!p.get("path").getAsString().equals("shaders/deferred.fsh"))continue;
            String before=p.get("before").getAsString(),after=p.get("after").getAsString();
            // Later recipe operations replace individual functions. Observe the
            // final consumer rather than treating its last function as a file.
            if(after.contains("void main("))finalDeferred=after;
            else if(finalDeferred!=null){
                int at=finalDeferred.indexOf(before);
                assertTrue(at>=0,"Missing subsequent deferred patch anchor");
                assertEquals(at,finalDeferred.lastIndexOf(before),"Ambiguous subsequent deferred patch anchor");
                finalDeferred=finalDeferred.substring(0,at)+after+finalDeferred.substring(at+before.length());
            }
        }
        assertNotNull(finalDeferred);
        return List.of(resource("sildur-ao/lighting.glsl"),resource("sildur-ao/producer.glsl"),finalDeferred);
    }
    private static String preprocess(String body,Map<String,String> additions) {
        var defines=new ArrayList<StringPair>();
        defines.add(new StringPair("SHADERLOD_EFFECT_ADAPTER","1"));defines.add(new StringPair("SSDO","1"));defines.add(new StringPair("TAA","1"));
        additions.forEach((k,v)->defines.add(new StringPair(k,v)));
        return JcppProcessor.glslPreprocessSource(body,defines);
    }
    @Test void authenticAoBranchesUseIrisGlslCapabilityAndPreserveUnsupportedPaths() throws Exception {
        record Case(Map<String,String> defines,boolean explicit) {}
        var cases=List.of(
            new Case(Map.of(),false),
            new Case(Map.of("GL_ARB_shader_texture_lod","1"),true),
            new Case(Map.of("IS_IRIS","1","MC_GLSL_VERSION","330"),true),
            new Case(Map.of("IS_IRIS","1","MC_GLSL_VERSION","410","MC_GL_VERSION","41"),true),
            new Case(Map.of("MC_GLSL_VERSION","410"),false),
            new Case(Map.of("IS_IRIS","1"),false),
            new Case(Map.of("IS_IRIS","1","MC_GLSL_VERSION","329"),false),
            new Case(Map.of("IS_IRIS","1","MC_GLSL_VERSION","410","DISTANT_HORIZONS","1"),false),
            new Case(Map.of("IS_IRIS","1","MC_GLSL_VERSION","410","VOXY","1"),false));
        for(var source:consumers())for(var test:cases){
            String kernel=function(source,"float shaderlodSildurDynamicSSDO(");
            String actual=preprocess(kernel,test.defines());
            assertEquals(test.explicit(),actual.contains("? texture2DLod(depthtex0,newTC,0.0).x : texture2D(depthtex0,newTC).x"),test.defines().toString());
            // Every selected path retains literal native implicit sampling when the live flag is Off.
            assertTrue(actual.contains("texture2D(depthtex0, newTC).x")||actual.contains(": texture2D(depthtex0,newTC).x"));
            if(source.contains("float shaderlodSildurNativeAo(")){
                String dispatch=function(source,"float shaderlodSildurNativeAo(");
                assertEquals(test.explicit(),preprocess(dispatch,test.defines()).contains("shaderlodAoUnused = shaderlodAoUnused ||"));
            }else if(source.contains("bool shaderlodAoUnused")){
                int begin=source.indexOf("\tfloat ao = 1.0;");assertTrue(begin>=0);
                int end=source.indexOf("//Emissive lighting",begin);assertTrue(end>begin);
                assertEquals(test.explicit(),preprocess(source.substring(begin,end),test.defines()).contains("shaderlodAoUnused = shaderlodAoUnused ||"));
            }
        }
    }
    @Test void onlyElevenFeatureGuardsChangeAndExtensionDirectiveStaysExtensionOnly() throws Exception {
        var sources=consumers();assertEquals(11,sources.stream().mapToInt(s->s.split(java.util.regex.Pattern.quote(CORE),-1).length-1).sum());
        for(var source:sources){
            if(source.contains("#extension GL_ARB_shader_texture_lod : enable"))assertTrue(source.contains("#ifdef GL_ARB_shader_texture_lod\n#extension GL_ARB_shader_texture_lod : enable\n#endif"));
        }
        var patches=JsonParser.parseString(resource("sildurs-vibrant-shaders.json")).getAsJsonObject().getAsJsonArray("patches");
        String previous=patches.get(42).getAsJsonObject().get("after").getAsString();
        assertEquals(function(previous,"float funSSDO("),function(sources.get(0),"float shaderlodSildurDynamicSSDO(").replace("float shaderlodSildurDynamicSSDO(","float funSSDO(").replace(CORE,"defined GL_ARB_shader_texture_lod"));
        assertEquals(function(previous,"float funSSDO("),function(sources.get(2),"float shaderlodSildurDynamicSSDO(").replace("float shaderlodSildurDynamicSSDO(","float funSSDO(").replace(CORE,"defined GL_ARB_shader_texture_lod"));
    }
}
