package com.pp.shaderlod.optimization.bsl;
import com.pp.shaderlod.optimization.unbounddepth.UnboundAoDepthContract;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.opengl.*;
import java.util.*;
/** BSL current-draw sampler proof. Reuses the working Unbound scalar contract; each family owns its programs and targets independently. */
public final class BslAoDepthRuntime {
 private record Owned(Object pipeline,RenderTargets targets,Object pack){}
 private static final Map<Program,Owned> owned=new IdentityHashMap<>();
 private static final String QUALIFICATION_UNIFORM="shaderlodBslAoDepthLod0Qualified";
 private static long qualifiedDraws;
 public static String status(){return "BSL identical-UV AO depth reuse; current owned programs="+owned.size()+"; qualified draws="+qualifiedDraws+"; avoided texture reads/GPU cost unmeasured";}
 private BslAoDepthRuntime(){}
 public static void invalidate(){owned.clear();}
 public static void destroy(Object pipeline){owned.entrySet().removeIf(e->e.getValue().pipeline()==pipeline);}
 public static boolean declaresQualification(String fragment){return fragment!=null&&fragment.contains(QUALIFICATION_UNIFORM);}
 public static void built(Object pipeline,RenderTargets targets,ProgramSource source,Program program){
  if(program!=null&&targets!=null&&exactTarget()&&BslAoApiPins.exactApi()&&java.util.Set.of("deferred","deferred1").contains(source.getName())&&
   declaresQualification(source.getFragmentSource().orElse("")))
   owned.put(program,new Owned(pipeline,targets,Iris.getCurrentPack().orElse(null)));
 }
 private static boolean exactTarget(){return net.minecraft.SharedConstants.getCurrentVersion().getName().equals("1.21.1")&&net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("fabricloader").map(m->m.getMetadata().getVersion().getFriendlyString().equals("0.19.5")).orElse(false);}
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
   return new UnboundAoDepthContract.Sampler(texture,targets.getDepthTexture(),
    GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),
    targets.getCurrentWidth(),targets.getCurrentHeight(),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,1,GL11.GL_TEXTURE_WIDTH),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,1,GL11.GL_TEXTURE_HEIGHT),
    GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x884A),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x8C16),
    parameter(sampler,0x884C),parameter(sampler,GL11.GL_TEXTURE_MIN_FILTER),parameter(sampler,GL11.GL_TEXTURE_MAG_FILTER),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x813C),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x813D),
    parameter(sampler,GL11.GL_TEXTURE_WRAP_S),parameter(sampler,GL11.GL_TEXTURE_WRAP_T),parameterFloat(sampler,0x8501),parameterFloat(sampler,0x813A),parameterFloat(sampler,0x813B),anisotropy);
  }finally{GL13.glActiveTexture(active);}
 }
 public static void beforeDraw(Object pipeline,Program selected){
  int current=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(current<=0)return;
  int location=GL20.glGetUniformLocation(current,QUALIFICATION_UNIFORM);if(location<0)return;
  GL20.glUniform1i(location,0); // revoke every draw, including disabled/changed setup.
  try{var graph=owned.get(selected);var core=OptimizationRuntimeContext.core();
   boolean active=core!=null&&core.settings().enabled()&&core.preset()!=LodPreset.QUALITY&&core.optimizations().enabled(OptimizationId.AO_DEPTH_REUSE)&&!MakeUpQuadLifecycle.suspended()&&CompileOptionControl.overworld(Iris.getCurrentDimension());
   if(!exactTarget()||graph==null||graph.pack()!=Iris.getCurrentPack().orElse(null)||pipeline!=graph.pipeline()||
    !UnboundAoDepthContract.context(graph.pipeline(),Iris.getPipelineManager().getPipelineNullable(),graph.targets(),graph.targets(),selected.getProgramId(),current,
      BslReceiverRuntime.source(),BslAoApiPins.exactApi(),active,integer(current,"shaderlodBridgeVersion"),integer(current,"shaderlodOptimizationFlagsVersion"),integer(current,"shaderlodOptAoDepthReuse"),integer(current,"shaderlodPreset"))||integer(current,"shaderlodEnabled")!=1)return;
   int[] v=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);
   if(!UnboundAoDepthContract.viewport(v[0],v[1],v[2],v[3],number(current,"viewWidth"),number(current,"viewHeight")))return;
   if(!UnboundAoDepthContract.sampler(sampler(current,graph.targets())))return;
   GL20.glUniform1i(location,1);qualifiedDraws++;
  }catch(RuntimeException|LinkageError unavailable){GL20.glUniform1i(location,0);}
 }
}
