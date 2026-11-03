package com.pp.shaderlod.optimization.blissspatial;
import java.util.*;import com.pp.shaderlod.optimization.blisspixel.BlissPixelContract;
/** Two explicit current-frame schedules, same native two-RGB quantity basis. */
public final class BlissSpatialContract {
 public static final String ID="bliss-shader",REUSE="SHADERLOD_BLISS_SPATIAL_REUSE",RATE="SHADERLOD_BLISS_LIGHTING_RATE";
 private BlissSpatialContract(){}
 public static Map<String,String> generated(){return BlissPixelContract.generated("0");}
 public static boolean options(Map<String,String> actual){if(actual==null)return false;var m=new TreeMap<>(actual);String pixel=m.put(BlissPixelContract.OPTION,"0");if(!Set.of("0","1").contains(pixel==null?"invalid":pixel))return false;for(String k:List.of(REUSE,RATE,"SHADERLOD_BLISS_AO_HALF_RES","SHADERLOD_BLISS_DEPTH_PRIMING"))if(!Set.of("0","1").contains(m.put(k,"0")))return false;return generated().equals(m);}
 public static int mode(Map<String,String> actual){return actual==null||!Set.of("0","1").contains(actual.getOrDefault(REUSE,"invalid"))||!Set.of("0","1").contains(actual.getOrDefault(RATE,"invalid"))?0:"1".equals(actual.get(REUSE))?1:"1".equals(actual.get(RATE))?2:0;}
 public static boolean viewport(int[] v,int w,int h,boolean half){return Arrays.equals(v,new int[]{0,0,half?w/2:w,half?h/2:h});}
}
