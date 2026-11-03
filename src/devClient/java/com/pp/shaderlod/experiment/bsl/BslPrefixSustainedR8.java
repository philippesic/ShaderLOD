package com.pp.shaderlod.experiment.bsl;

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

/** Bounded private BSL experiment, not catalog support. One finite readback per
 * native terrain owner. No per-frame image/source/GL readback; steady admission
 * uses actual CPU normal provider and named upload/resource/format fences.
 * Native CustomUniforms remains the sole source of the real core controls. */
public final class BslPrefixSustainedR8 {
 private static final String ARCHIVE="97e9b9b1fbbe7460dcdef9939c22b4d666fa7b1087cb7cfa5aaa02ce3fc54ab3";
 private static final String REPL="6a967ac89bd1f05c42c0d0fa2f606b5b894b3b52bcc624c72c48b33a1d48a2c6";
 // Private same-artifact A/B switch; unset/default selects the exact R8 branches.
 private static final boolean HOTPATH_R17="1".equals(System.getProperty("shaderlod.dev.bslPrefixBindingHotpathR17"));
 private static final Map<Object,Owner> OWNERS=new IdentityHashMap<>();
 private static Owner bound;
 private static long mutationGeneration;
 public static boolean requested(){return "1".equals(System.getProperty("shaderlod.dev.bslPrefixSustainedR8"));}
 private static final class Owner {
  final Object shader;final IrisRenderingPipeline pipeline;final int program;
  final BslPrefixEpochR8 epoch=new BslPrefixEpochR8();
  final List<BslPrefixOwnerSignalR8> signals=new ArrayList<>();
  ResourceLocation[] spriteKeys;boolean descriptorsPublished;
  ProgramSource source;GlProgram<?> glOwner;Object pack,world,resources;
  BslPrefixSpriteDiscovery.Found found;TextureFormat format;boolean noFormatMetadata;
  BslPrefixEpochR8.Token token;BslPrefixGpuDiagnostic.Result image;
  boolean attempted;volatile boolean enabled,retired;int firstFrame=-1;
  Owner(Object shader,IrisRenderingPipeline pipeline,int program){this.shader=shader;this.pipeline=pipeline;this.program=program;}
  BslPrefixOwnerSignalR8 signal(){var s=new BslPrefixOwnerSignalR8(shader,program);signals.add(s);return s;}
  BslPrefixEpochR8.Context context(){var c=Minecraft.getInstance();return new BslPrefixEpochR8.Context(Iris.getPipelineManager().getPipelineNullable(),c.level,c.getResourceManager(),Iris.getCurrentPack().orElse(null),found==null?null:found.normal(),BslPrefixNormalSetupR12.identity(TextureFormatLoader.getFormat(),noFormatMetadata));}
  boolean valid(){
   if(!enabled||retired||image==null||found==null||!epoch.active(token,context())||Iris.isFallback()||pipeline.getCurrentNormalTexture()!=image.image().texture())return false;
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
  if(!requested()||pipeline==null||(pass!=SodiumPrograms.Pass.TERRAIN&&pass!=SodiumPrograms.Pass.TERRAIN_CUTOUT))return;
  if(OWNERS.size()>=4)return; // unknown/new owner gets GL-default Active0; finite stage never grows indefinitely.
  Owner o=new Owner(shader,pipeline,program);OWNERS.put(shader,o);
  if(HOTPATH_R17){
  // Native ONCE defaults are established on the first setup, while Active is 0.
  // The one-shot admitted descriptor is published explicitly after capture, before Active1.
  var once=UniformUpdateFrequency.ONCE;
  holder.uniform1i(once,"shaderlodBslPrefixVersion",()->1);
  holder.uniform2f(once,"shaderlodBslPrefixImageSize",Vector2f::new);
  for(int i=0;i<3;i++)holder.uniform4f(once,"shaderlodBslPrefixRect"+i,()->new Vector4f(-1,-1,-2,-2));
  holder.uniform3f(once,"shaderlodBslPrefixHeights",()->new Vector3f(1));
  }else{
  // Descriptor updates precede Active publication. Distinct notifier per uniform.
  holder.uniform1i("shaderlodBslPrefixVersion",()->1,o.signal());
  holder.uniform2f("shaderlodBslPrefixImageSize",()->o.image==null?new Vector2f():new Vector2f(o.image.image().width(),o.image.image().height()),o.signal());
  for(int i=0;i<3;i++){final int n=i;holder.uniform4f("shaderlodBslPrefixRect"+i,()->{
   if(o.image==null)return new Vector4f(-1,-1,-2,-2);var r=o.image.pixels().get(n).rect();return new Vector4f(r.x0(),r.y0(),r.x1(),r.y1());},o.signal());}
  holder.uniform3f("shaderlodBslPrefixHeights",()->o.image==null?new Vector3f(1):new Vector3f(BslPrefixCertificate.upperHeight(o.image.pixels().get(0).maximumAlpha()),BslPrefixCertificate.upperHeight(o.image.pixels().get(1).maximumAlpha()),BslPrefixCertificate.upperHeight(o.image.pixels().get(2).maximumAlpha())),o.signal());
  }
  holder.uniform1i("shaderlodBslPrefixActive",()->bound==o&&o.valid()?1:0,o.signal());
 }
 public static synchronized void built(IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,ProgramSource source,GlProgram<?> program){
  if(!requested()||program==null)return;var o=OWNERS.get(program.getInterface());if(o==null)return;
  if(o.pipeline!=pipeline||o.program!=program.handle()||o.source!=null){o.retire();return;}
  o.source=source;o.glOwner=program;
 }
 public static synchronized void begin(Object shader){
  if(!requested())return;bound=OWNERS.get(shader);
  // DYNAMIC suppliers run later in native updateUniforms, after native samplers.update.
 }
 public static synchronized void ready(Object shader){
  if(!requested())return;Owner o=OWNERS.get(shader);if(o==null||o!=bound)return;
  var c=Minecraft.getInstance();
  if(o.attempted){if(o.enabled&&!o.valid()){o.enabled=false;o.fire();}return;}
  if(c.level==null||c.screen!=null||c.isPaused()||!c.isWindowActive())return;
  int frame=SystemTimeUniforms.COUNTER.getAsInt();if(o.firstFrame<0){o.firstFrame=frame;return;}if(frame-o.firstFrame<180)return;
  o.attempted=true;long captureMutationGeneration=mutationGeneration;var receipt=new JsonObject();
  try{
   if(o.retired||o.source==null||o.glOwner==null||Iris.isFallback()||Iris.getPipelineManager().getPipelineNullable()!=o.pipeline
      ||o.source.getParent().getPack()!=Iris.getCurrentPack().orElseThrow()||!o.source.getName().equals("gbuffers_terrain"))throw new IllegalStateException("current private source/owner mismatch");
   byte[] functionBytes=Files.readAllBytes(Path.of(required("Functions")));String functionPin=hash(functionBytes);
   boolean cueR9=functionPin.equals("9ddb9e22f126a9cb06d2658e56330cc54a363c564214bb35f2497cdb7e04c241");
   if(!cueR9&&!functionPin.equals("7afc4bdcd8b4bb161fc0cd60da50a5585d08e95158d6ae4ee57fb17a2b12d66d"))throw new IllegalStateException("unrecognized finite source registry entry");
   String expectedPack=cueR9?"BSL-prefix-height-r3-CUE-r9-NEVER-TIME.zip":"BSL-prefix-height-r3-private.zip";
   String expectedArchive=cueR9?"b854596dca3adaef0565ff6b426975835a0397b99aa57e75026b23d8357a9305":ARCHIVE;
   String packName=Iris.getCurrentPackName();if(!packName.equals(expectedPack)||!hash(Files.readAllBytes(Iris.getShaderpacksDirectory().resolve(packName))).equals(expectedArchive))throw new IllegalStateException("private archive/source registry mismatch");
   receipt.addProperty("finite_source_variant",cueR9?"CUE_R9_NEVER_TIME":"ORDINARY_R3");
   if(!c.options.resourcePacks.equals(List.of("vanilla","file/replete-1.2.0+mc1.20-1.21.4.zip","mod_resources"))
      ||!hash(Files.readAllBytes(c.gameDirectory.toPath().resolve("resourcepacks/replete-1.2.0+mc1.20-1.21.4.zip"))).equals(REPL))throw new IllegalStateException("resource archive/order mismatch");
   var options=Iris.getCurrentPack().orElseThrow().getShaderPackOptions();Map<String,String> actual=new TreeMap<>();
   options.getOptionSet().getBooleanOptions().keySet().forEach(k->actual.put(k,Boolean.toString(options.getOptionValues().getBooleanValueOrDefault(k))));
   options.getOptionSet().getStringOptions().keySet().forEach(k->actual.put(k,options.getOptionValues().getStringValueOrDefault(k)));
   byte[] optionBytes=Files.readAllBytes(Path.of(required("Options")));if(!hash(optionBytes).equals("bced4de2b66310c5ba7b6b076c73448fd7f9da3231037aa055c361ab114fc402"))throw new IllegalStateException("unrecognized options pin file");JsonObject expected=JsonParser.parseString(new String(optionBytes,StandardCharsets.UTF_8)).getAsJsonObject();
   if(!new Gson().toJsonTree(actual).equals(expected)||!"1".equals(actual.get("SHADERLOD_BSL_PREFIX_R3")))throw new IllegalStateException("full private native option map mismatch");receipt.add("actual_options",new Gson().toJsonTree(actual));
   if(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=o.program||GL20.glGetProgrami(o.program,GL20.GL_LINK_STATUS)!=1)throw new IllegalStateException("current linked owner mismatch");
   // Function bytes already admitted by the exact ordinary/cue registry above.
   var hashes=JsonParser.parseString(new String(functionBytes,StandardCharsets.UTF_8)).getAsJsonObject();Map<String,String> functions=new TreeMap<>();hashes.entrySet().forEach(e->functions.put(e.getKey(),e.getValue().getAsString()));
   int[] count={0},ids=new int[2];GL20.glGetAttachedShaders(o.program,count,ids);if(count[0]!=2)throw new IllegalStateException("two actual stages required");String fragment=null;
   for(int id:ids)if(GL20.glGetShaderi(id,GL20.GL_SHADER_TYPE)==GL20.GL_FRAGMENT_SHADER){if(fragment!=null)throw new IllegalStateException("duplicate fragment");fragment=GL20.glGetShaderSource(id);}
   if(fragment==null||!BslPrefixSourceR8.matches(fragment,functions))throw new IllegalStateException("actual driver prefix/native helper functions differ");receipt.addProperty("actual_driver_fragment_sha256",BslPrefixSourceR8.hash(fragment));
   var core=new JsonObject();for(String name:List.of("shaderlodBridgeVersion","shaderlodEnabled","shaderlodPreset")){
    int loc=GL20.glGetUniformLocation(o.program,name);if(loc<0)throw new IllegalStateException("real core missing");core.addProperty(name,GL20.glGetUniformi(o.program,loc));}
   if(core.get("shaderlodBridgeVersion").getAsInt()!=2||core.get("shaderlodEnabled").getAsInt()!=1||core.get("shaderlodPreset").getAsInt()!=1)throw new IllegalStateException("real core not performance");receipt.add("actual_real_core",core);
   if(cueR9){int debugLoc=GL20.glGetUniformLocation(o.program,"shaderlodDebug");if(debugLoc<0)throw new IllegalStateException("real main Debug uniform missing in cue");int debug=GL20.glGetUniformi(o.program,debugLoc);if(debug!=0&&debug!=1)throw new IllegalStateException("invalid real main Debug value");receipt.addProperty("actual_real_core_debug",debug);}
   o.found=BslPrefixSpriteDiscovery.current((TextureAtlas)c.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS),c.getResourceManager());
   if(HOTPATH_R17)o.spriteKeys=o.found.sprites().stream().map(s->ResourceLocation.parse(s.name())).toArray(ResourceLocation[]::new);
   o.format=TextureFormatLoader.getFormat();
   // Publish actual discriminants before admission/failure. Null from absent metadata is a named native branch.
   boolean formatMetadataPresent=c.getResourceManager().getResource(TextureFormatLoader.LOCATION).isPresent();
   receipt.addProperty("actual_format_is_null",o.format==null);receipt.addProperty("actual_format_class",o.format==null?"NULL":o.format.getClass().getName());
   receipt.addProperty("actual_format_metadata_location",TextureFormatLoader.LOCATION.toString());receipt.addProperty("actual_format_metadata_present",formatMetadataPresent);
   if(o.format==null)receipt.add("actual_format_can_interpolate_NORMAL",JsonNull.INSTANCE);else receipt.addProperty("actual_format_can_interpolate_NORMAL",o.format.canInterpolateValues(PBRType.NORMAL));
   o.noFormatMetadata=o.format==null&&!formatMetadataPresent;
   if(!BslPrefixNormalSetupR12.admitted(o.format,!formatMetadataPresent))throw new IllegalStateException("unknown normal setup implementation");
   receipt.addProperty("actual_NORMAL_parameter_write_contract",o.noFormatMetadata?"PINNED_NULL_ABSENT_METADATA_NATIVE_SKIPS_SETUP":"PINNED_LABPBR_NORMAL_DEFAULT_SETUP_NO_WRITE");
   o.token=o.epoch.begin(o.context());
   if(o.pipeline.getCurrentNormalTexture()!=o.found.normal().getId())throw new IllegalStateException("real normal provider mismatch");
   o.image=BslPrefixReadbackReceipt.observe(receipt,BslPrefixSustainedR8::readbackState,()->new BslPrefixGpuDiagnostic().captureOnce(o.program,o.pipeline.getCurrentNormalTexture(),o.found.sprites().stream().map(BslPrefixSpriteDiscovery.Sprite::rect).toList(),receipt));
   var im=o.image.image();if(!supported(im))throw new IllegalStateException("unsupported native current sampler");
   if(mutationGeneration!=captureMutationGeneration||SystemTimeUniforms.COUNTER.getAsInt()!=frame||!o.epoch.install(o.token,o.context()))throw new IllegalStateException("capture epoch changed");
   if(HOTPATH_R17)publishDescriptors(o);
   o.enabled=true;if(!o.valid())throw new IllegalStateException("current join invalid after capture");o.fire();
   int active=GL20.glGetUniformLocation(o.program,"shaderlodBslPrefixActive");if(active<0||GL20.glGetUniformi(o.program,active)!=1)throw new IllegalStateException("native DYNAMIC Active publication failed");
   var values=new JsonObject();checkInt(o.program,"shaderlodBslPrefixVersion",1,values);checkInt(o.program,"shaderlodBslPrefixActive",1,values);
   checkVector(o.program,"shaderlodBslPrefixImageSize",new float[]{im.width(),im.height()},values);
   for(int n=0;n<3;n++){var r=o.image.pixels().get(n).rect();checkVector(o.program,"shaderlodBslPrefixRect"+n,new float[]{r.x0(),r.y0(),r.x1(),r.y1()},values);}
   checkVector(o.program,"shaderlodBslPrefixHeights",new float[]{BslPrefixCertificate.upperHeight(o.image.pixels().get(0).maximumAlpha()),BslPrefixCertificate.upperHeight(o.image.pixels().get(1).maximumAlpha()),BslPrefixCertificate.upperHeight(o.image.pixels().get(2).maximumAlpha())},values);
   receipt.add("actual_prefix_uniform_values",values);receipt.add("actual_sprites",new Gson().toJsonTree(o.found.sprites().stream().map(x->Map.of("name",x.name(),"winning_pack",x.sourcePackId(),"resource_sha256",x.resourceSha256(),"sprite_identity",System.identityHashCode(x.pbrSprite()))).toList()));
   receipt.addProperty("actual_NORMAL_setup_format",o.format==null?"PINNED_NULL_ABSENT_METADATA":o.format.getClass().getName());receipt.addProperty("actual_format_identity",System.identityHashCode(o.format));receipt.addProperty("capture_mutation_generation",captureMutationGeneration);receipt.addProperty("capture_token_generation",o.token.generation());receipt.addProperty("iris_frame",frame);
   receipt.add("fresh_private_image",new Gson().toJsonTree(o.image));receipt.addProperty("status","FRESH_PRIVATE_SUSTAINED_BINDING_SOURCE_SPEC_CONDITIONAL_DRIVER_QUALITY_COST_PENDING");
  }catch(Exception|Error failure){o.retire();receipt.addProperty("status","FAILED_NATIVE_FALLBACK");receipt.addProperty("failure",failure.toString());}
  write(o,receipt);
 }
 // No callbacks or foreign-thread GL work in this publication. The program was linked once;
 // the owner proof cannot recapture or revive. Runtime intrinsic readbacks below stay unchanged.
 private static void publishDescriptors(Owner o){
  if(o.descriptorsPublished||o.retired||bound!=o||!RenderSystem.isOnRenderThread()
     ||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=o.program)throw new IllegalStateException("descriptor owner mismatch");
  String[] names={"shaderlodBslPrefixVersion","shaderlodBslPrefixImageSize","shaderlodBslPrefixRect0","shaderlodBslPrefixRect1","shaderlodBslPrefixRect2","shaderlodBslPrefixHeights"};
  int[] locations=new int[names.length];
  for(int n=0;n<names.length;n++){locations[n]=GL20.glGetUniformLocation(o.program,names[n]);if(locations[n]<0)throw new IllegalStateException("descriptor uniform missing "+names[n]);}
  var image=o.image.image();
  GL20.glUniform1i(locations[0],1);GL20.glUniform2f(locations[1],image.width(),image.height());
  for(int n=0;n<3;n++){var r=o.image.pixels().get(n).rect();GL20.glUniform4f(locations[n+2],r.x0(),r.y0(),r.x1(),r.y1());}
  GL20.glUniform3f(locations[5],BslPrefixCertificate.upperHeight(o.image.pixels().get(0).maximumAlpha()),BslPrefixCertificate.upperHeight(o.image.pixels().get(1).maximumAlpha()),BslPrefixCertificate.upperHeight(o.image.pixels().get(2).maximumAlpha()));
  o.descriptorsPublished=true;
 }
 public static boolean supported(BslPrefixGpuDiagnostic.Image i){return i!=null&&i.internalFormat()==0x8058&&i.alphaBits()==8&&i.swizzleA()==0x1906&&i.baseLevel()==0&&i.maxLevel()==4&&i.sampler()==0&&i.minFilter()==9986&&i.magFilter()==9728&&i.compareMode()==0&&i.anisotropy()==1&&i.lodBias()==0&&i.minLod()==0&&i.maxLod()==4&&i.width()>=16&&i.height()>=16&&i.width()<=16384&&i.height()<=16384;}
 public static synchronized void reset(Object shader){if(!requested())return;var o=OWNERS.get(shader);if(o!=null&&bound==o){o.enabled=false;o.fire();bound=null;}}
 // reset is a binding fence, not image retirement: next setup can re-enable the same captured storage proof.
 public static synchronized void restoreForSetup(Object shader){var o=OWNERS.get(shader);if(o!=null&&!o.retired&&o.image!=null)o.enabled=true;}
 public static synchronized void atlasMutating(Object atlas){mutationGeneration++;for(var o:OWNERS.values())if(o.found!=null&&o.found.normal()==atlas)o.retire();}
 public static synchronized void resourceReloading(Object manager){mutationGeneration++;for(var o:OWNERS.values())if(o.attempted&&(o.token==null||o.epoch.concerns(manager)))o.retire();}
 public static synchronized void allMutating(){mutationGeneration++;for(var o:OWNERS.values())if(o.attempted)o.retire();}
 public static synchronized void pipelineDestroying(Object pipeline){mutationGeneration++;for(var o:OWNERS.values())if(o.pipeline==pipeline)o.retire();}
 public static synchronized void normalSetup(TextureFormat format,PBRType type,Object texture){
  if(type!=PBRType.NORMAL)return;for(var o:OWNERS.values())if(o.found!=null&&o.found.normal()==texture
    &&(!BslPrefixNormalSetupR12.sameNoParameterWrite(format,o.format,o.noFormatMetadata)))o.retire();
 }
 public static synchronized void bindingChanged(){if(bound!=null)bound.fire();}
 private static void checkInt(int program,String name,int expected,JsonObject receipt){int loc=GL20.glGetUniformLocation(program,name);if(loc<0||GL20.glGetUniformi(program,loc)!=expected)throw new IllegalStateException("actual integer uniform mismatch "+name);receipt.addProperty(name,expected);}
 private static void checkVector(int program,String name,float[] expected,JsonObject receipt){int loc=GL20.glGetUniformLocation(program,name);if(loc<0)throw new IllegalStateException("actual vector uniform missing "+name);float[] actual=new float[expected.length];GL20.glGetUniformfv(program,loc,actual);if(!Arrays.equals(actual,expected))throw new IllegalStateException("actual vector uniform mismatch "+name);receipt.add(name,new Gson().toJsonTree(actual));}
 private static int[] readbackState(){int[] names={GL13.GL_ACTIVE_TEXTURE,GL30.GL_READ_FRAMEBUFFER_BINDING,GL11.GL_READ_BUFFER,GL21.GL_PIXEL_PACK_BUFFER_BINDING,GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST,GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES,GL30.GL_DRAW_FRAMEBUFFER_BINDING,GL20.GL_CURRENT_PROGRAM};int[] values=new int[names.length];for(int i=0;i<names.length;i++)values[i]=GL11.glGetInteger(names[i]);return values;}
 private static String required(String suffix){String p=System.getProperty("shaderlod.dev.bslPrefixR8"+suffix,"");if(p.isBlank())throw new IllegalStateException("missing finite "+suffix+" pin input");return p;}
 private static String hash(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 private static void write(Owner o,JsonObject receipt){try{Path dir=Path.of(required("Output"));Files.createDirectories(dir);Path p=dir.resolve("owner-"+o.program+".json"),tmp=p.resolveSibling(p.getFileName()+".tmp");Files.writeString(tmp,new GsonBuilder().setPrettyPrinting().create().toJson(receipt));Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(Exception e){System.err.println("BSL prefix R8 receipt failed: "+e);}}
 private BslPrefixSustainedR8(){}
}
