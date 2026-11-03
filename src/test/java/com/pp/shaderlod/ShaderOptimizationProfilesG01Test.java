package com.pp.shaderlod;
import com.pp.shaderlod.optimization.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ShaderOptimizationProfilesG01Test {
 @Test void readyWorldPreparationIsScopedToTheSingletonPerformanceRequest(){
  var flags=OptimizationSnapshot.noneRequested().with(OptimizationId.DEAD_FRAME_COPY,true);
  var core=new CoreUniformFrame.Core(new LodSettings(true,32,96,8,false),LodPreset.PERFORMANCE);
  assertTrue(ShaderOptimizationProfiles.deadCopyProfile("makeup-ultra-fast-shaders",core,flags));
  assertFalse(ShaderOptimizationProfiles.deadCopyProfile("bsl-shaders",core,flags));
  assertFalse(ShaderOptimizationProfiles.deadCopyProfile("makeup-ultra-fast-shaders",null,flags));
  assertFalse(ShaderOptimizationProfiles.deadCopyProfile("makeup-ultra-fast-shaders",new CoreUniformFrame.Core(new LodSettings(false,32,96,8,false),LodPreset.PERFORMANCE),flags));
  for(LodPreset preset:LodPreset.values())if(preset!=LodPreset.PERFORMANCE)assertFalse(ShaderOptimizationProfiles.deadCopyProfile("makeup-ultra-fast-shaders",new CoreUniformFrame.Core(core.settings(),preset),flags));
  assertFalse(ShaderOptimizationProfiles.deadCopyProfile("makeup-ultra-fast-shaders",core,OptimizationSnapshot.noneRequested()));
  for(var id:OptimizationId.values())if(id!=OptimizationId.DEAD_FRAME_COPY)assertFalse(ShaderOptimizationProfiles.deadCopyProfile("makeup-ultra-fast-shaders",core,flags.with(id,true)));
 }
 @Test void eachWorldPipelinePackTransitionCanPrepareOnlyOnce(){
  var ready=new ShaderOptimizationProfiles.ReadyContext();Object w=new Object(),p=new Object(),pack=new Object();
  assertFalse(ready.claim(null,p,pack));assertTrue(ready.claim(w,p,pack));assertFalse(ready.claim(w,p,pack));
  var nether=new Object();assertTrue(ready.claim(nether,p,pack));assertFalse(ready.claim(nether,p,pack));assertTrue(ready.claim(w,p,pack));
  var reloaded=new Object();assertTrue(ready.claim(w,reloaded,pack));assertFalse(ready.claim(w,reloaded,pack));
 }
}
