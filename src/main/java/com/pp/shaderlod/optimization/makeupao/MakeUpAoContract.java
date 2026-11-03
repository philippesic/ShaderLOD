package com.pp.shaderlod.optimization.makeupao;
import java.util.*;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility;
/** Exact native100 plus six declared generated controls; no typed BSL getters. */
public final class MakeUpAoContract {
 public static final String ID="makeup-ultra-fast-shaders",OPTION="SHADERLOD_MAKEUP_AO_HALF_RES";
 private MakeUpAoContract(){}
 public static Map<String,String> generated(String graph) {
  if(!Set.of("0","1").contains(graph))throw new IllegalArgumentException("graph0/1 required");
  var m=new TreeMap<>(MakeUpQuadEligibility.nativeOptions());
  m.put("SHADERLOD_REFINEMENT_ADAPTER","1");m.put("SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true");
  m.put("SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true");m.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");
  m.put("SHADERLOD_OPTIMIZATION_FLAGS_INCLUDED","true");m.put(OPTION,graph);m.put("SHADERLOD_MAKEUP_EARLY_ALPHA","0");m.put("SHADERLOD_MAKEUP_DEPTH_PRIMING","0");m.put("SHADERLOD_MAKEUP_SPATIAL_REUSE","0");m.put("SHADERLOD_MAKEUP_SPATIAL_RATE","0");m.put("SHADERLOD_MAKEUP_WORLD_PIXEL","0");m.put("SHADERLOD_MAKEUP_DEAD_FRAME_COPY","0");m.put("SHADERLOD_G04_TRANSLUCENT_FASTPATH","0");m.put("SHADERLOD_G06_AO_ATTENUATION","0");return Map.copyOf(m);
 }
 public static boolean options(Map<String,String> actual){actual=com.pp.shaderlod.optimization.framecopy.MakeUpDeadCopyAdmission.withoutInactiveControl(actual);return actual!=null&&actual.size()==111&&Set.of("0","1").contains(actual.getOrDefault(OPTION,"invalid"))&&depthNormalized(actual);}
 private static boolean depthNormalized(Map<String,String> actual){var n=new TreeMap<>(actual);if(!Set.of("0","1").contains(java.util.Objects.requireNonNullElse(n.put("SHADERLOD_MAKEUP_EARLY_ALPHA","0"),"invalid")))return false;if(!Set.of("0","1").contains(java.util.Objects.requireNonNullElse(n.put("SHADERLOD_MAKEUP_WORLD_PIXEL","0"),"invalid")))return false;if(!Set.of("0","1").contains(java.util.Objects.requireNonNullElse(n.put("SHADERLOD_MAKEUP_SPATIAL_RATE","0"),"invalid")))return false;if(!Set.of("0","1").contains(java.util.Objects.requireNonNullElse(n.put("SHADERLOD_MAKEUP_SPATIAL_REUSE","0"),"invalid")))return false;if(!Set.of("0","1").contains(java.util.Objects.requireNonNullElse(n.put("SHADERLOD_MAKEUP_DEPTH_PRIMING","0"),"invalid")))return false;return com.pp.shaderlod.optimization.framecopy.MakeUpDeadCopyAdmission.withoutInactiveControl(generated(actual.get(OPTION))).equals(n);}
 public static boolean projection(float[] p) {
  if(p==null||p.length!=16)return false;for(float x:p)if(!Float.isFinite(x)||Math.abs(x)>1e10f)return false;
  return p[2]==0&&p[6]==0&&p[3]==0&&p[7]==0&&p[11]==-1&&p[15]==0&&p[14]!=0;
 }
 public static boolean viewport(int[] v,int w,int h,boolean half){return v!=null&&Arrays.equals(v,new int[]{0,0,half?w/2:w,half?h/2:h});}
}
