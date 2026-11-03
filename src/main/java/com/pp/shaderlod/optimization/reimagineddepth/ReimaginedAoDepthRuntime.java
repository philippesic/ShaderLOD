package com.pp.shaderlod.optimization.reimagineddepth;
import com.google.gson.JsonParser;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle;
import com.pp.shaderlod.packs.PackSelectionService;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.opengl.*;
import java.util.*;
/** Owned normal composite Program.use receiver. No observer, source inference or forced ready. */
public final class ReimaginedAoDepthRuntime {
 private record Owned(Object pipeline,RenderTargets targets,Object pack,boolean halfProducer){}
 private static final Map<Program,Owned> owned=new IdentityHashMap<>();
 private static final String QUALIFICATION_UNIFORM="shaderlodReimaginedAODepthSamplingQualified";
 private static final String SOURCE="2d2467ef372ab37e303f3736179fed301dead8d2132c0eae486e3cb0dee617e9";
 private static final Map<String,String> PROFILE=ReimaginedAoDepthPins.profile();
 private static Object sourcePack;private static String sourceName;private static boolean sourceAdmitted;
 private ReimaginedAoDepthRuntime(){}
 /** Untimed development readback; never called by admission or the timed draw path. */
 public static Map<String,Object> controlProof(){var permissions=new ArrayList<Integer>();
  for(var program:owned.keySet())try{int id=program.getProgramId();int location=GL20.glGetUniformLocation(id,QUALIFICATION_UNIFORM);if(location>=0)permissions.add(GL20.glGetUniformi(id,location));}catch(RuntimeException|LinkageError unavailable){}
  return Map.of("source_calls",sourceCalls,"qualified_draws",qualifiedDraws,"permissions",List.copyOf(permissions));
 }

 private record Lease(Object pipeline,RenderTargets targets,Program program){}private static final ThreadLocal<Lease> LEASE=new ThreadLocal<>();
 public static void mark(Object pipeline,RenderTargets targets,Program program){LEASE.set(new Lease(pipeline,targets,program));}
 public static void leave(){LEASE.remove();}
 public static void beforeDraw(){var lease=LEASE.get();if(lease!=null)upload(lease.pipeline(),lease.targets(),lease.program());else revokeCurrent();}
 public static void currentQuad(Runnable original){beforeDraw();original.run();}
 private static void revokeCurrent(){int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(p>0){int l=GL20.glGetUniformLocation(p,QUALIFICATION_UNIFORM);if(l>=0)GL20.glUniform1i(l,0);}}

