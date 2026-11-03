package com.pp.shaderlod.optimization.blissao;
import java.util.*;
import com.pp.shaderlod.optimization.blisspixel.BlissPixelContract;
/** Native Bliss463 and declared generated controls; half AO never changes native AA. */
public final class BlissAoContract {
 public static final String ID="bliss-shader",OPTION="SHADERLOD_BLISS_AO_HALF_RES";
 private BlissAoContract(){}
 public static Map<String,String> generated(String graph){if(!Set.of("0","1").contains(graph))throw new IllegalArgumentException("binary graph");var m=new TreeMap<>(BlissPixelContract.generated("0"));m.put(OPTION,graph);return Map.copyOf(m);}
 public static boolean options(Map<String,String> actual){if(actual==null||!Set.of("0","1").contains(actual.getOrDefault(OPTION,"invalid")))return false;var m=new TreeMap<>(actual);String pixel=m.put(BlissPixelContract.OPTION,"0");if(!Set.of("0","1").contains(pixel==null?"invalid":pixel))return false;if(!Set.of("0","1").contains(m.put("SHADERLOD_BLISS_DEPTH_PRIMING","0")))return false;for(String key:List.of("SHADERLOD_BLISS_SPATIAL_REUSE","SHADERLOD_BLISS_LIGHTING_RATE"))if(!Set.of("0","1").contains(m.put(key,"0")))return false;return generated(actual.get(OPTION)).equals(m);}
 public static boolean projection(float[] p){return BlissPixelContract.projection(p);}
 public static boolean viewport(int[] v,int w,int h,boolean half){return Arrays.equals(v,new int[]{0,0,half?w/2:w,half?h/2:h});}
}
