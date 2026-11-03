package com.pp.shaderlod.optimization.solasao;
import java.util.*;
import com.pp.shaderlod.integration.solasquad.SolasQuadEligibility;
/** Exact native428 plus eight declared generated controls; no typed BSL getters. */
public final class SolasAoContract {
 public static final String ID="solas-shader",OPTION="SHADERLOD_SOLAS_AO_HALF_RES";
 private SolasAoContract(){}
 public static Map<String,String> generated(String graph) {
  if(!Set.of("0","1").contains(graph))throw new IllegalArgumentException("graph0/1 required");
  var m=new TreeMap<>(com.pp.shaderlod.integration.solasquad.SolasQuadEligibility.nativeOptions());
  m.put("SHADERLOD_REFINEMENT_ADAPTER","1");m.put("SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true");m.put("SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true");m.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");m.put("SHADERLOD_OPTIMIZATION_FLAGS_INCLUDED","true");m.put("SHADERLOD_PROJECTED_MATERIAL_BUDGET_INCLUDED","true");m.put("SHADERLOD_SOLAS_DEPTH_PRIMING","0");m.put("SHADERLOD_SOLAS_DEAD_MIP_CHAIN","0");m.put("SHADERLOD_SOLAS_PIXEL_GRAPH","0");m.put(OPTION,graph);return Map.copyOf(m);
 }
 public static boolean options(Map<String,String> actual){if(actual==null||!Set.of("0","1").contains(actual.getOrDefault(OPTION,"invalid")))return false;var n=new TreeMap<>(actual);String pixel=n.put("SHADERLOD_SOLAS_PIXEL_GRAPH","0");if(!"0".equals(pixel)&&!"1".equals(pixel))return false;String mip=n.put("SHADERLOD_SOLAS_DEAD_MIP_CHAIN","0");if(!"0".equals(mip)&&!"1".equals(mip))return false;String depth=n.put("SHADERLOD_SOLAS_DEPTH_PRIMING","0");if(!"0".equals(depth)&&!"1".equals(depth))return false;return generated(actual.get(OPTION)).equals(n);}
 public static boolean projection(float[] p) {
  if(p==null||p.length!=16)return false;for(float x:p)if(!Float.isFinite(x)||Math.abs(x)>1e10f)return false;
  return p[2]==0&&p[6]==0&&p[3]==0&&p[7]==0&&p[11]==-1&&p[15]==0&&p[14]!=0;
 }
 public static boolean viewport(int[] v,int w,int h,boolean half){return v!=null&&Arrays.equals(v,new int[]{0,0,half?w/2:w,half?h/2:h});}
}
