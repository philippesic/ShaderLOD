package com.pp.shaderlod.integration.unboundquad;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class UnboundQuadEligibilityTest {
 @TempDir Path dir;
 private final Path original=Path.of("dependencies/upstream/other-pack-research/ComplementaryUnbound_r5.9.3.zip");
 @Test void exactOriginalNative398PassesAndCrossPackCertificatesStaySeparate() throws Exception {
  assertEquals(398,UnboundQuadEligibility.nativeOptions().size());
  assertTrue(UnboundQuadEligibility.validate(original,UnboundQuadEligibility.nativeOptions()).eligible());
  assertFalse(MakeUpQuadEligibility.validate(original,UnboundQuadEligibility.nativeOptions()).eligible());
  assertFalse(UnboundQuadEligibility.validate(original,MakeUpQuadEligibility.nativeOptions()).eligible());
  assertFalse(UnboundQuadEligibility.validate(Path.of("dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip"),UnboundQuadEligibility.nativeOptions()).eligible());
 }
 @Test void changedAaPbrMissingExtraAndAbsentOptionsStayNative() {
  for(var edit:List.of(Map.entry("TAA","false"),Map.entry("RP_MODE","2"),Map.entry("UNKNOWN","1"))) {
   var actual=new TreeMap<>(UnboundQuadEligibility.nativeOptions());actual.put(edit.getKey(),edit.getValue());
   assertFalse(UnboundQuadEligibility.validate(original,actual).eligible(),edit.toString());
  }
  var missing=new TreeMap<>(UnboundQuadEligibility.nativeOptions());missing.remove("TAA");
  assertFalse(UnboundQuadEligibility.optionsMatch(missing));
  assertFalse(UnboundQuadEligibility.optionsMatch(Map.of()));assertFalse(UnboundQuadEligibility.optionsMatch(null));
 }
 @Test void changedArchiveMissingFileAndDirectoryStayNative() throws Exception {
  var changed=dir.resolve("Changed.zip");Files.copy(original,changed);Files.writeString(changed,"tamper",StandardOpenOption.APPEND);
  assertFalse(UnboundQuadEligibility.validate(changed,UnboundQuadEligibility.nativeOptions()).eligible());
  assertFalse(UnboundQuadEligibility.validate(null,UnboundQuadEligibility.nativeOptions()).eligible());
  assertFalse(UnboundQuadEligibility.validate(dir,UnboundQuadEligibility.nativeOptions()).eligible());
 }
}
