package com.pp.shaderlod.experiment.depth;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.GlStateManager;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.ShaderLodConfig;
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
import net.neoforged.fml.ModList;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;
/** Private current-frame subset; no targets/history/profiler/cross-frame list state. */
public final class DepthPrimingR2 {
 public static final String ARCHIVE_SHA="2222b3d24e982f3b0f3a26743a3046694d1c92301140e4466f3d0daebc5a80aa";
 public static final String OPTIONS_SHA="b64d94998c99bf867e80d350a4a0a8108c58507e0401eb053a70e3aca81a5036";
 private static final String SOLID_SHA="bf450e09a42b1dfb8bd4d7f7e27a89d4bd906a4c78427ad06a47e3637a048c6a";
 private static final String CUTOUT_SHA="788822057a31b0f74933b40b310b06c3d5d20d9fa3193afc382c69d3afb3d645";
 private static Object certifiedPack;
 private static String certifiedName;
 private static Map<String,String> expected;
 private static int sampledFrame=Integer.MIN_VALUE;
 private static boolean sampledMatch;
 private static final java.util.Set<String> admitted=new java.util.HashSet<>();
 private DepthPrimingR2(){}
 private static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 public static boolean candidatePack(){
  if(!Boolean.getBoolean("shaderlod.dev.unboundDepthPrimingR2") || ARCHIVE_SHA.length()!=64 || Iris.isFallback())return false;
  try {
   if(!SharedConstants.getCurrentVersion().getName().equals("1.21.1") || !ModList.get().isLoaded("shaderlod") ||
     !ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString().equals("21.1.253"))return false;
   for(String id:new String[]{"distanthorizons","voxy","create","flywheel"})if(ModList.get().isLoaded(id))return false;
   var pack=Iris.getCurrentPack().orElseThrow();String name=Iris.getCurrentPackName();
   if(pack!=certifiedPack || !name.equals(certifiedName)) {
    certifiedPack=null;certifiedName=null;sampledFrame=Integer.MIN_VALUE;admitted.clear();
    var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var path=dir.resolve(name).normalize();
    if(!path.startsWith(dir) || path.equals(dir) || !ARCHIVE_SHA.equals(sha(Files.readAllBytes(path))))return false;
    var bytes=Files.readAllBytes(Path.of(System.getProperty("shaderlod.dev.unboundDepthOptionsPin")));
    if(!OPTIONS_SHA.equals(sha(bytes)))return false;
    expected=new Gson().fromJson(new String(bytes,StandardCharsets.UTF_8),new TypeToken<Map<String,String>>(){}.getType());
    if(expected==null || expected.size()!=398)return false;
    certifiedPack=pack;certifiedName=name;
   }
   int frame=net.irisshaders.iris.uniforms.SystemTimeUniforms.COUNTER.getAsInt();
   if(frame==sampledFrame)return sampledMatch;
   var options=pack.getShaderPackOptions();var declared=options.getOptionSet();var values=options.getOptionValues();
   var actual=new TreeMap<String,String>();
   for(String key:declared.getBooleanOptions().keySet())actual.put(key,Boolean.toString(values.getBooleanValueOrDefault(key)));
   for(String key:declared.getStringOptions().keySet())actual.put(key,values.getStringValueOrDefault(key));
   sampledFrame=frame;return sampledMatch=actual.equals(expected);
  } catch(Exception|LinkageError unavailable){return false;}
 }
 public static boolean invariantStage(String source,boolean vertex){
  if(!source.matches("(?s).*#version\\s+330\\b.*"))return false;
  String direction=vertex?"out":"in";
  for(String name:new String[]{"texCoord","mat"}){
   var matcher=java.util.regex.Pattern.compile("([^;{}]*\\b"+name+"\\s*;)").matcher(source);boolean found=false;
   while(matcher.find()){String declaration=matcher.group(1);
    if(declaration.matches("(?s).*\\binvariant\\b.*") && declaration.matches("(?s).*\\b"+direction+"\\b.*") && declaration.matches("(?s).*\\b"+(name.equals("mat")?"int":"vec2")+"\\b.*") && (!name.equals("mat") || declaration.matches("(?s).*\\bflat\\b.*")))found=true;
   }
   if(!found)return false;
  }
  return !vertex || source.matches("(?s).*\\binvariant\\s+gl_Position\\s*;.*");
 }
 public static boolean needsAdmission(String pass){return !admitted.contains(pass);}
 public static void admission(String pass,int nativeId,int depthId,int draws,State s){
  if(admitted.add(pass))System.out.println("SHADERLOD_DEPTH_R2_ADMISSION pass="+pass+" nativeProgram="+nativeId+" coverageProgram="+depthId+" draws="+draws+" fbo="+s.drawFbo+" depth="+s.depthObject+" restored=true mode=whole_current_pass depthTest=true depthMask=true depthFunc=LEQUAL allMrtMasks=true allMrtBlend=false atlas="+s.textures[0]+" atlasSampler="+s.samplers[0]);
 }
 public static boolean drawEnabled(){
  return net.minecraft.client.Minecraft.getInstance().level!=null && net.minecraft.client.Minecraft.getInstance().level.dimension()==net.minecraft.world.level.Level.OVERWORLD && candidatePack() && ShaderLodConfig.SPEC.isLoaded() && ShaderLodConfig.snapshot().enabled() && ShaderLodConfig.PRESET.get()==LodPreset.PERFORMANCE;
 }
 public static String fragment(boolean cutout){
  String name="/shaderlod_depth_r2/coverage_"+(cutout?"cutout":"solid")+".frag";
  try(var in=DepthPrimingR2.class.getResourceAsStream(name)){
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
  public static State capture(int nativeProgram){
   if(!compatible() || GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM)!=nativeProgram)return null;
   int draw=GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
   if(draw==0 || GL30C.glGetFramebufferAttachmentParameteri(GL30C.GL_DRAW_FRAMEBUFFER,GL30C.GL_DEPTH_ATTACHMENT,GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)==0)return null;
   var s=new State(draw,GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING),GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE),GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST));
   GL11C.glGetIntegerv(GL11C.GL_VIEWPORT,s.viewport);GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX,s.scissor);
   // Exact coverage ABI has only tex/unit0; SodiumShader.bindTextures also touches lightmap/unit2.
   for(int i=0;i<2;i++){
    int unit=i*2;GlStateManager._activeTexture(GL13C.GL_TEXTURE0+unit);
    s.textures[i]=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);s.samplers[i]=GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING,unit);
   }
   GlStateManager._activeTexture(s.activeTexture);return s;
  }
  public boolean beginCoverage(){
   if(!compatible() || GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING)!=drawFbo)return false;
   // No cache-owning mask/depth setter is called inside the pure loop. Restore before native callbacks.
   GL11C.glColorMask(false,false,false,false);return true;
  }
  public void endCoverage(){GL11C.glColorMask(true,true,true,true);}
  public void assertRestored(int program){
   if(!compatible() || GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM)!=program || GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING)!=drawFbo || GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING)!=readFbo || GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE)!=activeTexture || GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST)!=scissorEnabled)throw new IllegalStateException("Depth R2 native GL restoration failed");
   int[] v=new int[4],c=new int[4];GL11C.glGetIntegerv(GL11C.GL_VIEWPORT,v);GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX,c);
   if(!java.util.Arrays.equals(v,viewport) || !java.util.Arrays.equals(c,scissor))throw new IllegalStateException("Depth R2 native raster restoration failed");
   for(int i=0;i<2;i++){GlStateManager._activeTexture(GL13C.GL_TEXTURE0+i*2);
    if(GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D)!=textures[i] || GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING,i*2)!=samplers[i]){GlStateManager._activeTexture(activeTexture);throw new IllegalStateException("Depth R2 texture restoration failed");}
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
