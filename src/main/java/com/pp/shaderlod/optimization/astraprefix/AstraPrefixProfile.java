package com.pp.shaderlod.optimization.astraprefix;
import com.google.gson.*;import java.util.*;import java.security.MessageDigest;
/** Explicit optional native POM profile; no new setting or CPU eligibility. */
public final class AstraPrefixProfile {
 private AstraPrefixProfile(){}
 private static Map<String,String> load(String path,String sha){try(var in=AstraPrefixProfile.class.getResourceAsStream("/assets/shaderlod/adapters/qualification/"+path)){if(in==null)return Map.of();byte[] bytes=in.readAllBytes();if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(sha))return Map.of();var m=new TreeMap<String,String>();for(var e:JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet())m.put(e.getKey(),e.getValue().getAsString());return Map.copyOf(m);}catch(Exception|LinkageError unavailable){return Map.of();}}
 private static final Map<String,String> EXPECTED=load("astra-prefix-options.json","465276161d4cb7d3b04d7ca9b80f36ad98c2e36b4d2a1d2010dbf1aa76f89009"),FUNCTIONS=load("astra-prefix-functions.json","129574070084df66ab8148c7ee75e05cdbb7bfe022602878abae760314e4130e");
 private static Map<String,String> canonical(Map<String,String> actual){if(actual==null)return Map.of();var result=new TreeMap<>(actual);for(String key:List.of("SHADERLOD_ASTRALEX_WORLD_GRAPH","SHADERLOD_ASTRALEX_DYNAMIC_GAUSSIAN","SHADERLOD_ASTRALEX_DEPTH_PRIMING","SHADERLOD_ASTRALEX_DEAD_MIP_CHAIN","SHADERLOD_ASTRALEX_AO_HALF_RES")){if(!Set.of("0","1").contains(result.getOrDefault(key,"invalid")))return Map.of();result.put(key,"0");}return result;}
 public static Map<String,String> expected(){return EXPECTED;}public static Map<String,String> functions(){return FUNCTIONS;}
 public static boolean accepts(Map<String,String> actual){return EXPECTED.size()==1027&&EXPECTED.equals(canonical(actual));}
}
