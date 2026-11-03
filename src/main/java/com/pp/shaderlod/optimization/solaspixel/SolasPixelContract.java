package com.pp.shaderlod.optimization.solaspixel;
import java.util.*;import com.google.gson.*;import java.security.MessageDigest;
/** Exact default438 and existing native-POM438 profiles. Native AA/material settings are compared, never normalized. */
public final class SolasPixelContract{
 public static final String ID="solas-shader",OPTION="SHADERLOD_SOLAS_PIXEL_GRAPH";private static final Map<String,String> BASE=load("options438.json","cec334bea3a1e3e5de638b856ed7b1ffaf5cfeb0538d4ad6de0e250a6218eb43"),PBR=load("pbr438.json","f3d21925cbb26c59be09d2fd415e07b16039acf9117778282c37864a7af60134");private SolasPixelContract(){}
 private static Map<String,String> load(String name,String sha){try(var in=SolasPixelContract.class.getResourceAsStream("/assets/shaderlod/solaspixel/"+name)){if(in==null)return Map.of();byte[] b=in.readAllBytes();if(!sha.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b))))return Map.of();var m=new TreeMap<String,String>();for(var e:JsonParser.parseString(new String(b,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet())m.put(e.getKey(),e.getValue().getAsString());return Map.copyOf(m);}catch(Exception|LinkageError failed){return Map.of();}}
 public static Map<String,String> generated(String graph){if(!Set.of("0","1").contains(graph)||BASE.size()!=438)return Map.of();var m=new TreeMap<>(BASE);m.put(OPTION,graph);return Map.copyOf(m);}
 /** Two complete explicit profiles; never rewrite a personal native material choice. */
 public static Map<String,String> pbrGenerated(String graph){if(!Set.of("0","1").contains(graph)||PBR.size()!=438)return Map.of();var m=new TreeMap<>(PBR);m.put(OPTION,graph);return Map.copyOf(m);}
 public static boolean options(Map<String,String> values){if(values==null||values.size()!=438||!Set.of("0","1").contains(values.getOrDefault(OPTION,"invalid")))return false;var n=new TreeMap<>(values);for(String key:List.of("SHADERLOD_SOLAS_AO_HALF_RES","SHADERLOD_SOLAS_DEPTH_PRIMING","SHADERLOD_SOLAS_DEAD_MIP_CHAIN")){String v=n.put(key,"0");if(!Set.of("0","1").contains(v==null?"invalid":v))return false;}return generated(values.get(OPTION)).equals(n)||pbrGenerated(values.get(OPTION)).equals(n);}
 public static boolean projection(float[] p){return com.pp.shaderlod.optimization.solasao.SolasAoContract.projection(p);}
 public static boolean viewport(int[] v,int w,int h,boolean reduced){return v!=null&&Arrays.equals(v,new int[]{0,0,reduced?(int)(w*.85f):w,reduced?(int)(h*.85f):h});}
}
