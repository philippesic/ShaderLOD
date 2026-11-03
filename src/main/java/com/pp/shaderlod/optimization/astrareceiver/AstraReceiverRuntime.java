package com.pp.shaderlod.optimization.astrareceiver;
import java.util.*;import net.irisshaders.iris.Iris;import net.irisshaders.iris.pipeline.IrisRenderingPipeline;import net.irisshaders.iris.shadows.ShadowRenderTargets;import net.irisshaders.iris.platform.IrisPlatformHelpers;import net.irisshaders.iris.shaderpack.ShaderPack;import org.lwjgl.opengl.*;
import com.pp.shaderlod.optimization.*;import com.pp.shaderlod.LodPreset;import com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle;import com.pp.shaderlod.optimization.makeupao.*;import com.pp.shaderlod.optimization.bsl.BslReceiverContract;
import com.pp.shaderlod.optimization.makeupshadow.mixin.ShadowPipelineView;
/** Exact owned native terrain programs; sampler permission uploaded only at native draw boundaries. */
public final class AstraReceiverRuntime {
 private record Owner(Object pipeline,Object pack,long epoch){}private static final Map<Integer,Owner> OWNERS=new HashMap<>();private static long epoch;private static Object sourcePack;private static Map<String,String> sourceOptions=Map.of();private static boolean sourceReady;private static String failure="not consumed";
 private static final OptimizationId[] SHADOW_CONTROLS = { OptimizationId.SHADOW_RECEIVER_FOOTPRINT };
 private AstraReceiverRuntime(){}
 /** The sampler contract is shared, but none of its three consumers depends on another switch. */
 private static boolean requested(com.pp.shaderlod.CoreUniformFrame.Core core) {
     for (var id : SHADOW_CONTROLS)
         if (core.optimizations().enabled(id) && OptimizationConfig.requested(id)) return true;
     return false;
 }
 private static boolean uploaded(com.pp.shaderlod.CoreUniformFrame.Core core, int program) {
     for (var id : SHADOW_CONTROLS)
         if (core.optimizations().enabled(id) && OptimizationConfig.requested(id)
             && integer(program, id.uniform()) == 1) return true;
     return false;
 }
 public static void invalidate(){epoch++;OWNERS.clear();sourcePack=null;sourceOptions=Map.of();sourceReady=false;}
 public static void destroy(Object pipeline){OWNERS.entrySet().removeIf(e->e.getValue().pipeline()==pipeline);frame();}
 public static void frame(){sourcePack=null;sourceReady=false;sourceOptions=Map.of();}
 public static void own(IrisRenderingPipeline pipeline,ShaderPack pack,String role,int id){if(pipeline!=null&&pack!=null&&role!=null&&role.startsWith("gbuffers_terrain")&&id>0)OWNERS.put(id,new Owner(pipeline,pack,epoch));}
/** Derived registration caller first proves its own linked vertex and exact shared native fragment. Never copies readiness. */
public static boolean ownsDerived(Object pipeline,Object pack,int id){var o=OWNERS.get(id);return id>0&&pipeline!=null&&pack!=null&&o!=null&&o.pipeline()==pipeline&&o.pack()==pack&&o.epoch()==epoch;}
public static void forgetDerived(int id){OWNERS.remove(id);}

