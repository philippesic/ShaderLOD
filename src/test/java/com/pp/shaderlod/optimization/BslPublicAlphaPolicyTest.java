package com.pp.shaderlod.optimization;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.bslearlyalpha.BslPostSampleAlpha;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BslPublicAlphaPolicyTest {
 @Test void publicRequestRequiresLoadedActualFamilyPerformanceSingleton(){
  var flags=OptimizationSnapshot.noneRequested().with(OptimizationId.EARLY_ALPHA,true);
  assertTrue(BslPostSampleAlpha.requested(true,"bsl-shaders",true,LodPreset.PERFORMANCE,flags));
  assertFalse(BslPostSampleAlpha.requested(false,"bsl-shaders",true,LodPreset.PERFORMANCE,flags));
  assertFalse(BslPostSampleAlpha.requested(true,"global",true,LodPreset.PERFORMANCE,flags));
  assertFalse(BslPostSampleAlpha.requested(true,"bsl-shaders",false,LodPreset.PERFORMANCE,flags));
  assertFalse(BslPostSampleAlpha.requested(true,"bsl-shaders",true,LodPreset.QUALITY,flags));
  for(var id:OptimizationId.values())if(id!=OptimizationId.EARLY_ALPHA)
   assertFalse(BslPostSampleAlpha.requested(true,"bsl-shaders",true,LodPreset.PERFORMANCE,flags.with(id,true)),id.name());
  assertFalse(BslPostSampleAlpha.singleton(null));
 }
 @Test void onlyCompletePinnedOptimizedPeerTupleIsAdmitted(){
  var stack=new HashMap<>(MakeUpQuadRuntime.REQUIRED_STACK);stack.put("shaderlod","test");
  assertFalse(BslPostSampleAlpha.stackMatches(stack));
  stack.put("modernfix","5.27.24+mc1.21.1");stack.put("ferritecore","7.0.3");stack.put("immediatelyfast","1.6.14+1.21.1");
  assertTrue(BslPostSampleAlpha.stackMatches(stack));
  for(String peer:List.of("distanthorizons","voxy","moreculling","cloth_config","conditional_mixin","unknown")){
   var changed=new HashMap<>(stack);changed.put(peer,"1");assertFalse(BslPostSampleAlpha.stackMatches(changed),peer);
  }
  stack.put("iris","changed");assertFalse(BslPostSampleAlpha.stackMatches(stack));
  assertFalse(BslPostSampleAlpha.platform("Mac OS X","aarch64"));assertFalse(BslPostSampleAlpha.platform("Linux","amd64"));
 }
 @Test void stableTerrainSlotAccessRequiresThePinnedIrisMap() throws Exception {
  var field=net.irisshaders.iris.pipeline.programs.SodiumPrograms.class.getDeclaredField("shaders");
  assertEquals(java.util.EnumMap.class,field.getType());
  assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()));
  assertEquals(Object.class,com.pp.shaderlod.optimization.bslearlyalpha.BslTerrainProgramView.class
      .getMethod("shaderlod$bslTerrainCutout").getReturnType());
 }

}
