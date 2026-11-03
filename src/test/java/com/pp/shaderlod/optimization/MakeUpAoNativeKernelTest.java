package com.pp.shaderlod.optimization;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MakeUpAoNativeKernelTest {
 private String resource(String path)throws Exception{try(var in=getClass().getResourceAsStream("/assets/shaderlod/adapters/"+path)){assertNotNull(in);return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
 private static String function(String text,String signature){int begin=text.indexOf(signature);assertTrue(begin>=0,signature);int brace=text.indexOf('{',begin),depth=1,end=brace+1;for(;depth>0&&end<text.length();end++){char c=text.charAt(end);if(c=='{')depth++;else if(c=='}')depth--;}assertEquals(0,depth);return text.substring(begin,end);}
 @Test void nativeDispatchRetainsExactOriginalKernelAndProducerUv()throws Exception{
  var recipe=JsonParser.parseString(resource("patches/adapters/makeup-ultra-fast-shaders.json")).getAsJsonObject();JsonObject ao=null;
  for(var element:recipe.getAsJsonArray("patches")){var patch=element.getAsJsonObject();if(ao==null&&patch.get("path").getAsString().equals("shaders/lib/ao.glsl"))ao=patch;}
  assertNotNull(ao);String original=function(ao.get("before").getAsString(),"float dbao(float dither)");String after=ao.get("after").getAsString();
  assertEquals(original.replace("float dbao(float dither)","float shaderlodMakeupNativeAo(float dither)"),function(after,"float shaderlodMakeupNativeAo(float dither)"));
  String producer=resource("patches/adapters/makeup_ao_kernel.glsl");
  assertEquals(original.replace("float dbao(float dither)","float shaderlodMakeupNativeAoAt(vec2 shaderlodMakeupNativeAoUv, float dither)").replace("texcoord.xy","shaderlodMakeupNativeAoUv"),function(producer,"float shaderlodMakeupNativeAoAt(vec2 shaderlodMakeupNativeAoUv, float dither)"));
  for(var defines:List.of(List.<StringPair>of(),List.of(new StringPair("DEFERRED_SHADER","1")),List.of(new StringPair("DEFERRED_SHADER","1"),new StringPair("DISTANT_HORIZONS","1")))){
   String actual=JcppProcessor.glslPreprocessSource("#define SHADERLOD_REFINEMENT_ADAPTER 1\n"+after.replace("#include \"/lib/shaderlod/adapter.glsl\"",""),defines);
   String dispatch=function(actual,"float dbao(float dither)");
   boolean dynamic=defines.size()==1;
   assertEquals(dynamic,dispatch.contains("if (!shaderlod_optimization_enabled(shaderlodOptAoBudget) && !shaderlod_optimization_enabled(shaderlodOptAoDepthReuse))"));
   assertTrue(dispatch.contains("return shaderlodMakeupNativeAo(dither);"));
   assertEquals(original.replace("float dbao(float dither)","float shaderlodMakeupNativeAo(float dither)"),function(actual,"float shaderlodMakeupNativeAo(float dither)"));
  }
 }
}
