package com.pp.shaderlod.packs;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.optimization.mips.BslLeanGraph;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BslPublicAlphaFallbackTest {
 @Test void singletonAlwaysKeepsOriginalWithoutNormalizingUnsupportedInputs(){
  var flags=OptimizationSnapshot.noneRequested().with(OptimizationId.EARLY_ALPHA,true);
  for(boolean exact:new boolean[]{false,true})for(boolean overworld:new boolean[]{false,true})for(boolean generated:new boolean[]{false,true}){
   var route=new BslLeanGraph.Route(flags,true,"bsl-shaders",exact,overworld);
   assertTrue(PackSelectionService.asahiAlphaOriginalFallback("Linux","aarch64","bsl-shaders",true,LodPreset.PERFORMANCE,Map.of("TAA","false"),route,generated));
   assertFalse(PackSelectionService.asahiAlphaOriginalFallback("Mac OS X","aarch64","bsl-shaders",true,LodPreset.PERFORMANCE,Map.of(),route,generated));
  }
  var mixed=new BslLeanGraph.Route(flags.with(OptimizationId.DEAD_MIP_CHAIN,true),true,"bsl-shaders",true,true);
  assertFalse(PackSelectionService.asahiAlphaOriginalFallback("Linux","aarch64","bsl-shaders",true,LodPreset.PERFORMANCE,Map.of(),mixed,false));
 }
 @Test void windowsKeepsOriginalOnlyForTheVerifiedSourceHostAndSingleton(){
  var flags=OptimizationSnapshot.noneRequested().with(OptimizationId.EARLY_ALPHA,true);
  var route=new BslLeanGraph.Route(flags,true,"bsl-shaders",true,true);
  assertTrue(PackSelectionService.asahiAlphaOriginalFallback("Windows 11","amd64","bsl-shaders",true,LodPreset.PERFORMANCE,Map.of(),route,false,true));
  assertFalse(PackSelectionService.asahiAlphaOriginalFallback("Windows 11","amd64","bsl-shaders",true,LodPreset.PERFORMANCE,Map.of(),route,false,false));
  assertFalse(PackSelectionService.asahiAlphaOriginalFallback("Mac OS X","aarch64","bsl-shaders",true,LodPreset.PERFORMANCE,Map.of(),route,false,true));
  var mixed=new BslLeanGraph.Route(flags.with(OptimizationId.DEAD_MIP_CHAIN,true),true,"bsl-shaders",true,true);
  assertFalse(PackSelectionService.asahiAlphaOriginalFallback("Windows 11","amd64","bsl-shaders",true,LodPreset.PERFORMANCE,Map.of(),mixed,false,true));
 }

}
