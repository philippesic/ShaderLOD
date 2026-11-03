package com.pp.shaderlod.fabric.dev;
import com.pp.shaderlod.LodPreset;
/** Explicit R/U native control stages; separate source/options ancestry, no MC/GL state. */
public final class ReimaginedNativeControlChecks {
 private ReimaginedNativeControlChecks(){}
 public static boolean launchAllows(String mode,String speed,String photonQuality,String candidate,String research){return ("reimagined".equals(mode)||"unbound".equals(mode))&&"false".equals(speed)&&"false".equals(photonQuality)&&"false".equals(candidate)&&"false".equals(research);}
 public static java.util.Map<String,String> nativeOptions(String mode){
  if("reimagined".equals(mode))return com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.nativeOptions();
  if("unbound".equals(mode)&&unboundAncestryProofsMatch())return com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.nativeOptions();
  return java.util.Map.of();
 }
 public static boolean optionsMatch(String mode,java.util.Map<String,String> options){
  if("reimagined".equals(mode))return com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.optionsMatch(options);
  return "unbound".equals(mode)&&unboundAncestryProofsMatch()&&com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.optionsMatch(options);
 }
 public static boolean sourceMatches(String mode,java.nio.file.Path archive,java.util.Map<String,String> options){
  if("reimagined".equals(mode))return com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.validate(archive,options).eligible();
  return "unbound".equals(mode)&&unboundAncestryProofsMatch()&&com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.validate(archive,options).eligible();
 }
 /** Unbound's main certificate pins archive/options only; keep its DISTINCT packaged source/API ancestry explicit. */
 public static boolean unboundAncestryProofsMatch(){
  return resourceMatches("source-proof.json","4da7e9f155925752ff043576856825898d70c211bd9b3b9792f4816f788dac2d")&&
      resourceMatches("api-proof.json","80768f1ca89a62e3a8746f0df5e49cdce8b45b91810f671dfd03b5f586544bb7");
 }
 private static boolean resourceMatches(String name,String expected){
  try(var in=ReimaginedNativeControlChecks.class.getResourceAsStream("/assets/shaderlod/unboundquad/"+name)){
   return in!=null&&expected.equals(com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.sha(in.readAllBytes()));
  }catch(Exception|LinkageError unavailable){return false;}
 }
 /** Null means unobserved/incomplete, false means a real byte/existence mismatch. */
 public static Boolean sidecarByteStateRestored(boolean beforeObserved,boolean beforeExists,String beforeSha,boolean afterObserved,boolean afterExists,String afterSha){
  if(!beforeObserved||!afterObserved||beforeExists&&(beforeSha==null||!beforeSha.matches("[0-9a-f]{64}"))||afterExists&&(afterSha==null||!afterSha.matches("[0-9a-f]{64}")))return null;
  return beforeExists==afterExists&&(!beforeExists||beforeSha.equals(afterSha));
 }
 public static String[] stages(){return new String[]{"original","quality","performance","quality-return","off"};}
 private static void requireStage(int stage){if(stage<0||stage>=5)throw new IllegalArgumentException("native R/U stage outside0..4");}
 public static boolean stageEnabled(int stage){requireStage(stage);return stage>0&&stage<4;}
 public static LodPreset stagePreset(int stage,LodPreset initial){requireStage(stage);if(initial!=LodPreset.PERFORMANCE)throw new IllegalArgumentException("initial native Off/Performance required");return stage==1||stage==3?LodPreset.QUALITY:initial;}
 public static boolean coreMatches(int stage,boolean enabled,LodPreset preset,LodPreset initial){return enabled==stageEnabled(stage)&&preset==stagePreset(stage,initial);}
}
