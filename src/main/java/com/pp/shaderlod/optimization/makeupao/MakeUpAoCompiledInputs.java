package com.pp.shaderlod.optimization.makeupao;
import com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs;
import com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs.*;
import java.util.Map;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
/** MakeUp consumer facade; compiler hooks and link evidence are shared across explicit families. */
public final class MakeUpAoCompiledInputs {
 private MakeUpAoCompiledInputs(){}
 public static Lookup lookup(Program producer,Program consumer,Object renderer,Object pipeline,Object targets){return IrisCompositeCompiledInputs.lookup(Family.MAKEUP,producer,consumer,renderer,pipeline,targets);}
 static Lookup lookup(boolean supported,Program producer,Program consumer,Object renderer,Object pipeline,Object targets){return IrisCompositeCompiledInputs.lookup(supported,Family.MAKEUP,producer,consumer,renderer,pipeline,targets);}
 public static void clear(){IrisCompositeCompiledInputs.clear(Family.MAKEUP);}
 public static void destroy(Object pipeline){IrisCompositeCompiledInputs.destroy(pipeline);}
 public static Map<String,Object> status(){return IrisCompositeCompiledInputs.status(Family.MAKEUP);}
 static boolean supportedStages(Map<PatchShaderType,String> stages){return IrisCompositeCompiledInputs.supportedStages(stages);}
}
