package com.pp.shaderlod.optimization.depthprime;
import java.util.*;
import java.util.function.Predicate;
import com.google.gson.JsonParser;
import com.pp.shaderlod.optimization.makeupprime.MakeUpDepthPriming;
import com.pp.shaderlod.optimization.reimaginedprime.ReimaginedDepthPriming;
import com.pp.shaderlod.optimization.sildurprime.SildurDepthPriming;
import com.pp.shaderlod.optimization.astraprime.AstraDepthPriming;
import com.pp.shaderlod.optimization.blissprime.BlissDepthPriming;
import com.pp.shaderlod.optimization.bslprime.BslDepthPriming;
import com.pp.shaderlod.optimization.photonprime.PhotonDepthPriming;
import com.pp.shaderlod.optimization.solasprime.SolasDepthPriming;
import com.pp.shaderlod.optimization.unboundprime.UnboundDepthPriming;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DepthNativeProfileTest {
 private Map<String,String> profile(String name)throws Exception{
  try(var in=getClass().getResourceAsStream("/assets/shaderlod/"+name)){
   assertNotNull(in);var values=new TreeMap<String,String>();JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(e->values.put(e.getKey(),e.getValue().getAsString()));return values;
  }
 }
 private void check(Map<String,String> values,String flag,String aa,Predicate<Map<String,String>> accepts){
  assertTrue(accepts.test(values));values.put(flag,"1");assertTrue(accepts.test(values));
  String nativeAA=values.put(aa,"unsupported");assertFalse(accepts.test(values));values.put(aa,nativeAA);assertTrue(accepts.test(values));
  values.put("UNDECLARED_NATIVE_OPTION","1");assertFalse(accepts.test(values));values.remove("UNDECLARED_NATIVE_OPTION");
  values.put(flag,"2");assertFalse(accepts.test(values));values.put(flag,"0");assertTrue(accepts.test(values));
  values.remove(aa);assertFalse(accepts.test(values));
 }
 @Test void makeupPreservesNativeAntialiasingAndExactDeclarations()throws Exception{
  var m=profile("makeupprime/options106.json");for(String key:List.of("SHADERLOD_MAKEUP_EARLY_ALPHA","SHADERLOD_MAKEUP_WORLD_PIXEL","SHADERLOD_MAKEUP_SPATIAL_RATE","SHADERLOD_MAKEUP_SPATIAL_REUSE","SHADERLOD_MAKEUP_AO_HALF_RES"))m.put(key,"0");
  check(m,MakeUpDepthPriming.OPTION,"AA_TYPE",MakeUpDepthPriming::profileMatch);
 }
 @Test void reimaginedKeepsPOMAndTaaNative()throws Exception{
  var m=profile("reimaginedprime/options406.json");for(String key:List.of("SHADERLOD_REIMAGINED_SPATIAL_REUSE","SHADERLOD_REIMAGINED_SPATIAL_RATE","SHADERLOD_REIMAGINED_PIXEL_GRAPH"))m.put(key,"0");
  assertEquals("true",m.get("POM"));var changed=new TreeMap<>(m);changed.put("POM","false");assertFalse(ReimaginedDepthPriming.profileMatch(changed));
  check(m,ReimaginedDepthPriming.OPTION,"TAA",ReimaginedDepthPriming::profileMatch);
 }
 @Test void sildurPreservesTaaAndRestoresControlProfile()throws Exception{
  var m=profile("sildurprime/options127.json");check(m,SildurDepthPriming.OPTION,"TAA",SildurDepthPriming::profileMatch);
 }
 @Test void unboundPreservesNativeTaaAndExactDeclarations()throws Exception{
  var m=profile("unboundprime/options406.json");for(String key:List.of("SHADERLOD_UNBOUND_SPATIAL_REUSE","SHADERLOD_UNBOUND_SPATIAL_RATE","SHADERLOD_UNBOUND_PIXEL_GRAPH"))m.put(key,"0");
  check(m,UnboundDepthPriming.OPTION,"TAA",UnboundDepthPriming::profileMatch);
 }
 @Test void bslPreservesNativeTaaAndExactDeclarations()throws Exception{
  check(profile("bslprime/options398.json"),BslDepthPriming.OPTION,"TAA",BslDepthPriming::profileMatch);
 }
 @Test void blissPreservesNativeTaaAndExactDeclarations()throws Exception{
  check(profile("blissprime/options471.json"),BlissDepthPriming.OPTION,"TAA",BlissDepthPriming::profileMatch);
 }
 @Test void photonPreservesNativeTaaAndExactDeclarations()throws Exception{
  check(profile("photonprime/options395.json"),PhotonDepthPriming.OPTION,"TAA",PhotonDepthPriming::profileMatch);
 }
 @Test void astralexPreservesNativeAaAndExactDeclarations()throws Exception{
  check(profile("astraprime/options1023.json"),AstraDepthPriming.OPTION,"AA",AstraDepthPriming::profileMatch);
 }
 @Test void solasPreservesNativeTaaAndExactDeclarations()throws Exception{
  check(profile("solasprime/options435.json"),SolasDepthPriming.OPTION,"TAA",SolasDepthPriming::profileMatch);
 }
}
