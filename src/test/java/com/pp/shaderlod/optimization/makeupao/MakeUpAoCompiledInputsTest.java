package com.pp.shaderlod.optimization.makeupao;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MakeUpAoCompiledInputsTest {
 @Test void bothFailedCapturesOnSupportedCompilerRefuseWithoutLegacyFallback(){
  var before=MakeUpAoCompiledInputs.status();
  var lookup=MakeUpAoCompiledInputs.lookup(true,null,null,new Object(),new Object(),new Object());
  assertEquals(com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs.State.STALE,lookup.state());assertNull(lookup.pair());
  assertEquals(before.get("legacy_frames"),MakeUpAoCompiledInputs.status().get("legacy_frames"));
 }
 @Test void unsupportedCompilerRetainsLegacyAdmission(){
  assertEquals(com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs.State.MISSING,MakeUpAoCompiledInputs.lookup(false,null,null,new Object(),new Object(),new Object()).state());
 }
 @Test void irisCompositeEnumMapPreservesAbsentStagesWithoutAdmittingExecutableTessellation(){
  var stages=new java.util.EnumMap<net.irisshaders.iris.pipeline.transform.PatchShaderType,String>(net.irisshaders.iris.pipeline.transform.PatchShaderType.class);
  for(var stage:net.irisshaders.iris.pipeline.transform.PatchShaderType.values())stages.put(stage,null);
  stages.put(net.irisshaders.iris.pipeline.transform.PatchShaderType.VERTEX,"vertex");stages.put(net.irisshaders.iris.pipeline.transform.PatchShaderType.FRAGMENT,"fragment");
  assertTrue(MakeUpAoCompiledInputs.supportedStages(stages));
  for(var stage:java.util.List.of(net.irisshaders.iris.pipeline.transform.PatchShaderType.GEOMETRY,net.irisshaders.iris.pipeline.transform.PatchShaderType.TESS_CONTROL,net.irisshaders.iris.pipeline.transform.PatchShaderType.TESS_EVAL)){
   stages.put(stage,"unexpected executable stage");assertFalse(MakeUpAoCompiledInputs.supportedStages(stages));stages.put(stage,null);
  }
  stages.remove(net.irisshaders.iris.pipeline.transform.PatchShaderType.FRAGMENT);assertFalse(MakeUpAoCompiledInputs.supportedStages(stages));
 }
}
