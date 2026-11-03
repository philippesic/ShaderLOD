package com.pp.shaderlod.integration;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility;
import com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility;
import com.pp.shaderlod.integration.photonquad.PhotonQuadEligibility;
import com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class QuadTransferEligibilityTest {
 @TempDir Path temporary;
 private final Path[] archives={Path.of("dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip"),Path.of("dependencies/upstream/other-pack-research/ComplementaryUnbound_r5.9.3.zip"),Path.of("dependencies/upstream/photon_v1.3b.zip"),Path.of("dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip")};
 private List<Map<String,String>> maps(){return List.of(MakeUpQuadEligibility.nativeOptions(),UnboundQuadEligibility.nativeOptions(),PhotonQuadEligibility.nativeOptions(),ReimaginedQuadEligibility.nativeOptions());}
 private boolean gate(int i,Path archive,Map<String,String> options){return switch(i){case 0->MakeUpQuadEligibility.validate(archive,options).eligible();case 1->UnboundQuadEligibility.validate(archive,options).eligible();case 2->PhotonQuadEligibility.validate(archive,options).eligible();default->ReimaginedQuadEligibility.validate(archive,options).eligible();};}
 @Test void allFourExactCertificatesAcceptAndCrossPackArchivesRefuse() {
  var maps=maps();assertEquals(List.of(100,398,366,398),maps.stream().map(Map::size).toList());
  for(int cert=0;cert<4;cert++)for(int pack=0;pack<4;pack++)assertEquals(cert==pack,gate(cert,archives[pack],maps.get(cert)),"cert "+cert+" archive "+pack);
 }
 @Test void transferredCertificatesRejectEveryChangedOrMissingOptionAndDerivativeInputs() {
  for(int cert=2;cert<4;cert++){
   var expected=maps().get(cert);
   for(String key:expected.keySet()){
    var wrong=new TreeMap<>(expected);wrong.remove(key);assertFalse(gate(cert,archives[cert],wrong),key+" missing");
    wrong.put(key,"changed");assertFalse(gate(cert,archives[cert],wrong),key+" changed");
   }
   var extra=new TreeMap<>(expected);extra.put("SHADERLOD_LIVE_PIXEL_BUDGET","true");assertFalse(gate(cert,archives[cert],extra));
   assertFalse(gate(cert,archives[cert],null));assertFalse(gate(cert,archives[cert],Map.of()));
  }
 }
 @Test void archiveCertificateUsesBytesAndRefusesAbsentOrTamperedInputs()throws Exception {
  for(int cert=2;cert<4;cert++){
   Path alias=temporary.resolve("alias"+cert+".zip");Files.copy(archives[cert],alias);assertTrue(gate(cert,alias,maps().get(cert)));
   Files.writeString(alias,"changed",StandardOpenOption.APPEND);assertFalse(gate(cert,alias,maps().get(cert)));
   assertFalse(gate(cert,null,maps().get(cert)));assertFalse(gate(cert,temporary,maps().get(cert)));
  }
 }
}
