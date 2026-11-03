package com.pp.shaderlod.optimization.mips;
import com.google.gson.*;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.packs.*;
import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.loading.*;
import net.irisshaders.iris.shaderpack.programs.*;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.targets.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;
/** Setup proof for three pinned dead mip chains. Reads actual source/API/options/graph and live storage; never changes filters or native quality. */
public final class DeadMipAdmission {
 private static final String RESOURCE="/assets/shaderlod/adapters/qualification/dead-mip-admission.json";
 private static final String SHA="da00218de679fdbd86ee6beb36a1cf3cf8d84b4003108d243bf83335beb683c1";
 private static final JsonObject PROOF=load();
 private static final String API=selectApi();
 // Immutable proof-resource data only; never cache actual options or runtime admission.
 private static final Map<String,String> LEAN_PROFILE=loadLeanProfile();
 private static final List<Map<String,String>> LEAN_PROFILES=loadLeanProfiles();
 private static final Map<String,String> STATUS=new HashMap<>();
 private DeadMipAdmission(){}
 private static JsonObject load(){try(var in=DeadMipAdmission.class.getResourceAsStream(RESOURCE)){if(in==null)return null;byte[] b=in.readAllBytes();if(!SHA.equals(hash(b)))return null;return JsonParser.parseString(new String(b,StandardCharsets.UTF_8)).getAsJsonObject();}catch(Exception|LinkageError unavailable){return null;}}
 private static String hash(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 public static void initialize(){for(String family:List.of("bsl-shaders","photon","astralex"))CompileOptionControl.configureAdmission(family,OptimizationId.DEAD_MIP_CHAIN,()->admitted(family));OptimizationRuntimeContext.configureImplementation(OptimizationId.DEAD_MIP_CHAIN,()->"source liveness compile option; native API variant="+API+"; setup/recertification GL target queries; no speed claim; admissions="+new TreeMap<>(STATUS));}
 public static void invalidate(){STATUS.clear();} // No source/API/GL qualification result is memoized across calls or epochs.
 public static String status(String family){return STATUS.getOrDefault(family,"not inspected");}
 /** Current parsed graph metadata only; no GL queries and no executed-work claim. */
 public static Map<String,Object> bslGraphObservation(){
  var result=new TreeMap<String,Object>();result.put("scope","Current parsed BSL pass directives; executed work requires the separate untimed witness");result.put("admission",status("bsl-shaders"));
  try{var dim=dimension();var pack=Iris.getCurrentPack().orElse(null);if(dim==null||pack==null){result.put("available",false);return result;}
   var programs=pack.getProgramSet(dim).getComposite(ProgramArrayId.Composite);boolean available=programs.length>7&&programs[6]!=null&&programs[7]!=null&&programs[6].isValid()&&programs[7].isValid();result.put("available",available);
   if(available){result.put("composite6_mips",new TreeSet<>(programs[6].getDirectives().getMipmappedBuffers()));result.put("composite7_mips",new TreeSet<>(programs[7].getDirectives().getMipmappedBuffers()));}
   boolean capability=pack instanceof com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.PackView view&&view.shaderlod$makeupPackInput()!=null&&view.shaderlod$makeupPackInput().glslDefines().stream().anyMatch(d->d.key().equals(BslMipRuntimeGuard.DEFINE)&&d.value().equals("1"));result.put("runtime_capability",capability);result.put("c6_runtime_capability",c6Capability(pack));
  }catch(RuntimeException|LinkageError unavailable){result.put("available",false);result.put("reason",unavailable.getClass().getSimpleName());}return Map.copyOf(result);
 }
 public static boolean c6Capability(ShaderPack pack){return pack instanceof com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.PackView view&&view.shaderlod$makeupPackInput()!=null&&view.shaderlod$makeupPackInput().glslDefines().stream().anyMatch(d->d.key().equals(BslMipRuntimeGuard.C6_DEFINE)&&d.value().equals("1"));}
 private static boolean reject(String f,String why){STATUS.put(f,why);return false;}
 private static String selectApi(){if(PROOF==null)return null;for(var row:PROOF.getAsJsonArray("api")){boolean match=true;for(var pin:row.getAsJsonObject().getAsJsonObject("classes").entrySet())try(var in=DeadMipAdmission.class.getClassLoader().getResourceAsStream(pin.getKey())){if(in==null||!pin.getValue().getAsString().equals(hash(in.readAllBytes()))){match=false;break;}}catch(Exception|LinkageError failure){match=false;break;}if(match)return row.getAsJsonObject().get("tuple").getAsString();}return null;}
 public static boolean bslCompiler(String tuple,String mc,String iris){if(PROOF==null||tuple==null||mc==null||iris==null)return false;for(var value:PROOF.getAsJsonArray("api")){var row=value.getAsJsonObject();if(tuple.equals(row.get("tuple").getAsString())){var c=row.getAsJsonObject("bsl_compiler");return c!=null&&mc.equals(c.get("mc_version").getAsString())&&iris.equals(c.get("iris_version").getAsString());}}return false;}
 public static String apiTuple(){return API;}
 /** C6 qualification is one complete profile with every peer graph at its recorded default. */
 public static boolean expectedC6Options(Map<String,String> actual){
  if(PROOF==null||actual==null)return false;
  var profile=PROOF.getAsJsonObject("families").getAsJsonObject("bsl-shaders").getAsJsonObject("options");
  var expected=new TreeMap<String,String>();profile.entrySet().forEach(e->expected.put(e.getKey(),e.getValue().getAsString()));
  expected.putIfAbsent(CompileOptionControl.BSL_DEPTH,"0");var normalized=new TreeMap<>(actual);
  String request=normalized.get(CompileOptionControl.BSL_DEAD_MIP);if(!"0".equals(request)&&!"1".equals(request))return false;
  normalized.put(CompileOptionControl.BSL_DEAD_MIP,"0");return expected.equals(normalized);
 }
 public static Map<String,String> leanProfile(){return LEAN_PROFILE;}
 private static Map<String,String> loadLeanProfile(){
  if(PROOF==null)return Map.of();var expected=new TreeMap<String,String>();
  PROOF.getAsJsonObject("families").getAsJsonObject("bsl-shaders").getAsJsonObject("lean_options").entrySet().forEach(e->expected.put(e.getKey(),e.getValue().getAsString()));
  return Map.copyOf(expected);
 }
 public static List<Map<String,String>> leanProfiles(){return LEAN_PROFILES;}
 private static List<Map<String,String>> loadLeanProfiles(){
  if(PROOF==null||LEAN_PROFILE.isEmpty())return List.of();
  var profiles=new ArrayList<Map<String,String>>();profiles.add(LEAN_PROFILE);
  var row=PROOF.getAsJsonObject("families").getAsJsonObject("bsl-shaders");
  if(row.has("lean_alternative_options"))for(var value:row.getAsJsonArray("lean_alternative_options")){
   var profile=new TreeMap<String,String>();value.getAsJsonObject().entrySet().forEach(e->profile.put(e.getKey(),e.getValue().getAsString()));profiles.add(Map.copyOf(profile));
  }
  return List.copyOf(profiles);
 }
 public static boolean expectedLeanOptions(Map<String,String> actual){
  for(var profile:LEAN_PROFILES)if(matchesLeanProfile(actual,profile))return true;
  return false;
 }
 private static boolean matchesLeanProfile(Map<String,String> actual,Map<String,String> profile){
  if(actual==null||actual.isEmpty()||actual.size()!=profile.size())return false;
  String request=actual.get(CompileOptionControl.BSL_DEAD_MIP);
  if(!"0".equals(request)&&!"1".equals(request))return false;
  for(var entry:profile.entrySet()){
   String value=entry.getKey().equals(CompileOptionControl.BSL_DEAD_MIP)?"0":actual.get(entry.getKey());
   if(!entry.getValue().equals(value))return false;
  }
  return true;
 }
 public static Map<String,String> effective(ShaderPack pack){if(pack==null)return Map.of();return com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(pack.getShaderPackOptions());}
 public static boolean expectedOptions(String family,Map<String,String> actual){
  if(PROOF==null||actual==null)return false;var row=PROOF.getAsJsonObject("families").getAsJsonObject(family);if(row==null)return false;
  if(family.equals("bsl-shaders")&&expectedLeanOptions(actual))return true;
  if(matchesOptions(family,row.getAsJsonObject("options"),actual))return true;
  // Each supported existing profile pins its whole native map; no native setting is normalized.
  if(row.has("alternative_options"))for(var profile:row.getAsJsonArray("alternative_options"))if(matchesOptions(family,profile.getAsJsonObject(),actual))return true;
  return false;
 }
 private static boolean matchesOptions(String family,JsonObject profile,Map<String,String> actual){if(profile==null||profile.size()==0)return false;var expected=new TreeMap<String,String>();profile.entrySet().forEach(e->expected.put(e.getKey(),e.getValue().getAsString()));var normalized=new TreeMap<>(actual);
  // The normal depth producer adds one mod-owned binary option after the native
  // liveness snapshot. Normalize only that option; every native value stays pinned.
  String depth=family.equals("bsl-shaders")?CompileOptionControl.BSL_DEPTH:family.equals("photon")?CompileOptionControl.PHOTON_DEPTH:null;
  if(depth!=null){String value=normalized.get(depth);if(value==null||!Set.of("0","1").contains(value))return false;expected.put(depth,"0");normalized.put(depth,"0");}
  for(String key:CompileOptionControl.Family.fromId(family)==CompileOptionControl.Family.BSL?Set.of(CompileOptionControl.BSL_DEAD_MIP,CompileOptionControl.BSL_AO_HALF,CompileOptionControl.BSL_SHADOW_FIELD,CompileOptionControl.BSL_RAW_QUERY):Set.of(CompileOptionControl.PHOTON_DEAD_MIP)){String value=normalized.get(key);if(value==null||!Set.of("0","1").contains(value))return false;normalized.put(key,"0");}
  if(family.equals("photon"))for(String key:Set.of(CompileOptionControl.PHOTON_SPATIAL,CompileOptionControl.PHOTON_LIGHTING_RATE)){String value=normalized.get(key);if(value==null||!Set.of("false","true").contains(value))return false;normalized.put(key,expected.get(key));}
  return expected.equals(normalized);
 }
 private static NamespacedId dimension(){var level=Minecraft.getInstance().level;if(level==null)return null;if(level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD))return new NamespacedId("minecraft","overworld");if(level.dimension().equals(net.minecraft.world.level.Level.NETHER))return new NamespacedId("minecraft","the_nether");if(level.dimension().equals(net.minecraft.world.level.Level.END))return new NamespacedId("minecraft","the_end");return null;}
 private static boolean nativeOriginal(ShaderPack current,Path directory,Path selected)throws Exception{
  var declared=current.getShaderPackOptions().getOptionSet();var actual=effective(current);boolean lean="0".equals(actual.get(BslLeanGraph.ADAPTER));if(lean&&(!AstraMipLeanGraph.profile(actual)||net.irisshaders.iris.platform.IrisPlatformHelpers.getInstance().isModLoaded("distanthorizons")||net.irisshaders.iris.platform.IrisPlatformHelpers.getInstance().isModLoaded("voxy")))return false;var addition=Set.of("SHADERLOD_ASTRALEX_WORLD_GRAPH","SHADERLOD_ASTRALEX_DYNAMIC_GAUSSIAN","SHADERLOD_ASTRALEX_DEAD_MIP_CHAIN","SHADERLOD_ASTRALEX_DEPTH_PRIMING","SHADERLOD_ASTRALEX_AO_HALF_RES","SHADERLOD_RECEIVER_FOOTPRINT_BUDGET_R1_INCLUDED","SHADERLOD_ASTRALEX_PROJECTED_POM_INCLUDED","SHADERLOD_OPTIMIZATION_FLAGS_INCLUDED","SHADERLOD_REFINEMENT_ADAPTER","SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","SHADERLOD_REFINEMENT_BUDGET_INCLUDED","SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED");
  var catalog=SupportedPackCatalog.inventory().stream().filter(p->p.id().equals("astralex")).findFirst().orElseThrow();
  JsonObject marker;try(var archive=new java.util.zip.ZipFile(selected.toFile())){var entry=archive.getEntry("shaderlod-selection.json");if(entry==null||entry.getSize()<0||entry.getSize()>65536)return false;try(var input=archive.getInputStream(entry)){marker=JsonParser.parseString(new String(input.readNBytes(65537),StandardCharsets.UTF_8)).getAsJsonObject();}}
  String name=marker.get("originalFile").getAsString();if(name.contains("/")||name.contains("\\")||name.equals(".")||name.equals(".."))return false;var original=directory.resolve(name).normalize();if(!original.getParent().equals(directory)||Files.isSymbolicLink(original))return false;
  var fingerprint=PackSourceReader.read(original);if(!marker.get("originalSha256").getAsString().equals(fingerprint.sourceSha256())||!catalog.originalSourceDigest().equals(SupportedPackCatalog.shaderSourceDigest(fingerprint.memberSha256())))return false;
   java.nio.file.FileSystem zip=null;try{Path shaders;if(Files.isDirectory(original))shaders=original.resolve("shaders");else{zip=FileSystems.newFileSystem(original,Map.of());shaders=zip.getPath("/shaders");}
    // Actual native ShaderPack consumer and current GL/platform macros, never a stub or broad all-file parser.
    var nativePack=new ShaderPack(shaders,actual,net.irisshaders.iris.gl.shader.StandardMacros.createStandardEnvironmentDefines(),true);var nativeValues=effective(nativePack);var expectedKeys=new HashSet<>(nativeValues.keySet());expectedKeys.addAll(addition);if(!expectedKeys.equals(actual.keySet()))return false;
    var nativeSet=nativePack.getShaderPackOptions().getOptionSet();
    for(var key:nativeValues.keySet()){
     if(!nativeValues.get(key).equals(actual.get(key)))return false;
     if(nativeSet.getBooleanOptions().containsKey(key)){if(!declared.getBooleanOptions().containsKey(key)||nativeSet.getBooleanOptions().get(key).getOption().getDefaultValue()!=declared.getBooleanOptions().get(key).getOption().getDefaultValue())return false;}
     else if(!declared.getStringOptions().containsKey(key)||!nativeSet.getStringOptions().get(key).getOption().getAllowedValues().equals(declared.getStringOptions().get(key).getOption().getAllowedValues())||!nativeSet.getStringOptions().get(key).getOption().getDefaultValue().equals(declared.getStringOptions().get(key).getOption().getDefaultValue()))return false;
    }
    for(String key:addition){String value=actual.get(key);if(key.equals(CompileOptionControl.ASTRALEX_WORLD_GRAPH)||key.equals(CompileOptionControl.ASTRALEX_DYNAMIC_GAUSSIAN)||key.equals(CompileOptionControl.ASTRALEX_DEAD_MIP)||key.equals(CompileOptionControl.ASTRALEX_DEPTH)||key.equals("SHADERLOD_ASTRALEX_AO_HALF_RES")){if(!Set.of("0","1").contains(value))return false;var option=declared.getStringOptions().get(key);if(option==null||!option.getOption().getDefaultValue().equals("0")||!Set.copyOf(option.getOption().getAllowedValues()).equals(Set.of("0","1")))return false;}else if(!value.equals(key.equals("SHADERLOD_REFINEMENT_ADAPTER")?(lean?"0":"1"):"true"))return false;}
    return true;
   }finally{if(zip!=null)zip.close();}
 }
 public static boolean admitted(String family){try{
  if(PROOF==null)return reject(family,"proof resource missing/mismatched");String tuple=apiTuple();if(tuple==null)return reject(family,"unsupported actual Iris API; pending qualification");
  var dim=dimension();if(dim==null)return reject(family,"unknown/no world");boolean ow=dim.getName().equals("overworld");if(family.equals("bsl-shaders")&&!ow)return reject(family,"BSL dead mip requires actual Overworld");if(family.equals("photon")&&ow)return reject(family,"Overworld colortex11 chain is live");
  if(family.equals("bsl-shaders")&&!bslCompiler(tuple,net.irisshaders.iris.gl.shader.StandardMacros.getMcVersion(),net.irisshaders.iris.gl.shader.StandardMacros.getFormattedIrisVersion()))return reject(family,"actual BSL compiler macros do not consume the pinned source guard");
  if(Iris.isFallback())return reject(family,"Iris fallback");var pack=Iris.getCurrentPack().orElse(null);var pipeline=Iris.getPipelineManager().getPipelineNullable();if(pack==null||pipeline==null)return reject(family,"actual pack/pipeline unavailable");
  var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var name=Iris.getIrisConfig().getShaderPackName().orElse(null);if(name==null||!name.equals(Iris.getCurrentPackName()))return reject(family,"selected/loaded archive mismatch");var selected=dir.resolve(name).normalize();if(!selected.startsWith(dir)||selected.equals(dir)||Files.isSymbolicLink(selected))return reject(family,"selected source path refused");
  var values=effective(pack);if(!family.equals(PackSelectionService.verifyGeneratedRendererGraph(selected,values)))return reject(family,"normal generated source/state/resource guard refused");
  var fingerprint=PackSourceReader.read(selected);if(!PROOF.getAsJsonObject("families").getAsJsonObject(family).get("source").getAsString().equals(SupportedPackCatalog.shaderSourceDigest(fingerprint.memberSha256())))return reject(family,"pinned liveness source mismatch");
  // Native constructor is reached only after exact selected source/state/resource admission.
  if(!(family.equals("astralex")?nativeOriginal(pack,dir,selected):expectedOptions(family,values)))return reject(family,"full actual native/selected map mismatch or native-original proof unavailable");
  if(family.equals("bsl-shaders")&&"0".equals(values.get(BslLeanGraph.ADAPTER))&&(net.irisshaders.iris.platform.IrisPlatformHelpers.getInstance().isModLoaded("distanthorizons")||net.irisshaders.iris.platform.IrisPlatformHelpers.getInstance().isModLoaded("voxy")))return reject(family,"lean mip profiles require qualified native graph without DH/Voxy");
  if(family.equals("bsl-shaders")&&"0".equals(values.get(BslLeanGraph.ADAPTER))&&(!c6Capability(pack)||!expectedLeanOptions(values)))return reject(family,"lean graph requires normal C6 capability and exact full profile");
  if(family.equals("bsl-shaders")&&c6Capability(pack)&&!expectedC6Options(values)&&!expectedLeanOptions(values))return reject(family,"C6 experiment requires its full native map and default peer graphs");
  if(family.equals("bsl-shaders")&&(!"true".equals(values.get("TAA"))||!"true".equals(values.get("LIGHT_SHAFT"))||!"false".equals(values.get("RETRO_FILTER"))))return reject(family,"native BSL last-use graph inactive");
  var set=pack.getProgramSet(dim);int target=family.equals("photon")?11:1;var group=family.equals("photon")?ProgramArrayId.Deferred:ProgramArrayId.Composite;int index=family.equals("bsl-shaders")?7:family.equals("photon")?4:13;var programs=set.getComposite(group);if(programs.length<=index||programs[index]==null||!programs[index].isValid())return reject(family,"actual selected pass unavailable");
  String key=family.equals("photon")?CompileOptionControl.PHOTON_DEAD_MIP:family.equals("bsl-shaders")?CompileOptionControl.BSL_DEAD_MIP:CompileOptionControl.ASTRALEX_DEAD_MIP;boolean enabled="1".equals(values.get(key));if(programs[index].getDirectives().getMipmappedBuffers().contains(target)==enabled)return reject(family,"actual Iris dead-mip directive differs from selected compile option");
  if(family.equals("bsl-shaders")&&(programs[6]==null||programs[6].getDirectives().getMipmappedBuffers().contains(1)==(enabled&&c6Capability(pack))))return reject(family,"BSL composite6 request differs from scoped experiment capability");
  var settings=set.getPackDirectives().getRenderTargetDirectives().getRenderTargetSettings().get(target);if(settings==null||settings.shouldClear()==family.equals("photon"))return reject(family,"native clear/history descriptor changed");
  if(!pipeline.getClass().getName().equals("net.irisshaders.iris.pipeline.IrisRenderingPipeline"))return reject(family,"unknown pipeline implementation");var field=pipeline.getClass().getDeclaredField("renderTargets");field.setAccessible(true);var targets=(RenderTargets)field.get(pipeline);var storage=targets.get(target);if(storage==null||storage.getInternalFormat()!=settings.getInternalFormat()||storage.getMainTexture()==storage.getAltTexture()||storage.getWidth()<=0||storage.getHeight()<=0)return reject(family,"actual current/history storage mismatch");
  if(!resetState(storage)||!samplersNative(storage)||!texture(storage.getMainTexture(),storage)||!texture(storage.getAltTexture(),storage))return reject(family,"actual current/history base/LOD/filter/format/storage state refused");
  if(Iris.getCurrentPack().orElse(null)!=pack||Iris.getPipelineManager().getPipelineNullable()!=pipeline||!values.equals(effective(pack)))return reject(family,"pipeline/source/option epoch changed during setup");STATUS.put(family,"admitted actual source/API/full map/graph/current-history storage; setup only, speed unmeasured");return true;
 }catch(Exception|LinkageError unavailable){return reject(family,"pending: "+unavailable.getClass().getSimpleName()+": "+unavailable.getMessage());}}
 private static boolean resetState(RenderTarget target)throws Exception {for(var entry:PROOF.getAsJsonArray("api")){var row=entry.getAsJsonObject();if(row.get("tuple").getAsString().equals(API)&&row.get("mip_filter_model").getAsString().startsWith("dynamic")){for(String name:List.of("mipmapsOnMain","mipmapsOnAlt")){var field=target.getClass().getDeclaredField(name);if(field.getType()!=boolean.class)return false;field.setAccessible(true);if(field.getBoolean(target))return false;}break;}}return true;}
 private static boolean samplersNative(RenderTarget target){int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);try{int max=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);for(int unit=0;unit<max;unit++){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);if((texture==target.getMainTexture()||texture==target.getAltTexture())&&!sampler(GL11.glGetInteger(GL33.GL_SAMPLER_BINDING)))return false;}return true;}finally{GL13.glActiveTexture(active);}}
 private static boolean sampler(int id){if(id==0)return true;if(!GL33.glIsSampler(id))return false;return Set.of(GL11.GL_LINEAR,GL11.GL_LINEAR_MIPMAP_LINEAR).contains(GL33.glGetSamplerParameteri(id,GL11.GL_TEXTURE_MIN_FILTER))&&GL33.glGetSamplerParameteri(id,GL11.GL_TEXTURE_MAG_FILTER)==GL11.GL_LINEAR&&GL33.glGetSamplerParameteri(id,GL11.GL_TEXTURE_WRAP_S)==GL12.GL_CLAMP_TO_EDGE&&GL33.glGetSamplerParameteri(id,GL11.GL_TEXTURE_WRAP_T)==GL12.GL_CLAMP_TO_EDGE&&GL33.glGetSamplerParameterf(id,GL12.GL_TEXTURE_MIN_LOD)==-1000f&&GL33.glGetSamplerParameterf(id,GL12.GL_TEXTURE_MAX_LOD)==1000f&&GL33.glGetSamplerParameterf(id,GL14.GL_TEXTURE_LOD_BIAS)==0f&&(!(GL.getCapabilities().GL_EXT_texture_filter_anisotropic||GL.getCapabilities().GL_ARB_texture_filter_anisotropic)||GL33.glGetSamplerParameterf(id,0x84FE)==1f);}
 private static boolean texture(int id,RenderTarget expected){if(id<=0||!GL11.glIsTexture(id))return false;int prior=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);try{GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);int min=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER);return min==GL11.GL_LINEAR&&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER)==GL11.GL_LINEAR&&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL)==0&&GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,GL14.GL_TEXTURE_LOD_BIAS)==0f&&(!(GL.getCapabilities().GL_EXT_texture_filter_anisotropic||GL.getCapabilities().GL_ARB_texture_filter_anisotropic)||GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,0x84FE)==1f)&&GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MIN_LOD)==-1000f&&GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LOD)==1000f&&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S)==GL12.GL_CLAMP_TO_EDGE&&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T)==GL12.GL_CLAMP_TO_EDGE&&GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT)==expected.getInternalFormat().getGlFormat()&&GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)==expected.getWidth()&&GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)==expected.getHeight();}finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,prior);}}
}
