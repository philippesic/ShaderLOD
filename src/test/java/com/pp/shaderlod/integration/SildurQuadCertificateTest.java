package com.pp.shaderlod.integration;
import com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class SildurQuadCertificateTest {
 @TempDir Path dir;
 private final Path original=Path.of("dependencies/upstream/pack-expansion/Sildur's Vibrant Shaders v2.02 Extreme.zip");
 @Test void exactNative115AdmitsButAllForeignOriginalArchivesRefuse() {
  var opts=SildurQuadEligibility.nativeOptions();assertEquals(115,opts.size());assertTrue(SildurQuadEligibility.validate(original,opts).eligible());
  for(String p:List.of("dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip","dependencies/upstream/other-pack-research/ComplementaryUnbound_r5.9.3.zip","dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip","dependencies/upstream/photon_v1.3b.zip"))assertFalse(SildurQuadEligibility.validate(Path.of(p),opts).eligible(),p);
 }
 @Test void everyMissingChangedExtraOrUnavailableOptionRefuses() {
  var expected=SildurQuadEligibility.nativeOptions();
  for(var key:expected.keySet()){
   var wrong=new TreeMap<>(expected);wrong.remove(key);assertFalse(SildurQuadEligibility.validate(original,wrong).eligible(),key);
   wrong.put(key,"changed");assertFalse(SildurQuadEligibility.validate(original,wrong).eligible(),key);
  }
  var extra=new TreeMap<>(expected);extra.put("SHADERLOD_EFFECT_ADAPTER","1");assertFalse(SildurQuadEligibility.validate(original,extra).eligible());
  assertFalse(SildurQuadEligibility.validate(original,null).eligible());assertFalse(SildurQuadEligibility.validate(original,Map.of()).eligible());
 }
 @Test void filenameAliasDoesNotBroadenByteIdentity()throws Exception {
  Path alias=dir.resolve("alias.zip");Files.copy(original,alias);assertTrue(SildurQuadEligibility.validate(alias,SildurQuadEligibility.nativeOptions()).eligible());
  Files.writeString(alias,"changed",StandardOpenOption.APPEND);assertFalse(SildurQuadEligibility.validate(alias,SildurQuadEligibility.nativeOptions()).eligible());
  assertFalse(SildurQuadEligibility.validate(null,SildurQuadEligibility.nativeOptions()).eligible());assertFalse(SildurQuadEligibility.validate(dir,SildurQuadEligibility.nativeOptions()).eligible());
 }
}
