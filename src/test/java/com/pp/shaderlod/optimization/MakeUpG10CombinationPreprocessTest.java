package com.pp.shaderlod.optimization;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MakeUpG10CombinationPreprocessTest {
 private static String assembled(String source,Map<String,String> options,int copy,boolean g10)throws Exception {
  var method=MakeUpCombinationPreprocessTest.class.getDeclaredMethod("assembled",String.class,Map.class,int.class,int.class,int.class,int.class,boolean.class);method.setAccessible(true);
  return (String)method.invoke(null,(g10?"#define SHADERLOD_COMBINATION_BLOOM_RUNTIME_GUARD\n":"")+source,options,copy,1,1,1,true);
 }
 private static String expand(java.util.zip.ZipFile zip,String name,Map<String,String> overrides,Map<String,String> cache)throws Exception {
  if(cache.containsKey(name))return cache.get(name);
  var entry=zip.getEntry(name);assertNotNull(entry,name);
  String source=overrides.containsKey(name)?overrides.get(name):new String(zip.getInputStream(entry).readAllBytes(),StandardCharsets.UTF_8);
  var matcher=java.util.regex.Pattern.compile("(?m)^\\s*#include\\s+\"([^\"]+)\"[^\\r\\n]*").matcher(source);var output=new StringBuilder();
  while(matcher.find()) {String include=matcher.group(1);String path=include.startsWith("/")?"shaders"+include:Path.of(name).getParent().resolve(include).normalize().toString();matcher.appendReplacement(output,java.util.regex.Matcher.quoteReplacement(expand(zip,path,overrides,cache)));}
  matcher.appendTail(output);String result=output.toString();cache.put(name,result);return result;
 }
 @Test void privateG10OffMatchesBeforeAndOnChangesOnlyActualBloomFragment()throws Exception {
  var dir=Files.createTempDirectory("makeup-g10-composition-");
  var field=com.pp.shaderlod.packs.PackSelectionService.class.getDeclaredField("recipeScopedSelections");field.setAccessible(true);boolean previous=field.getBoolean(null);
  try {
   Files.copy(Path.of("dependencies/shortlist-test-inputs/originals/MakeUpNativeOriginal.zip"),dir.resolve("Original.zip"));
   var options=com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.nativeOptions();com.pp.shaderlod.packs.PackSelectionService.enableRecipeScopedSelections();
   var result=new com.pp.shaderlod.packs.PackSelectionService().apply(dir,"Original.zip",true,com.pp.shaderlod.LodPreset.PERFORMANCE,options,false,options,options.keySet());assertTrue(result.accepted(),result.detail());
   Map<String,String> before=new HashMap<>();
   for(String path:List.of("shaders/lib/bloom.glsl","shaders/common/composite1_fragment.glsl")) {
    try(var in=getClass().getResourceAsStream("/com/pp/shaderlod/optimization/g10-before-"+Path.of(path).getFileName())) {assertNotNull(in);before.put(path,new String(in.readAllBytes(),StandardCharsets.UTF_8));}
   }
   try(var zip=new java.util.zip.ZipFile(dir.resolve(result.packFile()).toFile())) {
    var currentCache=new HashMap<String,String>();var oldCache=new HashMap<String,String>();int stages=0;
    for(var entry:Collections.list(zip.entries())) {
     String name=entry.getName();if(!name.startsWith("shaders/world0/")||!(name.endsWith(".fsh")||name.endsWith(".vsh")))continue;
     String current=expand(zip,name,Map.of(),currentCache),old=expand(zip,name,before,oldCache);
     for(int copy:new int[]{0,1}) {
      String inactive=assembled(current,options,copy,false);assertEquals(assembled(old,options,copy,false),inactive,"G10-off changed "+name+" copy="+copy);
      String active=assembled(current,options,copy,true);boolean bloom=name.equals("shaders/world0/composite"+(copy==1?"2":"1")+".fsh");
      if(bloom) {assertTrue(active.contains("g10Sample"),name);assertTrue(active.contains("dither==0.0"));assertEquals(4,active.split("\\+=g10Sample",-1).length-1);assertNotEquals(inactive,active);}
      else assertEquals(inactive,active,"G10 changed a non-bloom stage "+name+" copy="+copy);
     }
     stages++;
    }
    assertEquals(58,stages);
   }
  } finally {
   field.setBoolean(null,previous);try(var paths=Files.walk(dir)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
  }
 }
}
