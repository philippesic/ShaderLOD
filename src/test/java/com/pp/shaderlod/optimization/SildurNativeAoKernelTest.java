package com.pp.shaderlod.optimization;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SildurNativeAoKernelTest {
    private static final String CORE="(defined GL_ARB_shader_texture_lod || (defined IS_IRIS && defined MC_GLSL_VERSION && MC_GLSL_VERSION >= 330))";
    private String resource(String path)throws Exception {
        try(var in=getClass().getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/"+path)){
            assertNotNull(in);return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private static String function(String text,String signature){
        int begin=text.indexOf(signature);assertTrue(begin>=0,signature);assertEquals(-1,text.indexOf(signature,begin+1));
        int end=text.indexOf('{',begin)+1,depth=1;
        while(depth>0){char c=text.charAt(end++);if(c=='{')depth++;else if(c=='}')depth--;}
        return text.substring(begin,end);
    }
    @Test void sevenConsumersRetainNativeKernelAndPriorDynamicKernel()throws Exception {
        var recipe=JsonParser.parseString(resource("sildurs-vibrant-shaders.json")).getAsJsonObject();var patches=recipe.getAsJsonArray("patches");
        // These unchanged Nether/End preimages contain the authenticated original Overworld kernel.
        String original=function(patches.get(75).getAsJsonObject().get("before").getAsString(),"float funSSDO(");
        assertEquals(original,function(patches.get(78).getAsJsonObject().get("before").getAsString(),"float funSSDO("));
        String prior=function(patches.get(42).getAsJsonObject().get("after").getAsString(),"float funSSDO(");
        var consumers=new LinkedHashMap<String,String>();
        for(String name:List.of("sildur-ao/lighting.glsl","sildur-ao/producer.glsl","sildur-spatial/basis.glsl","sildur-spatial/consumer.glsl","sildur-world/native.glsl","sildur-world/consumer.glsl"))consumers.put(name,resource(name));
        consumers.put("deferred.fsh",patches.get(81).getAsJsonObject().get("after").getAsString());
        for(var entry:consumers.entrySet()){
            boolean producer=entry.getKey().equals("sildur-ao/producer.glsl");String source=entry.getValue();
            String expectedNative=original.replace("float funSSDO(","float shaderlodSildurNativeSSDO(");
            String expectedDynamic=prior.replace("float funSSDO(","float shaderlodSildurDynamicSSDO(");
            if(producer){expectedNative=expectedNative.replace("texcoord","shaderlodSildurAoCoord");expectedDynamic=expectedDynamic.replace("texcoord","shaderlodSildurAoCoord");}
            assertEquals(expectedNative,function(source,"float shaderlodSildurNativeSSDO("),entry.getKey());
            String dynamic=function(source,"float shaderlodSildurDynamicSSDO(").replace(CORE,"defined GL_ARB_shader_texture_lod");
            assertEquals(expectedDynamic,dynamic,entry.getKey());
            String dispatch=function(source,"float funSSDO(");
            for(boolean taa:List.of(false,true))for(String exclusion:List.of("none","DISTANT_HORIZONS","VOXY")){
                var defines=new ArrayList<StringPair>();defines.add(new StringPair("SHADERLOD_EFFECT_ADAPTER","1"));
                if(taa)defines.add(new StringPair("TAA","1"));if(!exclusion.equals("none"))defines.add(new StringPair(exclusion,"1"));
                String actual=JcppProcessor.glslPreprocessSource(dispatch,defines);
                assertTrue(actual.contains("return shaderlodSildurNativeSSDO(noise,fragpos,normal);"));
                assertEquals(exclusion.equals("none"),actual.contains("return shaderlodSildurDynamicSSDO"));
                for(String flag:List.of("shaderlodOptAoBudget","shaderlodOptAoDepthReuse","shaderlodOptAoSkyElision"))
                    assertEquals(exclusion.equals("none"),actual.contains("shaderlod_optimization_enabled("+flag+")"));
            }
        }
        for(int index:List.of(75,78)){
            String text=patches.get(index).getAsJsonObject().get("after").getAsString();
            assertEquals(original,function(text,"float funSSDO("));assertFalse(text.contains("shaderlodSildurDynamicSSDO"));
        }
        assertTrue(consumers.get("sildur-ao/producer.glsl").contains("floor(gl_FragCoord.xy)*2.0+vec2(1.5)"));
        assertTrue(consumers.get("sildur-spatial/basis.glsl").contains("#define texture2D(s,c) texture2DLod(s,c,0.0)"));
        assertTrue(consumers.get("sildur-world/consumer.glsl").contains("#define texture2D(s,c) (shaderlodSildurWorldReady==1?"));
    }
}
