package com.pp.shaderlod.optimization.bsl;
import com.pp.shaderlod.optimization.mips.BslMipWitness;
import com.pp.shaderlod.integration.bslquad.BslQuadEligibility;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BslNativeMipObserverTest {
 @Test void privateObservationRequiresExactArchiveSourceOptionsAndOptIn() {
  String archive="a6cb00f3e71f307452e48ee07a85c607189992ca51668bf61b29686b9b246f11",source="205a97debef534fee2832956b4084d0b1192379f8ab2f15f520442035f064220";
  var nativeValues=BslQuadEligibility.nativeOptions();assertEquals(384,nativeValues.size());
  assertTrue(BslMipWitness.nativeDiscriminator(archive,source,nativeValues,true));
  assertFalse(BslMipWitness.nativeDiscriminator(archive,source,nativeValues,false));
  assertFalse(BslMipWitness.nativeDiscriminator("0".repeat(64),source,nativeValues,true));
  assertFalse(BslMipWitness.nativeDiscriminator(archive,"0".repeat(64),nativeValues,true));
  var changed=new HashMap<>(nativeValues);changed.put("ADVANCED_MATERIALS","true");
  assertEquals(com.pp.shaderlod.optimization.bslprefix.BslPrefixProfile.nativeOptions(),changed);
  assertTrue(BslMipWitness.nativeDiscriminator(archive,source,changed,true));
  changed.put("PARALLAX_QUALITY","64");
  assertFalse(BslMipWitness.nativeDiscriminator(archive,source,changed,true));
 }
}
