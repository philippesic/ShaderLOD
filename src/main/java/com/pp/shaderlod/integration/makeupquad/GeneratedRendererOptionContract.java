package com.pp.shaderlod.integration.makeupquad;
import com.google.gson.*;import java.util.*;
/** Named source-owner parser/input maps. Missing/changed/extra live options refuse CPU admission. */
public final class GeneratedRendererOptionContract {
 private static final String SHA="b40de1d2f388ac3db4d0cea9d3bd1d4a7bfdb61f94ebe33d34ecc16508bf5546";
 private static final Map<String,Map<String,String>> EXPECTED=load();
 private static final Map<String,Map<String,String>> PHOTON_NORMAL=loadPhotonNormal();
 private static Map<String,Map<String,String>> loadPhotonNormal(){try(var in=GeneratedRendererOptionContract.class.getResourceAsStream("/assets/shaderlod/rendererquad/photon-normal-main-options.json")){if(in==null)return Map.of();byte[] bytes=in.readAllBytes();if(!"b0fa3507253a383429a66cd792aab587afe1f8597a7c978974e1175646cd4b8d".equals(MakeUpQuadEligibility.sha(bytes)))return Map.of();var result=new TreeMap<String,Map<String,String>>();for(var row:JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet()){var values=new TreeMap<String,String>();for(var e:row.getValue().getAsJsonObject().entrySet())values.put(e.getKey(),e.getValue().getAsString());if(values.size()!=395)return Map.of();result.put(row.getKey(),Map.copyOf(values));}return Map.copyOf(result);}catch(Exception|LinkageError unavailable){return Map.of();}}
 private GeneratedRendererOptionContract() {}
 private static Map<String,Map<String,String>> load() {
  try(var in=GeneratedRendererOptionContract.class.getResourceAsStream("/assets/shaderlod/rendererquad/generated-main-options.json")) {
   if(in==null)return Map.of();byte[] bytes=in.readAllBytes();
   if(!SHA.equals(MakeUpQuadEligibility.sha(bytes)))return Map.of();
   var root=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();var result=new TreeMap<String,Map<String,String>>();
   for(var family:root.entrySet()) {var values=new TreeMap<String,String>();for(var option:family.getValue().getAsJsonObject().entrySet())values.put(option.getKey(),option.getValue().getAsString());result.put(family.getKey(),Map.copyOf(values));}
   return Map.copyOf(result);
  } catch(Exception | LinkageError unavailable) {return Map.of();}
 }
 public static boolean accepts(String family,Map<String,String> actual) {
  if("makeup-ultra-fast-shaders".equals(family))return com.pp.shaderlod.optimization.makeupao.MakeUpAoContract.options(actual)
      ||com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.profile(actual)
      ||com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph.profile(actual);
  var expected=EXPECTED.get(family);if(expected==null || actual==null || !expected.keySet().equals(actual.keySet()))return false;
  if("complementary-reimagined".equals(family)){var normalized=new TreeMap<>(actual);for(String key:List.of("SHADERLOD_REIMAGINED_DEPTH_PRIMING","SHADERLOD_REIMAGINED_AO_HALF_RES","SHADERLOD_REIMAGINED_DEAD_MIP_CHAIN","SHADERLOD_REIMAGINED_SPATIAL_REUSE","SHADERLOD_REIMAGINED_SPATIAL_RATE","SHADERLOD_REIMAGINED_PIXEL_GRAPH")){String v=actual.get(key);if(v==null||!Set.of("0","1").contains(v))return false;normalized.put(key,"0");}return expected.equals(normalized);}
  if("astralex".equals(family)){var normalized=new TreeMap<>(actual);for(String key:List.of("SHADERLOD_ASTRALEX_WORLD_GRAPH","SHADERLOD_ASTRALEX_DYNAMIC_GAUSSIAN","SHADERLOD_ASTRALEX_DEPTH_PRIMING","SHADERLOD_ASTRALEX_AO_HALF_RES","SHADERLOD_ASTRALEX_DEAD_MIP_CHAIN")){String v=actual.get(key);if(v==null||!Set.of("0","1").contains(v))return false;normalized.put(key,"0");}return expected.equals(normalized);}
  if("solas-shader".equals(family)){var normalized=new TreeMap<>(actual);for(String key:List.of("SHADERLOD_SOLAS_DEPTH_PRIMING","SHADERLOD_SOLAS_AO_HALF_RES","SHADERLOD_SOLAS_DEAD_MIP_CHAIN","SHADERLOD_SOLAS_PIXEL_GRAPH")){String v=actual.get(key);if(v==null||!Set.of("0","1").contains(v))return false;normalized.put(key,"0");}return expected.equals(normalized);}
  if("complementary-unbound".equals(family)){var normalized=new TreeMap<>(actual);for(String key:List.of("SHADERLOD_UNBOUND_DEPTH_PRIMING","SHADERLOD_UNBOUND_AO_HALF_RES","SHADERLOD_UNBOUND_DEAD_MIP_CHAIN","SHADERLOD_UNBOUND_SPATIAL_REUSE","SHADERLOD_UNBOUND_SPATIAL_RATE","SHADERLOD_UNBOUND_PIXEL_GRAPH")){String v=actual.get(key);if(v==null||!Set.of("0","1").contains(v))return false;normalized.put(key,"0");}return expected.equals(normalized);}
  if("sildurs-vibrant-shaders".equals(family)) {
   String depth=actual.get("SHADERLOD_SILDUR_DEPTH_PRIMING");if(depth==null||!Set.of("0","1").contains(depth))return false;
   String graph=actual.get("SHADERLOD_SILDUR_SHADOW_QUERY_GRAPH");
   if(graph==null || !Set.of("0","1").contains(graph))return false;
   String half=actual.get("SHADERLOD_SILDUR_AO_HALF_RES");if(half==null||!Set.of("0","1").contains(half))return false;
   for(String key:List.of("SHADERLOD_SILDUR_SPATIAL_REUSE","SHADERLOD_SILDUR_LIGHTING_RATE","SHADERLOD_SILDUR_PIXEL_GRAPH"))if(!Set.of("0","1").contains(actual.get(key)))return false;
   var normalized=new TreeMap<>(actual);normalized.put("SHADERLOD_SILDUR_PIXEL_GRAPH","0");normalized.put("SHADERLOD_SILDUR_SPATIAL_REUSE","0");normalized.put("SHADERLOD_SILDUR_LIGHTING_RATE","0");normalized.put("SHADERLOD_SILDUR_AO_HALF_RES","0");normalized.put("SHADERLOD_SILDUR_SHADOW_QUERY_GRAPH","0");normalized.put("SHADERLOD_SILDUR_DEPTH_PRIMING","0");return expected.equals(normalized);
  }
  if("photon".equals(family)) {
   var normalized=new TreeMap<>(actual);
   String depth=actual.get("SHADERLOD_PHOTON_DEPTH_PRIMING");if(depth==null||!Set.of("0","1").contains(depth))return false;normalized.put("SHADERLOD_PHOTON_DEPTH_PRIMING","0");
   for(String key:List.of("SHADERLOD_SHARED_SPATIAL","SHADERLOD_LIGHTING_RATE_PROBE")) {
    String value=actual.get(key);if(value==null || !Set.of("true","false").contains(value) || !expected.containsKey(key))return false;
    normalized.put(key,expected.get(key));
   }
   String mip=actual.get("SHADERLOD_PHOTON_DEAD_MIP_CHAIN");if(mip==null || !Set.of("0","1").contains(mip))return false;
   normalized.put("SHADERLOD_PHOTON_DEAD_MIP_CHAIN","0");return expected.equals(normalized)||PHOTON_NORMAL.values().stream().anyMatch(normalized::equals);
  }
  if("bsl-shaders".equals(family)) {
   var normalized=new TreeMap<>(actual);
   for(String key:List.of("SHADERLOD_BSL_AO_HALF_RES","SHADERLOD_BSL_DEAD_MIP_CHAIN","SHADERLOD_BSL_SHADOW_FIELD","SHADERLOD_BSL_RAW_QUERY","SHADERLOD_BSL_DEPTH_PRIMING")) {
    String value=actual.get(key);if(value==null || !Set.of("0","1").contains(value) || !expected.containsKey(key))return false;
    normalized.put(key,"0");
   }
   return expected.equals(normalized);
  }
  if("bliss-shader".equals(family)){var normalized=new TreeMap<>(actual);for(String key:List.of("SHADERLOD_BLISS_PIXEL_GRAPH","SHADERLOD_BLISS_DEPTH_PRIMING","SHADERLOD_BLISS_AO_HALF_RES","SHADERLOD_BLISS_SPATIAL_REUSE","SHADERLOD_BLISS_LIGHTING_RATE")){String v=actual.get(key);if(v==null||!Set.of("0","1").contains(v))return false;normalized.put(key,"0");}return expected.equals(normalized);}
  return expected.equals(actual);
 }
}
