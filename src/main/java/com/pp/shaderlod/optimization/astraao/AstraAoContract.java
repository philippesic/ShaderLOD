package com.pp.shaderlod.optimization.astraao;
import java.util.*;

/** Exact native1015 plus twelve declared generated controls; no typed BSL getters. */
public final class AstraAoContract {
 public static final String ID="astralex",OPTION="SHADERLOD_ASTRALEX_AO_HALF_RES";
 private AstraAoContract(){}
 private static final Map<String,String> EXPECTED=load();
 private static Map<String,String> load(){try(var in=AstraAoContract.class.getResourceAsStream("/assets/shaderlod/astraao/options.json")){if(in==null)return Map.of();byte[] b=in.readAllBytes();if(!"e5e13a1f72009e82e155525b6f0b8919706e58a2a3bfa4d6a9e2602961f8efd9".equals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b))))return Map.of();var m=new TreeMap<String,String>();for(var e:com.google.gson.JsonParser.parseString(new String(b,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet())m.put(e.getKey(),e.getValue().getAsString());return Map.copyOf(m);}catch(Exception|LinkageError failure){return Map.of();}}
 public static Map<String,String> generated(String graph){if(!Set.of("0","1").contains(graph)||EXPECTED.size()!=1027)return Map.of();var m=new TreeMap<>(EXPECTED);m.put(OPTION,graph);return Map.copyOf(m);}
 public static boolean options(Map<String,String> actual){if(actual==null||actual.size()!=1027||!Set.of("0","1").contains(actual.getOrDefault(OPTION,"invalid")))return false;var n=new TreeMap<>(actual);for(String key:List.of("PARALLAX","COLORED_SHADOWS")){if(!Set.of("true","false").contains(n.getOrDefault(key,"invalid")))return false;}n.put("PARALLAX","false");n.put("COLORED_SHADOWS","true");if(!Set.of("1","2").contains(n.get("AO_QUALITY"))||!Set.of("1","2","3","4","5").contains(n.get("AO_FOVSCALE")))return false;n.put("AO_QUALITY","2");n.put("AO_FOVSCALE","4");for(String key:List.of("SHADERLOD_ASTRALEX_WORLD_GRAPH","SHADERLOD_ASTRALEX_DYNAMIC_GAUSSIAN","SHADERLOD_ASTRALEX_DEPTH_PRIMING","SHADERLOD_ASTRALEX_DEAD_MIP_CHAIN")){if(!Set.of("0","1").contains(n.getOrDefault(key,"invalid")))return false;n.put(key,"0");}return generated(actual.get(OPTION)).equals(n);}
 public static boolean projection(float[] p) {
  if(p==null||p.length!=16)return false;for(float x:p)if(!Float.isFinite(x)||Math.abs(x)>1e10f)return false;
  return p[2]==0&&p[6]==0&&p[3]==0&&p[7]==0&&p[11]==-1&&p[15]==0&&p[14]!=0;
 }
 public static boolean viewport(int[] v,int w,int h,boolean half){return v!=null&&Arrays.equals(v,new int[]{0,0,half?w/2:w,half?h/2:h});}
}
