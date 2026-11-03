package com.pp.shaderlod.optimization.sildurspatial;
import java.util.*;import com.pp.shaderlod.optimization.sildurao.SildurAoContract;
public final class SildurSpatialContract {
 public static final String ID="sildurs-vibrant-shaders",REUSE="SHADERLOD_SILDUR_SPATIAL_REUSE",RATE="SHADERLOD_SILDUR_LIGHTING_RATE";
 private SildurSpatialContract(){}
 public static int mode(Map<String,String> m){if(m==null||!Set.of("0","1").contains(m.getOrDefault(REUSE,"invalid"))||!Set.of("0","1").contains(m.getOrDefault(RATE,"invalid")))return 0;return "1".equals(m.get(REUSE))?1:"1".equals(m.get(RATE))?2:0;}
 public static boolean options(Map<String,String> m){if(m==null)return false;var n=new TreeMap<>(m);for(String key:List.of(REUSE,RATE))if(!Set.of("0","1").contains(java.util.Objects.requireNonNullElse(n.put(key,"0"),"invalid")))return false;return SildurAoContract.options(n);}
 public static boolean half(Map<String,String> options){return options!=null&&"1".equals(options.get(SildurAoContract.OPTION));}
 public static String stage(int n){return n==0?"deferred":"deferred"+n;}
 public static int lightingIndex(Map<String,String> options){return (half(options)?1:0)+(mode(options)>0?1:0);}
 public static String lighting(Map<String,String> options){return stage(lightingIndex(options));}
 public static String reflection(Map<String,String> options){return stage(lightingIndex(options)+1);}
 public static String aoProducer(Map<String,String> options){return stage(mode(options)>0?1:0);}
}
