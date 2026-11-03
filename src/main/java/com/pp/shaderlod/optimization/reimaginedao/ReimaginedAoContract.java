package com.pp.shaderlod.optimization.reimaginedao;
import java.util.*;
import com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility;
/** Exact native398 plus nine declared generated controls; no typed BSL getters. */
public final class ReimaginedAoContract {
 public static final String ID="complementary-reimagined",OPTION="SHADERLOD_REIMAGINED_AO_HALF_RES";
 private ReimaginedAoContract(){}
 public static Map<String,String> generated(String graph) {
  if(!Set.of("0","1").contains(graph))throw new IllegalArgumentException("graph0/1 required");
  var m=new TreeMap<>(ReimaginedQuadEligibility.nativeOptions());
  m.put("SHADERLOD_REFINEMENT_ADAPTER","1");m.put("SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true");
  m.put("SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true");m.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");
  m.put("SHADERLOD_OPTIMIZATION_FLAGS_INCLUDED","true");m.put("SHADERLOD_PROJECTED_POM_ADAPTER_INCLUDED","true");m.put("SHADERLOD_PROJECTED_MATERIAL_BUDGET_INCLUDED","true");m.put(OPTION,graph);m.put("SHADERLOD_REIMAGINED_DEPTH_PRIMING","0");m.put("SHADERLOD_REIMAGINED_DEAD_MIP_CHAIN","0");m.put("SHADERLOD_REIMAGINED_SPATIAL_REUSE","0");m.put("SHADERLOD_REIMAGINED_SPATIAL_RATE","0");m.put("SHADERLOD_REIMAGINED_PIXEL_GRAPH","0");return Map.copyOf(m);
 }
 public static boolean options(Map<String,String> actual){return actual!=null&&actual.size()==411&&Set.of("0","1").contains(actual.getOrDefault(OPTION,"invalid"))&&depthNormalized(actual);}
 private static boolean depthNormalized(Map<String,String> actual){var n=new TreeMap<>(actual);if(!Set.of("0","1").contains(n.put("SHADERLOD_REIMAGINED_DEPTH_PRIMING","0")))return false;if(!Set.of("0","1").contains(n.put("SHADERLOD_REIMAGINED_DEAD_MIP_CHAIN","0")))return false;for(String k:List.of("SHADERLOD_REIMAGINED_SPATIAL_REUSE","SHADERLOD_REIMAGINED_SPATIAL_RATE","SHADERLOD_REIMAGINED_PIXEL_GRAPH"))if(!Set.of("0","1").contains(n.getOrDefault(k,"invalid")))return false;n.put("SHADERLOD_REIMAGINED_SPATIAL_REUSE","0");n.put("SHADERLOD_REIMAGINED_SPATIAL_RATE","0");n.put("SHADERLOD_REIMAGINED_PIXEL_GRAPH","0");return generated(actual.get(OPTION)).equals(n);}
 public static Set<Integer> consumerMips(Map<String,String> actual){return options(actual)?("1".equals(actual.get("SHADERLOD_REIMAGINED_DEAD_MIP_CHAIN"))?Set.of():Set.of(0)):null;}
 public static boolean projection(float[] p) {
  if(p==null||p.length!=16)return false;for(float x:p)if(!Float.isFinite(x)||Math.abs(x)>1e10f)return false;
  return p[2]==0&&p[6]==0&&p[3]==0&&p[7]==0&&p[11]==-1&&p[15]==0&&p[14]!=0;
 }
 public static boolean viewport(int[] v,int w,int h,boolean half){return v!=null&&Arrays.equals(v,new int[]{0,0,half?w/2:w,half?h/2:h});}
}
