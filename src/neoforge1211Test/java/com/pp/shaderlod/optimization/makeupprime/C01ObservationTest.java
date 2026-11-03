package com.pp.shaderlod.optimization.makeupprime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class C01ObservationTest {
 @Test void depthSourcePinMatchesCurrentNormalProducer(){
  var pack=com.pp.shaderlod.packs.SupportedPackCatalog.inventory().stream().filter(p->p.id().equals("makeup-ultra-fast-shaders")).findFirst().orElseThrow();
  assertEquals(pack.adapterSourceDigest(),MakeUpDepthPriming.SOURCE);
 }
 @Test void zeroRasterIsNotOcclusion(){assertFalse(C01Observation.hidden(0,0,false));}
 @Test void equalityVisibleAndNearRemainVisible(){
  assertFalse(C01Observation.hidden(100,1,false));assertFalse(C01Observation.hidden(100,0,true));
  assertTrue(C01Observation.hidden(100,0,false));
 }
 @Test void commandJoinMustMatch(){
  C01Observation.verifyRegion(123,123,7,7);
  assertThrows(IllegalStateException.class,()->C01Observation.verifyRegion(123,124,7,7));
  assertThrows(IllegalStateException.class,()->C01Observation.verifyRegion(123,123,7,6));
 }
}
