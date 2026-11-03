package com.pp.shaderlod.optimization.photonprime;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.GlStateManager;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.fabric.FabricLodConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.minecraft.SharedConstants;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;
/** Private current-frame subset; no targets/history/profiler/cross-frame list state. */
public final class PhotonDepthPriming {
 public static final String OPTION="SHADERLOD_PHOTON_DEPTH_PRIMING";
 public static final String SOURCE="ee001a1a2824475a13ca05f304b75a163de72160d6d31e47b2a97eb6b76b6e60";
 private static final String SOLID_SHA="0c281031a86e35fccffe00aa5450220c06820126c32e8ef9f57b37ee2bb4a2f8",CUTOUT_SHA="0c281031a86e35fccffe00aa5450220c06820126c32e8ef9f57b37ee2bb4a2f8";
 private static final Map<String,String> expected=profile();private static final java.util.Set<String> admitted=new java.util.HashSet<>();
 private static boolean installed,disabled;
 private static long sourceEpoch;
 private static final com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache DEPTH_SOURCE_CACHE=new com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache();
 public static void invalidateSource(){DEPTH_SOURCE_CACHE.invalidate();sourceEpoch++;}
 public static void disable(){disabled=true;invalidateSource();}
 public static void rearm(){disabled=false;admitted.clear();invalidateSource();}
 public static void assertNoGlError(String where){int error=GL11C.glGetError();if(error!=GL11C.GL_NO_ERROR)throw new IllegalStateException(where+" GLerror="+error);}
 private PhotonDepthPriming(){}
 private static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 private static Map<String,String> profile(){try(var in=PhotonDepthPriming.class.getResourceAsStream("/assets/shaderlod/photonprime/options395.json")){if(in==null)return Map.of();byte[] b=in.readAllBytes();if(!sha(b).equals("fcc335fa1264ffea7c7f49fc51f0130a700e3ddd314482bb0c71f9c738f9232d"))return Map.of();var m=new TreeMap<String,String>();com.google.gson.JsonParser.parseString(new String(b,StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(e->m.put(e.getKey(),e.getValue().getAsString()));return m.size()==395?Map.copyOf(m):Map.of();}catch(Exception|LinkageError failed){return Map.of();}}
 public static synchronized void install(){if(installed)return;com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("photon",com.pp.shaderlod.optimization.OptimizationId.DEPTH_PRIMING,()->"material_mask0 subset; admitted="+admitted+"; disabled="+disabled+"; broader materials pending");com.pp.shaderlod.optimization.CompileOptionControl.configureAdmission("photon",com.pp.shaderlod.optimization.OptimizationId.DEPTH_PRIMING,PhotonDepthPriming::setup);installed=true;}
 public static boolean loaderStack(){try{var f=FabricLoader.getInstance();return f.getModContainer("fabricloader").orElseThrow().getMetadata().getVersion().getFriendlyString().equals("0.19.5") && f.getModContainer("iris").orElseThrow().getMetadata().getVersion().getFriendlyString().equals("1.8.8+mc1.21.1") && f.getModContainer("sodium").orElseThrow().getMetadata().getVersion().getFriendlyString().equals("0.6.13+mc1.21.1");}catch(RuntimeException|LinkageError unavailable){return false;}}
 public static boolean queueSupported(Map<String,String> queue){if(queue==null)return false;for(var e:queue.entrySet()){boolean valid=switch(e.getKey()){case "SHADERLOD_PHOTON_DEPTH_PRIMING","SHADERLOD_PHOTON_DEAD_MIP_CHAIN"->java.util.Set.of("0","1").contains(e.getValue());case "SHADERLOD_SHARED_SPATIAL","SHADERLOD_LIGHTING_RATE_PROBE"->java.util.Set.of("true","false").contains(e.getValue());default->false;};if(!valid)return false;}return true;}
 public static boolean setup(){return selected(false);}
 public static boolean candidatePack(){return selected(true);}
 public static boolean profileMatch(Map<String,String> values){if(expected.size()!=395||!values.keySet().equals(expected.keySet())||!java.util.Set.of("0","1").contains(values.get(OPTION)))return false;var m=new TreeMap<>(values);m.put(OPTION,"0");for(String k:java.util.List.of("SHADERLOD_SHARED_SPATIAL","SHADERLOD_LIGHTING_RATE_PROBE")){if(!java.util.Set.of("false","true").contains(m.get(k)))return false;m.put(k,expected.get(k));}String mip=m.get("SHADERLOD_PHOTON_DEAD_MIP_CHAIN");if(!java.util.Set.of("0","1").contains(mip))return false;m.put("SHADERLOD_PHOTON_DEAD_MIP_CHAIN","0");return expected.equals(m);}
 private static boolean selected(boolean active){
  try{if(!com.pp.shaderlod.optimization.photonprime.mixin.DepthMixinPlugin.exactApi()||!SharedConstants.getCurrentVersion().getName().equals("1.21.1")||!loaderStack())return refuseSource();
   var stack=new TreeMap<String,String>();for(var mod:FabricLoader.getInstance().getAllMods())stack.put(mod.getMetadata().getId(),mod.getMetadata().getVersion().getFriendlyString());if(!com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.stackMatches(stack))return refuseSource();
   if(Iris.isFallback())return refuseSource();
   for(String id:new String[]{"distanthorizons","voxy","create","flywheel"})if(FabricLoader.getInstance().isModLoaded(id))return refuseSource();
   var pack=Iris.getCurrentPack().orElse(null);String name=Iris.getCurrentPackName();if(pack==null||name==null||!queueSupported(Iris.getShaderPackOptionQueue()))return refuseSource();
   var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var path=dir.resolve(name).normalize();if(!path.startsWith(dir)||path.equals(dir))return refuseSource();
   var options=pack.getShaderPackOptions();var set=options.getOptionSet();var e=set.getStringOptions().get(OPTION);if(e==null||!e.getOption().getDefaultValue().equals("0")||!java.util.Set.copyOf(e.getOption().getAllowedValues()).equals(java.util.Set.of("0","1")))return refuseSource();
   var values=options.getOptionValues().mutableCopy();var actual=new TreeMap<String,String>();set.getBooleanOptions().forEach((k,v)->actual.put(k,Boolean.toString(values.getBooleanValues().getOrDefault(k,v.getOption().getDefaultValue()))));set.getStringOptions().forEach((k,v)->actual.put(k,values.getStringValues().getOrDefault(k,v.getOption().getDefaultValue())));
   if(!profileMatch(actual))return refuseSource();
   if(active&&!"1".equals(options.getOptionValues().getStringValueOrDefault(OPTION)))return refuseSource();
   var client=net.minecraft.client.Minecraft.getInstance();
   if(!com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.resourcesMatch(client.getResourcePackRepository().getSelectedIds()))return refuseSource();
   return sourceCached(dir,name,actual,pack,options.getOptionValues(),set,Iris.getPipelineManager().getPipelineNullable(),client.level,sourceEpoch,
     java.util.Arrays.asList(com.pp.shaderlod.optimization.OptimizationRuntimeContext.core(),Map.copyOf(Iris.getShaderPackOptionQueue()),disabled),
     client.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList(),()->selectedUncached(false));
  }catch(Exception|LinkageError unavailable){return refuseSource();}
 }
 private static boolean refuseSource(){DEPTH_SOURCE_CACHE.invalidate();return false;}
 /** Separate depth memo; full original source/graph validation remains the exact miss supplier. */
 static boolean sourceCached(Path dir,String name,Map<String,String> actual,Object pack,Object values,Object declared,Object pipeline,Object world,long epoch,Object coreQueue,java.util.Collection<String> resources,com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache.Validation validation)throws Exception {
  if(!profileMatch(actual))return refuseSource();
  try {
   var inputs=new java.util.ArrayList<Object>();inputs.add(coreQueue);
   for(var pin:java.util.Map.of("/assets/shaderlod/photonprime/coverage_mask0.frag",SOLID_SHA,"/assets/shaderlod/photonprime/options395.json","fcc335fa1264ffea7c7f49fc51f0130a700e3ddd314482bb0c71f9c738f9232d").entrySet()){
    try(var in=PhotonDepthPriming.class.getResourceAsStream(pin.getKey())){if(in==null)return refuseSource();String hash=sha(in.readAllBytes());if(!hash.equals(pin.getValue()))return refuseSource();inputs.add(java.util.List.of(pin.getKey(),hash));}
   }
   return DEPTH_SOURCE_CACHE.check(PhotonDepthPriming.class,dir,name,"photon",actual,pack,values,declared,pipeline,world,epoch,java.util.List.copyOf(inputs),resources,validation);
  }catch(Exception|Error failure){DEPTH_SOURCE_CACHE.invalidate();throw failure;}
 }
 public static boolean source(Path selected,Map<String,String> actual){try{if(!profileMatch(actual)||!SOURCE.equals(com.pp.shaderlod.packs.SupportedPackCatalog.shaderSourceDigest(com.pp.shaderlod.integration.PackSourceReader.read(selected).memberSha256())))return false;try(var in=PhotonDepthPriming.class.getResourceAsStream("/assets/shaderlod/adapters/patches/photon-experiments/live-pixel-budget.json")){if(in==null||!sha(in.readAllBytes()).equals("1d6bd267a12adf7727a8941731a653c23483f4fdd40dec63a648aa85618d15d6"))return false;}if(!"photon".equals(com.pp.shaderlod.packs.PackSelectionService.verifyGeneratedRendererGraph(selected,actual)))return false;var generated=archive(selected);var marker=com.google.gson.JsonParser.parseString(new String(generated.get("shaderlod-selection.json"),StandardCharsets.UTF_8)).getAsJsonObject();String name=marker.get("originalFile").getAsString();if(!Path.of(name).getFileName().toString().equals(name))return false;Path original=selected.toAbsolutePath().normalize().getParent().resolve(name);return com.pp.shaderlod.integration.photonquad.PhotonQuadEligibility.validate(original,com.pp.shaderlod.integration.photonquad.PhotonQuadEligibility.nativeOptions()).eligible()&&com.pp.shaderlod.integration.makeupquad.NamedVertexContract.accepts("photon",archive(original),generated);}catch(Exception|LinkageError e){return false;}}
 private static Map<String,byte[]> archive(Path p)throws Exception{if(!Files.isRegularFile(p,java.nio.file.LinkOption.NOFOLLOW_LINKS)||Files.size(p)>128L*1024*1024)throw new IllegalArgumentException("archive");var files=new TreeMap<String,byte[]>();long total=0;try(var zip=new java.util.zip.ZipInputStream(Files.newInputStream(p))){java.util.zip.ZipEntry e;while((e=zip.getNextEntry())!=null){byte[] b=zip.readNBytes(128*1024*1024+1);total+=b.length;if(total>128L*1024*1024)throw new IllegalArgumentException("extent");if(!e.isDirectory()&&files.put(e.getName(),b)!=null)throw new IllegalArgumentException("duplicate");}}return files;}
 private static boolean selectedUncached(boolean active){try{var pack=Iris.getCurrentPack().orElse(null);if(pack==null)return false;var options=pack.getShaderPackOptions();var set=options.getOptionSet();var v=options.getOptionValues();var m=new TreeMap<String,String>();set.getBooleanOptions().keySet().forEach(k->m.put(k,Boolean.toString(v.getBooleanValueOrDefault(k))));set.getStringOptions().keySet().forEach(k->m.put(k,v.getStringValueOrDefault(k)));return (!active||"1".equals(m.get(OPTION)))&&source(Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName()),m);}catch(Exception|LinkageError e){return false;}}
 public static boolean invariantStage(String source,boolean vertex){
  if(!source.matches("(?s).*#version\\s+400\\b.*"))return false;
  String direction=vertex?"out":"in";
  for(String name:new String[]{"uv","tint","material_mask"}){
   var matcher=java.util.regex.Pattern.compile("([^;{}]*\\b"+name+"\\s*;)").matcher(source);boolean found=false;
   while(matcher.find()){String declaration=matcher.group(1);
    if(declaration.matches("(?s).*\\binvariant\\b.*") && declaration.matches("(?s).*\\b"+direction+"\\b.*") && declaration.matches("(?s).*\\b"+(name.equals("material_mask")?"uint":name.equals("tint")?"vec4":"vec2")+"\\b.*") && (!name.equals("material_mask") || declaration.matches("(?s).*\\bflat\\b.*")))found=true;
   }
   if(!found)return false;
  }
  if(!vertex && (source.contains("clrwl_") || source.matches("(?s).*\\b(gl_FragDepth|imageStore|imageAtomic\\w*|atomic\\w*)\\b.*") || source.matches("(?s).*\\b(texture\\w*|texelFetch)\\s*\\(\\s*(depthtex\\w*|colortex\\w*).*")))return false;
  return !vertex || source.matches("(?s).*\\binvariant\\s+gl_Position\\s*;.*");
 }
 public static boolean needsAdmission(String pass){return !admitted.contains(pass);}
 public static void admission(String pass,int nativeId,int depthId,int draws,State s){
  if(admitted.add(pass))System.out.println("SHADERLOD_PHOTON_MASK0_DEPTH_ADMISSION pass="+pass+" nativeProgram="+nativeId+" coverageProgram="+depthId+" draws="+draws+" fbo="+s.drawFbo+" depth="+s.depthObject+" restored=true mode=mask0_subset_native_pass depthTest=true depthMask=true depthFunc=LEQUAL allMrtMasks=true allMrtBlend=false atlas="+s.textures[0]+" atlasSampler="+s.samplers[0]);
 }
 public static boolean requested(){return !disabled && FabricLodConfig.loaded() && FabricLodConfig.snapshot().enabled() && FabricLodConfig.snapshot().preset()!=LodPreset.QUALITY && com.pp.shaderlod.optimization.OptimizationConfig.snapshot().enabled(com.pp.shaderlod.optimization.OptimizationId.DEPTH_PRIMING) && !com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended();}
 public static boolean drawEnabled(){return requested() && net.minecraft.client.Minecraft.getInstance().level!=null && net.minecraft.client.Minecraft.getInstance().level.dimension()==net.minecraft.world.level.Level.OVERWORLD && candidatePack();}

 public static String fragment(boolean cutout){
  String name="/assets/shaderlod/photonprime/coverage_mask0.frag";
  try(var in=PhotonDepthPriming.class.getResourceAsStream(name)){
   if(in==null)throw new IllegalStateException("Missing pinned depth fragment "+name);
   byte[] bytes=in.readAllBytes();if(!sha(bytes).equals(cutout?CUTOUT_SHA:SOLID_SHA))throw new IllegalStateException("Depth fragment SHA mismatch");
   return new String(bytes,StandardCharsets.UTF_8);
  } catch(Exception e){throw new IllegalStateException("Pinned coverage source unavailable",e);}
 }
 /** Current GL state at native terrain, after native setup/matrices. Incompatible paths stay native. */
 public static final class State {
  public final int drawFbo;
  public final int depthObject;
  private final int readFbo,activeTexture;
  private final int[] viewport=new int[4],scissor=new int[4],units,textures,samplers;
  private final boolean scissorEnabled;
  private State(int draw,int read,int active,boolean enabled,int[] touched){units=touched.clone();textures=new int[units.length];samplers=new int[units.length];drawFbo=draw;depthObject=GL30C.glGetFramebufferAttachmentParameteri(GL30C.GL_DRAW_FRAMEBUFFER,GL30C.GL_DEPTH_ATTACHMENT,GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);readFbo=read;activeTexture=active;scissorEnabled=enabled;}
  private static boolean compatible(){
   if(!GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST) || !GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK) || GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC)!=GL11C.GL_LEQUAL)return false;
   for(int cap:new int[]{GL11C.GL_STENCIL_TEST,GL11C.GL_POLYGON_OFFSET_FILL,GL13C.GL_SAMPLE_ALPHA_TO_COVERAGE,GL13C.GL_SAMPLE_ALPHA_TO_ONE,GL13C.GL_SAMPLE_COVERAGE,org.lwjgl.opengl.GL32C.GL_SAMPLE_MASK,GL30C.GL_RASTERIZER_DISCARD})if(GL11C.glIsEnabled(cap))return false;
   try(var stack=MemoryStack.stackPush()){
    var mask=stack.malloc(4);int n=GL11C.glGetInteger(GL20C.GL_MAX_DRAW_BUFFERS);
    for(int i=0;i<n;i++){
     GL30C.glGetBooleani_v(GL11C.GL_COLOR_WRITEMASK,i,mask);
     for(int j=0;j<4;j++)if(mask.get(j)==0)return false;
     if(GL30C.glIsEnabledi(GL11C.GL_BLEND,i))return false;
    }
   }
   return true;
  }
  public static State capture(int nativeProgram,boolean cutout,net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface depthShader){
   if(cutout)return null;
   if(!(depthShader instanceof com.pp.shaderlod.optimization.photonprime.mixin.DepthSamplerViews.ShaderView view))return null;
   var sampler=view.shaderlodPhoton$samplers();if(!(sampler instanceof com.pp.shaderlod.optimization.photonprime.mixin.DepthSamplerViews.ProgramView program))return null;
   var touched=new java.util.TreeSet<Integer>(java.util.List.of(0,2));int limit=GL11C.glGetInteger(GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
   for(var binding:program.shaderlodPhoton$bindings()){if(!((Object)binding instanceof com.pp.shaderlod.optimization.photonprime.mixin.DepthSamplerViews.BindingView b)||b.shaderlodPhoton$type()!=net.irisshaders.iris.gl.texture.TextureType.TEXTURE_2D||b.shaderlodPhoton$unit()<0||b.shaderlodPhoton$unit()>=limit)return null;touched.add(b.shaderlodPhoton$unit());}

   if(!compatible() || GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM)!=nativeProgram)return null;
   assertNoGlError("native before priming");
   int draw=GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
   if(draw==0 || GL30C.glGetFramebufferAttachmentParameteri(GL30C.GL_DRAW_FRAMEBUFFER,GL30C.GL_DEPTH_ATTACHMENT,GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)==0)return null;
   var s=new State(draw,GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING),GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE),GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST),touched.stream().mapToInt(Integer::intValue).toArray());
   GL11C.glGetIntegerv(GL11C.GL_VIEWPORT,s.viewport);GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX,s.scissor);
   // Native shared VS still samples noisetex on discarded waving materials. Snapshot exact derivative sampler units plus Sodium atlas0/lightmap2.
   try{
   for(int i=0;i<s.units.length;i++){
    int unit=s.units[i];GlStateManager._activeTexture(GL13C.GL_TEXTURE0+unit);
    s.textures[i]=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);s.samplers[i]=GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
   }
   }finally{GlStateManager._activeTexture(s.activeTexture);}
   return s;
  }
  public boolean beginCoverage(){
   if(!compatible() || GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING)!=drawFbo)return false;
   // No cache-owning mask/depth setter is called inside the pure loop. Restore before native callbacks.
   GL11C.glColorMask(false,false,false,false);return true;
  }
  public void endCoverage(){GL11C.glColorMask(true,true,true,true);}
  public void assertRestored(int program){
   if(!compatible() || GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM)!=program || GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING)!=drawFbo || GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING)!=readFbo || GL30C.glGetFramebufferAttachmentParameteri(GL30C.GL_DRAW_FRAMEBUFFER,GL30C.GL_DEPTH_ATTACHMENT,GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)!=depthObject || GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE)!=activeTexture || GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST)!=scissorEnabled)throw new IllegalStateException("Depth R2 native GL restoration failed");
   int[] v=new int[4],c=new int[4];GL11C.glGetIntegerv(GL11C.GL_VIEWPORT,v);GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX,c);
   if(!java.util.Arrays.equals(v,viewport) || !java.util.Arrays.equals(c,scissor))throw new IllegalStateException("Depth R2 native raster restoration failed");
   for(int i=0;i<units.length;i++){GlStateManager._activeTexture(GL13C.GL_TEXTURE0+units[i]);
    if(GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D)!=textures[i] || GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING)!=samplers[i]){GlStateManager._activeTexture(activeTexture);throw new IllegalStateException("Depth R2 texture restoration failed");}
   }GlStateManager._activeTexture(activeTexture);
  }
  public void restoreBindingsAndRaster(){
   GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER,drawFbo);GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER,readFbo);
   for(int i=0;i<units.length;i++){IrisRenderSystem.bindTextureToUnit(GL11C.GL_TEXTURE_2D,units[i],textures[i]);IrisRenderSystem.bindSamplerToUnit(units[i],samplers[i]);}
   GlStateManager._activeTexture(activeTexture);
   GlStateManager._viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
   GlStateManager._scissorBox(scissor[0],scissor[1],scissor[2],scissor[3]);
   if(scissorEnabled)GlStateManager._enableScissorTest();else GlStateManager._disableScissorTest();
  }
 }
}