 public static String status(){return "Astra optional Gaussian9-to5 subpixel preview; disable colored shadows/POM; opaque mat0 above water only; eligible receiver tier precedes sample/temporal budgets on same Gaussian; visual equivalence unverified; owned="+OWNERS.size()+"; last="+failure;}
 private static Map<String,String> options(ShaderPack pack){if(pack==null)return Map.of();return com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(pack.getShaderPackOptions());}
 /** Only the declared colored-shadow false variant differs from the pinned working native option map. */
 public static boolean profile(Map<String,String> actual){if(actual==null||!"false".equals(actual.get("COLORED_SHADOWS"))||!"false".equals(actual.get("PARALLAX")))return false;var normalized=new TreeMap<>(actual);normalized.put("COLORED_SHADOWS","true");return com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts("astralex",normalized);}
 private static boolean source(ShaderPack pack){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(AstraReceiverRuntime.class,"source",pack),()->{sourcePack=null;var m=options(pack);if(!profile(m))return false;if(sourcePack!=pack){sourcePack=pack;sourceOptions=m;sourceReady=false;try{try(var in=AstraReceiverRuntime.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/astralex.json")){if(in==null||!"7965fc2930f80491528a91aca3307b342dc333064ee47c382cf796e7f69a2be6".equals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))))return false;}String name=Iris.getIrisConfig().getShaderPackName().orElse(null);if(name==null||!java.nio.file.Path.of(name).getFileName().toString().equals(name))return false;var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var p=dir.resolve(name).normalize();if(!p.startsWith(dir)||p.equals(dir))return false;sourceReady="astralex".equals(com.pp.shaderlod.packs.PackSelectionService.verifyGeneratedRendererGraph(p,m));}catch(Exception|LinkageError e){return false;}}return sourceReady&&sourceOptions.equals(m);
  });
 }
 private static int integer(int p,String n){int l=GL20.glGetUniformLocation(p,n);return l<0?-1:GL20.glGetUniformi(p,l);}private static int param(int s,int n){return s==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,n):GL33.glGetSamplerParameteri(s,n);}
 private static BslReceiverContract.Sampler depth(int p){int u=integer(p,"shadowtex0");if(u<0||u>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))return null;int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);try{GL13.glActiveTexture(GL13.GL_TEXTURE0+u);int t=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),s=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);var c=GL.getCapabilities();boolean anis=c.GL_EXT_texture_filter_anisotropic||c.GL_ARB_texture_filter_anisotropic;float a=anis?(s==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,0x84FE):GL33.glGetSamplerParameterf(s,0x84FE)):1;return new BslReceiverContract.Sampler(t,GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x884A),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x8C16),param(s,0x884C),param(s,0x884D),param(s,GL11.GL_TEXTURE_MIN_FILTER),param(s,GL11.GL_TEXTURE_MAG_FILTER),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x813C),param(s,GL11.GL_TEXTURE_WRAP_S),param(s,GL11.GL_TEXTURE_WRAP_T),a);}finally{GL13.glActiveTexture(active);}}
 public static boolean sampler(BslReceiverContract.Sampler s,int texture,int resolution){if(s==null||texture<=0||s.texture()!=texture||resolution!=3072||s.width()!=resolution||s.height()!=resolution)return false;return ((s.format()==0x81A5&&s.depthBits()==16&&s.depthType()==0x8C17)||(s.format()==0x81A6&&s.depthBits()==24&&s.depthType()==0x8C17)||(s.format()==0x81A7&&s.depthBits()==32&&s.depthType()==0x8C17)||(s.format()==0x8CAC&&s.depthBits()==32&&s.depthType()==0x1406))&&s.compare()==0x884E&&s.compareFunction()==0x0203&&s.min()==0x2601&&s.mag()==0x2601&&s.base()==0&&s.wrapS()==0x812F&&s.wrapT()==0x812F&&s.anisotropy()==1;}
 public static void revoke(){int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(p>0){int l=GL20.glGetUniformLocation(p,"shaderlodAstraReceiverPermission");if(l>=0)GL20.glUniform1i(l,0);}}
 /** Same exact native admission as draw, without changing permission. Called before a sparse viewport opens. */
 public static boolean ready(int p){if(p<=0)return false;failure="native/refused";try{var core=OptimizationRuntimeContext.core();if(core==null||!core.settings().enabled()||core.preset()==LodPreset.QUALITY||!requested(core)||MakeUpQuadLifecycle.suspended()||!com.pp.shaderlod.optimization.astraearlyalpha.mixin.AstraEarlyAlphaPlugin.exactApi()||Iris.isFallback())return false;if(GL20.glGetUniformLocation(p,"shaderlodAstraReceiverPermission")<0)return false;var platform=IrisPlatformHelpers.getInstance();if(!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()||!Iris.getShaderPackOptionQueue().isEmpty()||net.minecraft.client.Minecraft.getInstance().level==null||net.minecraft.client.Minecraft.getInstance().level.dimension()!=net.minecraft.world.level.Level.OVERWORLD)return false;if(platform.isModLoaded("distanthorizons")||platform.isModLoaded("voxy"))return false;var pipe=Iris.getPipelineManager().getPipelineNullable();var pack=Iris.getCurrentPack().orElse(null);Owner o=OWNERS.get(p);if(o==null||o.pipeline()!=pipe||o.pack()!=pack||o.epoch()!=epoch||!(pipe instanceof IrisRenderingPipeline)||pack==null||!source(pack))return false;if(net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered())return false;
 if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE||integer(p,"shaderlodBridgeVersion")!=2||integer(p,"shaderlodEnabled")!=1||integer(p,"shaderlodOptimizationFlagsVersion")!=1||!uploaded(core,p)||!Set.of(0,1,2).contains(integer(p,"shaderlodPreset")))return false;
 var view=(ShadowPipelineView)pipe;var targets=view.shadowlod$targets();var shadows=view.shadowlod$shadows();if(targets==null||shadows==null)return false;int[] v=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);var window=net.minecraft.client.Minecraft.getInstance().getWindow();if((!Arrays.equals(v,new int[]{0,0,targets.getCurrentWidth(),targets.getCurrentHeight()})||window.getWidth()!=v[2]||window.getHeight()!=v[3])&&!com.pp.shaderlod.optimization.astraspatial.AstraSpatialRuntime.receiverViewport(p))return false;if(!sampler(depth(p),shadows.getDepthTexture().getTextureId(),shadows.getResolution()))return false;failure="qualified actual bound native comparison sampler";return true;
 }catch(RuntimeException|LinkageError e){failure=e.getClass().getSimpleName();return false;}}
 public static void draw(){int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(p<=0)return;int l=GL20.glGetUniformLocation(p,"shaderlodAstraReceiverPermission");if(l<0)return;GL20.glUniform1i(l,0);if(ready(p))GL20.glUniform1i(l,1);}


 /** Invoked synchronously by the authentic pipeline build; never by a rendering callback. */
 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){try{source(pack);}catch(Exception|LinkageError refused){}}
}
