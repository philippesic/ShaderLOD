package com.pp.shaderlod.optimization.bsl;

import com.google.gson.*;
import com.pp.shaderlod.bslprefix.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BslPrefixStorageTest {
 @Test void alphaPrecisionAloneCannotAuthorizeUnsizedStorage() {
  var unknown=new BslPrefixGpuDiagnostic.Image(1,0,2,0,2048,1024,6408,8,6406,0,4,9986,9728,0,10497,10497,1,0,0,4);
  assertFalse(BslPrefixRuntime.supported(unknown));
  var verified=new BslPrefixGpuDiagnostic.Image(1,0,2,0,2048,1024,6408,8,6406,0,4,9986,9728,0,10497,10497,1,0,0,4,true);
  assertTrue(BslPrefixRuntime.supported(verified));
 }
 @Test void exactObservedTupleRejectsOtherChannelTypesAndIncompleteMipStorage() throws Exception {
  var row=JsonParser.parseString(Files.readString(Path.of("tests/fixtures/bsl-prefix-unsized-rgba.json"))).getAsJsonObject();
  assertTrue(BslPrefixStorageProperties.matches(row));
  for(String channel:new String[]{"red","green","blue","alpha"}) {
   var changed=row.deepCopy();changed.getAsJsonArray("levels").get(0).getAsJsonObject().addProperty(channel+"Type",5126);
   assertFalse(BslPrefixStorageProperties.matches(changed),channel+" float storage");
   changed=row.deepCopy();changed.getAsJsonArray("levels").get(4).getAsJsonObject().addProperty(channel+"Size",16);
   assertFalse(BslPrefixStorageProperties.matches(changed),channel+" mip precision");
  }
  var changed=row.deepCopy();changed.getAsJsonArray("levels").remove(4);
  assertFalse(BslPrefixStorageProperties.matches(changed));
  changed=row.deepCopy();changed.addProperty("lodBias",1);
  assertFalse(BslPrefixStorageProperties.matches(changed));
 }
}
