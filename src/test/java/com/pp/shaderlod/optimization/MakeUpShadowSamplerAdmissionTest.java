package com.pp.shaderlod.optimization;
import com.pp.shaderlod.optimization.makeupshadow.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MakeUpShadowSamplerAdmissionTest {
 @Test void eitherRequestOrCorePeerActivationRefusesReuse(){
  var allowed=Set.of(OptimizationId.SHADOW_SAMPLE_BUDGET,OptimizationId.INDEX_SCALAR_CACHE);
  assertTrue(MakeUpShadowSamplerPass.allowedFlags(allowed::contains,allowed::contains));
  for(var peer:List.of(OptimizationId.DEPTH_PRIMING,OptimizationId.MERGED_TERRAIN,OptimizationId.EARLY_ALPHA)){
   assertFalse(MakeUpShadowSamplerPass.allowedFlags(id->allowed.contains(id)||id==peer,allowed::contains));
   assertFalse(MakeUpShadowSamplerPass.allowedFlags(allowed::contains,id->allowed.contains(id)||id==peer));
  }
 }
 @Test void allCompiledPeerControlsMustBeExplicitlyZero(){
  var names=List.of("SHADERLOD_MAKEUP_EARLY_ALPHA","SHADERLOD_MAKEUP_AO_HALF_RES","SHADERLOD_MAKEUP_DEPTH_PRIMING","SHADERLOD_MAKEUP_SPATIAL_REUSE","SHADERLOD_MAKEUP_SPATIAL_RATE","SHADERLOD_MAKEUP_WORLD_PIXEL");
  var values=new HashMap<String,String>();for(var name:names)values.put(name,"0");
  assertTrue(MakeUpShadowSamplerPass.compiledProfile(values));
  for(var name:names){values.put(name,"1");assertFalse(MakeUpShadowSamplerPass.compiledProfile(values));values.put(name,"0");}
  values.remove(names.getFirst());assertFalse(MakeUpShadowSamplerPass.compiledProfile(values));
 }
 @Test void ordinaryObserverIsDistinctFromExperimentalBootstrapTransforms(){
  var properties=new Properties();properties.setProperty("shaderlod.dev.benchmarkTunerProbe","control.json");
  properties.setProperty("shaderlod.dev.compare","on");assertTrue(MakeUpShadowScopePins.safeProperties(properties));
  properties.setProperty("shaderlod.dev.makeupMergedSubmissionR1","true");assertFalse(MakeUpShadowScopePins.safeProperties(properties));
 }
 @Test void maintainedControllerAndObserversDoNotDisableCompiledProof(){
  var properties=new Properties();
  properties.setProperty("shaderlod.dev.benchmarkControlProbe","owned/control.json");
  properties.setProperty("shaderlod.dev.readinessEvents","owned/readiness.json");
  for(var key:List.of("savedAuthoredScene","rendererWorkCounters","nativeTerrainScene","privateObservedReload","mipFamilyRuntime","qualityMotionRuntime"))properties.setProperty("shaderlod.dev."+key,"true");
  assertTrue(MakeUpShadowScopePins.safeProperties(properties));
  for(var key:List.of("makeupMergedSubmissionR1","makeupDepthPriming","futureUnqualifiedTransform")){
   properties.setProperty("shaderlod.dev."+key,"true");assertFalse(MakeUpShadowScopePins.safeProperties(properties));properties.remove("shaderlod.dev."+key);
  }
  properties.setProperty("shaderlod.experimentalRendererResearch","true");assertFalse(MakeUpShadowScopePins.safeProperties(properties));
 }
 @Test void explicitContextIsValidatedAndRequiresTheMaintainedController(){
  var properties=new Properties();properties.setProperty("shaderlod.dev.benchmarkGlContext","4.6");
  assertFalse(MakeUpShadowScopePins.safeProperties(properties));
  properties.setProperty("shaderlod.dev.benchmarkControlProbe","owned/control.json");
  assertTrue(MakeUpShadowScopePins.safeProperties(properties));
  properties.setProperty("shaderlod.dev.benchmarkGlContext","true");
  assertFalse(MakeUpShadowScopePins.safeProperties(properties));
 }
}
