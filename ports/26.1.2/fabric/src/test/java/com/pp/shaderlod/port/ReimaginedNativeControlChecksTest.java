package com.pp.shaderlod.port;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.fabric.dev.ReimaginedNativeControlChecks;
import com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Mode separation and restoration stage boundaries; certificates use actual packaged native data. */
class ReimaginedNativeControlChecksTest {
 @Test void explicitFamilyAndConflictsRefuse(){
  assertTrue(ReimaginedNativeControlChecks.launchAllows("reimagined","false","false","false","false"));
  assertTrue(ReimaginedNativeControlChecks.launchAllows("unbound","false","false","false","false"));
  for(String mode:new String[]{"","photon","reimagined-other","unbound-other"})assertFalse(ReimaginedNativeControlChecks.launchAllows(mode,"false","false","false","false"));
  assertFalse(ReimaginedNativeControlChecks.launchAllows("reimagined","true","false","false","false"));
  assertFalse(ReimaginedNativeControlChecks.launchAllows("reimagined","false","true","false","false"));
  for(String value:new String[]{"true","unknown",""}){
   assertFalse(ReimaginedNativeControlChecks.launchAllows("reimagined","false","false",value,"false"));
   assertFalse(ReimaginedNativeControlChecks.launchAllows("reimagined","false","false","false",value));
  }
 }
 @Test void qualityPerformanceAndReturnRequireCorrectNativeCore(){
  assertTrue(ReimaginedNativeControlChecks.coreMatches(0,false,LodPreset.PERFORMANCE,LodPreset.PERFORMANCE));
  assertTrue(ReimaginedNativeControlChecks.coreMatches(1,true,LodPreset.QUALITY,LodPreset.PERFORMANCE));
  assertTrue(ReimaginedNativeControlChecks.coreMatches(2,true,LodPreset.PERFORMANCE,LodPreset.PERFORMANCE));
  assertTrue(ReimaginedNativeControlChecks.coreMatches(3,true,LodPreset.QUALITY,LodPreset.PERFORMANCE));
  assertTrue(ReimaginedNativeControlChecks.coreMatches(4,false,LodPreset.PERFORMANCE,LodPreset.PERFORMANCE));
  assertFalse(ReimaginedNativeControlChecks.coreMatches(4,true,LodPreset.PERFORMANCE,LodPreset.PERFORMANCE));
  assertFalse(ReimaginedNativeControlChecks.coreMatches(3,true,LodPreset.PERFORMANCE,LodPreset.PERFORMANCE));
  assertThrows(IllegalArgumentException.class,()->ReimaginedNativeControlChecks.stageEnabled(5));
  assertThrows(IllegalArgumentException.class,()->ReimaginedNativeControlChecks.stagePreset(0,LodPreset.QUALITY));
 }
 @Test void actualNative398MustRemainCompleteAndExact(){
  var nativeMap=ReimaginedQuadEligibility.nativeOptions();assertEquals(398,nativeMap.size());assertTrue(ReimaginedQuadEligibility.optionsMatch(nativeMap));
  var changed=new java.util.HashMap<>(nativeMap);changed.put("SHADER_STYLE","4");assertFalse(ReimaginedQuadEligibility.optionsMatch(changed));
  changed=new java.util.HashMap<>(nativeMap);changed.remove("RP_MODE");assertFalse(ReimaginedQuadEligibility.optionsMatch(changed));
  changed=new java.util.HashMap<>(nativeMap);changed.put("inventedOption","true");assertFalse(ReimaginedQuadEligibility.optionsMatch(changed));
 }
 @Test void unboundCertificateIsSeparateFromReimagined(){
  var r=ReimaginedNativeControlChecks.nativeOptions("reimagined");var u=ReimaginedNativeControlChecks.nativeOptions("unbound");
  assertEquals(398,u.size());assertTrue(ReimaginedNativeControlChecks.unboundAncestryProofsMatch());
  assertTrue(ReimaginedNativeControlChecks.optionsMatch("unbound",u));assertTrue(ReimaginedNativeControlChecks.optionsMatch("reimagined",r));
  assertFalse(ReimaginedNativeControlChecks.optionsMatch("unbound",r));assertFalse(ReimaginedNativeControlChecks.optionsMatch("reimagined",u));
  assertFalse(ReimaginedNativeControlChecks.optionsMatch("unknown",u));assertTrue(ReimaginedNativeControlChecks.nativeOptions("unknown").isEmpty());
  var changed=new java.util.HashMap<>(u);changed.remove("RP_MODE");assertFalse(ReimaginedNativeControlChecks.optionsMatch("unbound",changed));
  changed=new java.util.HashMap<>(u);changed.put("inventedOption","true");assertFalse(ReimaginedNativeControlChecks.optionsMatch("unbound",changed));
  assertFalse(ReimaginedNativeControlChecks.sourceMatches("unbound",java.nio.file.Path.of("dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip"),u));
  assertTrue(ReimaginedNativeControlChecks.sourceMatches("unbound",java.nio.file.Path.of("dependencies/upstream/pack-expansion/ComplementaryUnbound_r5.9.3.zip"),u));
  assertFalse(ReimaginedNativeControlChecks.launchAllows("unbound","true","false","false","false"));
  assertFalse(ReimaginedNativeControlChecks.launchAllows("unbound","false","true","false","false"));
  assertFalse(ReimaginedNativeControlChecks.launchAllows("unbound","false","false","unknown","false"));
 }
 @Test void sidecarAbsentChangedAndUnobservedAreDifferent(){
  String actualResourceHash=com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.OPTIONS_SHA;
  assertEquals(Boolean.TRUE,ReimaginedNativeControlChecks.sidecarByteStateRestored(true,false,null,true,false,null));
  assertEquals(Boolean.TRUE,ReimaginedNativeControlChecks.sidecarByteStateRestored(true,true,actualResourceHash,true,true,actualResourceHash));
  assertEquals(Boolean.FALSE,ReimaginedNativeControlChecks.sidecarByteStateRestored(true,true,actualResourceHash,true,false,null));
  assertNull(ReimaginedNativeControlChecks.sidecarByteStateRestored(false,false,null,true,false,null));
  assertNull(ReimaginedNativeControlChecks.sidecarByteStateRestored(true,true,null,true,false,null));
 }
}
