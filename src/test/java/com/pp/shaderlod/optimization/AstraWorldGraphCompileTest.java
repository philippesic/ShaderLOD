package com.pp.shaderlod.optimization;
import com.pp.shaderlod.*;
import com.pp.shaderlod.optimization.CompileOptionControl.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Pure normal compile policy/strict-map checks; authentic parser export is CompileFamilyChecksMain, not this fixture. */
class AstraWorldGraphCompileTest {
 private static final String KEY=CompileOptionControl.ASTRALEX_WORLD_GRAPH;
 private static CoreUniformFrame.Core core(boolean enabled,LodPreset preset){return new CoreUniformFrame.Core(new LodSettings(enabled,32,96,8,false),preset);}
 private static String desired(OptimizationSnapshot flags,boolean enabled,LodPreset preset,String dim,boolean dh,boolean voxy){return CompileOptionControl.plan(Family.ASTRALEX,core(enabled,preset),flags,Set.of(KEY),Map.of(),dim,dh,voxy,Map.of()).get(KEY);}
 @Test void allWorldRequestUnionsCompileWithoutCurrentlyAbsentDrawOwnership(){
  var peers=List.of(OptimizationId.SPATIAL_LIGHTING_REUSE,OptimizationId.SPATIAL_LIGHTING_RATE,OptimizationId.PIXEL_SCALE);
  for(int mask=0;mask<8;mask++){
   var flags=OptimizationSnapshot.noneRequested();for(int i=0;i<3;i++)flags=flags.with(peers.get(i),(mask&(1<<i))!=0);
   assertEquals(mask==0?"0":"1",desired(flags,true,LodPreset.PERFORMANCE,"minecraft:overworld",false,false));
   assertEquals("0",desired(flags,false,LodPreset.PERFORMANCE,"minecraft:overworld",false,false));
   assertEquals("0",desired(flags,true,LodPreset.QUALITY,"minecraft:overworld",false,false));
   for(String dim:List.of("minecraft:the_nether","minecraft:the_end","custom:moon","unavailable"))assertEquals("0",desired(flags,true,LodPreset.PERFORMANCE,dim,false,false));
   assertEquals("0",desired(flags,true,LodPreset.PERFORMANCE,"minecraft:overworld",true,false));
   assertEquals("0",desired(flags,true,LodPreset.PERFORMANCE,"minecraft:overworld",false,true));
  }
  assertEquals("0",desired(OptimizationSnapshot.noneRequested().with(OptimizationId.SHADOW_SAMPLE_BUDGET,true),true,LodPreset.PERFORMANCE,"minecraft:overworld",false,false));
 }
 @Test void exactBinaryDeclarationAndThreeExistingControlsOnly(){
  assertTrue(CompileOptionControl.declaredContract(KEY,new NativeOption("STRING",Set.of("0","1"),"0","1")));
  assertFalse(CompileOptionControl.declaredContract(KEY,new NativeOption("BOOLEAN",Set.of("false","true"),"false","true")));
  assertFalse(CompileOptionControl.declaredContract(KEY,new NativeOption("STRING",Set.of("0","1"),"1","1")));
  assertFalse(CompileOptionControl.declaredContract(KEY,new NativeOption("STRING",Set.of("0","1","2"),"0","1")));
  for(var id:OptimizationId.values())assertEquals(Set.of(OptimizationId.SPATIAL_LIGHTING_REUSE,OptimizationId.SPATIAL_LIGHTING_RATE,OptimizationId.PIXEL_SCALE).contains(id),CompileOptionControl.controlledOptions(id).contains(KEY));
 }
 @Test void completeProfileOnlyNormalizesBinaryOwnedKeyAndPreservesNativeAA(){
  var canonical=com.pp.shaderlod.optimization.astraao.AstraAoContract.generated("0");assertEquals(1027,canonical.size());
  for(String mode:List.of("0","1")){
   var map=new TreeMap<>(canonical);map.put(KEY,mode);assertTrue(com.pp.shaderlod.optimization.astraao.AstraAoContract.options(map));assertTrue(com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts("astralex",map));
   var bad=new TreeMap<>(map);bad.put(KEY,"2");assertFalse(com.pp.shaderlod.optimization.astraao.AstraAoContract.options(bad));
   bad=new TreeMap<>(map);bad.remove(KEY);assertFalse(com.pp.shaderlod.optimization.astraao.AstraAoContract.options(bad));
   bad=new TreeMap<>(map);bad.put("AA","0");assertFalse(com.pp.shaderlod.optimization.astraao.AstraAoContract.options(bad));
  }
 }
}
