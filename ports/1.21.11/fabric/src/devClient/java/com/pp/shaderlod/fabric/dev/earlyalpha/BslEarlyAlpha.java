package com.pp.shaderlod.fabric.dev.earlyalpha;
import com.pp.shaderlod.fabric.FabricLodConfig;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.bslquad.BslQuadEligibility;
import com.pp.shaderlod.integration.makeupquad.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.*;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
/** Explicit private dev bridge. Original sampler/test fallback whenever any gate is unavailable. */
public final class BslEarlyAlpha {
 public static final boolean REQUESTED=Boolean.getBoolean("shaderlod.dev.portBslEarlyAlpha");
 public static final Set<String> ARCHIVES=Set.of("82ddff3530ab9c99cee4e779cc8718643627074a4b1b308bc55dea40b8223086","ee73a4272233d5b7ca7e13d6d009d4e95253cdd9fd234d6393871d0091e69b92");
 private static Object certifiedPack,builtPack;
 private static String certifiedName;
 public static long qualifiedDraws,refusedDraws;
 public static boolean lastEnabled,lastQualified;
 public static com.google.gson.JsonObject lastObservation;
 private BslEarlyAlpha() {}
 private static boolean finite() {try{return Long.parseLong(System.getProperty("shaderlod.dev.startupDeadlineEpochMillis","0"))>System.currentTimeMillis();}catch(Exception e){return false;}}
 public static boolean packPinned() {
  if(!REQUESTED || !finite() || Boolean.getBoolean("shaderlod.dev.portBslGpuBudget") || Boolean.getBoolean("shaderlod.dev.portBslAlphaAdmission") || Boolean.getBoolean("shaderlod.dev.portBslOriginalSource") || Boolean.getBoolean("shaderlod.dev.portSildurOriginalSource") || Boolean.getBoolean("shaderlod.dev.portRendererSpeedProbe") || Boolean.getBoolean("shaderlod.rendererSpeedPortR1") || MakeUpQuadLifecycle.suspended() || Iris.isFallback())return false;
  try {
   var pack=Iris.getCurrentPack().orElseThrow();var name=Iris.getCurrentPackName();
   if(pack!=certifiedPack || !name.equals(certifiedName)) {
    certifiedPack=null;certifiedName=null;
    var root=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var path=root.resolve(name).normalize();
    if(!path.startsWith(root)||path.equals(root)||Files.isSymbolicLink(path)||!ARCHIVES.contains(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)))))return false;
    var mods=new TreeMap<String,String>();net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().forEach(m->{if(!m.getMetadata().getId().equals("java"))mods.put(m.getMetadata().getId(),m.getMetadata().getVersion().getFriendlyString());});
    if(!MakeUpQuadApiPins.exactApi()||!MakeUpQuadRuntime.stackMatches(mods))return false;
    certifiedPack=pack;certifiedName=name;
   }
   var o=Iris.getCurrentPack().orElseThrow().getShaderPackOptions();var v=o.getOptionValues();var map=new TreeMap<String,String>();o.getOptionSet().getBooleanOptions().keySet().forEach(k->map.put(k,Boolean.toString(v.getBooleanValueOrDefault(k))));o.getOptionSet().getStringOptions().keySet().forEach(k->map.put(k,v.getStringValueOrDefault(k)));
   return BslQuadEligibility.optionsMatch(map) && Iris.getShaderPackOptionQueue().isEmpty();
  } catch(Exception|LinkageError e) {return false;}
 }
 public static void built(boolean hasTest) {if(hasTest)builtPack=Iris.getCurrentPack().orElse(null);}
 public static boolean enabled() {
  var c=Minecraft.getInstance();var cfg=FabricLodConfig.snapshot();
  return builtPack==Iris.getCurrentPack().orElse(null) && packPinned() && FabricLodConfig.loaded() && cfg.enabled() && cfg.preset()==LodPreset.PERFORMANCE && !cfg.debug()
   && c.level!=null && c.getMainRenderTarget().width==1920 && c.getMainRenderTarget().height==1200 && c.getWindow().getWidth()==1920 && c.getWindow().getHeight()==1200 && Iris.getPipelineManager().getPipelineNullable()!=null && Iris.getPipelineManager().getPipelineNullable().getClass()==net.irisshaders.iris.pipeline.IrisRenderingPipeline.class && c.level.dimension()==net.minecraft.world.level.Level.OVERWORLD && !ShadowRenderingState.areShadowsCurrentlyBeingRendered()
   && !Boolean.getBoolean(RendererRolloutPolicy.RESEARCH_ROUTES) && !MakeUpQuadRuntime.enabled()
   && MakeUpQuadRuntime.resourcesMatch(c.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList());
 }
 /** Runs after all native samplers/uniforms are installed, immediately before actual draw. */
 public static void beforeDraw(TerrainRenderPass pass) {
  if(!REQUESTED){BslEarlyAlphaObserver.draw(pass);return;}
  int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);int enable=GL20.glGetUniformLocation(program,"shaderlodBslAlphaEnabled");if(enable<0){BslEarlyAlphaObserver.draw(pass);return;}
  boolean active=false,qualified=false;int unit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
  try {
   active=pass==DefaultTerrainRenderPasses.CUTOUT && enabled() && program==((net.irisshaders.iris.pipeline.IrisRenderingPipeline)Iris.getPipelineManager().getPipelineNullable()).getSodiumPrograms().getProgram(pass).handle();
   int alphaLocation=GL20.glGetUniformLocation(program,"iris_currentAlphaTest");
   active &= alphaLocation>=0 && GL20.glGetUniformf(program,alphaLocation)==0.5f;
   if(active) {
    qualified=!GL11.glIsEnabled(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE)&&!GL11.glIsEnabled(GL13.GL_SAMPLE_ALPHA_TO_ONE)&&!GL11.glIsEnabled(GL13.GL_SAMPLE_COVERAGE)&&!GL11.glIsEnabled(GL32.GL_SAMPLE_MASK);
    for(String name:List.of("shadowtex0","shadowtex1","shadowcolor0")) {
     int location=GL20.glGetUniformLocation(program,name);if(location<0){qualified=false;break;}
     GL13.glActiveTexture(GL13.GL_TEXTURE0+GL20.glGetUniformi(program,location));int sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
     int min=param(sampler,GL11.GL_TEXTURE_MIN_FILTER),mag=param(sampler,GL11.GL_TEXTURE_MAG_FILTER);
     qualified &= GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=0 && min==GL11.GL_LINEAR && mag==min && GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL)==0 && GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,1,GL11.GL_TEXTURE_WIDTH)==0;
     if(name.equals("shadowcolor0"))qualified &= param(sampler,GL14.GL_TEXTURE_COMPARE_MODE)==GL11.GL_NONE;
     if(!name.equals("shadowcolor0"))qualified &= param(sampler,GL14.GL_TEXTURE_COMPARE_MODE)==GL30.GL_COMPARE_REF_TO_TEXTURE && param(sampler,GL14.GL_TEXTURE_COMPARE_FUNC)==GL11.GL_LEQUAL;
     if(GL.getCapabilities().GL_EXT_texture_filter_anisotropic) qualified &= (sampler==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT):GL33.glGetSamplerParameterf(sampler,EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT))==1f;
    }
   }
  } catch(Exception|LinkageError e) {active=false;qualified=false;} finally {GL13.glActiveTexture(unit);}
  GL20.glUniform1i(enable,active?1:0);setI(program,"shaderlodBslAlphaQualified",qualified?1:0);setI(program,"shaderlodBslAlphaVersion",1);setI(program,"shaderlodBslAlphaPreset",FabricLodConfig.snapshot().preset()==LodPreset.PERFORMANCE?1:0);
  int ref=GL20.glGetUniformLocation(program,"iris_currentAlphaTest"),out=GL20.glGetUniformLocation(program,"shaderlodBslAlphaReference");if(ref>=0&&out>=0)GL20.glUniform1f(out,GL20.glGetUniformf(program,ref));
  if(lastObservation==null || lastEnabled!=active || lastQualified!=qualified || lastObservation.get("program").getAsInt()!=program) {
   var observation=new com.google.gson.JsonObject();observation.addProperty("program",program);observation.addProperty("nativeAlphaValue",ref<0?-1:GL20.glGetUniformf(program,ref));
   for(String name:List.of("shaderlodBslAlphaEnabled","shaderlodBslAlphaQualified","shaderlodBslAlphaVersion","shaderlodBslAlphaPreset")){int loc=GL20.glGetUniformLocation(program,name);observation.addProperty(name+"Location",loc);if(loc>=0)observation.addProperty(name+"Readback",GL20.glGetUniformi(program,loc));}
   observation.addProperty("privateAlphaLocation",out);if(out>=0)observation.addProperty("privateAlphaReadback",GL20.glGetUniformf(program,out));observation.addProperty("capturedUtc",java.time.Instant.now().toString());lastObservation=observation;
  }
  lastEnabled=active;lastQualified=qualified;if(active&&qualified)qualifiedDraws++;else refusedDraws++;
  BslEarlyAlphaObserver.draw(pass);
 }
 private static int param(int sampler,int pname){return sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,pname):GL33.glGetSamplerParameteri(sampler,pname);}
 private static void setI(int program,String name,int value){int loc=GL20.glGetUniformLocation(program,name);if(loc>=0)GL20.glUniform1i(loc,value);}
}