 public static void invalidate(){leave();owned.clear();sourcePack=null;sourceName=null;sourceAdmitted=false;}
 public static void destroy(Object pipeline){owned.entrySet().removeIf(e->e.getValue().pipeline()==pipeline);}
 public static boolean declaresQualification(String fragment){return fragment!=null&&fragment.contains(QUALIFICATION_UNIFORM);}
 public static void built(Object pipeline,RenderTargets targets,ProgramSource source,Program program){
  int role=ReimaginedAoDepthContract.sourceRole(source.getName(),source.getFragmentSource().orElse(null));
  if(program!=null&&targets!=null&&ReimaginedAoDepthPins.exactApi()&&role>=0)
   owned.put(program,new Owned(pipeline,targets,Iris.getCurrentPack().orElse(null),role==1));
 }
 public static boolean declared(net.irisshaders.iris.shaderpack.option.ShaderPackOptions o){var keys=new HashSet<String>();keys.addAll(o.getOptionSet().getBooleanOptions().keySet());keys.addAll(o.getOptionSet().getStringOptions().keySet());if(!keys.removeAll(Set.of("SHADERLOD_REIMAGINED_SPATIAL_REUSE","SHADERLOD_REIMAGINED_SPATIAL_RATE","SHADERLOD_REIMAGINED_PIXEL_GRAPH")))return false;for(String k:List.of("SHADERLOD_REIMAGINED_SPATIAL_REUSE","SHADERLOD_REIMAGINED_SPATIAL_RATE","SHADERLOD_REIMAGINED_PIXEL_GRAPH")){var item=o.getOptionSet().getStringOptions().get(k);if(item==null||!item.getOption().getDefaultValue().equals("0")||!Set.copyOf(item.getOption().getAllowedValues()).equals(Set.of("0","1")))return false;}return PROFILE.size()==408&&keys.equals(PROFILE.keySet());}
 private static Map<String,String> options(net.irisshaders.iris.shaderpack.option.ShaderPackOptions o){
  if(!declared(o))return Map.of();return com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(o);}
 private static long qualifiedDraws;private static long sourceCalls;public static long sourceCalls(){return sourceCalls;}
 private static boolean source(){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(ReimaginedAoDepthRuntime.class,"source"),()->{sourcePack=null;sourceCalls++;
  var p=Iris.getCurrentPack().orElse(null);var name=Iris.getIrisConfig().getShaderPackName().orElse(null);
  if(p==null||name==null||Iris.isFallback()||PROFILE.size()!=408)return false;
  var effective=options(p.getShaderPackOptions());var normalized=new TreeMap<>(effective);for(String k:java.util.List.of("SHADERLOD_REIMAGINED_DEPTH_PRIMING","SHADERLOD_REIMAGINED_AO_HALF_RES","SHADERLOD_REIMAGINED_DEAD_MIP_CHAIN")){if(!java.util.Set.of("0","1").contains(normalized.put(k,"0")))return false;}for(String k:List.of("SHADERLOD_REIMAGINED_SPATIAL_REUSE","SHADERLOD_REIMAGINED_SPATIAL_RATE","SHADERLOD_REIMAGINED_PIXEL_GRAPH"))if(!Set.of("0","1").contains(normalized.getOrDefault(k,"invalid")))return false;normalized.remove("SHADERLOD_REIMAGINED_SPATIAL_REUSE");normalized.remove("SHADERLOD_REIMAGINED_SPATIAL_RATE");normalized.remove("SHADERLOD_REIMAGINED_PIXEL_GRAPH");if(!PROFILE.equals(normalized))return false;
  if(sourcePack!=p||!name.equals(sourceName)){
   sourcePack=p;sourceName=name;sourceAdmitted=false;
   try{var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var path=dir.resolve(name).normalize();
    if(path.equals(dir)||!path.startsWith(dir))return false;
    sourceAdmitted="complementary-reimagined".equals(PackSelectionService.verifyGeneratedRendererGraph(path,effective)) && SOURCE.equals(com.pp.shaderlod.packs.SupportedPackCatalog.shaderSourceDigest(com.pp.shaderlod.integration.PackSourceReader.read(path).memberSha256()));
   }catch(Exception|LinkageError failed){sourceAdmitted=false;}
  }return sourceAdmitted;
 
  });
 }
 private static int integer(int program,String name){int l=GL20.glGetUniformLocation(program,name);return l<0?-1:GL20.glGetUniformi(program,l);}
 private static float number(int program,String name){int l=GL20.glGetUniformLocation(program,name);return l<0?Float.NaN:GL20.glGetUniformf(program,l);}
 private static int parameter(int sampler,int key){return sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,key):GL33.glGetSamplerParameteri(sampler,key);}
 private static float parameterFloat(int sampler,int key){return sampler==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,key):GL33.glGetSamplerParameterf(sampler,key);}
 private static ReimaginedAoDepthContract.Sampler sampler(int program,RenderTargets targets){
  int unit=integer(program,"depthtex0");if(unit<0||unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))return null;
  int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
  try{int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);if(texture<=0)return null;
   int sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);var c=GL.getCapabilities();
   float anisotropy=c.GL_EXT_texture_filter_anisotropic||c.GL_ARB_texture_filter_anisotropic?parameterFloat(sampler,0x84FE):1;
   return new ReimaginedAoDepthContract.Sampler(texture,ReimaginedAoDepthId.current(targets),
    GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),
    targets.getCurrentWidth(),targets.getCurrentHeight(),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,1,GL11.GL_TEXTURE_WIDTH),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,1,GL11.GL_TEXTURE_HEIGHT),
    GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x884A),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x8C16),
    parameter(sampler,0x884C),parameter(sampler,GL11.GL_TEXTURE_MIN_FILTER),parameter(sampler,GL11.GL_TEXTURE_MAG_FILTER),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x813C),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x813D),
    parameter(sampler,GL11.GL_TEXTURE_WRAP_S),parameter(sampler,GL11.GL_TEXTURE_WRAP_T),parameterFloat(sampler,0x8501),parameterFloat(sampler,0x813A),parameterFloat(sampler,0x813B),anisotropy);
  }finally{GL13.glActiveTexture(active);}
 }
 public static void upload(Object pipeline,RenderTargets currentTargets,Program selected){
  int current=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(current<=0)return;
  int location=GL20.glGetUniformLocation(current,QUALIFICATION_UNIFORM);if(location<0)return;
  GL20.glUniform1i(location,0); // revoke every draw, including disabled/changed setup.
  try{var graph=owned.get(selected);var core=OptimizationRuntimeContext.core();
   boolean active=core!=null&&core.settings().enabled()&&core.preset()!=LodPreset.QUALITY&&core.optimizations().enabled(OptimizationId.AO_DEPTH_REUSE)&&!MakeUpQuadLifecycle.suspended()&&CompileOptionControl.overworld(Iris.getCurrentDimension());
   if(!active)return; // No source/options/GL admission work can grant while inactive.

   if(graph==null||graph.pack()!=Iris.getCurrentPack().orElse(null)||pipeline!=graph.pipeline()||
    !ReimaginedAoDepthContract.context(graph.pipeline(),Iris.getPipelineManager().getPipelineNullable(),graph.targets(),currentTargets,selected.getProgramId(),current,
      source(),ReimaginedAoDepthPins.exactApi(),active,integer(current,"shaderlodBridgeVersion"),integer(current,"shaderlodOptimizationFlagsVersion"),integer(current,"shaderlodOptAoDepthReuse"),integer(current,"shaderlodPreset"))||integer(current,"shaderlodEnabled")!=1)return;
   int[] v=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);
   if(!ReimaginedAoDepthContract.viewport(v[0],v[1],v[2],v[3],number(current,"viewWidth"),number(current,"viewHeight"),graph.halfProducer()))return;
   if(!ReimaginedAoDepthContract.sampler(sampler(current,graph.targets())))return;
   GL20.glUniform1i(location,1);qualifiedDraws++;
  }catch(RuntimeException|LinkageError unavailable){GL20.glUniform1i(location,0);}
 }

 /** Invoked synchronously by the authentic pipeline build; never by a rendering callback. */
 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){try{source();}catch(Exception|LinkageError refused){}}
}
