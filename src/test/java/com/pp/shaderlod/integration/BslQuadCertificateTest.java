package com.pp.shaderlod.integration;
import com.pp.shaderlod.integration.bslquad.BslQuadEligibility;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class BslQuadCertificateTest {
 @TempDir Path dir;
 private final Path original=Path.of("dependencies/upstream/other-pack-research/BSL_v10.1.8.zip");
 @Test void exactNative384AdmitsButAllForeignOriginalArchivesRefuse() {
  var opts=BslQuadEligibility.nativeOptions();assertEquals(384,opts.size());assertTrue(BslQuadEligibility.validate(original,opts).eligible());
  for(String p:List.of("dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip","dependencies/upstream/other-pack-research/ComplementaryUnbound_r5.9.3.zip","dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip","dependencies/upstream/photon_v1.3b.zip"))assertFalse(BslQuadEligibility.validate(Path.of(p),opts).eligible(),p);
 }
 @Test void everyMissingChangedExtraOrUnavailableOptionRefuses() {
  var expected=BslQuadEligibility.nativeOptions();
  for(var key:expected.keySet()){
   var wrong=new TreeMap<>(expected);wrong.remove(key);assertFalse(BslQuadEligibility.validate(original,wrong).eligible(),key);
   wrong.put(key,"changed");assertFalse(BslQuadEligibility.validate(original,wrong).eligible(),key);
  }
  var extra=new TreeMap<>(expected);extra.put("SHADERLOD_REFINEMENT_ADAPTER","1");assertFalse(BslQuadEligibility.validate(original,extra).eligible());
  assertFalse(BslQuadEligibility.validate(original,null).eligible());assertFalse(BslQuadEligibility.validate(original,Map.of()).eligible());
 }
 @Test void filenameAliasDoesNotBroadenByteIdentity()throws Exception {
  Path alias=dir.resolve("alias.zip");Files.copy(original,alias);assertTrue(BslQuadEligibility.validate(alias,BslQuadEligibility.nativeOptions()).eligible());
  Files.writeString(alias,"changed",StandardOpenOption.APPEND);assertFalse(BslQuadEligibility.validate(alias,BslQuadEligibility.nativeOptions()).eligible());
  assertFalse(BslQuadEligibility.validate(null,BslQuadEligibility.nativeOptions()).eligible());assertFalse(BslQuadEligibility.validate(dir,BslQuadEligibility.nativeOptions()).eligible());
 }
}
