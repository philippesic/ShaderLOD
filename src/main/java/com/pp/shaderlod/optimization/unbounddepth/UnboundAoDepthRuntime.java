package com.pp.shaderlod.optimization.unbounddepth;
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
public final class UnboundAoDepthRuntime {
 private record Owned(Object pipeline,RenderTargets targets,Object pack,boolean halfProducer){}
 private static final Map<Program,Owned> owned=new IdentityHashMap<>();
 private static final String QUALIFICATION_UNIFORM="shaderlodUnboundAODepthSamplingQualified";
 private static final String SOURCE="14ee368b6f2890b07cc8b31ebab56da0dfd5e2bc237784768ae745b9f2bec0d7";
 private static final Map<String,String> PROFILE=UnboundAoDepthPins.profile();
 private static Object sourcePack;private static String sourceName;private static boolean sourceAdmitted;
 private UnboundAoDepthRuntime(){}
 /** Untimed development readback; never called by admission or the timed draw path. */
 public static Map<String,Long> costCounters(){return Map.of("qualified_draws",qualifiedDraws,"source_calls",sourceCalls,"owned_programs",(long)owned.size());}
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
  int role=UnboundAoDepthContract.sourceRole(source.getName(),source.getFragmentSource().orElse(null));
  if(program!=null&&targets!=null&&UnboundAoDepthPins.exactApi()&&role>=0)
   owned.put(program,new Owned(pipeline,targets,Iris.getCurrentPack().orElse(null),role==1));
 }
 public static boolean declared(net.irisshaders.iris.shaderpack.option.ShaderPackOptions o){var keys=new HashSet<String>();keys.addAll(o.getOptionSet().getBooleanOptions().keySet());keys.addAll(o.getOptionSet().getStringOptions().keySet());if(!keys.removeAll(Set.of("SHADERLOD_UNBOUND_SPATIAL_REUSE","SHADERLOD_UNBOUND_SPATIAL_RATE","SHADERLOD_UNBOUND_PIXEL_GRAPH")))return false;for(String k:List.of("SHADERLOD_UNBOUND_SPATIAL_REUSE","SHADERLOD_UNBOUND_SPATIAL_RATE","SHADERLOD_UNBOUND_PIXEL_GRAPH")){var item=o.getOptionSet().getStringOptions().get(k);if(item==null||!item.getOption().getDefaultValue().equals("0")||!Set.copyOf(item.getOption().getAllowedValues()).equals(Set.of("0","1")))return false;}return PROFILE.size()==408&&keys.equals(PROFILE.keySet());}
 private static Map<String,String> options(net.irisshaders.iris.shaderpack.option.ShaderPackOptions o){
  if(!declared(o))return Map.of();return com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(o);}
 private static long qualifiedDraws;private static long sourceCalls;public static long sourceCalls(){return sourceCalls;}
 private static boolean source(){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(UnboundAoDepthRuntime.class,"source"),()->{sourcePack=null;sourceCalls++;
  var p=Iris.getCurrentPack().orElse(null);var name=Iris.getIrisConfig().getShaderPackName().orElse(null);
  if(p==null||name==null||Iris.isFallback()||PROFILE.size()!=408)return false;
  var effective=options(p.getShaderPackOptions());var normalized=new TreeMap<>(effective);String depth=normalized.get("SHADERLOD_UNBOUND_DEPTH_PRIMING");if(!Set.of("0","1").contains(depth))return false;normalized.put("SHADERLOD_UNBOUND_DEPTH_PRIMING","0");if(!Set.of("0","1").contains(normalized.put("SHADERLOD_UNBOUND_AO_HALF_RES","0")))return false;if(!Set.of("0","1").contains(normalized.put("SHADERLOD_UNBOUND_DEAD_MIP_CHAIN","0")))return false;for(String k:List.of("SHADERLOD_UNBOUND_SPATIAL_REUSE","SHADERLOD_UNBOUND_SPATIAL_RATE","SHADERLOD_UNBOUND_PIXEL_GRAPH"))if(!Set.of("0","1").contains(java.util.Objects.toString(normalized.remove(k),"invalid")))return false;if(!PROFILE.equals(normalized))return false;
  if(sourcePack!=p||!name.equals(sourceName)){
   sourcePack=p;sourceName=name;sourceAdmitted=false;
   try{var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var path=dir.resolve(name).normalize();
    if(path.equals(dir)||!path.startsWith(dir))return false;
    sourceAdmitted="complementary-unbound".equals(PackSelectionService.verifyGeneratedRendererGraph(path,effective)) && SOURCE.equals(com.pp.shaderlod.packs.SupportedPackCatalog.shaderSourceDigest(com.pp.shaderlod.integration.PackSourceReader.read(path).memberSha256()));
   }catch(Exception|LinkageError failed){sourceAdmitted=false;}
  }return sourceAdmitted;
 
  });
 }
 private static int integer(int program,String name){int l=GL20.glGetUniformLocation(program,name);return l<0?-1:GL20.glGetUniformi(program,l);}
 private static float number(int program,String name){int l=GL20.glGetUniformLocation(program,name);return l<0?Float.NaN:GL20.glGetUniformf(program,l);}
 private static int parameter(int sampler,int key){return sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,key):GL33.glGetSamplerParameteri(sampler,key);}
 private static float parameterFloat(int sampler,int key){return sampler==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,key):GL33.glGetSamplerParameterf(sampler,key);}
 private static UnboundAoDepthContract.Sampler sampler(int program,RenderTargets targets){
  int unit=integer(program,"depthtex0");if(unit<0||unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))return null;
  int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
  try{int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);if(texture<=0)return null;
   int sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);var c=GL.getCapabilities();
   float anisotropy=c.GL_EXT_texture_filter_anisotropic||c.GL_ARB_texture_filter_anisotropic?parameterFloat(sampler,0x84FE):1;
   return new UnboundAoDepthContract.Sampler(texture,UnboundAoDepthId.current(targets),
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
    !UnboundAoDepthContract.context(graph.pipeline(),Iris.getPipelineManager().getPipelineNullable(),graph.targets(),currentTargets,selected.getProgramId(),current,
      source(),UnboundAoDepthPins.exactApi(),active,integer(current,"shaderlodBridgeVersion"),integer(current,"shaderlodOptimizationFlagsVersion"),integer(current,"shaderlodOptAoDepthReuse"),integer(current,"shaderlodPreset"))||integer(current,"shaderlodEnabled")!=1)return;
   int[] v=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);
   if(!UnboundAoDepthContract.viewport(v[0],v[1],v[2],v[3],number(current,"viewWidth"),number(current,"viewHeight"),graph.halfProducer()))return;
   if(!UnboundAoDepthContract.sampler(sampler(current,graph.targets())))return;
   GL20.glUniform1i(location,1);qualifiedDraws++;
  }catch(RuntimeException|LinkageError unavailable){GL20.glUniform1i(location,0);}
 }

 /** Invoked synchronously by the authentic pipeline build; never by a rendering callback. */
 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){try{source();}catch(Exception|LinkageError refused){}}
}
