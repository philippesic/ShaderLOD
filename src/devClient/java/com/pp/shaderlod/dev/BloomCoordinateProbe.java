package com.pp.shaderlod.dev;
import com.google.gson.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
/** Untimed native12 bloom coordinates. Raw output is owned disposable census input. */
public final class BloomCoordinateProbe {
 private BloomCoordinateProbe(){}
 private static String pendingDigest;
 private static int pendingPhase,selectedCounter;
 private static Path pendingOutput;
 private static JsonObject completed;
 private static JsonObject selectedSampler;
 private static boolean drawing;
 private static int selectedBank,selectedProgram,selectedDrawFbo;
 private static final int[] selectedTextures=new int[7];
 private static final float[] SHIFTS={.0625f,.4375f,.875f,.625f,.25f,.8125f,.125f,.9375f,.3125f,.5f,.375f,.5625f,.75f,.6875f,.1875f,0f};
 // Pure validator also used by cold fixtures. Every pixel must belong to this draw.
 public static void validateHeader(FloatBuffer data,int phase,int counter,float lod){
  if(counter<0||(counter&15)!=phase||!Float.isFinite(lod)||lod<=0||data.remaining()%4!=0||!data.hasRemaining())throw new IllegalStateException("Invalid draw header expectation");
  for(int i=data.position();i<data.limit();i+=4)
   if(data.get(i)!=phase||data.get(i+1)!=SHIFTS[phase]||Float.floatToIntBits(data.get(i+2))!=Float.floatToIntBits(lod)||data.get(i+3)!=counter)throw new IllegalStateException("Incomplete/stale viewport header at pixel "+i/4);
 }
 public static int checkedBank(int previous,int texture,int main,int alt){
  int bank=texture==main?0:texture==alt?1:-1;
  if(bank<0||(previous>=0&&previous!=bank))throw new IllegalStateException("Mixed/unknown diagnostic bank");return bank;
 }
 private static void bindDrawTargets()throws Exception{
  var pipeline=Iris.getPipelineManager().getPipelineNullable();
  if(!(pipeline instanceof IrisRenderingPipeline iris))throw new IllegalStateException("No Iris draw");
  var field=IrisRenderingPipeline.class.getDeclaredField("renderTargets");field.setAccessible(true);var targets=(RenderTargets)field.get(iris);
  selectedDrawFbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);selectedProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);selectedBank=-1;
  for(int i=0;i<7;i++){
   int attachment=GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+i+1);
   if(attachment<GL30.GL_COLOR_ATTACHMENT0||GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,attachment,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)!=GL11.GL_TEXTURE)throw new IllegalStateException("Missing diagnostic draw attachment");
   int tex=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,attachment,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);var target=targets.get(i+8);
   int bank=checkedBank(selectedBank,tex,target.getMainTexture(),target.getAltTexture());
   if(bank<0||(selectedBank>=0&&selectedBank!=bank))throw new IllegalStateException("Mixed/unknown diagnostic bank");
   selectedBank=bank;selectedTextures[i]=tex;
  }
  // Direct texture clear ignores viewport, scissor, color masks and framebuffer binding.
  // Poison every coordinate too: fresh header alone cannot certify a missing MRT write.
  float[] poison={Float.NaN,Float.NaN,Float.NaN,Float.NaN};
  for(int texture:selectedTextures)GL44.glClearTexImage(texture,0,GL11.GL_RGBA,GL11.GL_FLOAT,poison);
  GL42.glMemoryBarrier(GL42.GL_TEXTURE_UPDATE_BARRIER_BIT|GL42.GL_FRAMEBUFFER_BARRIER_BIT);
 }
 public static void beforeQuad(){
  if(pendingOutput==null||drawing)return;
  try{
   int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
   int phase=GL20.glGetUniformLocation(program,"frameMod"),counter=GL20.glGetUniformLocation(program,"frameCounter");
   int lod=GL20.glGetUniformLocation(program,"softLod"),image=GL20.glGetUniformLocation(program,"colortex0");
   if(phase<0||counter<0||lod<0||image<0||GL20.glGetUniformi(program,phase)!=pendingPhase)return;
   selectedCounter=GL20.glGetUniformi(program,counter);
   selectedSampler=com.pp.shaderlod.dev.timing.CloudSamplerStateProbe.bloomCoordinateState();
   float soft=GL20.glGetUniformf(program,lod);int unit=GL20.glGetUniformi(program,image),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);var levels=new JsonArray();
   try{GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);for(int level:new int[]{(int)Math.floor(soft),(int)Math.ceil(soft)}){var row=new JsonObject();row.addProperty("level",level);row.addProperty("width",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,level,GL11.GL_TEXTURE_WIDTH));row.addProperty("height",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,level,GL11.GL_TEXTURE_HEIGHT));levels.add(row);}}finally{GL13.glActiveTexture(active);}
   bindDrawTargets();selectedSampler.add("actual_sampled_mip_levels",levels);selectedSampler.addProperty("soft_lod",soft);selectedSampler.addProperty("frame_counter",selectedCounter);selectedSampler.addProperty("status","complete");drawing=true;
  }catch(Exception e){completed=new JsonObject();completed.addProperty("error",e.toString());pendingOutput=null;}
 }
 public static void afterQuad(){
  if(!drawing)return;
  try{if(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=selectedProgram||GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=selectedDrawFbo)throw new IllegalStateException("Draw boundary changed");GL11.glFinish();completed=readNow(pendingDigest,0,pendingPhase,pendingOutput);if(!completed.get("selected").getAsBoolean())throw new IllegalStateException("Exact draw header absent");completed.add("actual_sampler",selectedSampler);Files.writeString(pendingOutput.resolve("receipt.json"),new GsonBuilder().setPrettyPrinting().create().toJson(completed),StandardOpenOption.CREATE_NEW);}
  catch(Exception e){completed=new JsonObject();completed.addProperty("error",e.toString());}
  finally{drawing=false;pendingOutput=null;}
 }
 static JsonObject read(String digest,int group,int phase,Path output)throws Exception{
  if(!Boolean.getBoolean("shaderlod.dev.bloomCoordinateCensus")||group!=0||phase<0||phase>15)throw new IllegalStateException("Explicit native12 census only");
  if(completed!=null){var result=completed;completed=null;return result;}
  if(pendingOutput==null){pendingDigest=digest;pendingPhase=phase;pendingOutput=output;}
  else if(!pendingDigest.equals(digest)||pendingPhase!=phase)throw new IllegalStateException("Census request changed");
  var result=new JsonObject();result.addProperty("selected",false);return result;
 }
 static JsonObject samplerStatus(){var out=new JsonObject();out.addProperty("status",pendingOutput==null?"idle":"pending");return out;}
 private static void attach(int texture,int w,int h){
  GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
  if(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)!=w||GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)!=h||GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT)!=GL30.GL_RGBA32F)throw new IllegalStateException("Coordinate image shape/format mismatch");
  GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
  if(GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Coordinate read FBO incomplete");
 }
 private static JsonObject readNow(String digest,int wantedGroup,int wantedFrame,Path output)throws Exception{
  if(!Boolean.getBoolean("shaderlod.dev.bloomCoordinateCensus")||!"Linux".equals(System.getProperty("os.name"))||!"aarch64".equals(System.getProperty("os.arch"))||!"Apple M1 (G13G B1)".equals(GL11.glGetString(GL11.GL_RENDERER))||!"4.6 (Core Profile) Mesa 25.1.0-asahi20250221".equals(GL11.glGetString(GL11.GL_VERSION))||!com.pp.shaderlod.experiment.makeup.mixin.MakeupAlphaMixinPlugin.exactApi()||!Boolean.getBoolean("shaderlod.dev.fragmentWorkProbe")||wantedGroup<0||wantedGroup>0||wantedFrame<0||wantedFrame>15)throw new IllegalStateException("Bounded untimed coordinate request required");
  var c=Minecraft.getInstance();var pipeline=Iris.getPipelineManager().getPipelineNullable();
  if(!(pipeline instanceof IrisRenderingPipeline iris)||c.level==null||Iris.isFallback()||c.screen!=null||!BackgroundLaunch.qualityWindowReady(c)||!Iris.getShaderPackOptionQueue().isEmpty())throw new IllegalStateException("Settled native pipeline required");
  String name=Iris.getCurrentPackName();if(!name.matches("[A-Za-z0-9_.-]+\\.zip")||!digest.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Safe exact archive required");
  Path archive=Iris.getShaderpacksDirectory().resolve(name);if(Files.isSymbolicLink(archive)||!LoadedArtifactEvidence.fileSha256(archive).equals(digest))throw new IllegalStateException("Archive changed");
  JsonObject marker;try(var z=new ZipFile(archive.toFile())){var e=z.getEntry("SHADERLOD_BLOOM_COORDINATE_DIAGNOSTIC.json");if(e==null||e.getSize()<0||e.getSize()>8192)throw new IllegalStateException("Bounded coordinate marker absent");marker=JsonParser.parseString(new String(z.getInputStream(e).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
  if(!marker.get("diagnostic_only").getAsBoolean()||!"G10-native12-coordinates".equals(marker.get("kind").getAsString())||!"0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8".equals(marker.get("native").getAsString()))throw new IllegalStateException("Unpinned coordinate diagnostic");
  var pack=Iris.getCurrentPack().orElseThrow();var opts=pack.getShaderPackOptions();if(!"3.0".equals(opts.getOptionValues().getStringValueOrDefault("BLOOM_SAMPLES")))throw new IllegalStateException("Actual native12tap selection required");
  var f=IrisRenderingPipeline.class.getDeclaredField("renderTargets");f.setAccessible(true);var targets=(RenderTargets)f.get(iris);int w=c.getWindow().getWidth(),h=c.getWindow().getHeight();if(w<1||h<1||w>4096||h>4096)throw new IllegalStateException("Bounded extent required");
  for(int t=8;t<=14;t++)if(targets.get(t)==null)throw new IllegalStateException("Coordinate target absent");
  var out=new JsonObject();out.addProperty("selected",false);out.addProperty("width",w);out.addProperty("height",h);out.addProperty("archive_sha256",digest);out.add("diagnostic",marker);int frame=SystemTimeUniforms.COUNTER.getAsInt();
  int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),buffer=GL11.glGetInteger(GL11.GL_READ_BUFFER),texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),pbo=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
  int[] keys={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST};int[] prior=Arrays.stream(keys).map(GL11::glGetInteger).toArray();int scratch=GL30.glGenFramebuffers();
  try{
   GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);for(int key:keys)GL11.glPixelStorei(key,key==GL11.GL_PACK_ALIGNMENT?4:0);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,scratch);
   int bank=selectedBank;float[] header={wantedFrame,SHIFTS[wantedFrame],selectedSampler.get("soft_lod").getAsFloat(),selectedCounter};
   if(bank>=0){
    long needed=(long)w*h*16*7;if(Files.getFileStore(output.getParent()).getUsableSpace()<32L*1024*1024*1024+needed)throw new IllegalStateException("Coordinate raw storage reserve required");Files.createDirectory(output);
    out.addProperty("selected_program",selectedProgram);out.addProperty("draw_framebuffer",selectedDrawFbo);out.add("attached_textures",new Gson().toJsonTree(selectedTextures));out.addProperty("draw_completed",true);out.addProperty("full_viewport_header",true);out.addProperty("all_targets_poisoned_before_draw",true);out.addProperty("selected",true);out.addProperty("bank",bank==0?"main":"alt");out.addProperty("frame_mod",header[0]);out.addProperty("dither_shift",header[1]);out.addProperty("soft_lod",header[2]);out.addProperty("group",wantedGroup);out.addProperty("start_tap",0);out.addProperty("frame_counter",selectedCounter);out.addProperty("byte_order",ByteOrder.nativeOrder().toString());out.addProperty("raw_bytes",needed);
    var files=new JsonArray();var bytes=MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(w,h),16)).order(ByteOrder.nativeOrder());
    try{for(int t=8;t<=14;t++){attach(selectedTextures[t-8],w,h);bytes.clear();var floats=bytes.asFloatBuffer();if(floats.remaining()!=Math.multiplyExact(Math.multiplyExact(w,h),4))throw new IllegalStateException("Coordinate native buffer extent changed");GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_FLOAT,floats);
     if(t==8)validateHeader(floats,wantedFrame,selectedCounter,header[2]);
     else for(int i=0;i<floats.limit();i++)if(!Float.isFinite(floats.get(i)))throw new IllegalStateException("Incomplete coordinate target "+t+" component "+i);
     Path path=output.resolve("rt"+t+".f32");bytes.position(0);try(var channel=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){while(bytes.hasRemaining())channel.write(bytes);}var row=new JsonObject();row.addProperty("target",t);row.addProperty("path",path.toString());row.addProperty("sha256",LoadedArtifactEvidence.fileSha256(path));files.add(row);}}
    finally{MemoryUtil.memFree(bytes);}out.add("files",files);
    var sampling=new JsonObject();var image=targets.get(0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,image.getMainTexture());
    for(int key:new int[]{GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_TEXTURE_WRAP_S,GL11.GL_TEXTURE_WRAP_T,GL12.GL_TEXTURE_BASE_LEVEL,GL12.GL_TEXTURE_MAX_LEVEL})sampling.addProperty(Integer.toString(key),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,key));
    for(int key:new int[]{GL12.GL_TEXTURE_MIN_LOD,GL12.GL_TEXTURE_MAX_LOD,GL14.GL_TEXTURE_LOD_BIAS})sampling.addProperty(Integer.toString(key),GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,key));
    var levels=new JsonArray();for(int level:new int[]{(int)Math.floor(header[2]),(int)Math.ceil(header[2])}){var row=new JsonObject();row.addProperty("level",level);row.addProperty("width",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,level,GL11.GL_TEXTURE_WIDTH));row.addProperty("height",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,level,GL11.GL_TEXTURE_HEIGHT));levels.add(row);}sampling.add("levels",levels);out.add("colortex0_texture_parameters",sampling);
    

   }
  }finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL11.glReadBuffer(buffer);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo);for(int i=0;i<keys.length;i++)GL11.glPixelStorei(keys[i],prior[i]);GL30.glDeleteFramebuffers(scratch);}
  if(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)!=read||GL11.glGetInteger(GL11.GL_READ_BUFFER)!=buffer||GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=texture||GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)!=pbo||SystemTimeUniforms.COUNTER.getAsInt()!=frame||Iris.getCurrentPack().orElse(null)!=pack||Iris.getPipelineManager().getPipelineNullable()!=pipeline)throw new IllegalStateException("Coordinate readback context changed");for(int i=0;i<keys.length;i++)if(GL11.glGetInteger(keys[i])!=prior[i])throw new IllegalStateException("Pixelstore restore failed");out.addProperty("state_restored",true);return out;
 }
}
