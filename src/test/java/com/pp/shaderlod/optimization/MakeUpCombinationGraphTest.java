package com.pp.shaderlod.optimization;
import com.pp.shaderlod.optimization.makeupexact.*;
import com.pp.shaderlod.optimization.framecopy.*;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MakeUpCombinationGraphTest {
 private static final String SWITCH="shaderlod.dev.shortlistCombinationRuntime",PROBE="shaderlod.dev.benchmarkControlProbe";
 private static Map<String,String> options(){
  var m=new TreeMap<>(MakeUpQuadEligibility.nativeOptions());m.put("SHADERLOD_REFINEMENT_ADAPTER","0");
  for(var key:List.of(CompileOptionControl.MAKEUP_AO_HALF,CompileOptionControl.MAKEUP_DEPTH,CompileOptionControl.MAKEUP_SPATIAL_REUSE,CompileOptionControl.MAKEUP_SPATIAL_RATE,CompileOptionControl.MAKEUP_WORLD_PIXEL,CompileOptionControl.MAKEUP_EARLY_ALPHA,CompileOptionControl.MAKEUP_DEAD_FRAME_COPY,CompileOptionControl.MAKEUP_TRANSLUCENT_FASTPATH,CompileOptionControl.MAKEUP_AO_ATTENUATION))m.put(key,"0");
  for(var key:List.of("SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","SHADERLOD_REFINEMENT_BUDGET_INCLUDED","SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","SHADERLOD_OPTIMIZATION_FLAGS_INCLUDED"))m.put(key,"true");return m;
 }
 @Test void experimentalHostBoundaryIsExplicit(){
  assertTrue(MakeUpCombinationGraph.permittedHost("Linux","aarch64"));
  assertFalse(MakeUpCombinationGraph.permittedHost("Mac OS X","aarch64"));
  assertFalse(MakeUpCombinationGraph.permittedHost("Linux","x86_64"));
  assertFalse(MakeUpCombinationGraph.permittedHost(null,null));
 }
 @Test void explicitProbeAndSwitchRequiredAndEveryOtherFeatureRefuses(){
  String old=System.getProperty(SWITCH),probe=System.getProperty(PROBE);
  try{
   var pair=OptimizationSnapshot.noneRequested().with(OptimizationId.DEAD_FRAME_COPY,true).with(OptimizationId.AO_ATTENUATION,true);
   System.clearProperty(SWITCH);System.clearProperty(PROBE);assertFalse(MakeUpCombinationGraph.pair(pair));
   System.setProperty(SWITCH,"true");assertFalse(MakeUpCombinationGraph.pair(pair));System.setProperty(PROBE,"fixture.json");
   boolean host=MakeUpCombinationGraph.permittedHost(System.getProperty("os.name"),System.getProperty("os.arch"));
   assertEquals(host,MakeUpCombinationGraph.pair(pair));assertEquals(host,MakeUpLeanGraph.onlyDeadCopy(pair));assertEquals(host,MakeUpExactGraph.only(pair,OptimizationId.AO_ATTENUATION));assertFalse(MakeUpExactGraph.only(pair,OptimizationId.TRANSLUCENT_FASTPATH));
   assertFalse(MakeUpCombinationGraph.pair(pair.with(OptimizationId.DEAD_FRAME_COPY,false)));
   var allowed=List.of(OptimizationId.DEAD_FRAME_COPY,OptimizationId.EARLY_ALPHA,OptimizationId.TRANSLUCENT_FASTPATH,OptimizationId.AO_ATTENUATION);
   for(int mask=0;mask<16;mask++) {
    var subset=OptimizationSnapshot.noneRequested();for(int i=0;i<4;i++)subset=subset.with(allowed.get(i),(mask&(1<<i))!=0);
    assertEquals(host&&Integer.bitCount(mask)>=2,MakeUpCombinationGraph.pair(subset),"mask "+mask);
    for(var id:OptimizationId.values())assertEquals(host&&Integer.bitCount(mask)>=2&&subset.enabled(id),MakeUpCombinationGraph.selected(subset,id));
   }

   for(var id:OptimizationId.values())if(id!=OptimizationId.DEAD_FRAME_COPY&&id!=OptimizationId.AO_ATTENUATION&&id!=OptimizationId.TRANSLUCENT_FASTPATH&&id!=OptimizationId.EARLY_ALPHA)assertFalse(MakeUpCombinationGraph.pair(pair.with(id,true)),id.toString());
  }finally{restore(SWITCH,old);restore(PROBE,probe);}
 }
 @Test void combinedProfilePreservesEveryNativeAndUnrelatedControl(){
  String old=System.getProperty(SWITCH),probe=System.getProperty(PROBE);
  try{
   var m=options();m.put(CompileOptionControl.MAKEUP_DEAD_FRAME_COPY,"1");m.put(CompileOptionControl.MAKEUP_AO_ATTENUATION,"1");
   System.clearProperty(SWITCH);assertFalse(MakeUpLeanGraph.profile(m));
   System.setProperty(SWITCH,"true");System.setProperty(PROBE,"fixture.json");assertEquals(MakeUpCombinationGraph.permittedHost(System.getProperty("os.name"),System.getProperty("os.arch")),MakeUpLeanGraph.profile(m));assertTrue(MakeUpExactGraph.profile(m));
   var typed=new TreeMap<String,CompileOptionControl.NativeOption>();m.forEach((key,value)->typed.put(key,new CompileOptionControl.NativeOption(value.equals("true")?"BOOLEAN":"STRING",value.equals("true")?Set.of("false","true"):new java.util.HashSet<>(java.util.List.of("0","1",value)),value.equals("true")?"true":value,value)));
   // Typed controls need exact binary declarations; native values keep their original values.
   for(var key:List.of(CompileOptionControl.MAKEUP_DEAD_FRAME_COPY,CompileOptionControl.MAKEUP_AO_ATTENUATION,"SHADERLOD_REFINEMENT_ADAPTER",CompileOptionControl.MAKEUP_AO_HALF,CompileOptionControl.MAKEUP_DEPTH,CompileOptionControl.MAKEUP_SPATIAL_REUSE,CompileOptionControl.MAKEUP_SPATIAL_RATE,CompileOptionControl.MAKEUP_WORLD_PIXEL,CompileOptionControl.MAKEUP_EARLY_ALPHA,CompileOptionControl.MAKEUP_TRANSLUCENT_FASTPATH))typed.put(key,new CompileOptionControl.NativeOption("STRING",Set.of("0","1"),"0",m.get(key)));
   assertEquals(MakeUpCombinationGraph.permittedHost(System.getProperty("os.name"),System.getProperty("os.arch")),MakeUpDeadCopyAdmission.options(typed));
   for(var key:MakeUpQuadEligibility.nativeOptions().keySet()){var changed=new TreeMap<>(m);changed.put(key,"mutation");assertFalse(MakeUpLeanGraph.profile(changed),key);}
   for(var key:List.of(CompileOptionControl.MAKEUP_DEPTH,CompileOptionControl.MAKEUP_SPATIAL_REUSE)){var changed=new TreeMap<>(m);changed.put(key,"1");assertFalse(MakeUpLeanGraph.profile(changed),key);}
   var extra=new TreeMap<>(m);extra.put("UNKNOWN","1");assertFalse(MakeUpLeanGraph.profile(extra));
   m.put(CompileOptionControl.MAKEUP_AO_ATTENUATION,"2");assertFalse(MakeUpLeanGraph.profile(m));
  }finally{restore(SWITCH,old);restore(PROBE,probe);}
 }
 private static void restore(String key,String value){if(value==null)System.clearProperty(key);else System.setProperty(key,value);}
}
