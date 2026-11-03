package com.pp.shaderlod.astraprefix;

import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.minecraft.resources.ResourceLocation;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.pbr.format.*;
import net.irisshaders.iris.pbr.texture.*;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.*;
import org.lwjgl.opengl.*;

/** Normal Astra alpha-prefix producer, adapted from the accepted finite Astra image law. One bounded readback per
 * native terrain owner. No per-frame image/source readback; publication retains the existing current-program integer check. Steady admission
 * uses actual CPU normal provider and named upload/resource/format fences.
 * Native CustomUniforms remains the sole source of the real core controls. */
public final class AstraPrefixRuntime {
 // Private same-artifact A/B switch; unset/default selects the exact R8 branches.
 private static final boolean HOTPATH_R17=true;
 private static final Map<Object,Owner> OWNERS=new IdentityHashMap<>();
 private static Owner bound;
 private static long mutationGeneration;
 public static boolean requested(){var c=com.pp.shaderlod.optimization.OptimizationRuntimeContext.core();return c!=null&&c.settings().enabled()&&c.preset()==com.pp.shaderlod.LodPreset.PERFORMANCE&&com.pp.shaderlod.optimization.OptimizationConfig.snapshot().enabled(com.pp.shaderlod.optimization.OptimizationId.POM_EMPTY_HEIGHT_PREFIX);}
 private static final class Owner {
  final Object shader;final IrisRenderingPipeline pipeline;final int program;
  final AstraPrefixEpochR8 epoch=new AstraPrefixEpochR8();
  final List<AstraPrefixOwnerSignalR8> signals=new ArrayList<>();
  ResourceLocation[] spriteKeys;boolean descriptorsPublished;
  String sourceSha;ProgramSource source;GlProgram<?> glOwner;Object pack,world,resources;
  AstraPrefixSpriteDiscovery.Found found;TextureFormat format;boolean noFormatMetadata;
  AstraPrefixEpochR8.Token token;AstraPrefixGpuDiagnostic.Result image;
  boolean attempted;volatile boolean enabled,retired;int firstFrame=-1;
  Owner(Object shader,IrisRenderingPipeline pipeline,int program){this.shader=shader;this.pipeline=pipeline;this.program=program;}
  AstraPrefixOwnerSignalR8 signal(){var s=new AstraPrefixOwnerSignalR8(shader,program);signals.add(s);return s;}
  AstraPrefixEpochR8.Context context(){var c=Minecraft.getInstance();return new AstraPrefixEpochR8.Context(Iris.getPipelineManager().getPipelineNullable(),c.level,c.getResourceManager(),Iris.getCurrentPack().orElse(null),found==null?null:found.normal(),AstraPrefixNormalSetupR12.identity(TextureFormatLoader.getFormat(),noFormatMetadata));}
  boolean valid(){
   if(!enabled||retired||image==null||found==null||!Iris.getShaderPackOptionQueue().isEmpty()||!epoch.active(token,context())||Iris.isFallback()||pipeline.getCurrentNormalTexture()!=image.image().texture())return false;
   if(HOTPATH_R17){
   var holder=PBRTextureManager.INSTANCE.getHolder(found.base().getId());
   var atlasHolder=((TextureAtlasExtension)found.base()).getPBRHolder();
   if(holder==null||holder.normalTexture()!=found.normal()
      ||atlasHolder==null||atlasHolder.getNormalAtlas()!=found.normal())return false;
   if(spriteKeys==null||spriteKeys.length!=found.sprites().size())return false;
   for(int n=0;n<spriteKeys.length;n++)if(found.normal().getSprite(spriteKeys[n])!=found.sprites().get(n).pbrSprite())return false;
   }else{
   if(PBRTextureManager.INSTANCE.getHolder(found.base().getId())==null
      ||PBRTextureManager.INSTANCE.getHolder(found.base().getId()).normalTexture()!=found.normal()
      ||((TextureAtlasExtension)found.base()).getPBRHolder()==null
      ||((TextureAtlasExtension)found.base()).getPBRHolder().getNormalAtlas()!=found.normal())return false;
   for(var s:found.sprites())if(found.normal().getSprite(net.minecraft.resources.ResourceLocation.parse(s.name()))!=s.pbrSprite())return false;
   }
   return true;
  }
  void fire(){
   // Iris listeners call glUniform*, so dispatch only to the actual currently bound native owner.
   if(bound!=this||!RenderSystem.isOnRenderThread()||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=program)return;
   for(var s:signals)s.fire(shader,program);
  }
  void retire(){enabled=false;retired=true;epoch.invalidate();fire();}
 }
 public static synchronized void register(DynamicUniformHolder holder,Object shader,IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,int program){
  if(pipeline==null||(pass!=SodiumPrograms.Pass.TERRAIN&&pass!=SodiumPrograms.Pass.TERRAIN_CUTOUT))return;
  if(program<=0||GL20.glGetUniformLocation(program,"shaderlodAstraPrefixActive")<0)return;
  if(OWNERS.size()>=16)return; // unknown/new owner gets GL-default Active0; finite stage never grows indefinitely.
  Owner o=new Owner(shader,pipeline,program);OWNERS.put(shader,o);
  if(HOTPATH_R17){
  // Native ONCE defaults are established on the first setup, while Active is 0.
  // The one-shot admitted descriptor is published explicitly after capture, before Active1.
  var once=UniformUpdateFrequency.ONCE;
  holder.uniform1i(once,"shaderlodAstraPrefixVersion",()->1);
  holder.uniform2f(once,"shaderlodAstraPrefixImageSize",Vector2f::new);
  for(int i=0;i<3;i++)holder.uniform4f(once,"shaderlodAstraPrefixRect"+i,()->new Vector4f(-1,-1,-2,-2));
  holder.uniform3f(once,"shaderlodAstraPrefixHeights",()->new Vector3f(1));
  }else{
  // Descriptor updates precede Active publication. Distinct notifier per uniform.
  holder.uniform1i("shaderlodAstraPrefixVersion",()->1,o.signal());
  holder.uniform2f("shaderlodAstraPrefixImageSize",()->o.image==null?new Vector2f():new Vector2f(o.image.image().width(),o.image.image().height()),o.signal());
  for(int i=0;i<3;i++){final int n=i;holder.uniform4f("shaderlodAstraPrefixRect"+i,()->{
   if(o.image==null)return new Vector4f(-1,-1,-2,-2);var r=o.image.pixels().get(n).rect();return new Vector4f(r.x0(),r.y0(),r.x1(),r.y1());},o.signal());}
  holder.uniform3f("shaderlodAstraPrefixHeights",()->o.image==null?new Vector3f(1):new Vector3f(AstraPrefixCertificate.upperHeight(o.image.pixels().get(0).maximumAlpha()),AstraPrefixCertificate.upperHeight(o.image.pixels().get(1).maximumAlpha()),AstraPrefixCertificate.upperHeight(o.image.pixels().get(2).maximumAlpha())),o.signal());
  }
  holder.uniform1i("shaderlodAstraPrefixActive",()->requested()&&bound==o&&o.valid()?1:0,o.signal());
 }
 public static synchronized void built(IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,ProgramSource source,GlProgram<?> program){
  if(program==null)return;var o=OWNERS.get(program.getInterface());if(o==null)return;
  if(o.pipeline!=pipeline||o.program!=program.handle()||o.source!=null){o.retire();return;}
  o.source=source;o.glOwner=program;o.sourceSha=linkedSource(program.handle());
 }
 public static synchronized void begin(Object shader){
  bound=OWNERS.get(shader);
  // DYNAMIC suppliers run later in native updateUniforms, after native samplers.update.
 }
 public static synchronized void ready(Object shader){
  Owner o=OWNERS.get(shader);if(o==null||o!=bound)return;
  if(!requested()){o.enabled=false;o.fire();return;}
  if(o.attempted){if(o.enabled&&!o.valid())o.retire();else if(o.image!=null&&!o.retired)o.enabled=true;o.fire();return;}
  var c=Minecraft.getInstance();if(c.level==null)return;if(!c.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)){o.retire();return;}o.attempted=true;long captureMutationGeneration=mutationGeneration;var receipt=new JsonObject();
  try{
   if(!Iris.getShaderPackOptionQueue().isEmpty()||o.retired||o.source==null||o.glOwner==null||Iris.isFallback()||Iris.getPipelineManager().getPipelineNullable()!=o.pipeline||o.source.getParent().getPack()!=Iris.getCurrentPack().orElseThrow()||!o.source.getName().equals("gbuffers_terrain"))throw new IllegalStateException("current terrain source/owner mismatch");
   if(!qualifiedSource())throw new IllegalStateException("source was not qualified by the actual loading producer");
   var actual=com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(Iris.getCurrentPack().orElseThrow().getShaderPackOptions());
   var platform=net.irisshaders.iris.platform.IrisPlatformHelpers.getInstance();if(platform.isModLoaded("distanthorizons")||platform.isModLoaded("voxy"))throw new IllegalStateException("unsupported alternate terrain owner");
   if(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=o.program||GL20.glGetProgrami(o.program,GL20.GL_LINK_STATUS)!=1)throw new IllegalStateException("current linked program mismatch");
   if(o.sourceSha==null)throw new IllegalStateException("linked driver source receipt absent");
   var actualCore=new JsonObject();checkInt(o.program,"shaderlodBridgeVersion",2,actualCore);checkInt(o.program,"shaderlodEnabled",1,actualCore);checkInt(o.program,"shaderlodOptimizationFlagsVersion",1,actualCore);checkInt(o.program,"shaderlodOptPomEmptyHeightPrefix",1,actualCore);checkInt(o.program,"shaderlodPreset",com.pp.shaderlod.optimization.OptimizationRuntimeContext.core().preset().ordinal(),actualCore);receipt.add("actual_core_and_feature",actualCore);receipt.add("actual_effective_options",new Gson().toJsonTree(actual));receipt.addProperty("actual_driver_fragment_sha256",o.sourceSha);
   o.found=AstraPrefixSpriteDiscovery.current((TextureAtlas)c.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS),c.getResourceManager());o.spriteKeys=o.found.sprites().stream().map(s->ResourceLocation.parse(s.name())).toArray(ResourceLocation[]::new);
   o.format=TextureFormatLoader.getFormat();boolean metadata=c.getResourceManager().getResource(TextureFormatLoader.LOCATION).isPresent();o.noFormatMetadata=o.format==null&&!metadata;
   if(!AstraPrefixNormalSetupR12.admitted(o.format,!metadata))throw new IllegalStateException("unsupported normal setup/format");o.token=o.epoch.begin(o.context());
   if(o.pipeline.getCurrentNormalTexture()!=o.found.normal().getId())throw new IllegalStateException("actual normal owner differs");
   o.image=AstraPrefixReadbackReceipt.observe(receipt,AstraPrefixRuntime::readbackState,()->new AstraPrefixGpuDiagnostic().captureOnce(o.program,o.pipeline.getCurrentNormalTexture(),o.found.sprites().stream().map(AstraPrefixSpriteDiscovery.Sprite::rect).toList(),receipt));
   int atlasLocation=GL20.glGetUniformLocation(o.program,"atlasSize");int[] nativeExtent=new int[2];if(atlasLocation<0)throw new IllegalStateException("native atlas extent missing");GL20.glGetUniformiv(o.program,atlasLocation,nativeExtent);if(nativeExtent[0]!=o.image.image().width()||nativeExtent[1]!=o.image.image().height())throw new IllegalStateException("native atlas/image extent differs");
   if(!supported(o.image.image()))throw new IllegalStateException("unsupported actual base nearest MAG normal sampler");
   if(mutationGeneration!=captureMutationGeneration||!o.epoch.install(o.token,o.context()))throw new IllegalStateException("capture epoch changed");publishDescriptors(o);o.enabled=true;if(!o.valid())throw new IllegalStateException("published owner is no longer current");o.fire();var abi=new JsonObject();checkInt(o.program,"shaderlodAstraPrefixVersion",1,abi);checkInt(o.program,"shaderlodAstraPrefixActive",1,abi);var im=o.image.image();checkVector(o.program,"shaderlodAstraPrefixImageSize",new float[]{im.width(),im.height()},abi);for(int n=0;n<3;n++){var r=o.image.pixels().get(n).rect();checkVector(o.program,"shaderlodAstraPrefixRect"+n,new float[]{r.x0(),r.y0(),r.x1(),r.y1()},abi);}checkVector(o.program,"shaderlodAstraPrefixHeights",new float[]{AstraPrefixCertificate.upperHeight(o.image.pixels().get(0).maximumAlpha()),AstraPrefixCertificate.upperHeight(o.image.pixels().get(1).maximumAlpha()),AstraPrefixCertificate.upperHeight(o.image.pixels().get(2).maximumAlpha())},abi);receipt.add("actual_prefix_abi",abi);receipt.add("actual_fresh_image_and_bounds",new Gson().toJsonTree(o.image));receipt.addProperty("captured_mutation_generation",captureMutationGeneration);receipt.addProperty("capture_token_generation",o.token.generation());receipt.addProperty("status","current certified alpha prefix; driver rendering/quality/cost unverified");
  }catch(Exception|Error failure){o.retire();receipt.addProperty("status","native fallback");receipt.addProperty("reason",failure.toString());}
  lastStatus=receipt.toString();
 }
 private static String lastStatus="not captured";
 public static String status(){return "Optional native POM + pinned Replete stone/cobble/planks; certified prefix precedes approximate height cache on same ray; native count only; "+lastStatus;}
 // No callbacks or foreign-thread GL work in this publication. The program was linked once;
 // the owner proof cannot recapture or revive. Runtime intrinsic readbacks below stay unchanged.
 private static void publishDescriptors(Owner o){
  if(o.descriptorsPublished||o.retired||bound!=o||!RenderSystem.isOnRenderThread()
     ||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=o.program)throw new IllegalStateException("descriptor owner mismatch");
  String[] names={"shaderlodAstraPrefixVersion","shaderlodAstraPrefixImageSize","shaderlodAstraPrefixRect0","shaderlodAstraPrefixRect1","shaderlodAstraPrefixRect2","shaderlodAstraPrefixHeights"};
  int[] locations=new int[names.length];
  for(int n=0;n<names.length;n++){locations[n]=GL20.glGetUniformLocation(o.program,names[n]);if(locations[n]<0)throw new IllegalStateException("descriptor uniform missing "+names[n]);}
  var image=o.image.image();
  GL20.glUniform1i(locations[0],1);GL20.glUniform2f(locations[1],image.width(),image.height());
  for(int n=0;n<3;n++){var r=o.image.pixels().get(n).rect();GL20.glUniform4f(locations[n+2],r.x0(),r.y0(),r.x1(),r.y1());}
  GL20.glUniform3f(locations[5],AstraPrefixCertificate.upperHeight(o.image.pixels().get(0).maximumAlpha()),AstraPrefixCertificate.upperHeight(o.image.pixels().get(1).maximumAlpha()),AstraPrefixCertificate.upperHeight(o.image.pixels().get(2).maximumAlpha()));
  o.descriptorsPublished=true;
 }
 public static boolean supported(AstraPrefixGpuDiagnostic.Image i){return i!=null&&i.internalFormat()==0x8058&&i.alphaBits()==8&&i.swizzleA()==0x1906&&i.baseLevel()==0&&i.maxLevel()==4&&i.sampler()==0&&i.minFilter()==9986&&i.magFilter()==9728&&i.compareMode()==0&&java.util.Set.of(10497,33071).contains(i.wrapS())&&java.util.Set.of(10497,33071).contains(i.wrapT())&&i.anisotropy()==1&&i.lodBias()==0&&i.minLod()==0&&i.maxLod()==4&&i.width()>=16&&i.height()>=16&&i.width()<=16384&&i.height()<=16384;}
 public static synchronized void reset(Object shader){var o=OWNERS.get(shader);if(o!=null&&bound==o){o.enabled=false;o.fire();bound=null;}}
 // reset is a binding fence, not image retirement: next setup can re-enable the same captured storage proof.
 public static synchronized void restoreForSetup(Object shader){var o=OWNERS.get(shader);if(o!=null&&!o.retired&&o.image!=null)o.enabled=true;}
 public static synchronized void atlasMutating(Object atlas){mutationGeneration++;for(var o:OWNERS.values())if(o.found!=null&&o.found.normal()==atlas)o.retire();}
 public static synchronized void resourceReloading(Object manager){mutationGeneration++;for(var o:OWNERS.values())if(o.attempted&&(o.token==null||o.epoch.concerns(manager)))o.retire();}
 public static synchronized void allMutating(){mutationGeneration++;for(var o:OWNERS.values())if(o.attempted)o.retire();}
 public static synchronized void pipelineDestroying(Object pipeline){mutationGeneration++;for(var o:OWNERS.values())if(o.pipeline==pipeline)o.retire();OWNERS.entrySet().removeIf(e->e.getValue().pipeline==pipeline);if(bound!=null&&bound.pipeline==pipeline)bound=null;}
 public static synchronized void normalSetup(TextureFormat format,PBRType type,Object texture){
  if(type!=PBRType.NORMAL)return;for(var o:OWNERS.values())if(o.found!=null&&o.found.normal()==texture
    &&(!AstraPrefixNormalSetupR12.sameNoParameterWrite(format,o.format,o.noFormatMetadata)))o.retire();
 }
 public static synchronized void bindingChanged(){if(bound!=null)bound.fire();}
 private static void checkInt(int program,String name,int expected,JsonObject receipt){int loc=GL20.glGetUniformLocation(program,name);if(loc<0||GL20.glGetUniformi(program,loc)!=expected)throw new IllegalStateException("actual integer uniform mismatch "+name);receipt.addProperty(name,expected);}
 private static void checkVector(int program,String name,float[] expected,JsonObject receipt){int loc=GL20.glGetUniformLocation(program,name);if(loc<0)throw new IllegalStateException("actual vector uniform missing "+name);float[] actual=new float[expected.length];GL20.glGetUniformfv(program,loc,actual);if(!Arrays.equals(actual,expected))throw new IllegalStateException("actual vector uniform mismatch "+name);receipt.add(name,new Gson().toJsonTree(actual));}
 private static int[] readbackState(){int[] names={GL13.GL_ACTIVE_TEXTURE,GL30.GL_READ_FRAMEBUFFER_BINDING,GL11.GL_READ_BUFFER,GL21.GL_PIXEL_PACK_BUFFER_BINDING,GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST,GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES,GL30.GL_DRAW_FRAMEBUFFER_BINDING,GL20.GL_CURRENT_PROGRAM};int[] values=new int[names.length];for(int i=0;i<names.length;i++)values[i]=GL11.glGetInteger(names[i]);return values;}
 private AstraPrefixRuntime(){}

 private static boolean qualifiedSource(){return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.List.of(AstraPrefixRuntime.class,"source"),()->{
   var directory=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var selected=directory.resolve(Iris.getIrisConfig().getShaderPackName().orElseThrow()).normalize();if(selected.equals(directory)||!selected.startsWith(directory))throw new IllegalStateException("unsafe selected source child");var source=com.pp.shaderlod.integration.PackSourceReader.read(selected);
   if(!com.pp.shaderlod.packs.SupportedPackCatalog.shaderSourceDigest(source.memberSha256()).equals("9c29b0a9e3b5d69d9abb38702f9c1fa92472ffb5a7d8ed9e6add65f72b476f88"))throw new IllegalStateException("selected source changed");
   var actual=com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(Iris.getCurrentPack().orElseThrow().getShaderPackOptions());
   if(!com.pp.shaderlod.optimization.astraprefix.AstraPrefixProfile.accepts(actual)||!"astralex".equals(com.pp.shaderlod.packs.PackSelectionService.verifyGeneratedRendererGraph(Iris.getShaderpacksDirectory().resolve(Iris.getIrisConfig().getShaderPackName().orElseThrow()),actual)))throw new IllegalStateException("complete native POM source/state/option profile refused");
  return true;
 });}
 private static String linkedSource(int program){
 var actual=com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(Iris.getCurrentPack().orElseThrow().getShaderPackOptions());
   int[] count={0},ids=new int[2];GL20.glGetAttachedShaders(program,count,ids);if(count[0]!=2)return null;String fragment=null;
   for(int id:ids)if(GL20.glGetShaderi(id,GL20.GL_SHADER_TYPE)==GL20.GL_FRAGMENT_SHADER){if(fragment!=null)return null;fragment=GL20.glGetShaderSource(id);}
   if(fragment==null||!AstraPrefixSourceR8.matches(fragment,com.pp.shaderlod.optimization.astraprefix.AstraPrefixProfile.functions()))return null;
   return AstraPrefixSourceR8.hash(fragment);

 }
 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){qualifiedSource();}
}
