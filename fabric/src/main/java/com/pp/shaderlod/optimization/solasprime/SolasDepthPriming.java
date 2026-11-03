package com.pp.shaderlod.optimization.solasprime;
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
public final class SolasDepthPriming {
 public static final String OPTION="SHADERLOD_SOLAS_DEPTH_PRIMING";
 public static final String SOURCE="0ddecbc96a5521f1f519e44fe7aec5814bf03447fc07928ed49ce4c2691b4039";
 private static final String SOLID_SHA="63c2a3aee29f8f6466b6fd492704681de070516eb431c4676198e70078e70697",CUTOUT_SHA="63c2a3aee29f8f6466b6fd492704681de070516eb431c4676198e70078e70697";
 private static final Map<String,String> expected=profile();private static final java.util.Set<String> admitted=new java.util.HashSet<>();
 private static boolean installed,disabled;
 private static long sourceEpoch;
 private static final com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache DEPTH_SOURCE_CACHE=new com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache();
 public static void invalidateSource(){DEPTH_SOURCE_CACHE.invalidate();sourceEpoch++;}
 public static void disable(){disabled=true;invalidateSource();}
 public static void rearm(){disabled=false;admitted.clear();invalidateSource();}
 public static void assertNoGlError(String where){int error=GL11C.glGetError();if(error!=GL11C.GL_NO_ERROR)throw new IllegalStateException(where+" GLerror="+error);}
 private SolasDepthPriming(){}
 private static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 private static Map<String,String> profile(){try(var in=SolasDepthPriming.class.getResourceAsStream("/assets/shaderlod/solasprime/options435.json")){if(in==null)return Map.of();byte[] b=in.readAllBytes();if(!sha(b).equals("e8f64dc22b072d060e33ecef5defcb53085f838420104af4386ae295b33c2dd5"))return Map.of();var m=new TreeMap<String,String>();com.google.gson.JsonParser.parseString(new String(b,StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(e->m.put(e.getKey(),e.getValue().getAsString()));return m.size()==435?Map.copyOf(m):Map.of();}catch(Exception|LinkageError failed){return Map.of();}}
 public static synchronized void install(){if(installed)return;com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("solas-shader",com.pp.shaderlod.optimization.OptimizationId.DEPTH_PRIMING,()->"rock20302/20303/20317 subset; admitted="+admitted+"; disabled="+disabled+"; broader materials pending");com.pp.shaderlod.optimization.CompileOptionControl.configureAdmission("solas-shader",com.pp.shaderlod.optimization.OptimizationId.DEPTH_PRIMING,SolasDepthPriming::setup);installed=true;}
 public static boolean loaderStack(){try{var f=FabricLoader.getInstance();return f.getModContainer("fabricloader").orElseThrow().getMetadata().getVersion().getFriendlyString().equals("0.19.5") && f.getModContainer("iris").orElseThrow().getMetadata().getVersion().getFriendlyString().equals("1.8.8+mc1.21.1") && f.getModContainer("sodium").orElseThrow().getMetadata().getVersion().getFriendlyString().equals("0.6.13+mc1.21.1");}catch(RuntimeException|LinkageError unavailable){return false;}}
 public static boolean setup(){return selected(false);}
 public static boolean candidatePack(){return selected(true);}
 public static boolean profileMatch(Map<String,String> values){if(values==null)return false;var n=new TreeMap<>(values);String pixel=n.remove("SHADERLOD_SOLAS_PIXEL_GRAPH");if(pixel!=null&&!java.util.Set.of("0","1").contains(pixel))return false;String mip=n.remove("SHADERLOD_SOLAS_DEAD_MIP_CHAIN");if(mip!=null&&!java.util.Set.of("0","1").contains(mip))return false;String ao=n.remove("SHADERLOD_SOLAS_AO_HALF_RES");if(ao!=null&&!java.util.Set.of("0","1").contains(ao))return false;if(expected.size()!=435||!n.keySet().equals(expected.keySet())||!java.util.Set.of("0","1").contains(n.get(OPTION)))return false;n.put(OPTION,"0");return expected.equals(n);}
 private static boolean selected(boolean active){
  try{if(!com.pp.shaderlod.optimization.solasprime.mixin.DepthMixinPlugin.exactApi()||!SharedConstants.getCurrentVersion().getName().equals("1.21.1")||!loaderStack())return refuseSource();
   if(Iris.isFallback())return refuseSource();
   var stack=new TreeMap<String,String>();for(var mod:FabricLoader.getInstance().getAllMods())stack.put(mod.getMetadata().getId(),mod.getMetadata().getVersion().getFriendlyString());if(!com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.stackMatches(stack))return refuseSource();
   for(String id:new String[]{"distanthorizons","voxy","create","flywheel"})if(FabricLoader.getInstance().isModLoaded(id))return refuseSource();
   var pack=Iris.getCurrentPack().orElse(null);String name=Iris.getCurrentPackName();if(pack==null||name==null||Iris.getShaderPackOptionQueue().entrySet().stream().anyMatch(e->!e.getKey().equals(OPTION)||!java.util.Set.of("0","1").contains(e.getValue())))return refuseSource();
   var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var path=dir.resolve(name).normalize();if(!path.startsWith(dir)||path.equals(dir))return refuseSource();
   var options=pack.getShaderPackOptions();var set=options.getOptionSet();var e=set.getStringOptions().get(OPTION);if(e==null||!e.getOption().getDefaultValue().equals("0")||!java.util.Set.copyOf(e.getOption().getAllowedValues()).equals(java.util.Set.of("0","1")))return refuseSource();
   var values=options.getOptionValues().mutableCopy();var actual=new TreeMap<String,String>();set.getBooleanOptions().forEach((k,v)->actual.put(k,Boolean.toString(values.getBooleanValues().getOrDefault(k,v.getOption().getDefaultValue()))));set.getStringOptions().forEach((k,v)->actual.put(k,values.getStringValues().getOrDefault(k,v.getOption().getDefaultValue())));
   if(!profileMatch(actual))return refuseSource();
   if(active&&!"1".equals(options.getOptionValues().getStringValueOrDefault(OPTION)))return refuseSource();
   var client=net.minecraft.client.Minecraft.getInstance();
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
   for(var pin:java.util.Map.of("/assets/shaderlod/solasprime/coverage_rock.frag",SOLID_SHA,"/assets/shaderlod/solasprime/options435.json","e8f64dc22b072d060e33ecef5defcb53085f838420104af4386ae295b33c2dd5").entrySet()){
    try(var in=SolasDepthPriming.class.getResourceAsStream(pin.getKey())){if(in==null)return refuseSource();String hash=sha(in.readAllBytes());if(!hash.equals(pin.getValue()))return refuseSource();inputs.add(java.util.List.of(pin.getKey(),hash));}
   }
   return DEPTH_SOURCE_CACHE.check(SolasDepthPriming.class,dir,name,"solas-shader",actual,pack,values,declared,pipeline,world,epoch,java.util.List.copyOf(inputs),resources,validation);
  }catch(Exception|Error failure){DEPTH_SOURCE_CACHE.invalidate();throw failure;}
 }
 private static boolean selectedUncached(boolean active){
  try{if(!com.pp.shaderlod.optimization.solasprime.mixin.DepthMixinPlugin.exactApi()||!SharedConstants.getCurrentVersion().getName().equals("1.21.1")||!loaderStack())return false;
   if(Iris.isFallback())return false;
   var stack=new TreeMap<String,String>();for(var mod:FabricLoader.getInstance().getAllMods())stack.put(mod.getMetadata().getId(),mod.getMetadata().getVersion().getFriendlyString());if(!com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.stackMatches(stack))return false;
   for(String id:new String[]{"distanthorizons","voxy","create","flywheel"})if(FabricLoader.getInstance().isModLoaded(id))return false;
   var pack=Iris.getCurrentPack().orElse(null);String name=Iris.getCurrentPackName();if(pack==null||name==null||Iris.getShaderPackOptionQueue().entrySet().stream().anyMatch(e->!e.getKey().equals(OPTION)||!java.util.Set.of("0","1").contains(e.getValue())))return false;
   var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var path=dir.resolve(name).normalize();if(!path.startsWith(dir)||path.equals(dir)||!SOURCE.equals(com.pp.shaderlod.packs.SupportedPackCatalog.shaderSourceDigest(com.pp.shaderlod.integration.PackSourceReader.read(path).memberSha256())))return false;
   var options=pack.getShaderPackOptions();var set=options.getOptionSet();var e=set.getStringOptions().get(OPTION);if(e==null||!e.getOption().getDefaultValue().equals("0")||!java.util.Set.copyOf(e.getOption().getAllowedValues()).equals(java.util.Set.of("0","1")))return false;
   var values=options.getOptionValues().mutableCopy();var actual=new TreeMap<String,String>();set.getBooleanOptions().forEach((k,v)->actual.put(k,Boolean.toString(values.getBooleanValues().getOrDefault(k,v.getOption().getDefaultValue()))));set.getStringOptions().forEach((k,v)->actual.put(k,values.getStringValues().getOrDefault(k,v.getOption().getDefaultValue())));
   if(!profileMatch(actual))return false;
   if(!"solas-shader".equals(com.pp.shaderlod.packs.PackSelectionService.verifyGeneratedRendererGraph(path,actual)))return false;
   return !active||"1".equals(options.getOptionValues().getStringValueOrDefault(OPTION));
  }catch(Exception|LinkageError unavailable){return false;}
 }
 public static boolean invariantStage(String source,boolean vertex){
  if(source==null||!source.matches("(?s).*#version\\s+330\\b.*"))return false;
  String direction=vertex?"out":"in";
  for(String pair:new String[]{"int mat","vec2 texCoord","vec4 color"}){
   String declaration=pair.equals("int mat")?"invariant flat "+direction+" "+pair:"invariant "+direction+" "+pair;
   if(!source.replaceAll("\\s+"," ").contains(declaration+";"))return false;
  }
  if(vertex)return source.matches("(?s).*\\binvariant\\s+gl_Position\\s*;.*");
  if(!alphaLineage(source))return false;
  if(!com.pp.shaderlod.optimization.solasearlyalpha.SolasAlphaInterface.transformed(source,net.irisshaders.iris.gl.blending.AlphaTests.ONE_TENTH_ALPHA))return false;
  // Native selected profile has exactly early helper discard + actual late GREATER alpha; no hidden rejection.
  if(java.util.regex.Pattern.compile("\\bdiscard\\b").matcher(source).results().count()!=2)return false;
  return !source.contains("clrwl_")&&!source.matches("(?s).*\\b(gl_FragDepth|imageStore|imageAtomic\\w*|atomic\\w*)\\b.*")&&!source.matches("(?s).*\\b(texture\\w*|texelFetch)\\s*\\(\\s*(depthtex\\w*|colortex\\w*).*" );
 }
 private static boolean alphaLineage(String source){
  if(source.matches("(?s).*\\balbedo\\s*\\[.*"))return false;
  var assignments=java.util.regex.Pattern.compile("\\balbedo\\s*(?:\\.\\s*([a-z]+))?\\s*(\\*=|/=|\\+=|-=|=(?!=))\\s*([^;]*);").matcher(source);
  var whole=new java.util.ArrayList<String>();
  while(assignments.find()){
   if(assignments.group(1)!=null){if(!assignments.group(1).matches("[rgbxyz]+"))return false;}
   else whole.add((assignments.group(2)+assignments.group(3)).replaceAll("\\s+",""));
  }
  if(!whole.equals(java.util.List.of("=albedoTexture","*=color")))return false;
  String compact=source.replaceAll("\\s+","");
  return compact.contains("voidmain(){vec4albedoTexture=texture(gtexture,texCoord);vec4albedo=albedoTexture;albedo*=color;")&&
    compact.contains("iris_FragData0=albedo;")&&java.util.regex.Pattern.compile("\\biris_FragData0\\s*(?:\\.\\s*[a-z]+|\\[[^]]+\\])?\\s*(\\*=|/=|\\+=|-=|=(?!=))").matcher(source).results().count()==1;
 }
 public static boolean needsAdmission(String pass){return !admitted.contains(pass);}
 public static void admission(String pass,int nativeId,int depthId,int draws,State s){
  if(admitted.add(pass))System.out.println("SHADERLOD_SOLAS_ROCK_DEPTH_ADMISSION pass="+pass+" nativeProgram="+nativeId+" coverageProgram="+depthId+" draws="+draws+" fbo="+s.drawFbo+" depth="+s.depthObject+" restored=true mode=rock_subset_native_SOLID depthTest=true depthMask=true depthFunc=LEQUAL allMrtMasks=true allMrtBlend=false atlas="+s.textures[0]+" atlasSampler="+s.samplers[0]+" nativeAlphaRef="+s.alphaReference);
 }
 public static boolean requested(){return !disabled && FabricLodConfig.loaded() && FabricLodConfig.snapshot().enabled() && FabricLodConfig.snapshot().preset()!=LodPreset.QUALITY && com.pp.shaderlod.optimization.OptimizationConfig.snapshot().enabled(com.pp.shaderlod.optimization.OptimizationId.DEPTH_PRIMING) && !com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended();}
 public static boolean drawEnabled(){return requested() && net.minecraft.client.Minecraft.getInstance().level!=null && net.minecraft.client.Minecraft.getInstance().level.dimension()==net.minecraft.world.level.Level.OVERWORLD && candidatePack();}

 public static String fragment(boolean cutout){
  String name="/assets/shaderlod/solasprime/coverage_rock.frag";
  try(var in=SolasDepthPriming.class.getResourceAsStream(name)){
   if(in==null)throw new IllegalStateException("Missing pinned depth fragment "+name);
   byte[] bytes=in.readAllBytes();if(!sha(bytes).equals(cutout?CUTOUT_SHA:SOLID_SHA))throw new IllegalStateException("Depth fragment SHA mismatch");
   return new String(bytes,StandardCharsets.UTF_8);
  } catch(Exception e){throw new IllegalStateException("Pinned coverage source unavailable",e);}
 }
 /** Current GL state at native terrain, after native setup/matrices. Incompatible paths stay native. */
 public static final class State {
  public final int drawFbo;
  public final int depthObject;
  private final int readFbo,activeTexture;private float alphaReference;
  private final int[] viewport=new int[4],scissor=new int[4],textures=new int[2],samplers=new int[2];
  private final boolean scissorEnabled;
  private State(int draw,int read,int active,boolean enabled){drawFbo=draw;depthObject=GL30C.glGetFramebufferAttachmentParameteri(GL30C.GL_DRAW_FRAMEBUFFER,GL30C.GL_DEPTH_ATTACHMENT,GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);readFbo=read;activeTexture=active;scissorEnabled=enabled;}
  private static boolean compatible(){
   if(!GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST) || !GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK) || GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC)!=GL11C.GL_LEQUAL)return false;
   for(int cap:new int[]{GL11C.GL_STENCIL_TEST,GL11C.GL_POLYGON_OFFSET_FILL,GL13C.GL_SAMPLE_ALPHA_TO_COVERAGE,GL13C.GL_SAMPLE_COVERAGE,GL30C.GL_RASTERIZER_DISCARD})if(GL11C.glIsEnabled(cap))return false;
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
  public static State capture(int nativeProgram,boolean cutout){
   if(cutout)return null;
   int cutoff=GL20C.glGetUniformLocation(nativeProgram,"iris_currentAlphaTest");
   if(cutoff<0)return null;
   float nativeRef=GL20C.glGetUniformf(nativeProgram,cutoff);if(!Float.isFinite(nativeRef)||nativeRef<0.0f||nativeRef>1.0f)return null;
   int atlas=GL20C.glGetUniformLocation(nativeProgram,"gtexture");if(atlas<0||GL20C.glGetUniformi(nativeProgram,atlas)!=0)return null;
   int early=GL20C.glGetUniformLocation(nativeProgram,"shaderlodSolasEarlyAlphaPermission");
   if(early>=0 && GL20C.glGetUniformi(nativeProgram,early)!=0)return null; // SOLID native colour has no early-alpha lease.
   if(!compatible() || GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM)!=nativeProgram)return null;
   assertNoGlError("native before priming");
   int draw=GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
   if(draw==0 || GL30C.glGetFramebufferAttachmentParameteri(GL30C.GL_DRAW_FRAMEBUFFER,GL30C.GL_DEPTH_ATTACHMENT,GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)==0)return null;
   var s=new State(draw,GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING),GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE),GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST));
   s.alphaReference=nativeRef;
   GL11C.glGetIntegerv(GL11C.GL_VIEWPORT,s.viewport);GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX,s.scissor);
   // Exact coverage ABI has only tex/unit0; SodiumShader.bindTextures also touches lightmap/unit2.
   try{
   for(int i=0;i<2;i++){
    int unit=i*2;GlStateManager._activeTexture(GL13C.GL_TEXTURE0+unit);
    s.textures[i]=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);s.samplers[i]=GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
   }
   }finally{GlStateManager._activeTexture(s.activeTexture);}
   return s;
  }
  public boolean prepareAlpha(int coverageProgram){
   if(GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM)!=coverageProgram)return false;
   int alpha=GL20C.glGetUniformLocation(coverageProgram,"iris_currentAlphaTest"),atlas=GL20C.glGetUniformLocation(coverageProgram,"tex");
   if(alpha<0||atlas<0||GL20C.glGetUniformi(coverageProgram,atlas)!=0)return false;
   GL20C.glUniform1f(alpha,alphaReference);int active=GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
   try{GlStateManager._activeTexture(GL13C.GL_TEXTURE0);return GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D)==textures[0]&&GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING)==samplers[0];}
   finally{GlStateManager._activeTexture(active);}
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
   for(int i=0;i<2;i++){GlStateManager._activeTexture(GL13C.GL_TEXTURE0+i*2);
    if(GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D)!=textures[i] || GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING)!=samplers[i]){GlStateManager._activeTexture(activeTexture);throw new IllegalStateException("Depth R2 texture restoration failed");}
   }GlStateManager._activeTexture(activeTexture);
  }
  public void restoreBindingsAndRaster(){
   GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER,drawFbo);GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER,readFbo);
   for(int i=0;i<2;i++){IrisRenderSystem.bindTextureToUnit(GL11C.GL_TEXTURE_2D,i*2,textures[i]);IrisRenderSystem.bindSamplerToUnit(i*2,samplers[i]);}
   GlStateManager._activeTexture(activeTexture);
   GlStateManager._viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
   GlStateManager._scissorBox(scissor[0],scissor[1],scissor[2],scissor[3]);
   if(scissorEnabled)GlStateManager._enableScissorTest();else GlStateManager._disableScissorTest();
  }
 }
}
