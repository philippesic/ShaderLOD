package com.pp.shaderlod;
import com.pp.shaderlod.optimization.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ShaderOptimizationProfilesG02Test {
 @Test void exactNativePredicatePreservesProjectionDecisionsWithoutConstructingAProjection(){
  var certified=com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions();
  var actual=new java.util.TreeMap<>(certified);
  java.util.function.Predicate<java.util.Map<String,String>> old=map->{
   if(map==null)return false;
   var projection=new java.util.TreeMap<>(map);projection.keySet().removeIf(k->k.startsWith("SHADERLOD_"));
   return certified.size()==384&&certified.equals(projection);
  };
  assertTrue(ShaderOptimizationProfiles.deadMipNativeOptions(actual));
  actual.put("SHADERLOD_BSL_DEAD_MIP_CHAIN","1");actual.put("SHADERLOD_unknown_owned_key","arbitrary");
  assertEquals(old.test(actual),ShaderOptimizationProfiles.deadMipNativeOptions(actual));
  for(var entry:certified.entrySet()){
   actual.put(entry.getKey(),entry.getValue()+"_different");
   assertFalse(ShaderOptimizationProfiles.deadMipNativeOptions(actual),entry.getKey());
   assertEquals(old.test(actual),ShaderOptimizationProfiles.deadMipNativeOptions(actual));
   actual.remove(entry.getKey());
   assertFalse(ShaderOptimizationProfiles.deadMipNativeOptions(actual),entry.getKey());
   assertEquals(old.test(actual),ShaderOptimizationProfiles.deadMipNativeOptions(actual));
   actual.put(entry.getKey(),entry.getValue());
  }
  actual.put("unexpectedNativeOption","true");assertEquals(old.test(actual),ShaderOptimizationProfiles.deadMipNativeOptions(actual));
  actual.remove("unexpectedNativeOption");assertTrue(ShaderOptimizationProfiles.deadMipNativeOptions(actual));
  assertEquals(old.test(null),ShaderOptimizationProfiles.deadMipNativeOptions(null));
  assertEquals(old.test(java.util.Map.of()),ShaderOptimizationProfiles.deadMipNativeOptions(java.util.Map.of()));
  assertFalse(ShaderOptimizationProfiles.deadMipNativeOptions(actual,java.util.Map.of()));
  assertFalse(ShaderOptimizationProfiles.deadMipNativeOptions(actual,null));
 }
 @Test void forcedReadyReloadStillUsesTheOwnedTransactionAndRecovery()throws Exception{
  var calls=new java.util.ArrayList<String>();
  CompileOptionControl.Session original=new CompileOptionControl.Session(){
   public boolean prepare(){calls.add("prepare");return false;}
   public void reload(OptimizationEditTransaction.Reload r)throws Exception{calls.add("reload");r.run();}
   public void restore(){calls.add("restore");}
   public void recoveryReload(OptimizationEditTransaction.Reload r)throws Exception{calls.add("recovery");r.run();}
   public void finish(){calls.add("finish");}
   public void releaseRecovery(){calls.add("release");}
  };
  assertSame(original,ShaderOptimizationProfiles.readyReload(original,false));
  var forced=ShaderOptimizationProfiles.readyReload(original,true);assertTrue(forced.prepare());
  forced.reload(()->calls.add("actualReload"));forced.restore();forced.recoveryReload(()->calls.add("actualRecovery"));forced.finish();forced.releaseRecovery();
  assertEquals(java.util.List.of("prepare","reload","actualReload","restore","recovery","actualRecovery","finish","release"),calls);
 }
 @Test void nativePreferenceEditsCannotEnterTheWorldPreparationTransaction(){
  var nativeOptions=com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions();
  assertEquals(384,nativeOptions.size());assertTrue(ShaderOptimizationProfiles.deadMipNativeOptions(nativeOptions));
  var generated=new java.util.TreeMap<>(nativeOptions);generated.put("SHADERLOD_BSL_DEAD_MIP_CHAIN","0");generated.put("SHADERLOD_REFINEMENT_ADAPTER","0");
  assertTrue(ShaderOptimizationProfiles.deadMipNativeOptions(generated));
  generated.put("TAA","false");assertFalse(ShaderOptimizationProfiles.deadMipNativeOptions(generated));
  generated.put("TAA",nativeOptions.get("TAA"));generated.remove("FXAA");assertFalse(ShaderOptimizationProfiles.deadMipNativeOptions(generated));
  generated.put("FXAA",nativeOptions.get("FXAA"));generated.put("unexpectedNativeOption","true");assertFalse(ShaderOptimizationProfiles.deadMipNativeOptions(generated));
  assertFalse(ShaderOptimizationProfiles.deadMipNativeOptions(null));
 }
 @Test void onlyEnabledSingletonBslPerformanceCanPrepareAtTheReadyWorld(){
  var flags=OptimizationSnapshot.noneRequested().with(OptimizationId.DEAD_MIP_CHAIN,true);
  var core=new CoreUniformFrame.Core(new LodSettings(true,32,96,8,false),LodPreset.PERFORMANCE);
  assertTrue(ShaderOptimizationProfiles.deadMipProfile("bsl-shaders",core,flags));
  for(String family:new String[]{"makeup-ultra-fast-shaders","global","complementary-reimagined"})
   assertFalse(ShaderOptimizationProfiles.deadMipProfile(family,core,flags));
  assertFalse(ShaderOptimizationProfiles.deadMipProfile("bsl-shaders",null,flags));
  assertFalse(ShaderOptimizationProfiles.deadMipProfile("bsl-shaders",new CoreUniformFrame.Core(new LodSettings(false,32,96,8,false),LodPreset.PERFORMANCE),flags));
  for(LodPreset preset:LodPreset.values())if(preset!=LodPreset.PERFORMANCE)
   assertFalse(ShaderOptimizationProfiles.deadMipProfile("bsl-shaders",new CoreUniformFrame.Core(core.settings(),preset),flags));
  assertFalse(ShaderOptimizationProfiles.deadMipProfile("bsl-shaders",core,OptimizationSnapshot.noneRequested()));
  for(var id:OptimizationId.values())if(id!=OptimizationId.DEAD_MIP_CHAIN)
   assertFalse(ShaderOptimizationProfiles.deadMipProfile("bsl-shaders",core,flags.with(id,true)));
 }
}
