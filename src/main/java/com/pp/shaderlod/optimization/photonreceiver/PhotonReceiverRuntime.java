package com.pp.shaderlod.optimization.photonreceiver;
import java.util.*;import java.nio.file.*;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.optimization.bsl.BslReceiverContract;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle;
import com.pp.shaderlod.optimization.makeupshadow.mixin.ShadowPipelineView;
import com.pp.shaderlod.packs.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.opengl.*;
/** Current owned deferred draw → approximate receiver quadrature permission.
 * This does not qualify min-field/raw-query or assert any visibility error bound. */
public final class PhotonReceiverRuntime {
 private static final String UNIFORM="shaderlodPhotonReceiverQualified",SOURCE="ee001a1a2824475a13ca05f304b75a163de72160d6d31e47b2a97eb6b76b6e60";
 // Program.getProgramId() validates Iris GlResource's lifetime; an integer GL name alone does not.
 private record Owner(Object pipeline,Object pack,RenderTargets targets,long epoch,int id,Object context,Map<String,Integer> locations){}
 private record Lease(Object pipeline,RenderTargets targets,Program program){}
 private record Permission(Program program,Owner owner,int location){}
 private static final Map<Program,Owner> OWNED=new IdentityHashMap<>();
 private static final ThreadLocal<Lease> LEASE=new ThreadLocal<>();
 private static final ThreadLocal<Permission> PERMISSION=new ThreadLocal<>();
 private static final com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache SOURCE_CACHE=new com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache();
 private static long draws,currentProgramQueries,locationQueries,sourceChecks,grants,revokes;
 /** Cumulative render-thread observations; reading counters performs no GL work. */
 public static Map<String,Long> costCounters(){return Map.of("draws",draws,"current_program_queries",currentProgramQueries,"location_queries",locationQueries,"source_checks",sourceChecks,"grants",grants,"revokes",revokes,"pending_permissions",PERMISSION.get()==null?0L:1L,"owners",(long)OWNED.size());}
 private static long epoch;private static String last="native; no qualified receiver draw";
 private PhotonReceiverRuntime(){}
 public static String status(){return "Photon owned deferred receiver quadrature; quality approximation unvalidated; "+last;}
 public static void invalidate(){try{leave();}finally{SOURCE_CACHE.invalidate();epoch++;OWNED.clear();last="native; receiver invalidated";}}
 public static void destroy(Object pipeline){try{leave();}finally{SOURCE_CACHE.invalidate();OWNED.entrySet().removeIf(e->e.getValue().pipeline()==pipeline);last="native; receiver pipeline destroyed";}}
 public static void leave(){try{revoke();}finally{LEASE.remove();}}
 public static void mark(Object pipeline,RenderTargets targets,Program program){
  if(OWNED.containsKey(program))LEASE.set(new Lease(pipeline,targets,program));else LEASE.remove();
 }
 public static void built(Object pipeline,RenderTargets targets,ProgramSource source,Program program){
  OWNED.remove(program);
  if(program!=null&&targets!=null&&pipeline instanceof IrisRenderingPipeline&&PhotonReceiverApi.exact()&&source!=null&&"deferred4".equals(source.getName())&&source.getParent()!=null&&source.getParent().getPack()==Iris.getCurrentPack().orElse(null)&&source.getFragmentSource().orElse("").contains("uniform int "+UNIFORM+";")){OWNED.put(program,new Owner(pipeline,source.getParent().getPack(),targets,epoch,program.getProgramId(),GL.getCapabilities(),new HashMap<>()));if(com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.loading())try{source(source.getParent().getPack(),pipeline);}catch(Exception|LinkageError refused){}}
 }
 private static Map<String,String> options(net.irisshaders.iris.shaderpack.ShaderPack pack){if(pack==null)return Map.of();return com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(pack.getShaderPackOptions());}
 private static boolean source(net.irisshaders.iris.shaderpack.ShaderPack pack,Object pipeline)throws Exception{
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(PhotonReceiverRuntime.class,"source",pack),()->{
  var values=options(pack);if(!com.pp.shaderlod.optimization.photonprime.PhotonDepthPriming.profileMatch(values)&&!com.pp.shaderlod.optimization.photon.PhotonSpatialProfiles.eligible(values))return false;
  String name=Iris.getIrisConfig().getShaderPackName().orElse(null);var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();if(name==null||!Path.of(name).getFileName().toString().equals(name))return false;
  var client=net.minecraft.client.Minecraft.getInstance();var o=pack.getShaderPackOptions();
  return SOURCE_CACHE.check(PhotonReceiverRuntime.class,dir,name,"photon",values,pack,o.getOptionValues(),o.getOptionSet(),pipeline,client.level,epoch,Arrays.asList(OptimizationRuntimeContext.core(),Map.copyOf(Iris.getShaderPackOptionQueue())),client.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList(),()->SOURCE.equals(SupportedPackCatalog.shaderSourceDigest(com.pp.shaderlod.integration.PackSourceReader.read(dir.resolve(name)).memberSha256()))&&"photon".equals(PackSelectionService.verifyGeneratedRendererGraph(dir.resolve(name),values)));
 
  });
 }
 private static int location(Owner owner,String name){
  var known=owner.locations().get(name);if(known!=null)return known;
  locationQueries++;int actual=GL20.glGetUniformLocation(owner.id(),name);owner.locations().put(name,actual);return actual;
 }
 private static int integer(Owner owner,String name){int l=location(owner,name);return l<0?-1:GL20.glGetUniformi(owner.id(),l);}
 private static int parameter(int sampler,int name){return sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,name):GL33.glGetSamplerParameteri(sampler,name);}
 private static float number(int sampler,int name){return sampler==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,name):GL33.glGetSamplerParameterf(sampler,name);}
 private static boolean sampler(Owner owner,int ownedTexture,int resolution){
  int unit=integer(owner,"shadowtex1");if(unit<0||unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))return false;
  int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);try{
   GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);var caps=GL.getCapabilities();float aniso=caps.GL_EXT_texture_filter_anisotropic||caps.GL_ARB_texture_filter_anisotropic?number(sampler,0x84FE):1;
   var s=new BslReceiverContract.Sampler(texture,GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x884A),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x8C16),parameter(sampler,0x884C),parameter(sampler,0x884D),parameter(sampler,GL11.GL_TEXTURE_MIN_FILTER),parameter(sampler,GL11.GL_TEXTURE_MAG_FILTER),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL),parameter(sampler,GL11.GL_TEXTURE_WRAP_S),parameter(sampler,GL11.GL_TEXTURE_WRAP_T),aniso);
   return PhotonReceiverContract.sampler(s,ownedTexture,resolution,number(sampler,GL14.GL_TEXTURE_LOD_BIAS),number(sampler,GL12.GL_TEXTURE_MIN_LOD),number(sampler,GL12.GL_TEXTURE_MAX_LOD));
  }finally{GL13.glActiveTexture(active);}
 }
 /** Revoke only our own grant, including after controls/ownership have been invalidated.
  * Keep a deferred revocation across context changes; never write a reused name in another context. */
 private static boolean revoke(){
  var permission=PERMISSION.get();if(permission==null)return true;
  var owner=permission.owner();
  try{if(permission.program().getProgramId()!=owner.id()){PERMISSION.remove();return true;}}
  catch(IllegalStateException deleted){PERMISSION.remove();return true;}
  if(!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()||GL.getCapabilities()!=owner.context())return false;
  GL41.glProgramUniform1i(owner.id(),permission.location(),0);revokes++;PERMISSION.remove();return true;
 }
 public static void beforeDraw(){
  draws++;
  // Fresh links default this sole-writer uniform to zero. No grant means no cleanup GL work.
  if(!revoke()){last="native; permission revocation awaits owning context";return;}
  try{
   var lease=LEASE.get();var owner=lease==null?null:OWNED.get(lease.program());
   if(owner==null||owner.epoch()!=epoch||owner.pipeline()!=lease.pipeline()||owner.targets()!=lease.targets())return;
   last="native/refused";
   var core=OptimizationRuntimeContext.core();if(core==null)return;var client=net.minecraft.client.Minecraft.getInstance();
   if(!PhotonReceiverContract.controls(core.settings().enabled(),core.preset()==LodPreset.QUALITY,core.optimizations().enabled(OptimizationId.SHADOW_RECEIVER_FOOTPRINT)&&OptimizationConfig.requested(OptimizationId.SHADOW_RECEIVER_FOOTPRINT),MakeUpQuadLifecycle.suspended(),client.level!=null&&client.level.dimension()==net.minecraft.world.level.Level.OVERWORLD,!Iris.getShaderPackOptionQueue().isEmpty(),Iris.isFallback()))return;
   if(!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()||GL.getCapabilities()!=owner.context()||!GL.getCapabilities().OpenGL41||!PhotonReceiverApi.exact()||owner.pipeline()!=Iris.getPipelineManager().getPipelineNullable()||owner.pack()!=Iris.getCurrentPack().orElse(null)||lease.program().getProgramId()!=owner.id()||net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered())return;
   var platform=net.irisshaders.iris.platform.IrisPlatformHelpers.getInstance();if(platform.isModLoaded("distanthorizons")||platform.isModLoaded("voxy"))return;
   currentProgramQueries++;int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(program!=owner.id()||GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE)return;
   int permissionLocation=location(owner,UNIFORM);if(permissionLocation<0)return;
   if(integer(owner,"shaderlodBridgeVersion")!=2||integer(owner,"shaderlodEnabled")!=1||integer(owner,"shaderlodOptimizationFlagsVersion")!=1||integer(owner,"shaderlodOptShadowReceiverFootprint")!=1||integer(owner,"shaderlodPreset")!=core.preset().ordinal())return;
   if(!(owner.pipeline() instanceof ShadowPipelineView view)||view.shadowlod$targets()!=owner.targets())return;var shadows=view.shadowlod$shadows();if(shadows==null)return;
   int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);if(!Arrays.equals(viewport,new int[]{0,0,owner.targets().getCurrentWidth(),owner.targets().getCurrentHeight()})||GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)return;
   if(!sampler(owner,shadows.getDepthTextureNoTranslucents().getTextureId(),shadows.getResolution()))return;
   sourceChecks++;if(!source((net.irisshaders.iris.shaderpack.ShaderPack)owner.pack(),owner.pipeline()))return;
   // Record before the write so an exceptional upload still follows the revocation path.
   PERMISSION.set(new Permission(lease.program(),owner,permissionLocation));GL20.glUniform1i(permissionLocation,1);grants++;
   last="qualified current owned native comparison sampler; approximate receiver quadrature only";
  }catch(Exception|LinkageError unavailable){SOURCE_CACHE.invalidate();revoke();last="native "+unavailable.getClass().getSimpleName();}
 }
 public static void currentQuad(Runnable draw){
  Throwable primary=null;
  try{beforeDraw();draw.run();}catch(RuntimeException|Error failure){primary=failure;throw failure;}
  finally{try{revoke();}catch(RuntimeException|Error failure){if(primary!=null)primary.addSuppressed(failure);else throw failure;}}
 }
}
