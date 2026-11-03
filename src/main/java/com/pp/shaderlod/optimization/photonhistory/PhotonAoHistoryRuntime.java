package com.pp.shaderlod.optimization.photonhistory;
import java.util.*;import com.google.gson.*;import com.pp.shaderlod.optimization.*;import com.pp.shaderlod.LodPreset;import com.pp.shaderlod.packs.*;import com.pp.shaderlod.integration.PackSourceReader;import net.irisshaders.iris.Iris;import net.irisshaders.iris.gl.program.Program;import net.irisshaders.iris.targets.*;import net.irisshaders.iris.shaderpack.programs.ProgramSource;import org.lwjgl.opengl.*;
/** Native AO previous-frame ownership → draw-bound permission. No new target or history-age encoding. */
public final class PhotonAoHistoryRuntime {
 public static final String UNIFORM="shaderlodPhotonAOHistoryQualified";
 private static final String SOURCE="ee001a1a2824475a13ca05f304b75a163de72160d6d31e47b2a97eb6b76b6e60";
 private static final Map<String,String> PROFILE=profile();
 private static Map<String,String> profile(){try(var in=PhotonAoHistoryRuntime.class.getResourceAsStream("/assets/shaderlod/photonhistory/options394.json")){if(in==null)return Map.of();byte[] b=in.readAllBytes();if(!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b)).equals("ab0aa5f8281702b2a1ae3750d193821f70c17e39de3d47e55bee26b67e307e1c"))return Map.of();var m=new TreeMap<String,String>();JsonParser.parseString(new String(b,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(e->m.put(e.getKey(),e.getValue().getAsString()));return m.size()==395?Map.copyOf(m):Map.of();}catch(Exception failure){return Map.of();}}
 private static class Owned{final Object pipeline,pack;final RenderTargets targets;final PhotonAoHistoryCompletion history=new PhotonAoHistoryCompletion();Owned(Object p,RenderTargets t){pipeline=p;targets=t;pack=Iris.getCurrentPack().orElse(null);}}
 private record Lease(Object pipeline,RenderTargets targets,Program program){}private record Pending(Owned owner,PhotonAoHistoryContract.Frame frame){}
 private static final Map<Program,Owned> OWNED=new IdentityHashMap<>();private static final ThreadLocal<Lease> LEASE=new ThreadLocal<>();private static final ThreadLocal<Pending> PENDING=new ThreadLocal<>();
 private static long sourceChecks,sourceNanos,inactiveDraws,grants,revokes;
 /** Cumulative render-thread costs. Source counts exclude compilation; permission counts are successful GL writes. */
 public static Map<String,Long> costCounters(){return Map.of("source_checks",sourceChecks,"source_nanos",sourceNanos,"inactive_draws",inactiveDraws,"grants",grants,"revokes",revokes);}
 private static boolean sourceForDraw(){sourceChecks++;long start=System.nanoTime();try{return source();}finally{sourceNanos+=System.nanoTime()-start;}}
 private static void permission(int location,int value){GL20.glUniform1i(location,value);if(value==1)grants++;else revokes++;}
 private PhotonAoHistoryRuntime(){}
 public static void invalidate(){OWNED.clear();leave();}public static void destroy(Object p){OWNED.entrySet().removeIf(e->e.getValue().pipeline==p);leave();}public static void mark(Object p,RenderTargets t,Program program){LEASE.set(new Lease(p,t,program));}public static void leave(){var pending=PENDING.get();if(pending!=null)pending.owner().history.clear();LEASE.remove();PENDING.remove();}
 public static boolean declaresQualification(String s){return s!=null&&s.contains("uniform int "+UNIFORM+";");}
 public static void built(Object pipeline,RenderTargets targets,ProgramSource source,Program program){if(program!=null&&targets!=null&&PhotonAoHistoryPins.exactApi()&&source!=null&&source.getName().equals("deferred3")&&source.getParent()!=null&&source.getParent().getPack()==Iris.getCurrentPack().orElse(null)&&Arrays.equals(source.getDirectives().getDrawBuffers(),new int[]{6,14})&&declaresQualification(source.getFragmentSource().orElse("")))OWNED.put(program,new Owned(pipeline,targets));}
 /** Admit complete pinned profiles, varying only independently controlled mod graph options.
  * Native material, AA and quality settings remain exact within each reviewed profile. */
 public static boolean profileMatch(Map<String,String> actual){
  if(actual==null||PROFILE.size()!=395||!actual.keySet().equals(PROFILE.keySet()))return false;
  var normalized=new TreeMap<>(actual);
  for(String key:List.of("SHADERLOD_PHOTON_DEPTH_PRIMING","SHADERLOD_PHOTON_DEAD_MIP_CHAIN")){
   if(!Set.of("0","1").contains(actual.get(key)))return false;normalized.put(key,PROFILE.get(key));
  }
  for(String key:List.of("SHADERLOD_SHARED_SPATIAL","SHADERLOD_LIGHTING_RATE_PROBE")){
   if(!Set.of("false","true").contains(actual.get(key)))return false;normalized.put(key,PROFILE.get(key));
  }
  return PROFILE.equals(normalized)||
   com.pp.shaderlod.optimization.photonprime.PhotonDepthPriming.profileMatch(actual)||
   com.pp.shaderlod.optimization.photon.PhotonSpatialProfiles.eligible(actual)||
   com.pp.shaderlod.optimization.photonearlyalpha.PhotonEarlyAlphaProfile.accepts(actual);
 }
 public static boolean declared(net.irisshaders.iris.shaderpack.option.ShaderPackOptions o){var keys=new HashSet<String>();keys.addAll(o.getOptionSet().getBooleanOptions().keySet());keys.addAll(o.getOptionSet().getStringOptions().keySet());return PROFILE.size()==395&&keys.equals(PROFILE.keySet());}
 private static boolean source(){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(PhotonAoHistoryRuntime.class,"source"),()->{try{var pack=Iris.getCurrentPack().orElse(null);if(pack==null||Iris.isFallback()||!Iris.getShaderPackOptionQueue().isEmpty()||!declared(pack.getShaderPackOptions()))return false;var o=pack.getShaderPackOptions();var v=o.getOptionValues();var m=new TreeMap<String,String>();o.getOptionSet().getBooleanOptions().keySet().forEach(k->m.put(k,Boolean.toString(v.getBooleanValueOrDefault(k))));o.getOptionSet().getStringOptions().keySet().forEach(k->m.put(k,v.getStringValueOrDefault(k)));if(!profileMatch(m))return false;var platform=net.irisshaders.iris.platform.IrisPlatformHelpers.getInstance();if(platform.isModLoaded("distanthorizons")||platform.isModLoaded("voxy"))return false;String name=Iris.getIrisConfig().getShaderPackName().orElse(null);if(name==null)return false;var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var path=dir.resolve(name).normalize();return path.startsWith(dir)&&!path.equals(dir)&&"photon".equals(PackSelectionService.verifyGeneratedRendererGraph(path,m))&&SOURCE.equals(SupportedPackCatalog.shaderSourceDigest(PackSourceReader.read(path).memberSha256()));}catch(Exception|LinkageError failed){return false;}
  });
 }
 private static int integer(int p,String name){int l=GL20.glGetUniformLocation(p,name);return l<0?-1:GL20.glGetUniformi(p,l);}private static int param(int s,int k){return s==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,k):GL33.glGetSamplerParameteri(s,k);}private static float number(int s,int k){return s==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,k):GL33.glGetSamplerParameterf(s,k);}
 private static PhotonAoHistoryContract.Image image(int p,String name,RenderTarget target){if(target==null)return null;int unit=integer(p,name);if(unit<0||unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))return null;int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);try{GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);int id=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),s=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);var caps=GL.getCapabilities();float aniso=caps.GL_EXT_texture_filter_anisotropic||caps.GL_ARB_texture_filter_anisotropic?number(s,0x84FE):1;return new PhotonAoHistoryContract.Image(id,target.getMainTexture(),target.getAltTexture(),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),target.getWidth(),target.getHeight(),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,1,GL11.GL_TEXTURE_WIDTH),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,1,GL11.GL_TEXTURE_HEIGHT),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL),param(s,GL14.GL_TEXTURE_COMPARE_MODE),param(s,GL11.GL_TEXTURE_MIN_FILTER),param(s,GL11.GL_TEXTURE_MAG_FILTER),param(s,GL11.GL_TEXTURE_WRAP_S),param(s,GL11.GL_TEXTURE_WRAP_T),number(s,0x8501),number(s,0x813A),number(s,0x813B),aniso,GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x8E42),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x8E43),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x8E44),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x8E45));}finally{GL13.glActiveTexture(active);}}
 private static int output(int slot){int a=GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+slot);if(a<GL30.GL_COLOR_ATTACHMENT0||a>=GL30.GL_COLOR_ATTACHMENT0+GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS)||GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,a,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)!=GL11.GL_TEXTURE||GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,a,GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL)!=0)return 0;return GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,a,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);}
 private static boolean owner(RenderTarget t,int id){return t!=null&&id>0&&(id==t.getMainTexture()||id==t.getAltTexture());}
 private static PhotonAoHistoryContract.Frame observe(Owned g,int p){var a=image(p,"colortex6",g.targets.get(6));var b=image(p,"colortex14",g.targets.get(14));if(!PhotonAoHistoryContract.image(a,0x805B)||!PhotonAoHistoryContract.image(b,0x822F)||a.width()!=b.width()||a.height()!=b.height())return null;int[] view=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,view);if(!Arrays.equals(view,new int[]{0,0,a.width(),a.height()})||GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE||GL11.glIsEnabled(GL11.GL_BLEND)||GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)||GL11.glIsEnabled(GL11.GL_DEPTH_TEST)||GL11.glIsEnabled(GL11.GL_STENCIL_TEST)||GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD))return null;for(int slot=0;slot<2;slot++){var mask=org.lwjgl.BufferUtils.createByteBuffer(4);GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK,slot,mask);for(int channel=0;channel<4;channel++)if(mask.get(channel)!=GL11.GL_TRUE)return null;}int x=output(0),y=output(1);if(!owner(g.targets.get(6),x)||!owner(g.targets.get(14),y)||x==a.id()||y==b.id()||x==b.id()||y==a.id()||x==y||a.id()==b.id())return null;int frame=integer(p,"frameCounter");if(frame<0)return null;return new PhotonAoHistoryContract.Frame(frame,OptimizationConfig.generation(),a.width(),a.height(),a.id(),b.id(),x,y);}
 private static void beforeDrawChecked(){
  var previous=PENDING.get();if(previous!=null)previous.owner().history.clear();PENDING.remove();
  int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(p<=0)return;
  int l=GL20.glGetUniformLocation(p,UNIFORM);if(l<0)return;permission(l,0);
  try{
   var lease=LEASE.get();if(lease==null)return;var g=OWNED.get(lease.program());if(g==null)return;
   // Disabled draws must neither validate archives nor observe/complete history.
   var core=OptimizationRuntimeContext.core();
   boolean active=core!=null&&core.settings().enabled()&&core.preset()!=LodPreset.QUALITY&&core.optimizations().enabled(OptimizationId.AO_HISTORY_REUSE)&&!com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended()&&integer(p,"shaderlodEnabled")==1&&integer(p,"shaderlodBridgeVersion")==2&&integer(p,"shaderlodOptimizationFlagsVersion")==1&&integer(p,"shaderlodOptAoHistoryReuse")==1;
   if(active){int preset=integer(p,"shaderlodPreset");active=preset>=0&&preset<=2;}
   if(!active){inactiveDraws++;g.history.clear();return;}
   if(lease.pipeline()!=g.pipeline||lease.targets()!=g.targets||g.pipeline!=Iris.getPipelineManager().getPipelineNullable()||g.pack!=Iris.getCurrentPack().orElse(null)||lease.program().getProgramId()!=p||!PhotonAoHistoryPins.exactApi()||!GL.getCapabilities().OpenGL33||!CompileOptionControl.overworld(Iris.getCurrentDimension())||!sourceForDraw()){g.history.clear();return;}
   var frame=observe(g,p);if(frame==null){g.history.clear();return;}
   PENDING.set(new Pending(g,frame));permission(l,PhotonAoHistoryContract.history(g.history.current(),frame)?1:0);
  }catch(RuntimeException|LinkageError unavailable){var lease=LEASE.get();if(lease!=null){var g=OWNED.get(lease.program());if(g!=null)g.history.clear();}PENDING.remove();revokeCurrent();}
 }
 private static void revokeCurrent(){try{int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(p>0){int l=GL20.glGetUniformLocation(p,UNIFORM);if(l>=0)permission(l,0);}}catch(RuntimeException|LinkageError unavailable){/* A missing GL entry/context cannot be forced; Java history remains cleared. */}}
 public static void beforeDraw(){try{beforeDrawChecked();}catch(RuntimeException|LinkageError unavailable){var lease=LEASE.get();if(lease!=null){var g=OWNED.get(lease.program());if(g!=null)g.history.clear();}PENDING.remove();revokeCurrent();}}
 public static void afterDraw(){var pending=PENDING.get();PENDING.remove();if(pending==null)return;var g=pending.owner();g.history.finish(pending.frame(),()->{var lease=LEASE.get();if(lease==null)return null;int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);return lease.program().getProgramId()==p?observe(g,p):null;},PhotonAoHistoryRuntime::revokeCurrent);}
 public static void currentQuad(Runnable draw){beforeDraw();try{draw.run();afterDraw();}catch(RuntimeException|Error failure){var pending=PENDING.get();if(pending!=null)pending.owner().history.clear();PENDING.remove();revokeCurrent();throw failure;}}

 /** Invoked synchronously by the authentic pipeline build; never by a rendering callback. */
 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){try{source();}catch(Exception|LinkageError refused){}}
}
