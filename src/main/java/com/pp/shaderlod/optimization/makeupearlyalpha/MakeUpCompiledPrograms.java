package com.pp.shaderlod.optimization.makeupearlyalpha;
import java.util.Map;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
/** MakeUp owns its independent compile chain; generic joins never grant permission by themselves. */
public final class MakeUpCompiledPrograms {
 private MakeUpCompiledPrograms() {}
 private static final com.pp.shaderlod.optimization.sourcecache.LinkedCompilationProof PROOF=
  new com.pp.shaderlod.optimization.sourcecache.LinkedCompilationProof("makeup-ultra-fast-shaders","gbuffers_terrain");
 public static String status(){return PROOF.status();}
 public static void clear(){PROOF.clear();}
 public static void discardPreparation(ProgramSource source){PROOF.discardPreparation(source);}
 public static void prepare(ProgramSource source,ProgramSet set,AlphaTest alpha,String vertex,String geometry,String control,String evaluation,String fragment,Object textures,long epoch){PROOF.prepare(source,set,alpha,vertex,geometry,control,evaluation,fragment,textures,epoch);}
 public static void transformed(ProgramSource source,ProgramSet set,AlphaTest alpha,Map<PatchShaderType,String> output,boolean accepted){PROOF.transformed(source,set,alpha,output,accepted);}
 public static void compiled(Map<?,?> input,Map<?,?> output){PROOF.compiled(input,output);}
 public static MakeUpCompiledSource.Certificate linked(ProgramSource source,AlphaTest alpha,Map<?,?> shaders){return PROOF.linked(source,alpha,shaders);}
}
