package com.pp.shaderlod.optimization.sildurao;
import java.util.*;
/** Exact native115 source profile and128 generated declarations; scalarAO separate from native lighting. */
public final class SildurAoContract {
 public static final String ID="sildurs-vibrant-shaders",OPTION="SHADERLOD_SILDUR_AO_HALF_RES";
 private SildurAoContract(){}
 /** Metadata has only the unshifted producer -> lighting dataflow proof. Graph0 spatial paths stay independent. */
 public static boolean metadataRoute(Map<String,String> actual){return actual!=null&&"0".equals(actual.get("SHADERLOD_SILDUR_SPATIAL_REUSE"))&&"0".equals(actual.get("SHADERLOD_SILDUR_LIGHTING_RATE"));}
 public static Map<String,String> generated(String graph){if(!Set.of("0","1").contains(graph))throw new IllegalArgumentException("graph0/1 required");var m=new TreeMap<>(com.pp.shaderlod.integration.sildurquad.SildurSourceCertificate.generatedOptions());m.put("SHADERLOD_SILDUR_PIXEL_GRAPH","0");m.put(OPTION,graph);m.put("SHADERLOD_SILDUR_SPATIAL_REUSE","0");m.put("SHADERLOD_SILDUR_LIGHTING_RATE","0");return Map.copyOf(m);}
 public static boolean options(Map<String,String> actual){if(actual==null||actual.size()!=131||!Set.of("0","1").contains(actual.getOrDefault(OPTION,"invalid"))||!"0".equals(actual.get("SHADERLOD_SILDUR_SHADOW_QUERY_GRAPH")))return false;if("1".equals(actual.get(OPTION))&&!metadataRoute(actual))return false;var m=new TreeMap<>(actual);if(!Set.of("0","1").contains(m.put("SHADERLOD_SILDUR_PIXEL_GRAPH","0")))return false;for(String k:java.util.List.of("SHADERLOD_SILDUR_SPATIAL_REUSE","SHADERLOD_SILDUR_LIGHTING_RATE"))if(!java.util.Set.of("0","1").contains(m.put(k,"0")))return false;if(!Set.of("0","1").contains(m.put("SHADERLOD_SILDUR_DEPTH_PRIMING","0")))return false;return generated(actual.get(OPTION)).equals(m);}
 public static boolean projection(float[] p){return com.pp.shaderlod.optimization.reimaginedao.ReimaginedAoContract.projection(p);}
 public static boolean viewport(int[] v,int w,int h,boolean half){return v!=null&&Arrays.equals(v,new int[]{0,0,half?w/2:w,half?h/2:h});}
}
