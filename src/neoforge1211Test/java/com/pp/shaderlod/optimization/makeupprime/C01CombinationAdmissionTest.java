package com.pp.shaderlod.optimization.makeupprime;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility;
import static org.junit.jupiter.api.Assertions.*;
class C01CombinationAdmissionTest {
 @Test void generatedCombinationRetainsCompleteNativeQualityAndUnknownOptionRefusal() {
  String old=System.getProperty("shaderlod.dev.shortlistCombinationRuntime"),probe=System.getProperty("shaderlod.dev.benchmarkControlProbe");
  try {
   var options=new TreeMap<>(MakeUpQuadEligibility.nativeOptions());
   for(String key:List.of("SHADERLOD_REFINEMENT_ADAPTER",CompileOptionControl.MAKEUP_AO_HALF,CompileOptionControl.MAKEUP_DEPTH,CompileOptionControl.MAKEUP_EARLY_ALPHA,CompileOptionControl.MAKEUP_DEAD_FRAME_COPY,CompileOptionControl.MAKEUP_TRANSLUCENT_FASTPATH,CompileOptionControl.MAKEUP_AO_ATTENUATION,CompileOptionControl.MAKEUP_SPATIAL_REUSE,CompileOptionControl.MAKEUP_SPATIAL_RATE,CompileOptionControl.MAKEUP_WORLD_PIXEL))options.put(key,"0");
   for(String key:List.of("SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","SHADERLOD_REFINEMENT_BUDGET_INCLUDED","SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","SHADERLOD_OPTIMIZATION_FLAGS_INCLUDED"))options.put(key,"true");
   options.put(CompileOptionControl.MAKEUP_DEAD_FRAME_COPY,"1");options.put(CompileOptionControl.MAKEUP_AO_ATTENUATION,"1");
   System.clearProperty("shaderlod.dev.shortlistCombinationRuntime");assertFalse(C01NativeAdmission.combinationOptions(options));
   System.setProperty("shaderlod.dev.shortlistCombinationRuntime","true");System.setProperty("shaderlod.dev.benchmarkControlProbe","fixture");
   boolean host=com.pp.shaderlod.optimization.makeupexact.MakeUpCombinationGraph.permittedHost(System.getProperty("os.name"),System.getProperty("os.arch"));
   assertEquals(host,C01NativeAdmission.combinationOptions(options));
   for(String key:MakeUpQuadEligibility.nativeOptions().keySet()) {var changed=new TreeMap<>(options);changed.put(key,"changed");assertFalse(C01NativeAdmission.combinationOptions(changed),key);}
   for(String key:List.of(CompileOptionControl.MAKEUP_DEPTH,CompileOptionControl.MAKEUP_SPATIAL_REUSE,"UNKNOWN")) {var changed=new TreeMap<>(options);changed.put(key,"1");assertFalse(C01NativeAdmission.combinationOptions(changed),key);}
   assertFalse(C01NativeAdmission.stageAllowed("unrecognized",true));assertFalse(C01NativeAdmission.stageAllowed("unrecognized",false));
  } finally {
   restore("shaderlod.dev.shortlistCombinationRuntime",old);restore("shaderlod.dev.benchmarkControlProbe",probe);
  }
 }
 private static void restore(String key,String value){if(value==null)System.clearProperty(key);else System.setProperty(key,value);}
}
