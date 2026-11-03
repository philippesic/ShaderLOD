package com.pp.shaderlod.optimization.blissprefix;
import java.util.*;import com.google.gson.*;import java.security.*;
public final class BlissPrefixProfile{
 private BlissPrefixProfile(){}private static Map<String,String> load(String path,String sha){try(var in=BlissPrefixProfile.class.getResourceAsStream("/assets/shaderlod/adapters/qualification/"+path)){if(in==null)return Map.of();byte[] b=in.readAllBytes();if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)).equals(sha))return Map.of();var m=new TreeMap<String,String>();JsonParser.parseString(new String(b,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(e->m.put(e.getKey(),e.getValue().getAsString()));return Map.copyOf(m);}catch(Exception bad){return Map.of();}}
 private static final Map<String,String> EXPECTED=load("bliss-prefix-options.json","9a818423f86da7cb067324f0ff1f648be1bf40ef82b3dffd3a1743c5d58315dd"),FUNCTIONS=load("bliss-prefix-functions.json","95f2d337f218ca94f5449474e756a68551f6aa7b72d475b3894b0dc25065a736");
 public static boolean accepts(Map<String,String> actual){if(actual==null||EXPECTED.isEmpty())return false;var m=new TreeMap<>(actual);for(String k:List.of("SHADERLOD_BLISS_DEPTH_PRIMING","SHADERLOD_BLISS_AO_HALF_RES","SHADERLOD_BLISS_PIXEL_GRAPH","SHADERLOD_BLISS_SPATIAL_REUSE","SHADERLOD_BLISS_LIGHTING_RATE"))if(!Set.of("0","1").contains(m.put(k,"0")))return false;return EXPECTED.equals(m);}
 public static Map<String,String> functions(){return FUNCTIONS;}public static Map<String,String> expected(){return EXPECTED;}
}
