package com.pp.shaderlod.optimization;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MakeUpCombinationPreprocessTest {
 private static String read(String name)throws Exception{try(var in=MakeUpCombinationPreprocessTest.class.getResourceAsStream(name)){assertNotNull(in,name);return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
 private static String preprocess(String kernel,int copy,int ao,boolean capability,String extra){
  var defines=new StringBuilder("#version 120\n#define IS_IRIS\n#define IRIS_VERSION 10812\n#define MC_VERSION 12101\n#define MC_OS_LINUX\n#define SHADERLOD_G06_ASAHI_RUNTIME_GUARD\n#define AO 1\n#define AOSTEPS 5\n#define AA_TYPE 2\n");
  for(var key:List.of("SHADERLOD_REFINEMENT_ADAPTER","SHADERLOD_G04_TRANSLUCENT_FASTPATH","SHADERLOD_MAKEUP_AO_HALF_RES","SHADERLOD_MAKEUP_DEPTH_PRIMING","SHADERLOD_MAKEUP_SPATIAL_REUSE","SHADERLOD_MAKEUP_SPATIAL_RATE","SHADERLOD_MAKEUP_WORLD_PIXEL","SHADERLOD_MAKEUP_EARLY_ALPHA"))defines.append("#define ").append(key).append(" 0\n");
  defines.append("#define SHADERLOD_MAKEUP_DEAD_FRAME_COPY ").append(copy).append("\n#define SHADERLOD_G06_AO_ATTENUATION ").append(ao).append("\n");
  if(capability)defines.append("#define SHADERLOD_COMBINATION_RUNTIME_GUARD\n");
  return JcppProcessor.glslPreprocessSource(defines+extra+kernel.replaceAll("(?m)^\\s*#include[^\\r\\n]*", ""),List.of()).replaceAll("\\s+", "");
 }
 @Test void conditionalCompositionUsesSameAoCodeAndLeavesExistingModesUnchanged()throws Exception{
  for(var stage:List.of("fragment","vertex")){
   String before=read("/com/pp/shaderlod/optimization/g06-before-composition-"+stage+".glsl"),after=read("/assets/shaderlod/adapters/patches/adapters/makeup-exact/shaders/lib/g06/deferred_"+stage+".glsl");
   for(int copy:new int[]{0,1})for(int ao:new int[]{0,1})for(boolean cap:new boolean[]{false,true}){
    if(copy==1&&ao==1&&cap)continue;
    assertEquals(preprocess(before,copy,ao,cap,""),preprocess(after,copy,ao,cap,""),stage+" native/singleton/refused combination changed");
   }
   String pair=preprocess(after,1,1,true,"");
   assertEquals(preprocess(after,0,1,false,""),pair,stage+" AO code differs from existing singleton");
   if(stage.equals("fragment"))assertTrue(pair.contains("boolshaderlodG06Skipped="));
   for(String refusal:List.of("#define DISTANT_HORIZONS\n","#define VOXY\n","#define NETHER\n","#undef IRIS_VERSION\n#define IRIS_VERSION 19999\n","#undef AA_TYPE\n#define AA_TYPE 1\n","#undef SHADERLOD_MAKEUP_DEPTH_PRIMING\n#define SHADERLOD_MAKEUP_DEPTH_PRIMING 1\n"))
    assertEquals(preprocess(before,1,1,true,refusal),preprocess(after,1,1,true,refusal),stage+" unsupported route changed");
  }
 }
 @Test void incomingCapabilityCannotAuthorizeItself(){
  var input=List.of(new net.irisshaders.iris.helpers.StringPair("SHADERLOD_COMBINATION_RUNTIME_GUARD","1"),new net.irisshaders.iris.helpers.StringPair("SHADERLOD_COMBINATION_BLOOM_RUNTIME_GUARD","1"));
  assertTrue(com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph.constructorDefines(input,false).stream().noneMatch(p->p.key().startsWith("SHADERLOD_COMBINATION")));
 }
 @Test void assembledPairPreservesEveryG01StageExceptTheTwoAoStages()throws Exception{
  var dir=java.nio.file.Files.createTempDirectory("makeup-composition-");
  var field=com.pp.shaderlod.packs.PackSelectionService.class.getDeclaredField("recipeScopedSelections");field.setAccessible(true);boolean previous=field.getBoolean(null);
  try{
   java.nio.file.Files.copy(java.nio.file.Path.of("dependencies/shortlist-test-inputs/originals/MakeUpNativeOriginal.zip"),dir.resolve("Original.zip"));
   var nativeOptions=com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.nativeOptions();
   com.pp.shaderlod.packs.PackSelectionService.enableRecipeScopedSelections();
   var result=new com.pp.shaderlod.packs.PackSelectionService().apply(dir,"Original.zip",true,com.pp.shaderlod.LodPreset.PERFORMANCE,nativeOptions,false,nativeOptions,nativeOptions.keySet());assertTrue(result.accepted(),result.detail());
   try(var zip=new java.util.zip.ZipFile(dir.resolve(result.packFile()).toFile())){
    var cache=new HashMap<String,String>();int checked=0;
    for(var entry:Collections.list(zip.entries())){
     String name=entry.getName();if(!name.startsWith("shaders/world0/")||!(name.endsWith(".fsh")||name.endsWith(".vsh")))continue;
     String flat=expand(zip,name,cache,new HashSet<>());
     String pair=assembled(flat,nativeOptions,1,1,true);
     boolean deferred=name.equals("shaders/world0/deferred.fsh")||name.equals("shaders/world0/deferred.vsh");
     assertEquals(assembled(flat,nativeOptions,deferred?0:1,deferred?1:0,false),pair,"composition altered stage "+name);
     for(int copy:new int[]{0,1})for(int ao:new int[]{0,1})for(int translucent:new int[]{0,1})for(int early:new int[]{0,1}) {
      if(copy+ao+translucent+early<2)continue;
      boolean water=name.equals("shaders/world0/gbuffers_water.fsh")||name.equals("shaders/world0/gbuffers_water.vsh");
      String combined=assembled(flat,nativeOptions,copy,ao,translucent,early,true);
      int expectedCopy=copy,expectedAo=0,expectedTranslucent=0;
      if(deferred&&ao==1){expectedCopy=0;expectedAo=1;}
      if(water&&translucent==1){expectedCopy=0;expectedTranslucent=1;}
      assertEquals(assembled(flat,nativeOptions,expectedCopy,expectedAo,expectedTranslucent,false),combined,"subset composition altered "+name+" "+copy+ao+translucent+early);
     }
     if(name.equals("shaders/world0/gbuffers_water.fsh")||name.equals("shaders/world0/gbuffers_water.vsh")||name.equals("shaders/world0/gbuffers_terrain.fsh")||name.equals("shaders/world0/gbuffers_terrain.vsh"))
      assertEquals(assembled(flat,nativeOptions,0,0,false),assembled(flat,nativeOptions,1,0,false),"G01 changes native terrain/water contract: "+name);
     checked++;
    }
    assertEquals(58,checked,"All maintained world0 vertex/fragment stages checked");
    String properties=expand(zip,"shaders/shaders.properties",cache,new HashSet<>());
    assertEquals(assembled(properties,nativeOptions,1,0,false),assembled(properties,nativeOptions,1,1,true),"Copy/render graph properties differ from G01 singleton");
   }
  }finally{
   field.setBoolean(null,previous);
   try(var paths=java.nio.file.Files.walk(dir)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())java.nio.file.Files.delete(path);}
  }
 }
 private static String expand(java.util.zip.ZipFile zip,String name,Map<String,String> cache,Set<String> stack)throws Exception{
  if(cache.containsKey(name))return cache.get(name);assertTrue(stack.add(name),"Include cycle: "+name);
  var entry=zip.getEntry(name);assertNotNull(entry,name);String source=new String(zip.getInputStream(entry).readAllBytes(),StandardCharsets.UTF_8);
  var matcher=java.util.regex.Pattern.compile("(?m)^\\s*#include\\s+\"([^\"]+)\"[^\\r\\n]*").matcher(source);var output=new StringBuilder();
  while(matcher.find()){
   String include=matcher.group(1);String path=include.startsWith("/")?"shaders"+include:java.nio.file.Path.of(name).getParent().resolve(include).normalize().toString();
   matcher.appendReplacement(output,java.util.regex.Matcher.quoteReplacement(expand(zip,path,cache,stack)));
  }
  matcher.appendTail(output);stack.remove(name);String result=output.toString();cache.put(name,result);return result;
 }
 private static String assembled(String source,Map<String,String> nativeOptions,int copy,int ao,boolean capability){return assembled(source,nativeOptions,copy,ao,0,capability);}
 private static String assembled(String source,Map<String,String> nativeOptions,int copy,int ao,int translucent,boolean capability){return assembled(source,nativeOptions,copy,ao,translucent,0,capability);}
 private static String assembled(String source,Map<String,String> nativeOptions,int copy,int ao,int translucent,int early,boolean capability){
  var options=new HashMap<>(nativeOptions);
  for(var key:List.of("SHADERLOD_REFINEMENT_ADAPTER","SHADERLOD_G04_TRANSLUCENT_FASTPATH","SHADERLOD_MAKEUP_AO_HALF_RES","SHADERLOD_MAKEUP_DEPTH_PRIMING","SHADERLOD_MAKEUP_SPATIAL_REUSE","SHADERLOD_MAKEUP_SPATIAL_RATE","SHADERLOD_MAKEUP_WORLD_PIXEL","SHADERLOD_MAKEUP_EARLY_ALPHA"))options.put(key,"0");
  options.put("SHADERLOD_MAKEUP_EARLY_ALPHA",Integer.toString(early));
  options.put("SHADERLOD_G04_TRANSLUCENT_FASTPATH",Integer.toString(translucent));
  options.put("SHADERLOD_MAKEUP_DEAD_FRAME_COPY",Integer.toString(copy));options.put("SHADERLOD_G06_AO_ATTENUATION",Integer.toString(ao));
  for(var option:options.entrySet()){
   String replacement=option.getValue().equals("false")?"// disabled "+option.getKey():"#define "+option.getKey()+(option.getValue().equals("true")?"":" "+option.getValue());
   source=source.replaceAll("(?m)^\\s*(?://\\s*)?#define\\s+"+java.util.regex.Pattern.quote(option.getKey())+"\\b[^\\r\\n]*",java.util.regex.Matcher.quoteReplacement(replacement));
  }
  String prefix="#define IS_IRIS\n#define IRIS_VERSION 10812\n#define MC_VERSION 12101\n#define MC_OS_LINUX\n#define SHADERLOD_G06_ASAHI_RUNTIME_GUARD\n#define SHADERLOD_G04_ASAHI_RUNTIME_GUARD\n#define SHADERLOD_FRAMECOPY_RUNTIME_GUARD\n"+(capability?"#define SHADERLOD_COMBINATION_RUNTIME_GUARD\n":"");
  String result=JcppProcessor.glslPreprocessSource(prefix+source,List.of());
  return result.replaceAll("(?s)/\\*.*?\\*/","").replaceAll("(?m)//[^\\r\\n]*","").replaceAll("\\s+","");
 }

}
