package com.pp.shaderlod.optimization.makeupearlyalpha;

import java.util.*;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.optimization.sourcecache.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.caffeinemc.mods.sodium.client.gl.shader.*;
import org.lwjgl.opengl.*;

/** Untimed benchmark observation only. Never grants a shader/draw permission or changes GL state. */
public final class MakeUpPostSampleAlphaWitness {
 private MakeUpPostSampleAlphaWitness() {}
 private record Setup(ProgramSource source, ShaderPack pack, IrisCompiledSource.PackInput input,
                      String selected,long epoch,Map<PatchShaderType,String> text) {}
 private record Owner(Setup setup,IrisRenderingPipeline pipeline,GlProgram<?> program,Object context) {}
 private static final CompilationLinkEvidence<Setup> LINK=new CompilationLinkEvidence<>();
 private static final Map<Map<?,?>,Setup> TEXT=new IdentityHashMap<>();
 private static final Map<Integer,Owner> OWNERS=new HashMap<>();
 private static boolean observing;
 private static long batches,commands;
 private static String last="not observed";
 private static boolean benchmark(){return !System.getProperty("shaderlod.dev.benchmarkControlProbe","").isBlank();}
 public static boolean observing(){return observing;}
 public static void observe(boolean enabled,boolean reset){
  if(enabled&&!benchmark())throw new IllegalStateException("Post-sample witness requires owned benchmark");
  observing=enabled;if(reset){batches=commands=0;last="reset";}
 }
 public static Map<String,Object> snapshot(){return Map.of("observing",observing,"linked_programs",OWNERS.size(),
  "completed_batches",batches,"submitted_commands",commands,"last",last,
  "scope","Qualified transformed text, actual shader objects, successful immutable program and completed native submission; not fragments or avoided work");}
 public static void invalidate(){observing=false;LINK.clear();TEXT.clear();OWNERS.clear();last="invalidated";}
 public static void destroy(Object pipeline){observing=false;LINK.clear();TEXT.clear();OWNERS.entrySet().removeIf(e->e.getValue().pipeline()==pipeline);last="destroyed";}
 public static void transformed(ProgramSource source,IrisCompiledSource.PackInput input,Map<PatchShaderType,String> text){
  if(!benchmark())return;
  var copy=new EnumMap<PatchShaderType,String>(PatchShaderType.class);copy.putAll(text);
  var setup=new Setup(source,source.getParent().getPack(),input,Iris.getCurrentPackName(),MakeUpEarlyAlphaRuntime.epoch(),Collections.unmodifiableMap(copy));
  TEXT.put(text,setup);LINK.transformed(text,setup);
 }
 public static void compiled(Map<PatchShaderType,String> text,Map<PatchShaderType,GlShader> shaders){
  var setup=TEXT.get(text);LINK.compiled(text,shaders);if(setup==null)return;
  boolean valid=shaders!=null&&shaders.size()==2&&shaders.keySet().equals(Set.of(PatchShaderType.VERTEX,PatchShaderType.FRAGMENT));
  if(valid)for(var entry:shaders.entrySet()){
   int id=entry.getValue().handle();int type=entry.getKey()==PatchShaderType.VERTEX?GL20.GL_VERTEX_SHADER:GL20.GL_FRAGMENT_SHADER;
   if(!GL20.glIsShader(id)||GL20.glGetShaderi(id,GL20.GL_SHADER_TYPE)!=type||GL20.glGetShaderi(id,GL20.GL_COMPILE_STATUS)!=GL11.GL_TRUE
       ||!Objects.equals(setup.text().get(entry.getKey()),GL20.glGetShaderSource(id))){valid=false;break;}
  }
  if(!valid){LINK.compiled(Map.of(),shaders);last="actual shader text/type/compile refused";}
 }
 public static void linked(IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,ProgramSource source,
                           Map<PatchShaderType,GlShader> shaders,GlProgram<?> program){
  if(program!=null)OWNERS.remove(program.handle());
  var setup=LINK.linked(shaders);
  if(setup==null||pipeline==null||pass!=SodiumPrograms.Pass.TERRAIN_CUTOUT||setup.source()!=source||program==null
      ||program.getClass()!=GlProgram.class||program.handle()<=0||!GL20.glIsProgram(program.handle())
      ||GL20.glGetProgrami(program.handle(),GL20.GL_LINK_STATUS)!=GL11.GL_TRUE)return;
  OWNERS.put(program.handle(),new Owner(setup,pipeline,program,GL.getCapabilities()));last="qualified rewritten CUTOUT linked";
 }
 public static void completedDraw(int submittedCommands){
  if(!observing||submittedCommands<=0||OWNERS.isEmpty())return;
  int id=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);var owner=OWNERS.get(id);if(owner==null)return;
  var setup=owner.setup();var pack=Iris.getCurrentPack().orElse(null);
  if(owner.program().handle()!=id||owner.context()!=GL.getCapabilities()||owner.pipeline()!=Iris.getPipelineManager().getPipelineNullable()
      ||setup.pack()!=pack||setup.epoch()!=MakeUpEarlyAlphaRuntime.epoch()||Iris.isFallback()
      ||!Objects.equals(setup.selected(),Iris.getCurrentPackName())||!Iris.getShaderPackOptionQueue().isEmpty()
      ||!setup.input().options().current(pack.getShaderPackOptions())||!MakeUpEarlyAlphaCompilation.enabled(pack)
      ||!com.pp.shaderlod.ShaderLodConfig.snapshot().enabled()||!OptimizationConfig.requested(OptimizationId.EARLY_ALPHA))return;
  batches++;commands+=submittedCommands;last="current rewritten CUTOUT native submission completed";
 }
}
