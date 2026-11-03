package com.pp.shaderlod.experiment.hierarchy;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.opengl.*;

/** Private build-cost gate: one conservative 128x128 depth min/max level after the authentic copy. */
public final class DepthHierarchyR50 {
 private DepthHierarchyR50() {}
 private static Object pack,pipeline;
 private static int program,vao,fbo,image,scratchA,scratchB,width,height,copiedDepth,copiedFrame=-1;
 private static long builds,allocations,positivePermissions,falsePermissions;
 // Private R3 cost admission is absent by default; legacy/public behavior stays unchanged.
 private static boolean costEnabled;
 private static boolean costOptIn(){return Boolean.getBoolean("shaderlod.dev.depthHierarchyCostAsahiR3");}
 private static boolean producerEnabled(){return !costOptIn()||costEnabled;}
 public static JsonObject costControl(boolean enabled){
  com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
  if(!costOptIn()||!lowerOnly()||!Boolean.getBoolean("shaderlod.dev.depthHierarchyR50")||!failure.isEmpty()||!qualified())throw new IllegalStateException("Exact private idle cost control required");
  // Always invalidate, even on repeated requests. Enable only re-arms the next authentic copy.
  costEnabled=false;destroy(pipeline);costEnabled=enabled;
  last=enabled?"armed; fresh authentic copy required":"producer disabled; no current resources";
  return status();
 }
 private static int boundProgram,boundDepth,boundFrame=-1,boundUnit=-1,boundSampler=-1;
 private static String failure="",last="idle";
 private static int[] timer;
 private static boolean timedBuild,timedWater;
 private static final Map<String,String> CONSUMERS=Map.of("29450048817b419d3c3a1eda0924defb0b92fcfd8338c47680b1eed699d38bdc","clone","f85934a4032f9f5cd9cd349dd56c83d70460a62324b3ad2297bcc1584dc1e792","forcedfalse","31934e1f2f7bc26a5509e6640d1219d793d8555d81405db25dd6916d4634d16b","skip","e81cd4fa78d183bbf05cdfd93056d2f12b62ae3e1d035edbbc3697866261dfb3","skip");
 private static final Map<String,String> ASAHI=Map.of("dfb7c9b67b5a98b118933adc7026eaf7be898f1d1f032efdaa327644c9180661","forcedfalse","dcab0b4c100211d4c3b8af7f0eb6a05cda5fe538743ddedc90bfacc5aaa4239d","skip","ae06fc644370c8faf264df81b08db404a06c43b64f19c6616d7303023a8464d1","forcedfalse","903b63b093eb1401cf24a3ffe33c9e638d66fdf3da5b0580c9333ad3846863e0","skip");
 private static final Map<String,String> FIXED16=Map.of("fc3c1863905dfe74f89dbb3ba6b98afce8ca910fe48cf017874d623e6082ae79","forcedfalse","ee6d3d2c47457b3011200c5837d69a2f2f892a7d8acc980fe855eb8dae33224a","skip","13809a5d57aa760abeccdddd7e03d95b9bd3dbf446462c06820542556486bc3d","forcedfalse","545bec198f2f79a2337bb6853a4dbd5447a1380153c2a78fdffa18792d82b375","skip");
 private static boolean fixed16(){return Boolean.getBoolean("shaderlod.dev.depthHierarchyFixed16AsahiR1");}
 private static int cellSize(){return fixed16()?16:128;}
 private static Map<String,String> asahiConsumers(){return fixed16()?FIXED16:ASAHI;}
 private static boolean lowerOnly(){return Boolean.getBoolean("shaderlod.dev.depthHierarchyLowerAsahiR2");}
 private static String control="native";
 private static final String NATIVE="0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8";
 private static boolean qualified(){
  if(fixed16()&&(!lowerOnly()||!costOptIn()||!Boolean.getBoolean("shaderlod.dev.depthHierarchyParallelR70")))return false;
  if(lowerOnly()){
   if(!Boolean.getBoolean("shaderlod.dev.depthHierarchyParallelR70")||!com.pp.shaderlod.experiment.makeup.mixin.MakeupAlphaMixinPlugin.exactApi()
    ||!"Linux".equals(System.getProperty("os.name"))||!"aarch64".equals(System.getProperty("os.arch"))
    ||!"Apple M1 (G13G B1)".equals(GL11.glGetString(GL11.GL_RENDERER))
    ||!"4.6 (Core Profile) Mesa 25.1.0-asahi20250221".equals(GL11.glGetString(GL11.GL_VERSION)))return false;
   var mods=net.neoforged.fml.ModList.get();
   if(!mods.getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString().equals("21.1.253"))return false;
   for(String id:List.of("distanthorizons","voxy"))if(mods.isLoaded(id))return false;
  }
  var current=Iris.getCurrentPack().orElse(null);if(current==null||Iris.isFallback())return false;
  if(current!=pack){
   var file=Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName());String digest;
   try {digest=com.pp.shaderlod.dev.LoadedArtifactEvidence.fileSha256(file);if(Files.isSymbolicLink(file)||(lowerOnly()?!(digest.equals(NATIVE)||asahiConsumers().containsKey(digest)):!Set.of(NATIVE,"29450048817b419d3c3a1eda0924defb0b92fcfd8338c47680b1eed699d38bdc","f85934a4032f9f5cd9cd349dd56c83d70460a62324b3ad2297bcc1584dc1e792","31934e1f2f7bc26a5509e6640d1219d793d8555d81405db25dd6916d4634d16b","e81cd4fa78d183bbf05cdfd93056d2f12b62ae3e1d035edbbc3697866261dfb3").contains(digest)))return false;}
   catch(Exception e){return false;}
   var expected=new Gson().fromJson(new String(readOptions(),java.nio.charset.StandardCharsets.UTF_8),JsonObject.class);
   var options=current.getShaderPackOptions();var actual=new JsonObject();
   options.getOptionSet().getBooleanOptions().keySet().forEach(k->actual.addProperty(k,Boolean.toString(options.getOptionValues().getBooleanValueOrDefault(k))));
   options.getOptionSet().getStringOptions().keySet().forEach(k->actual.addProperty(k,options.getOptionValues().getStringValueOrDefault(k)));
   control=(lowerOnly()?asahiConsumers():CONSUMERS).getOrDefault(digest,"native");if(!control.equals("native"))expected.addProperty("SHADERLOD_G05_DEPTH_HIERARCHY","1");
   if(!expected.equals(actual))return false;pack=current;
  }
  var c=net.minecraft.client.Minecraft.getInstance();return c.level!=null&&c.level.dimension()==net.minecraft.world.level.Level.OVERWORLD&&Iris.getShaderPackOptionQueue().isEmpty();
 }
 private static byte[] readOptions(){try(var in=DepthHierarchyR50.class.getResourceAsStream("/depth-hierarchy-native100.json")){return in.readAllBytes();}catch(Exception e){throw new IllegalStateException(e);}}
 private static int shader(int type,String source){int id=GL20.glCreateShader(type);GL20.glShaderSource(id,source);GL20.glCompileShader(id);if(GL20.glGetShaderi(id,GL20.GL_COMPILE_STATUS)==0){String error=GL20.glGetShaderInfoLog(id);GL20.glDeleteShader(id);throw new IllegalStateException(error);}return id;}
 private static void create(int w,int h){
  if(program==0){int v=0,f=0,p=0;try{
   v=shader(GL20.GL_VERTEX_SHADER,"#version 330 core\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0.0,1.0);}");
   f=shader(GL20.GL_FRAGMENT_SHADER,Boolean.getBoolean("shaderlod.dev.depthHierarchyParallelR70")?"""
    #version 330 core
    uniform sampler2D nativeDepth;
    uniform ivec2 logicalSize;
    uniform int factor,rawDepth,lowerOnly;
float conservativeLower(float d) {
    uint b=floatBitsToUint(d);
    if(b>0x3f800000u || b<0x38800000u) return 0.0;
    return uintBitsToFloat(b & 0xffffe000u);
}

    layout(location=0) out vec2 bounds;
    void main(){
      ivec2 origin=ivec2(gl_FragCoord.xy)*factor;
      float lo=1.0,hi=0.0;bool invalid=false;
      for(int y=0;y<4;y++)for(int x=0;x<4;x++)if(x<factor&&y<factor){
        ivec2 p=origin+ivec2(x,y);
        if(p.x<logicalSize.x&&p.y<logicalSize.y){
          vec2 d=texelFetch(nativeDepth,p,0).rg;if(rawDepth!=0)d=vec2(d.r);
          if(any(isnan(d))||any(isinf(d))||d.x<0.0||d.y>1.0||d.x>d.y)invalid=true;
          else{lo=min(lo,d.x);hi=max(hi,d.y);}
        }
      }
      bounds=lowerOnly!=0?vec2(invalid?0.0:conservativeLower(lo),1.0):(invalid?vec2(0.0,1.0):vec2(lo,hi));
    }
    """:"""
    #version 330 core
    uniform sampler2D nativeDepth;
    layout(location=0) out vec2 bounds;
    void main(){
      ivec2 size=textureSize(nativeDepth,0),origin=ivec2(gl_FragCoord.xy)*128;
      float lo=1.0,hi=0.0;bool invalid=false;
      for(int y=0;y<128;y++)for(int x=0;x<128;x++){
        ivec2 p=origin+ivec2(x,y);
        if(p.x<size.x&&p.y<size.y){float d=texelFetch(nativeDepth,p,0).r;if(isnan(d)||isinf(d)||d<0.0||d>1.0)invalid=true;else{lo=min(lo,d);hi=max(hi,d);}}
      }
      bounds=invalid?vec2(0.0,1.0):vec2(lo,hi);
    }
    """);
   p=GL20.glCreateProgram();GL20.glAttachShader(p,v);GL20.glAttachShader(p,f);GL20.glLinkProgram(p);if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(p));program=p;p=0;vao=GL30.glGenVertexArrays();fbo=GL30.glGenFramebuffers();
  }finally{if(v!=0)GL20.glDeleteShader(v);if(f!=0)GL20.glDeleteShader(f);if(p!=0)GL20.glDeleteProgram(p);}}
  if(w!=width||h!=height||image==0){
   for(int id:new int[]{image,scratchA,scratchB})if(id!=0)GL11.glDeleteTextures(id);
   image=texture((w+cellSize()-1)/cellSize(),(h+cellSize()-1)/cellSize());scratchA=scratchB=0;
   if(Boolean.getBoolean("shaderlod.dev.depthHierarchyParallelR70")){scratchA=texture((w+3)/4,(h+3)/4);if(!fixed16())scratchB=texture((w+15)/16,(h+15)/16);}
   width=w;height=h;
  }
 }
 private static int texture(int w,int h){allocations++;int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,lowerOnly()?GL30.GL_RG16F:GL30.GL_RG32F,w,h,0,GL30.GL_RG,GL11.GL_FLOAT,(java.nio.ByteBuffer)null);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);if(fixed16()){GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL,0);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LEVEL,0);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL14.GL_TEXTURE_COMPARE_MODE,GL11.GL_NONE);if(GL.getCapabilities().GL_EXT_texture_filter_anisotropic||GL.getCapabilities().GL_ARB_texture_filter_anisotropic)GL11.glTexParameterf(GL11.GL_TEXTURE_2D,0x84FE,1.0f);}return id;}
 public static void copied(RenderTargets targets){
  if(costOptIn()&&(targets.getCurrentWidth()!=2560||targets.getCurrentHeight()!=1600))return;
  if(!producerEnabled()||!Boolean.getBoolean("shaderlod.dev.depthHierarchyR50")||!failure.isEmpty()||!qualified())return;
  var current=Iris.getPipelineManager().getPipelineNullable();if(pipeline!=null&&pipeline!=current){destroy(pipeline);if(!qualified())return;}pipeline=current;
  var state=new State();
  try{
   if(fixed16()){copiedDepth=0;copiedFrame=-1;}
   GL13.glActiveTexture(GL13.GL_TEXTURE0);create(targets.getCurrentWidth(),targets.getCurrentHeight());
   GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,image,0);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Hierarchy framebuffer incomplete");
   state.disable();GL11.glViewport(0,0,(width+cellSize()-1)/cellSize(),(height+cellSize()-1)/cellSize());GL30.glBindVertexArray(vao);GL20.glUseProgram(program);GL20.glUniform1i(GL20.glGetUniformLocation(program,"lowerOnly"),lowerOnly()?1:0);GL20.glUniform1i(GL20.glGetUniformLocation(program,"nativeDepth"),0);
   GL11.glBindTexture(GL11.GL_TEXTURE_2D,targets.getDepthTextureNoTranslucents().getTextureId());GL33.glBindSampler(0,0);
   if(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)!=width||GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)!=height||GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL)!=0||GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL14.GL_TEXTURE_COMPARE_MODE)!=GL11.GL_NONE)throw new IllegalStateException("Copied native depth layout unsupported");
   boolean timing=timer!=null&&!timedBuild;if(timing)GL33.glQueryCounter(timer[0],GL33.GL_TIMESTAMP);
   if(scratchA!=0){
    int source=targets.getDepthTextureNoTranslucents().getTextureId(),sw=width,sh=height;
    for(int step=0;step<(fixed16()?2:4);step++){
     int factor=!fixed16()&&step==3?2:4,w=(sw+factor-1)/factor,h=(sh+factor-1)/factor,destination=fixed16()?(step==0?scratchA:image):(step==3?image:step==1?scratchB:scratchA);
     GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,destination,0);if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Reduction framebuffer incomplete");
     GL11.glViewport(0,0,w,h);GL11.glBindTexture(GL11.GL_TEXTURE_2D,source);GL20.glUniform2i(GL20.glGetUniformLocation(program,"logicalSize"),sw,sh);GL20.glUniform1i(GL20.glGetUniformLocation(program,"factor"),factor);GL20.glUniform1i(GL20.glGetUniformLocation(program,"rawDepth"),step==0?1:0);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);source=destination;sw=w;sh=h;
    }
   }else GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
   if(timing){GL33.glQueryCounter(timer[1],GL33.GL_TIMESTAMP);timedBuild=true;}copiedDepth=targets.getDepthTextureNoTranslucents().getTextureId();copiedFrame=net.irisshaders.iris.uniforms.SystemTimeUniforms.COUNTER.getAsInt();builds++;last=fixed16()?"one consumed16level; exact4/4 reductions with one scratch texture":scratchA!=0?"one consumed128level; exact4/4/4/2 reductions with two scratch textures":"built after authentic depthtex1 copy; native consumers unchanged";
  }catch(RuntimeException e){failure=e.toString();if(!fixed16())throw e;copiedDepth=0;copiedFrame=-1;}finally{state.restore();}
 }
 public static void destroy(Object owner){if(pipeline!=null&&pipeline!=owner)return;unbindWater();clearTimer();safeUnits.clear();for(int id:new int[]{image,scratchA,scratchB})if(id!=0)GL11.glDeleteTextures(id);if(fbo!=0)GL30.glDeleteFramebuffers(fbo);if(vao!=0)GL30.glDeleteVertexArrays(vao);if(program!=0)GL20.glDeleteProgram(program);image=scratchA=scratchB=fbo=vao=program=width=height=0;pack=pipeline=null;copiedDepth=boundProgram=boundDepth=0;copiedFrame=boundFrame=boundUnit=boundSampler=-1;control="native";}
 private static void clearTimer(){if(timer!=null){for(int q:timer)GL15.glDeleteQueries(q);timer=null;}timedBuild=timedWater=false;}
 public static void armTimer(){if(!Boolean.getBoolean("shaderlod.dev.depthHierarchyProbe")||!qualified())throw new IllegalStateException("Explicit pinned diagnostic timer required");clearTimer();timer=new int[]{GL15.glGenQueries(),GL15.glGenQueries(),GL15.glGenQueries(),GL15.glGenQueries()};}
 public static void waterBegin(net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass pass){if(timer!=null&&!timedWater&&pass==net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.TRANSLUCENT&&!net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()&&qualified())GL33.glQueryCounter(timer[2],GL33.GL_TIMESTAMP);}
 public static void waterEnd(net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass pass){if(timer!=null&&!timedWater&&pass==net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.TRANSLUCENT&&!net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()&&qualified()){GL33.glQueryCounter(timer[3],GL33.GL_TIMESTAMP);timedWater=true;}}
 public static JsonObject timerResult(){var out=new JsonObject();boolean build=Boolean.getBoolean("shaderlod.dev.depthHierarchyR50");boolean ready=timer!=null&&timedWater&&(!build||timedBuild)&&GL15.glGetQueryObjecti(timer[3],GL15.GL_QUERY_RESULT_AVAILABLE)!=0&&(!build||GL15.glGetQueryObjecti(timer[1],GL15.GL_QUERY_RESULT_AVAILABLE)!=0);out.addProperty("ready",ready);out.addProperty("timestamp_counter_bits",GL15.glGetQueryi(GL33.GL_TIMESTAMP,GL15.GL_QUERY_COUNTER_BITS));if(ready){out.addProperty("native_entire_translucent_pass_ns",GL33.glGetQueryObjectui64(timer[3],GL15.GL_QUERY_RESULT)-GL33.glGetQueryObjectui64(timer[2],GL15.GL_QUERY_RESULT));if(build)out.addProperty("hierarchy_draw_gpu_ns",GL33.glGetQueryObjectui64(timer[1],GL15.GL_QUERY_RESULT)-GL33.glGetQueryObjectui64(timer[0],GL15.GL_QUERY_RESULT));out.addProperty("scope","Untimed diagnostic timestamps; entire native translucent pass is an intentionally loose upper bound on all removable SSR fragment work. Hierarchy draw excludes CPU guards/state/setup and first allocation/compile.");clearTimer();}return out;}
 private static Binding binding;
 private static final Set<Integer> safeUnits=new HashSet<>();
 private record Binding(int program,int location,int texture,int sampler){}
 public static void bindWater(net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass pass){
  if(!producerEnabled()||!failure.isEmpty()||!Boolean.getBoolean("shaderlod.dev.depthHierarchyR50")||pass!=net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.TRANSLUCENT||net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()||!qualified()||control.equals("native")||image==0)return;
  if(binding!=null)throw new IllegalStateException("Nested hierarchy consumer");
  int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),permission=GL20.glGetUniformLocation(p,"shaderlodHierarchyPermission"),samplerLocation=GL20.glGetUniformLocation(p,"shaderlodDepthHierarchy"),size=GL20.glGetUniformLocation(p,"shaderlodHierarchySize"),depth=GL20.glGetUniformLocation(p,"depthtex1");
  if(permission<0||samplerLocation<0||size<0||depth<0)return;
  GL20.glUniform1i(permission,0);
  var c=net.minecraft.client.Minecraft.getInstance();if(copiedFrame!=net.irisshaders.iris.uniforms.SystemTimeUniforms.COUNTER.getAsInt()||pipeline!=Iris.getPipelineManager().getPipelineNullable()||width!=c.getWindow().getWidth()||height!=c.getWindow().getHeight())return;
  int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),unit=GL20.glGetUniformi(p,depth);if(unit<0||unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)||unit==15)return;
  try{
   GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);int sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);if(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=copiedDepth||GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL)!=0)return;
   for(int key:new int[]{GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_TEXTURE_MAG_FILTER})if((sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,key):GL33.glGetSamplerParameteri(sampler,key))!=GL11.GL_NEAREST)return;
   for(int key:new int[]{GL11.GL_TEXTURE_WRAP_S,GL11.GL_TEXTURE_WRAP_T})if((sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,key):GL33.glGetSamplerParameteri(sampler,key))!=GL12.GL_CLAMP_TO_EDGE)return;
   if((sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL14.GL_TEXTURE_COMPARE_MODE):GL33.glGetSamplerParameteri(sampler,GL14.GL_TEXTURE_COMPARE_MODE))!=GL11.GL_NONE)return;
   if(GL.getCapabilities().GL_EXT_texture_filter_anisotropic||GL.getCapabilities().GL_ARB_texture_filter_anisotropic)if((sampler==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,0x84FE):GL33.glGetSamplerParameterf(sampler,0x84FE))!=1.0f)return;
   if(fixed16()&&(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)!=width||GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)!=height))return;
   if(!safeUnits.contains(p)){int uniforms=GL20.glGetProgrami(p,GL20.GL_ACTIVE_UNIFORMS);try(var stack=org.lwjgl.system.MemoryStack.stackPush()){var count=stack.mallocInt(1);var type=stack.mallocInt(1);for(int i=0;i<uniforms;i++){String name=GL20.glGetActiveUniform(p,i,count,type);int t=type.get(0);if((t==GL20.GL_SAMPLER_2D||t==GL20.GL_SAMPLER_3D||t==GL20.GL_SAMPLER_CUBE||t==GL20.GL_SAMPLER_2D_SHADOW)&&!name.equals("shaderlodDepthHierarchy")&&GL20.glGetUniformi(p,GL20.glGetUniformLocation(p,name))==15)return;}}safeUnits.add(p);}
   GL13.glActiveTexture(GL13.GL_TEXTURE0+15);binding=new Binding(p,permission,GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),GL11.glGetInteger(GL33.GL_SAMPLER_BINDING));GL11.glBindTexture(GL11.GL_TEXTURE_2D,image);if(fixed16()&&(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)!=(width+15)/16||GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)!=(height+15)/16)){unbindWater();return;}GL33.glBindSampler(15,0);GL20.glUniform1i(samplerLocation,15);GL20.glUniform2f(size,width,height);GL20.glUniform1i(permission,control.equals("forcedfalse")?0:1);if(control.equals("forcedfalse"))falsePermissions++;else positivePermissions++;boundProgram=p;boundDepth=copiedDepth;boundFrame=copiedFrame;boundUnit=unit;boundSampler=sampler;
  }finally{GL13.glActiveTexture(active);}
 }
 public static void unbindWater(){var b=binding;if(b==null)return;binding=null;int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);try{if(GL20.glIsProgram(b.program()))GL41.glProgramUniform1i(b.program(),b.location(),0);GL13.glActiveTexture(GL13.GL_TEXTURE0+15);GL11.glBindTexture(GL11.GL_TEXTURE_2D,b.texture());GL33.glBindSampler(15,b.sampler());}finally{GL13.glActiveTexture(active);}}
 public static JsonObject status(){var out=new JsonObject();out.addProperty("fixed16",fixed16());out.addProperty("cell_size",cellSize());out.addProperty("builds",builds);out.addProperty("allocations",allocations);out.addProperty("positive_permissions",positivePermissions);out.addProperty("false_permissions",falsePermissions);out.addProperty("idle_last_program_permission",boundProgram!=0&&GL20.glIsProgram(boundProgram)&&GL20.glGetUniformLocation(boundProgram,"shaderlodHierarchyPermission")>=0?GL20.glGetUniformi(boundProgram,GL20.glGetUniformLocation(boundProgram,"shaderlodHierarchyPermission")):0);out.addProperty("cost_opt_in",costOptIn());out.addProperty("producer_enabled",producerEnabled());out.addProperty("copied_frame",copiedFrame);out.addProperty("binding_active",binding!=null);out.addProperty("image",image);out.addProperty("scratch_a",scratchA);out.addProperty("scratch_b",scratchB);out.addProperty("program",program);out.addProperty("bound_fields_scope","historical last admitted draw until destroy; binding_active is current");out.addProperty("control",control);out.addProperty("bound_program",boundProgram);out.addProperty("bound_depth",boundDepth);out.addProperty("bound_copy_frame",boundFrame);out.addProperty("bound_native_unit",boundUnit);out.addProperty("bound_native_sampler",boundSampler);out.addProperty("pipeline_identity",System.identityHashCode(pipeline));out.addProperty("copied_depth",copiedDepth);out.addProperty("current_frame",net.irisshaders.iris.uniforms.SystemTimeUniforms.COUNTER.getAsInt());out.addProperty("width",width);out.addProperty("height",height);out.addProperty("level_width",(width+cellSize()-1)/cellSize());out.addProperty("level_height",(height+cellSize()-1)/cellSize());out.addProperty("status",last);out.addProperty("failure",failure);out.addProperty("native_output_unchanged",true);out.addProperty("lower_only",lowerOnly());out.addProperty("scope","Private build/consumer development; status alone is not output evidence. Legacy: Build-only one RG32F level, all native depthtex1 queries retained. Full setup/build/state costs charged; no skip or GPU saving claim.");return out;}
 public static JsonObject verify(){
  if(image==0||copiedDepth==0||builds==0||(fixed16()&&copiedFrame!=net.irisshaders.iris.uniforms.SystemTimeUniforms.COUNTER.getAsInt())||!qualified()||pipeline!=Iris.getPipelineManager().getPipelineNullable())throw new IllegalStateException("Live owned hierarchy required");
  int drawPrevious=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),previous=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),buffer=GL11.glGetInteger(GL11.GL_READ_BUFFER),pbo=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);int[] keys={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST},prior=Arrays.stream(keys).map(GL11::glGetInteger).toArray();
  int scratch=GL30.glGenFramebuffers(),w=(width+cellSize()-1)/cellSize(),h=(height+cellSize()-1)/cellSize();var depth=org.lwjgl.system.MemoryUtil.memAllocFloat(Math.multiplyExact(width,height));var bounds=org.lwjgl.system.MemoryUtil.memAllocFloat(w*h*2);int bad=0;
  try{
   GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);for(int key:keys)GL11.glPixelStorei(key,key==GL11.GL_PACK_ALIGNMENT?4:0);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,scratch);GL11.glDrawBuffer(GL11.GL_NONE);GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,copiedDepth,0);GL11.glReadBuffer(GL11.GL_NONE);if(GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Depth read FBO incomplete");GL11.glReadPixels(0,0,width,height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth);
   GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,0,0);GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,image,0);GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);if(GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Bounds read FBO incomplete");GL11.glReadPixels(0,0,w,h,GL30.GL_RG,GL11.GL_FLOAT,bounds);
   for(int by=0;by<h;by++)for(int bx=0;bx<w;bx++){float lo=1,hi=0;boolean invalid=false;for(int y=by*cellSize();y<Math.min((by+1)*cellSize(),height);y++)for(int x=bx*cellSize();x<Math.min((bx+1)*cellSize(),width);x++){float d=depth.get(y*width+x);if(!Float.isFinite(d)||d<0||d>1)invalid=true;else{lo=Math.min(lo,d);hi=Math.max(hi,d);}}if(invalid){lo=0;hi=1;}int index=(by*w+bx)*2;if(lowerOnly()){
     int b=Float.floatToRawIntBits(lo);float expected=Float.intBitsToFloat(Integer.compareUnsigned(b,0x3f800000)>0||Integer.compareUnsigned(b,0x38800000)<0?0:b&0xffffe000);
     if(bounds.get(index)>lo||bounds.get(index)<0||!Float.isFinite(bounds.get(index))||bounds.get(index)!=expected||bounds.get(index+1)!=1)bad++;
    }else if(Float.floatToRawIntBits(lo)!=Float.floatToRawIntBits(bounds.get(index))||Float.floatToRawIntBits(hi)!=Float.floatToRawIntBits(bounds.get(index+1)))bad++;}
  }finally{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,drawPrevious);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,previous);GL11.glReadBuffer(buffer);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo);for(int i=0;i<keys.length;i++)GL11.glPixelStorei(keys[i],prior[i]);GL30.glDeleteFramebuffers(scratch);org.lwjgl.system.MemoryUtil.memFree(depth);org.lwjgl.system.MemoryUtil.memFree(bounds);}
  var out=status();out.addProperty("verified_tiles",w*h);out.addProperty("bit_mismatched_tiles",bad);out.addProperty("copied_frame",copiedFrame);out.addProperty("frame",net.irisshaders.iris.uniforms.SystemTimeUniforms.COUNTER.getAsInt());out.addProperty("state_restored",true);if(bad>0)throw new IllegalStateException("Hierarchy GPU/CPU minmax mismatch: "+bad);return out;
 }
 /** Raw scoped state is restored before native cached GL callbacks resume. Only changed fields are captured. */
 private static final class State {
  final int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
  final int texture,sampler;final int[] viewport=new int[4],polygon=new int[2];final boolean blend=GL30.glIsEnabledi(GL11.GL_BLEND,0);final boolean[] mask=new boolean[4];
  final Map<Integer,Boolean> caps=new LinkedHashMap<>();
  State(){
   GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetIntegerv(GL11.GL_POLYGON_MODE,polygon);if(polygon[0]!=polygon[1])throw new IllegalStateException("Asymmetric polygon mode unsupported");
   for(int cap:new int[]{GL11.GL_DEPTH_TEST,GL11.GL_STENCIL_TEST,GL11.GL_CULL_FACE,GL11.GL_SCISSOR_TEST,GL11.GL_COLOR_LOGIC_OP,GL11.GL_DITHER,GL11.GL_POLYGON_OFFSET_FILL,GL11.GL_POLYGON_OFFSET_LINE,GL11.GL_POLYGON_OFFSET_POINT,GL13.GL_SAMPLE_ALPHA_TO_COVERAGE,GL13.GL_SAMPLE_COVERAGE,GL32.GL_SAMPLE_MASK,GL30.GL_RASTERIZER_DISCARD,GL30.GL_FRAMEBUFFER_SRGB})caps.put(cap,GL11.glIsEnabled(cap));
   for(int i=0;i<GL11.glGetInteger(GL30.GL_MAX_CLIP_DISTANCES);i++)caps.put(GL30.GL_CLIP_DISTANCE0+i,GL11.glIsEnabled(GL30.GL_CLIP_DISTANCE0+i));
   try(var stack=org.lwjgl.system.MemoryStack.stackPush()){var b=stack.malloc(4);GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK,0,b);for(int i=0;i<4;i++)mask[i]=b.get(i)!=0;}
   GL13.glActiveTexture(GL13.GL_TEXTURE0);texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);GL13.glActiveTexture(active);
  }
  void disable(){caps.keySet().forEach(GL11::glDisable);GL30.glDisablei(GL11.GL_BLEND,0);GL30.glColorMaski(0,true,true,true,true);GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,GL11.GL_FILL);}
  void restore(){GL20.glUseProgram(program);GL30.glBindVertexArray(vao);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,polygon[0]);caps.forEach((cap,on)->{if(on)GL11.glEnable(cap);else GL11.glDisable(cap);});if(blend)GL30.glEnablei(GL11.GL_BLEND,0);else GL30.glDisablei(GL11.GL_BLEND,0);GL30.glColorMaski(0,mask[0],mask[1],mask[2],mask[3]);GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL33.glBindSampler(0,sampler);GL13.glActiveTexture(active);}
 }
}
